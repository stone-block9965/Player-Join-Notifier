package dev.playerjoinnotifier;

import dev.playerjoinnotifier.core.Translator;
import net.minecraft.network.chat.Component;

/**
 * Localized text lookup. Strings live in {@code assets/player_join_notifier/lang/*.json}.
 * The language is the one chosen in the config ({@code language}: auto / en / ja). "auto" follows the game language.
 */
final class Texts {
	private static final Translator TRANSLATOR = new Translator();
	private static volatile ConfigManager config;

	private Texts() {
	}

	/** Called once at startup so lookups can read the language setting. */
	static void bind(ConfigManager configManager) {
		config = configManager;
	}

	/** The language in use right now ("en" or "ja"): the config value, or the game language when it is "auto". */
	static String activeLanguage() {
		ConfigManager c = config;
		String setting = c == null ? "auto" : c.get().language;
		return Translator.resolve(setting, "auto".equals(setting) ? gameLanguageCode() : null);
	}

	/** The game's own language code, e.g. "en_us" or "jpn_JP" (from the vanilla "language.code" string). */
	static String gameLanguageCode() {
		return Component.translatable("language.code").getString();
	}

	static String str(String key, Object... args) {
		Object[] plain = new Object[args.length];
		for (int i = 0; i < args.length; i++) {
			plain[i] = args[i] instanceof Component component ? component.getString() : args[i];
		}
		return TRANSLATOR.get(activeLanguage(), PlayerJoinNotifierMod.MOD_ID + "." + key, plain);
	}

	static Component tr(String key, Object... args) {
		return Component.literal(str(key, args));
	}

	/** Notification title: the config value, or the localized default when it is empty. */
	static String title(NotifierConfig cfg) {
		return cfg.title.isBlank() ? str("notification.title") : cfg.title;
	}

	/** Notification body template ({player} is replaced later): the config value, or the localized default. */
	static String bodyTemplate(NotifierConfig cfg) {
		return cfg.body.isBlank() ? str("notification.body") : cfg.body;
	}
}
