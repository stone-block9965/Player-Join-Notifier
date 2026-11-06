import dev.playerjoinnotifier.core.JoinDetector;
import dev.playerjoinnotifier.core.NotificationText;
import dev.playerjoinnotifier.core.ServerAddress;
import dev.playerjoinnotifier.core.Translator;
import dev.playerjoinnotifier.dbus.DBusNotificationClient;
import dev.playerjoinnotifier.platform.MacNotificationBackend;
import dev.playerjoinnotifier.platform.OperatingSystem;
import dev.playerjoinnotifier.platform.WindowsNotificationBackend;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 依存なしの自己テスト。実行方法は README の「テスト」を参照。 */
public class CoreSelfTest {
	static int failures = 0;
	static int checks = 0;

	static void eq(String label, Object expected, Object actual) {
		checks++;
		if (!java.util.Objects.equals(expected, actual)) {
			failures++;
			System.out.println("FAIL " + label + "\n   expected: " + expected + "\n   actual:   " + actual);
		}
	}

	static String n(String s) {
		return ServerAddress.normalize(s).orElse(null);
	}

	public static void main(String[] args) {
		addressTests();
		detectorTests();
		busAddressTests();
		textTests();
		platformTests();
		translatorTests();
		System.out.println(checks + " checks, " + failures + " failures");
		System.exit(failures == 0 ? 0 : 1);
	}

	// ------------------------------------------------------------ アドレス

	static void addressTests() {
		eq("domain", "example.com:25565", n("example.com"));
		eq("domain upper", "example.com:25565", n("Example.COM"));
		eq("explicit default port", "example.com:25565", n("example.com:25565"));
		eq("custom port", "play.example.net:25566", n("play.example.net:25566"));
		eq("ipv4", "192.168.1.100:25565", n("192.168.1.100:25565"));
		eq("ipv4 no port", "192.168.1.100:25565", n("192.168.1.100"));
		eq("spaces around", "play.example.net:25566", n("  play.example.net : 25566  "));
		eq("trailing dot", "example.com:25565", n("example.com."));
		eq("tab/newline edges", "example.com:25565", n("\t example.com\n"));
		eq("ipv6 bracket no port", "[0:0:0:0:0:0:0:1]:25565", n("[::1]"));
		eq("ipv6 bracket port", "[0:0:0:0:0:0:0:1]:25566", n("[::1]:25566"));
		eq("ipv6 bare", "[0:0:0:0:0:0:0:1]:25565", n("::1"));
		eq("ipv6 forms equal", n("[2001:db8::1]:25565"), n("[2001:0DB8:0:0:0:0:0:1]"));
		eq("ipv6 zone dropped", n("[fe80::1]"), n("[fe80::1%eth0]"));
		eq("idn", "xn--bcher-kva.example:25565", n("bücher.example"));
		eq("underscore host", "my_server.local:25565", n("my_server.local"));

		eq("invalid empty", null, n(""));
		eq("invalid blank", null, n("   "));
		eq("invalid null", null, n(null));
		eq("invalid port text", null, n("example.com:abc"));
		eq("invalid port zero", null, n("example.com:0"));
		eq("invalid port big", null, n("example.com:65536"));
		eq("invalid empty port", null, n("example.com:"));
		eq("invalid ipv6 unclosed", null, n("[::1"));
		eq("invalid ipv6 junk after", null, n("[::1]x"));
		eq("invalid ipv6 garbage", null, n("[zzzz::1]"));
		eq("invalid inner space", null, n("exa mple.com"));
		eq("invalid empty label", null, n("a..b"));
		eq("invalid slash", null, n("example.com/path"));

		Set<String> wl = ServerAddress.normalizeAll(List.of("example.com", "play.example.net:25566",
				"192.168.1.100:25565", " [::1]:25567 ", "not valid", ""));
		eq("whitelist size (invalid dropped)", 4, wl.size());
		eq("match domain default", true, ServerAddress.matches(wl, "EXAMPLE.com"));
		eq("match domain explicit port", true, ServerAddress.matches(wl, "example.com:25565"));
		eq("different port is different server", false, ServerAddress.matches(wl, "example.com:25566"));
		eq("custom port match", true, ServerAddress.matches(wl, "play.example.net:25566"));
		eq("custom port, default is different", false, ServerAddress.matches(wl, "play.example.net"));
		eq("ip match", true, ServerAddress.matches(wl, "192.168.1.100"));
		eq("ip other host", false, ServerAddress.matches(wl, "192.168.1.101"));
		eq("ipv6 match", true, ServerAddress.matches(wl, "[::1]:25567"));
		eq("subdomain is NOT matched", false, ServerAddress.matches(wl, "evil.example.com"));
		eq("suffix trick not matched", false, ServerAddress.matches(wl, "example.com.evil.net"));
		eq("prefix trick not matched", false, ServerAddress.matches(wl, "notexample.com"));
		eq("null server", false, ServerAddress.matches(wl, null));
		eq("empty whitelist", false, ServerAddress.matches(Set.of(), "example.com"));
	}

