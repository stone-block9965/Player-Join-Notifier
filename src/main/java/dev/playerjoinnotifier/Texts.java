package dev.playerjoinnotifier;

import net.minecraft.network.chat.Component;

/**
 * Localized text lookup. Strings live in {@code assets/player_join_notifier/lang/*.json}
 * (en_us is the fallback; ja_jp is also provided) and follow the game's language setting.
 */
final class Texts {
	private static final String PREFIX = PlayerJoinNotifierMod.MOD_ID + ".";

	private Texts() {
	}

	static Component tr(String key, Object... args) {
		return Component.translatable(PREFIX + key, args);
	}

	/** Notification title: the config value, or the localized default when it is empty. */
	static String title(NotifierConfig cfg) {
		return cfg.title.isBlank() ? tr("notification.title").getString() : cfg.title;
	}

	/** Notification body template ({player} is replaced later): the config value, or the localized default. */
	static String bodyTemplate(NotifierConfig cfg) {
		return cfg.body.isBlank() ? tr("notification.body").getString() : cfg.body;
	}
}
