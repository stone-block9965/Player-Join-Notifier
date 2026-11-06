package dev.playerjoinnotifier.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Minimal translator for the mod's own strings. It reads the same flat JSON files that Minecraft uses
 * ({@code assets/player_join_notifier/lang/<lang>.json}) but is independent of the game's language
 * setting, so the user can override the language from the config. No dependencies.
 */
public final class Translator {
	/** Languages the user can choose. "auto" follows the game language. */
	public static final List<String> SETTINGS = List.of("auto", "en", "ja");
	public static final String FALLBACK = "en";

	private static final String BASE = "/assets/player_join_notifier/lang/";

	private final Map<String, Map<String, String>> tables = new HashMap<>();

	/** Resolves a config value + the game's language code to a concrete language ("en" or "ja"). */
	public static String resolve(String setting, String gameLanguageCode) {
		if ("en".equals(setting) || "ja".equals(setting)) {
			return setting;
		}
		String code = gameLanguageCode == null ? "" : gameLanguageCode.toLowerCase(Locale.ROOT);
		// Minecraft reports Japanese as "jpn_JP" (ISO 639-2) in its language.code value; accept "ja_jp" too
		return (code.startsWith("jpn") || code.startsWith("ja")) ? "ja" : FALLBACK;
	}

	/** Normalizes a user-entered setting ("auto", "en", "ja", case-insensitive); null if unknown. */
	public static String normalizeSetting(String raw) {
		if (raw == null) {
			return null;
		}
		String s = raw.strip().toLowerCase(Locale.ROOT);
		return SETTINGS.contains(s) ? s : null;
	}

	/** Looks up {@code key} in {@code lang} (falls back to English, then to the key itself) and formats it. */
	public String get(String lang, String key, Object... args) {
		String template = table(lang).get(key);
		if (template == null) {
			template = table(FALLBACK).get(key);
		}
		if (template == null) {
			return key;
		}
		if (args == null || args.length == 0) {
			return template;
		}
		try {
			return String.format(Locale.ROOT, template, args);
		} catch (java.util.IllegalFormatException e) {
			return template;
		}
	}

	public Map<String, String> table(String lang) {
		return tables.computeIfAbsent(lang, Translator::load);
	}

	private static Map<String, String> load(String lang) {
		String file = switch (lang) {
			case "ja" -> "ja_jp.json";
			default -> "en_us.json";
		};
		try (InputStream in = Translator.class.getResourceAsStream(BASE + file)) {
			if (in == null) {
				return Map.of();
			}
			return parseFlatJson(new String(in.readAllBytes(), StandardCharsets.UTF_8));
		} catch (IOException | RuntimeException e) {
			return Map.of();
		}
	}

	/** Parses a flat {@code {"key": "value", ...}} JSON object (string values only). */
	public static Map<String, String> parseFlatJson(String json) {
		Map<String, String> out = new HashMap<>();
		int[] pos = {0};
		skipWs(json, pos);
		expect(json, pos, '{');
		skipWs(json, pos);
		if (peek(json, pos) == '}') {
			return out;
		}
		while (true) {
			skipWs(json, pos);
			String key = readString(json, pos);
			skipWs(json, pos);
			expect(json, pos, ':');
			skipWs(json, pos);
			String value = readString(json, pos);
			out.put(key, value);
			skipWs(json, pos);
			char c = next(json, pos);
			if (c == '}') {
				return out;
			}
			if (c != ',') {
				throw new IllegalArgumentException("Expected ',' or '}' at " + (pos[0] - 1));
			}
		}
	}

	private static char peek(String s, int[] pos) {
		if (pos[0] >= s.length()) {
			throw new IllegalArgumentException("Unexpected end of JSON");
		}
		return s.charAt(pos[0]);
	}

	private static char next(String s, int[] pos) {
		char c = peek(s, pos);
		pos[0]++;
		return c;
	}

	private static void expect(String s, int[] pos, char want) {
		char c = next(s, pos);
		if (c != want) {
			throw new IllegalArgumentException("Expected '" + want + "' at " + (pos[0] - 1));
		}
	}

	private static void skipWs(String s, int[] pos) {
		while (pos[0] < s.length() && Character.isWhitespace(s.charAt(pos[0]))) {
			pos[0]++;
		}
	}

	private static String readString(String s, int[] pos) {
		expect(s, pos, '"');
		StringBuilder sb = new StringBuilder();
		while (true) {
			char c = next(s, pos);
			if (c == '"') {
				return sb.toString();
			}
			if (c != '\\') {
				sb.append(c);
				continue;
			}
			char e = next(s, pos);
			switch (e) {
				case '"', '\\', '/' -> sb.append(e);
				case 'n' -> sb.append('\n');
				case 't' -> sb.append('\t');
				case 'r' -> sb.append('\r');
				case 'b' -> sb.append('\b');
				case 'f' -> sb.append('\f');
				case 'u' -> {
					if (pos[0] + 4 > s.length()) {
						throw new IllegalArgumentException("Bad \\u escape");
					}
					sb.append((char) Integer.parseInt(s.substring(pos[0], pos[0] + 4), 16));
					pos[0] += 4;
				}
				default -> throw new IllegalArgumentException("Bad escape \\" + e);
			}
		}
	}
}
