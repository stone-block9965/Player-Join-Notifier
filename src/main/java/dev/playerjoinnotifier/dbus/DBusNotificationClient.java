package dev.playerjoinnotifier.dbus;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 依存ライブラリ・外部コマンドなしで {@code org.freedesktop.Notifications.Notify} を呼ぶ最小の D-Bus クライアント。
 *
 * <p>セッションバスの Unix ドメインソケットに直接接続し、EXTERNAL 認証 → Hello → Notify の順に送る。
 * 1 回の通知ごとに接続を開いて閉じる（バス再起動・セッション切替に強く、状態を持たない）。
 * 全体に 5 秒のウォッチドッグがあり、バスが応答しなくても呼び出し元スレッドが固まり続けない。
 *
 * <p>制限: {@code unix:abstract=...} 形式のバスアドレスは Java が扱えないため非対応
 * （systemd 系ディストリビューションの標準である {@code unix:path=...} は対応）。
 */
public final class DBusNotificationClient {
	private static final int TIMEOUT_SECONDS = 5;
	private static final ScheduledExecutorService WATCHDOG = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "PlayerJoinNotifier-DBus-Watchdog");
		t.setDaemon(true);
		return t;
	});

	private static final byte TYPE_METHOD_RETURN = 2;
	private static final byte TYPE_ERROR = 3;

	private DBusNotificationClient() {
	}

	/** セッションバスを自動検出して通知する。 */
	public static void notify(String appName, String icon, String summary, String body, int timeoutMillis)
			throws IOException {
		notify(findSessionBusSocket(), appName, icon, summary, body, timeoutMillis);
	}

	/**
	 * 指定のバスソケットに通知を送る。
	 *
	 * @param timeoutMillis 表示時間（ミリ秒）。-1 でサーバー既定、0 で消えない。
	 * @throws IOException バスに接続できない、認証失敗、Notify がエラーを返した等
	 */
	public static void notify(Path busSocket, String appName, String icon, String summary, String body,
			int timeoutMillis) throws IOException {
		try (SocketChannel ch = SocketChannel.open(StandardProtocolFamily.UNIX)) {
			ScheduledFuture<?> watchdog = WATCHDOG.schedule(() -> closeQuietly(ch), TIMEOUT_SECONDS, TimeUnit.SECONDS);
			try {
				ch.connect(UnixDomainSocketAddress.of(busSocket));
				InputStream in = Channels.newInputStream(ch);
				OutputStream out = Channels.newOutputStream(ch);

				authenticate(in, out);

				// Hello（これを最初に呼ばないとバスに切断される）
				send(out, methodCall(1, "/org/freedesktop/DBus", "org.freedesktop.DBus", "org.freedesktop.DBus",
						"Hello", "", new byte[0]));
				awaitReply(in, 1);

				// Notify(susssasa{sv}i)
				Marshaller b = new Marshaller();
				b.string(appName);          // app_name
				b.uint32(0);                // replaces_id
				b.string(icon);             // app_icon
				b.string(summary);          // summary
				b.string(body);             // body
				b.uint32(0);                // actions: as（空）
				b.uint32(0);                // hints: a{sv}（空）
				b.align(8);                 // 空配列でも要素アライメント（辞書エントリ=8）までパディングが必要
				b.int32(timeoutMillis);     // expire_timeout
				send(out, methodCall(2, "/org/freedesktop/Notifications", "org.freedesktop.Notifications",
						"org.freedesktop.Notifications", "Notify", "susssasa{sv}i", b.toByteArray()));
				awaitReply(in, 2);
			} finally {
				watchdog.cancel(false);
			}
		}
	}

	// ---------------------------------------------------------------- バスアドレスの検出

	/** DBUS_SESSION_BUS_ADDRESS → $XDG_RUNTIME_DIR/bus → /run/user/UID/bus の順で探す。 */
	public static Path findSessionBusSocket() throws IOException {
		String env = System.getenv("DBUS_SESSION_BUS_ADDRESS");
		if (env != null && !env.isBlank()) {
			Path p = socketFromAddress(env, System.getenv("XDG_RUNTIME_DIR"));
			if (p != null) {
				return p;
			}
		}
		String runtime = System.getenv("XDG_RUNTIME_DIR");
		if (runtime != null && !runtime.isBlank()) {
			Path p = Path.of(runtime, "bus");
			if (Files.exists(p)) {
				return p;
			}
		}
		Path p = Path.of("/run/user/" + currentUid() + "/bus");
		if (Files.exists(p)) {
			return p;
		}
		throw new IOException("D-Bus session bus not found (DBUS_SESSION_BUS_ADDRESS is unset, or it is an abstract socket)");
	}

	/** "unix:path=/run/user/1000/bus,guid=..." のような（;区切り可）アドレス文字列から path を取り出す。 */
	public static Path socketFromAddress(String address, String runtimeDir) {
		for (String entry : address.split(";")) {
			entry = entry.strip();
			if (!entry.startsWith("unix:")) {
				continue;
			}
			String path = null;
			boolean runtime = false;
			for (String kv : entry.substring(5).split(",")) {
				int eq = kv.indexOf('=');
				if (eq < 0) {
					continue;
				}
				String key = kv.substring(0, eq);
				String val = percentDecode(kv.substring(eq + 1));
				if (key.equals("path")) {
					path = val;
				} else if (key.equals("runtime") && val.equals("yes")) {
					runtime = true;
				}
			}
			if (path != null) {
				return Path.of(path);
			}
			if (runtime && runtimeDir != null && !runtimeDir.isBlank()) {
				return Path.of(runtimeDir, "bus");
			}
		}
		return null;
	}

	private static String percentDecode(String s) {
		if (s.indexOf('%') < 0) {
			return s;
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '%' && i + 2 < s.length() && isHex(s.charAt(i + 1)) && isHex(s.charAt(i + 2))) {
				out.write(Integer.parseInt(s.substring(i + 1, i + 3), 16));
				i += 2;
			} else {
				byte[] b = String.valueOf(c).getBytes(StandardCharsets.UTF_8);
				out.write(b, 0, b.length);
			}
		}
		return out.toString(StandardCharsets.UTF_8);
	}

	private static boolean isHex(char c) {
		return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
	}

	static long currentUid() throws IOException {
		List<String> lines = Files.readAllLines(Path.of("/proc/self/status"));
		for (String line : lines) {
			if (line.startsWith("Uid:")) {
				String[] parts = line.substring(4).strip().split("\\s+");
				try {
					return Long.parseLong(parts[0]);
				} catch (NumberFormatException e) {
					break;
				}
			}
		}
		throw new IOException("Cannot determine the UID (/proc/self/status)");
	}

	// ---------------------------------------------------------------- 認証

	private static void authenticate(InputStream in, OutputStream out) throws IOException {
		String uid = Long.toString(currentUid());
		StringBuilder hex = new StringBuilder();
		for (byte b : uid.getBytes(StandardCharsets.US_ASCII)) {
			hex.append(String.format("%02x", b));
		}
		out.write(("\0AUTH EXTERNAL " + hex + "\r\n").getBytes(StandardCharsets.US_ASCII));
		out.flush();
		String line = readLine(in);
		if (!line.startsWith("OK")) {
			throw new IOException("D-Bus authentication failed: " + line);
		}
		out.write("BEGIN\r\n".getBytes(StandardCharsets.US_ASCII));
		out.flush();
	}

	private static String readLine(InputStream in) throws IOException {
		StringBuilder sb = new StringBuilder();
		while (sb.length() < 1024) {
			int c = in.read();
			if (c < 0) {
				throw new IOException("Connection closed during D-Bus authentication");
			}
			if (c == '\n') {
				return sb.toString().strip();
			}
			sb.append((char) c);
		}
		throw new IOException("Invalid D-Bus authentication response");
	}

	// ---------------------------------------------------------------- メッセージ組み立て

	/** リトルエンディアンの D-Bus ワイヤ形式を組み立てる小さなバッファ。 */
	private static final class Marshaller {
		private final ByteArrayOutputStream out = new ByteArrayOutputStream();

		int size() {
			return out.size();
		}

		void align(int n) {
			while (out.size() % n != 0) {
				out.write(0);
			}
		}

		void u8(int v) {
			out.write(v);
		}

		void uint32(long v) {
			align(4);
			out.write((int) (v & 0xff));
			out.write((int) ((v >> 8) & 0xff));
			out.write((int) ((v >> 16) & 0xff));
			out.write((int) ((v >> 24) & 0xff));
		}

		void int32(int v) {
			uint32(v & 0xffffffffL);
		}

		/** STRING / OBJECT_PATH: uint32 長 + UTF-8 + NUL */
		void string(String s) {
			byte[] b = s.getBytes(StandardCharsets.UTF_8);
			uint32(b.length);
			out.write(b, 0, b.length);
			out.write(0);
		}

		/** SIGNATURE: uint8 長 + ASCII + NUL */
		void signature(String s) {
			byte[] b = s.getBytes(StandardCharsets.US_ASCII);
			out.write(b.length);
			out.write(b, 0, b.length);
			out.write(0);
		}

		void raw(byte[] b) {
			out.write(b, 0, b.length);
		}

		byte[] toByteArray() {
			return out.toByteArray();
		}
	}

	/**
	 * METHOD_CALL メッセージ全体を作る。
	 * ヘッダフィールド配列はメッセージ先頭からのオフセットが 16（8 の倍数）で始まるため、
	 * 別バッファで作ってもアライメントは一致する。
	 */
	private static byte[] methodCall(int serial, String path, String destination, String iface, String member,
			String signature, byte[] body) {
		Marshaller fields = new Marshaller();
		field(fields, 1, 'o', path);
		field(fields, 6, 's', destination);
		field(fields, 2, 's', iface);
		field(fields, 3, 's', member);
		if (!signature.isEmpty()) {
			field(fields, 8, 'g', signature);
		}

		Marshaller msg = new Marshaller();
		msg.u8('l');                 // リトルエンディアン
		msg.u8(1);                   // METHOD_CALL
		msg.u8(0);                   // flags
		msg.u8(1);                   // プロトコルバージョン
		msg.uint32(body.length);
		msg.uint32(serial);
		msg.uint32(fields.size());   // ヘッダフィールド配列の長さ
		msg.raw(fields.toByteArray());
		msg.align(8);                // ヘッダは 8 バイト境界まで（長さには含まない）
		msg.raw(body);
		return msg.toByteArray();
	}

	private static void field(Marshaller m, int code, char type, String value) {
		m.align(8);                  // STRUCT は 8 バイト境界
		m.u8(code);
		m.signature(String.valueOf(type)); // VARIANT: シグネチャ + 値
		if (type == 'g') {
			m.signature(value);
		} else {
			m.string(value);
		}
	}

	private static void send(OutputStream out, byte[] message) throws IOException {
		out.write(message);
		out.flush();
	}

	// ---------------------------------------------------------------- 応答の読み取り

	private static void awaitReply(InputStream in, long expectedSerial) throws IOException {
		// NameAcquired などのシグナルが先に届くことがあるので、対応する返信が来るまで読み飛ばす
		for (int i = 0; i < 32; i++) {
			byte[] fixed = readFully(in, 16);
			ByteOrder order = fixed[0] == 'B' ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
			ByteBuffer fb = ByteBuffer.wrap(fixed).order(order);
			byte type = fixed[1];
			int bodyLen = fb.getInt(4);
			int fieldsLen = fb.getInt(12);
			if (bodyLen < 0 || fieldsLen < 0 || bodyLen > (1 << 20) || fieldsLen > (1 << 16)) {
				throw new IOException("Invalid D-Bus message");
			}
			byte[] fields = readFully(in, fieldsLen);
			int pad = (8 - ((16 + fieldsLen) % 8)) % 8;
			readFully(in, pad);
			byte[] body = readFully(in, bodyLen);

			long replySerial = -1;
			String errorName = null;
			ByteBuffer f = ByteBuffer.wrap(fields).order(order);
			int pos = 0;
			parse:
			while (pos < fields.length) {
				pos = alignUp(pos, 8);
				if (pos + 2 > fields.length) {
					break;
				}
				int code = fields[pos++] & 0xff;
				int sigLen = fields[pos++] & 0xff;
				if (sigLen < 1 || pos + sigLen + 1 > fields.length) {
					break;
				}
				char t = (char) fields[pos];
				pos += sigLen + 1;
				switch (t) {
					case 'u' -> {
						pos = alignUp(pos, 4);
						if (pos + 4 > fields.length) {
							break parse;
						}
						if (code == 5) {
							replySerial = f.getInt(pos) & 0xffffffffL;
						}
						pos += 4;
					}
					case 's', 'o' -> {
						pos = alignUp(pos, 4);
						if (pos + 4 > fields.length) {
							break parse;
						}
						int len = f.getInt(pos);
						pos += 4;
						if (len < 0 || pos + len + 1 > fields.length) {
							break parse;
						}
						if (code == 4) {
							errorName = new String(fields, pos, len, StandardCharsets.UTF_8);
						}
						pos += len + 1;
					}
					case 'g' -> {
						if (pos >= fields.length) {
							break parse;
						}
						int len = fields[pos] & 0xff;
						pos += 1 + len + 1;
					}
					default -> {
						break parse; // 未知の型は読み飛ばせないので打ち切る
					}
				}
			}

			if ((type == TYPE_METHOD_RETURN || type == TYPE_ERROR) && replySerial == expectedSerial) {
				if (type == TYPE_ERROR) {
					String message = firstString(body, order);
					throw new IOException("D-Bus error: " + errorName + (message.isEmpty() ? "" : " (" + message + ")"));
				}
				return;
			}
		}
		throw new IOException("No D-Bus reply received");
	}

	private static String firstString(byte[] body, ByteOrder order) {
		if (body.length < 5) {
			return "";
		}
		int len = ByteBuffer.wrap(body).order(order).getInt(0);
		if (len < 0 || 4 + len > body.length) {
			return "";
		}
		return new String(body, 4, len, StandardCharsets.UTF_8);
	}

	private static int alignUp(int pos, int n) {
		return (pos + n - 1) / n * n;
	}

	private static byte[] readFully(InputStream in, int n) throws IOException {
		byte[] buf = new byte[n];
		int off = 0;
		while (off < n) {
			int r = in.read(buf, off, n - off);
			if (r < 0) {
				throw new IOException("D-Bus connection closed unexpectedly");
			}
			off += r;
		}
		return buf;
	}

	private static void closeQuietly(SocketChannel ch) {
		try {
			ch.close();
		} catch (IOException ignored) {
			// 閉じるだけなので無視
		}
	}
}
