package dev.playerjoinnotifier.core;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.IDN;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * サーバーアドレスの正規化と照合。
 *
 * <p>正規化後の形式は {@code host:port}（IPv6 は {@code [addr]:port}）。
 * ポート省略時は 25565 として扱う。DNS 解決は一切行わない（リテラル解析のみ）。
 */
public final class ServerAddress {
	public static final int DEFAULT_PORT = 25565;

	private ServerAddress() {
	}

	/** 正規化できない（不正な）入力の場合は empty。 */
	public static Optional<String> normalize(String raw) {
		if (raw == null) {
			return Optional.empty();
		}
		String s = raw.strip();
		if (s.isEmpty()) {
			return Optional.empty();
		}
		// ':' '[' ']' の前後の空白は許容して詰める。それ以外の内部空白は不正。
		s = s.replaceAll("\\s*([:\\[\\]])\\s*", "$1");
		for (int i = 0; i < s.length(); i++) {
			if (Character.isWhitespace(s.charAt(i))) {
				return Optional.empty();
			}
		}

		String host;
		String portStr = null;
		if (s.startsWith("[")) {
			int end = s.indexOf(']');
			if (end < 0) {
				return Optional.empty();
			}
			host = s.substring(1, end);
			String rest = s.substring(end + 1);
			if (!rest.isEmpty()) {
				if (!rest.startsWith(":")) {
					return Optional.empty();
				}
				portStr = rest.substring(1);
			}
		} else {
			int first = s.indexOf(':');
			if (first < 0) {
				host = s;
			} else if (first == s.lastIndexOf(':')) {
				host = s.substring(0, first);
				portStr = s.substring(first + 1);
			} else {
				// コロンが複数: 角括弧なしの IPv6 リテラル（ポートなし）
				host = s;
			}
		}

		int port = DEFAULT_PORT;
		if (portStr != null) {
			if (portStr.isEmpty() || portStr.length() > 5) {
				return Optional.empty();
			}
			for (int i = 0; i < portStr.length(); i++) {
				char c = portStr.charAt(i);
				if (c < '0' || c > '9') {
					return Optional.empty();
				}
			}
			port = Integer.parseInt(portStr);
			if (port < 1 || port > 65535) {
				return Optional.empty();
			}
		}

		boolean v6 = host.indexOf(':') >= 0;
		Optional<String> canonical = v6 ? canonicalIpv6(host) : canonicalHostname(host);
		if (canonical.isEmpty()) {
			return Optional.empty();
		}
		String h = canonical.get();
		// IPv4-mapped IPv6 は IPv4 として返ってくるので、コロンの有無で括弧を決める
		return Optional.of(h.indexOf(':') >= 0 ? "[" + h + "]:" + port : h + ":" + port);
	}

	private static Optional<String> canonicalHostname(String host) {
		while (host.endsWith(".")) {
			host = host.substring(0, host.length() - 1);
		}
		if (host.isEmpty()) {
			return Optional.empty();
		}
		try {
			host = IDN.toASCII(host.toLowerCase(Locale.ROOT)).toLowerCase(Locale.ROOT);
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
		if (host.isEmpty() || host.length() > 253) {
			return Optional.empty();
		}
		for (String label : host.split("\\.", -1)) {
			if (label.isEmpty() || label.length() > 63) {
				return Optional.empty();
			}
			for (int i = 0; i < label.length(); i++) {
				char c = label.charAt(i);
				boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_';
				if (!ok) {
					return Optional.empty();
				}
			}
		}
		return Optional.of(host);
	}

	private static Optional<String> canonicalIpv6(String host) {
		int zone = host.indexOf('%');
		if (zone >= 0) {
			host = host.substring(0, zone);
		}
		if (host.isEmpty()) {
			return Optional.empty();
		}
		for (int i = 0; i < host.length(); i++) {
			char c = host.charAt(i);
			boolean ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')
					|| c == ':' || c == '.';
			if (!ok) {
				return Optional.empty();
			}
		}
		try {
			// ':' を含むリテラルのみを渡すので DNS 問い合わせは発生しない
			InetAddress addr = InetAddress.getByName(host);
			String text = addr.getHostAddress();
			int z = text.indexOf('%');
			if (z >= 0) {
				text = text.substring(0, z);
			}
			if (addr instanceof Inet4Address) {
				return Optional.of(text);
			}
			return Optional.of(text.toLowerCase(Locale.ROOT));
		} catch (UnknownHostException e) {
			return Optional.empty();
		}
	}

	/** 複数の生アドレスを正規化し、不正なものは除外して返す。 */
	public static Set<String> normalizeAll(Collection<String> raws) {
		Set<String> out = new LinkedHashSet<>();
		if (raws != null) {
			for (String raw : raws) {
				normalize(raw).ifPresent(out::add);
			}
		}
		return out;
	}

	/** 接続先アドレス（サーバー一覧に入力した文字列）が正規化済みホワイトリストに含まれるか。 */
	public static boolean matches(Set<String> normalizedWhitelist, String connectedAddress) {
		if (normalizedWhitelist == null || normalizedWhitelist.isEmpty()) {
			return false;
		}
		return normalize(connectedAddress).map(normalizedWhitelist::contains).orElse(false);
	}
}