	// ------------------------------------------------------------ 参加検知

	static final UUID ME = UUID.fromString("00000000-0000-0000-0000-00000000000a");
	static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
	static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
	static final UUID CAROL = UUID.fromString("00000000-0000-0000-0000-0000000000c3");

	@SafeVarargs
	static Map<UUID, String> list(Map.Entry<UUID, String>... es) {
		Map<UUID, String> m = new LinkedHashMap<>();
		for (var e : es) {
			m.put(e.getKey(), e.getValue());
		}
		return m;
	}

	static Map.Entry<UUID, String> p(UUID id, String name) {
		return Map.entry(id, name);
	}

	static List<String> names(List<JoinDetector.Joined> l) {
		return l.stream().map(JoinDetector.Joined::name).toList();
	}

	static void detectorTests() {
		// 接続直後: 既存プレイヤー（Alice, Bob）が段階的に届いても通知されない
		JoinDetector d = new JoinDetector(40, 60);
		eq("t1 empty list", List.of(), d.update(list(), ME));
		eq("t2 self only", List.of(), d.update(list(p(ME, "me")), ME));
		eq("t3 alice arrives during sync", List.of(), d.update(list(p(ME, "me"), p(ALICE, "Alice")), ME));
		eq("t4 bob arrives during sync", List.of(),
				d.update(list(p(ME, "me"), p(ALICE, "Alice"), p(BOB, "Bob")), ME));
		for (int i = 0; i < 60; i++) {
			eq("steady state tick " + i + " no notifications", List.of(),
					d.update(list(p(ME, "me"), p(ALICE, "Alice"), p(BOB, "Bob")), ME));
		}
		eq("ready after grace", true, d.isReady());

		// 新規参加: Carol だけ通知、1 回だけ
		var withCarol = list(p(ME, "me"), p(ALICE, "Alice"), p(BOB, "Bob"), p(CAROL, "Carol"));
		eq("carol joins", List.of("Carol"), names(d.update(withCarol, ME)));
		eq("carol again (dup)", List.of(), names(d.update(withCarol, ME)));
		for (int i = 0; i < 100; i++) {
			d.update(withCarol, ME);
		}

		// Ping/表示名更新に相当する「同じ UUID の中身だけ変化」は無視（名前が変わっても通知しない）
		var renamed = list(p(ME, "me"), p(ALICE, "Alice"), p(BOB, "Bob"), p(CAROL, "Carol[AFK]"));
		eq("profile/display update ignored", List.of(), names(d.update(renamed, ME)));

		// 退出 → 再参加（抑制窓を過ぎた後）なら再通知
		var withoutCarol = list(p(ME, "me"), p(ALICE, "Alice"), p(BOB, "Bob"));
		eq("carol leaves", List.of(), names(d.update(withoutCarol, ME)));
		for (int i = 0; i < 100; i++) {
			d.update(withoutCarol, ME);
		}
		eq("carol rejoins -> notified again", List.of("Carol"), names(d.update(withCarol, ME)));

		// 自分自身の（遅れた）出現は通知しない
		JoinDetector d2 = new JoinDetector(5, 0);
		eq("d2 alice only", List.of(), d2.update(list(p(ALICE, "Alice")), ME));
		for (int i = 0; i < 10; i++) {
			d2.update(list(p(ALICE, "Alice")), ME);
		}
		eq("self appearing late is not notified", List.of(), names(d2.update(list(p(ALICE, "Alice"), p(ME, "me")), ME)));

		// 同一ティックの複数参加
		var two = list(p(ALICE, "Alice"), p(ME, "me"), p(BOB, "Bob"), p(CAROL, "Carol"));
		eq("two join at once", List.of("Bob", "Carol"), names(d2.update(two, ME)));

		// remove→add の瞬間的な作り直しは抑制窓で 1 回に抑える
		JoinDetector d3 = new JoinDetector(2, 60);
		for (int i = 0; i < 5; i++) {
			d3.update(list(p(ME, "me"), p(ALICE, "Alice")), ME);
		}
		eq("bob joins", List.of("Bob"), names(d3.update(list(p(ME, "me"), p(ALICE, "Alice"), p(BOB, "Bob")), ME)));
		d3.update(list(p(ME, "me"), p(ALICE, "Alice")), ME); // 一瞬消える
		eq("tab list rebuild within suppress window -> no duplicate", List.of(),
				names(d3.update(list(p(ME, "me"), p(ALICE, "Alice"), p(BOB, "Bob")), ME)));

		// reset 後は再び基準線から（サーバー切替・再接続）
		d.reset();
		eq("after reset not ready", false, d.isReady());
		eq("after reset existing players are baseline", List.of(),
				d.update(list(p(ME, "me"), p(ALICE, "Alice"), p(BOB, "Bob"), p(CAROL, "Carol")), ME));

		// リストが一度も来ないサーバーでも、いずれ基準線が確定して後から通知できる
		JoinDetector d4 = new JoinDetector(40, 0);
		for (int i = 0; i < 210; i++) {
			d4.update(list(), ME);
		}
		eq("gave up waiting -> ready", true, d4.isReady());
		eq("join after giving up is notified", List.of("Alice"), names(d4.update(list(p(ALICE, "Alice")), ME)));
	}

