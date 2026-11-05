package dev.playerjoinnotifier.platform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * macOS: OS 標準の {@code osascript}（{@code display notification}）を呼ぶ。
 *
 * <p>macOS では AWT のトレイ通知が Minecraft の GLFW と同じプロセスで衝突しやすいため使わず、
 * 追加インストール不要の osascript を使う。シェルを経由せず引数リストで起動し、文字列は
 * AppleScript のリテラルとして安全になるようエスケープする。表示時間は macOS の設定に従う。
 *
 * <p>macOS 13 以降では、通知が「スクリプトエディタ」名義で表示される。表示されない場合は
 * システム設定 → 通知 → スクリプトエディタ で許可する。
 */
public final class MacNotificationBackend implements NotificationBackend {
	private static final int TIMEOUT_SECONDS = 5;

	@Override
	public String name() {
		return "macOS (osascript)";
	}

	@Override
	public void send(String title, String body, String icon, int timeoutMillis) throws Exception {
		ProcessBuilder pb = new ProcessBuilder(buildCommand(title, body));
		pb.redirectErrorStream(true);
		Process p = pb.start();
		try {
			if (!p.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
				throw new IOException("osascript did not finish within " + TIMEOUT_SECONDS + " seconds");
			}
			if (p.exitValue() != 0) {
				String out = new String(p.getInputStream().readNBytes(512), StandardCharsets.UTF_8).strip();
				throw new IOException("osascript exited with code " + p.exitValue() + ": " + out);
			}
		} finally {
			p.destroyForcibly();
		}
	}

	/** 実行するコマンド（テスト用に分離）。 */
	public static List<String> buildCommand(String title, String body) {
		String script = "display notification \"" + escape(body) + "\" with title \"" + escape(title) + "\"";
		return List.of("osascript", "-e", script);
	}

	/** AppleScript の文字列リテラル用エスケープ。制御文字は空白にする。 */
	public static String escape(String s) {
		if (s == null) {
			return "";
		}
		StringBuilder sb = new StringBuilder(s.length() + 8);
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '\\' || c == '"') {
				sb.append('\\').append(c);
			} else if (c < 0x20 || c == 0x7F) {
				sb.append(' ');
			} else {
				sb.append(c);
			}
		}
		return sb.toString();
	}
}