	// ------------------------------------------------------------ バスアドレス

	static void busAddressTests() {
		eq("addr path", Path.of("/run/user/1000/bus"),
				DBusNotificationClient.socketFromAddress("unix:path=/run/user/1000/bus", null));
		eq("addr path+guid", Path.of("/run/user/1000/bus"),
				DBusNotificationClient.socketFromAddress("unix:path=/run/user/1000/bus,guid=abcdef", null));
		eq("addr multiple", Path.of("/tmp/b"),
				DBusNotificationClient.socketFromAddress("unix:abstract=/tmp/x;unix:path=/tmp/b", null));
		eq("addr abstract only unsupported", null,
				DBusNotificationClient.socketFromAddress("unix:abstract=/tmp/dbus-xyz,guid=1", null));
		eq("addr runtime", Path.of("/run/user/1000/bus"),
				DBusNotificationClient.socketFromAddress("unix:runtime=yes", "/run/user/1000"));
		eq("addr percent", Path.of("/tmp/a b/bus"),
				DBusNotificationClient.socketFromAddress("unix:path=/tmp/a%20b/bus", null));
		eq("addr tcp ignored", null, DBusNotificationClient.socketFromAddress("tcp:host=localhost,port=1", null));
	}

	// ------------------------------------------------------------ 通知文面

	static void textTests() {
		eq("body template", "Steve がサーバーに参加しました。",
				NotificationText.body("{player} がサーバーに参加しました。", "Steve"));
		eq("body escapes markup", "&lt;b&gt;x&lt;/b&gt; &amp; y", NotificationText.body("{player}", "<b>x</b> & y"));
		eq("body strips control chars", "ab", NotificationText.body("{player}", "a\u0007\nb"));
		eq("body caps name length", 64, NotificationText.body("{player}", "x".repeat(500)).length());
		eq("body null name", "", NotificationText.body("{player}", null));
	}

	// ------------------------------------------------------------ OS 別の通知

	static void platformTests() {
		eq("os windows 11", OperatingSystem.WINDOWS, OperatingSystem.detect("Windows 11"));
		eq("os windows 10", OperatingSystem.WINDOWS, OperatingSystem.detect("Windows 10"));
		eq("os mac", OperatingSystem.MACOS, OperatingSystem.detect("Mac OS X"));
		eq("os darwin (contains 'win')", OperatingSystem.MACOS, OperatingSystem.detect("Darwin"));
		eq("os linux", OperatingSystem.LINUX, OperatingSystem.detect("Linux"));
		eq("os freebsd falls back to dbus", OperatingSystem.LINUX, OperatingSystem.detect("FreeBSD"));
		eq("os null", OperatingSystem.LINUX, OperatingSystem.detect(null));

		eq("mac command", List.of("osascript", "-e",
				"display notification \"Steve がサーバーに参加しました。\" with title \"Minecraft\""),
				MacNotificationBackend.buildCommand("Minecraft", "Steve がサーバーに参加しました。"));
		eq("mac escape quote/backslash", "a\\\"b\\\\c", MacNotificationBackend.escape("a\"b\\c"));
		eq("mac escape control chars", "a b c", MacNotificationBackend.escape("a\nb\tc"));
		eq("mac injection stays inside literal",
				"display notification \"x\\\" & (do shell script \\\"id\\\") & \\\"\" with title \"t\"",
				MacNotificationBackend.buildCommand("t", "x\" & (do shell script \"id\") & \"").get(2));

		eq("win truncate short", "abc", WindowsNotificationBackend.truncate("abc", 63));
		eq("win truncate long", 63, WindowsNotificationBackend.truncate("x".repeat(300), 63).length());
		String emoji = "a".repeat(61) + "\uD83D\uDE00\uD83D\uDE00";
		eq("win truncate keeps surrogate pairs",
				true, !Character.isHighSurrogate(WindowsNotificationBackend.truncate(emoji, 63).charAt(
						WindowsNotificationBackend.truncate(emoji, 63).length() - 2)));
	}

	// ------------------------------------------------------------ 翻訳（言語切替）

	static void translatorTests() {
		eq("resolve auto jpn_JP", "ja", Translator.resolve("auto", "jpn_JP"));
		eq("resolve auto ja_jp", "ja", Translator.resolve("auto", "ja_jp"));
		eq("resolve auto en_us", "en", Translator.resolve("auto", "en_us"));
		eq("resolve auto eng_GB", "en", Translator.resolve("auto", "eng_GB"));
		eq("resolve auto other language falls back to en", "en", Translator.resolve("auto", "fra_FR"));
		eq("resolve auto null", "en", Translator.resolve("auto", null));
		eq("resolve explicit ja beats game language", "ja", Translator.resolve("ja", "en_us"));
		eq("resolve explicit en beats game language", "en", Translator.resolve("en", "jpn_JP"));
		eq("resolve unknown setting behaves like auto", "ja", Translator.resolve("xx", "jpn_JP"));

		eq("setting auto", "auto", Translator.normalizeSetting("auto"));
		eq("setting upper/space", "ja", Translator.normalizeSetting("  JA "));
		eq("setting invalid", null, Translator.normalizeSetting("fr"));
		eq("setting null", null, Translator.normalizeSetting(null));

		Map<String, String> parsed = Translator.parseFlatJson(
				"{ \"a\": \"x\\ny\", \"b\": \"\\u65e5\\u672c \\\"q\\\"\", \"c\": \"\" }");
		eq("json escapes", "x\ny", parsed.get("a"));
		eq("json unicode+quote", "日本 \"q\"", parsed.get("b"));
		eq("json empty value", "", parsed.get("c"));
		eq("json empty object", 0, Translator.parseFlatJson("{}").size());

		Translator t = new Translator();
		String p = "player_join_notifier.";
		eq("en body", "{player} joined the server.", t.get("en", p + "notification.body"));
		eq("ja body", "{player} がサーバーに参加しました。", t.get("ja", p + "notification.body"));
		eq("format args", "Added: example.com", t.get("en", p + "command.add.added", "example.com"));
		eq("unknown key returns key", "no.such.key", t.get("ja", "no.such.key"));
		eq("unknown language falls back to en", "{player} joined the server.", t.get("fr", p + "notification.body"));

		// ja と en のキーが一致し、%s の個数も一致する
		Map<String, String> en = t.table("en");
		Map<String, String> ja = t.table("ja");
		eq("lang files loaded", true, en.size() > 20 && ja.size() > 20);
		eq("same key set", en.keySet(), ja.keySet());
		for (String key : en.keySet()) {
			eq("placeholder count " + key, countOf(en.get(key), "%s"), countOf(ja.get(key), "%s"));
		}

		// ソース中で使っているキーが全て翻訳ファイルにある（タイプミス検出）
		try (var files = java.nio.file.Files.walk(Path.of("src/main/java"))) {
			java.util.regex.Pattern pat = java.util.regex.Pattern.compile("\"((?:command|gui|notification)\\.[a-z_.]+)\"");
			for (Path f : (Iterable<Path>) files.filter(x -> x.toString().endsWith(".java"))::iterator) {
				var m = pat.matcher(java.nio.file.Files.readString(f));
				while (m.find()) {
					eq("key exists: " + m.group(1) + " (" + f.getFileName() + ")", true, en.containsKey(p + m.group(1)));
				}
			}
		} catch (java.io.IOException e) {
			throw new RuntimeException(e);
		}
	}

	static int countOf(String s, String sub) {
		int n = 0;
		for (int i = s.indexOf(sub); i >= 0; i = s.indexOf(sub, i + sub.length())) {
			n++;
		}
		return n;
	}
}
