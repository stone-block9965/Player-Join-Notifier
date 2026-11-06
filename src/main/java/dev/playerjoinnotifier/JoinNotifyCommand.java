package dev.playerjoinnotifier;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.playerjoinnotifier.core.NotificationText;
import dev.playerjoinnotifier.core.ServerAddress;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

/**
 * クライアントサイドコマンド {@code /joinnotify}（サーバーには送信されない）。
 * 設定ファイルを直接編集する代わりに、ゲーム内からホワイトリストを操作できる。
 */
final class JoinNotifyCommand {
	private JoinNotifyCommand() {
	}

	static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, PlayerJoinNotifierMod mod) {
		dispatcher.register(ClientCommands.literal("joinnotify")
				.then(ClientCommands.literal("list").executes(c -> list(c.getSource(), mod)))
				.then(ClientCommands.literal("status").executes(c -> status(c.getSource(), mod)))
				.then(ClientCommands.literal("add")
						.executes(c -> addCurrent(c.getSource(), mod))
						.then(ClientCommands.argument("address", StringArgumentType.greedyString())
								.executes(c -> add(c.getSource(), mod, StringArgumentType.getString(c, "address")))))
				.then(ClientCommands.literal("remove")
						.then(ClientCommands.argument("address", StringArgumentType.greedyString())
								.executes(c -> remove(c.getSource(), mod, StringArgumentType.getString(c, "address")))))
				.then(ClientCommands.literal("language")
						.executes(c -> showLanguage(c.getSource(), mod))
						.then(ClientCommands.literal("auto").executes(c -> setLanguage(c.getSource(), mod, "auto")))
						.then(ClientCommands.literal("en").executes(c -> setLanguage(c.getSource(), mod, "en")))
						.then(ClientCommands.literal("ja").executes(c -> setLanguage(c.getSource(), mod, "ja"))))
				.then(ClientCommands.literal("settings")
						.executes(c -> showSettings(c.getSource(), mod))
						.then(ClientCommands.literal("enabled")
								.then(ClientCommands.argument("value", BoolArgumentType.bool())
										.executes(c -> setEnabled(c.getSource(), mod, BoolArgumentType.getBool(c, "value")))))
						.then(ClientCommands.literal("onlyWhenUnfocused")
								.then(ClientCommands.argument("value", BoolArgumentType.bool())
										.executes(c -> setOnlyWhenUnfocused(c.getSource(), mod,
												BoolArgumentType.getBool(c, "value"))))))
				.then(ClientCommands.literal("reload").executes(c -> reload(c.getSource(), mod)))
				.then(ClientCommands.literal("test").executes(c -> test(c.getSource(), mod))));
	}

	private static void say(FabricClientCommandSource source, String key, Object... args) {
		source.sendFeedback(Component.literal("[JoinNotify] ").append(Texts.tr(key, args)));
	}

	private static Component state(boolean on) {
		return Texts.tr(on ? "command.state.on" : "command.state.off");
	}

	/** Human-readable language setting, e.g. "English" / "auto (currently English)". */
	static String languageLabel(String setting) {
		String shown = "ja".equals(Texts.activeLanguage()) ? "日本語" : "English";
		return switch (setting) {
			case "en" -> "English";
			case "ja" -> "日本語";
			default -> Texts.str("command.language.auto_value", shown);
		};
	}

	private static int showLanguage(FabricClientCommandSource source, PlayerJoinNotifierMod mod) {
		say(source, "command.language.current", languageLabel(mod.config().get().language));
		return 1;
	}

	private static int setLanguage(FabricClientCommandSource source, PlayerJoinNotifierMod mod, String setting) {
		mod.config().update(c -> c.language = setting);
		say(source, "command.language.set", languageLabel(setting));
		return 1;
	}

	private static int showSettings(FabricClientCommandSource source, PlayerJoinNotifierMod mod) {
		NotifierConfig cfg = mod.config().get();
		say(source, "command.settings.header");
		say(source, "command.settings.line", "enabled", state(cfg.enabled));
		say(source, "command.settings.line", "onlyWhenUnfocused", state(cfg.onlyWhenUnfocused));
		say(source, "command.settings.line", "language", languageLabel(cfg.language));
		return 1;
	}

	private static int setEnabled(FabricClientCommandSource source, PlayerJoinNotifierMod mod, boolean value) {
		mod.config().update(c -> c.enabled = value);
		mod.requestRestart();
		say(source, "command.settings.changed", "enabled", state(value));
		return 1;
	}

	private static int setOnlyWhenUnfocused(FabricClientCommandSource source, PlayerJoinNotifierMod mod, boolean value) {
		mod.config().update(c -> c.onlyWhenUnfocused = value);
		say(source, "command.settings.changed", "onlyWhenUnfocused", state(value));
		return 1;
	}

	private static int list(FabricClientCommandSource source, PlayerJoinNotifierMod mod) {
		var servers = mod.config().get().servers;
		if (servers.isEmpty()) {
			say(source, "command.list.empty");
		} else {
			say(source, "command.list.header", servers.size());
			for (String s : servers) {
				var normalized = ServerAddress.normalize(s);
				if (normalized.isPresent()) {
					say(source, "command.list.entry", s, normalized.get());
				} else {
					say(source, "command.list.entry_invalid", s);
				}
			}
		}
		return servers.size();
	}

	private static int status(FabricClientCommandSource source, PlayerJoinNotifierMod mod) {
		say(source, "command.status.enabled", state(mod.config().get().enabled));
		say(source, "command.status.server",
				mod.sessionAddress() == null ? Texts.tr("command.status.server_unknown") : mod.sessionAddress());
		say(source, "command.status.session", state(mod.isSessionActive()));
		say(source, "command.status.config", mod.config().file().toString());
		return 1;
	}

	private static int add(FabricClientCommandSource source, PlayerJoinNotifierMod mod, String address) {
		switch (mod.config().addServer(address)) {
			case ADDED -> {
				say(source, "command.add.added", address.strip());
				mod.requestRestart();
			}
			case DUPLICATE -> say(source, "command.add.duplicate", address.strip());
			case INVALID -> say(source, "command.add.invalid", address);
		}
		return 1;
	}

	private static int addCurrent(FabricClientCommandSource source, PlayerJoinNotifierMod mod) {
		String current = mod.currentAddress();
		if (current == null) {
			say(source, "command.add.no_server");
			return 0;
		}
		return add(source, mod, current);
	}

	private static int remove(FabricClientCommandSource source, PlayerJoinNotifierMod mod, String address) {
		int removed = mod.config().removeServer(address);
		if (removed > 0) {
			say(source, "command.remove.removed", address.strip());
			mod.requestRestart();
		} else {
			say(source, "command.remove.not_found", address.strip());
		}
		return removed;
	}

	private static int reload(FabricClientCommandSource source, PlayerJoinNotifierMod mod) {
		if (mod.config().load()) {
			mod.requestRestart();
			say(source, "command.reload.ok", mod.config().normalizedServers().size());
			return 1;
		}
		say(source, "command.reload.failed");
		return 0;
	}

	private static int test(FabricClientCommandSource source, PlayerJoinNotifierMod mod) {
		NotifierConfig cfg = mod.config().get();
		mod.notifier().send(Texts.title(cfg), NotificationText.body(Texts.bodyTemplate(cfg), "TestPlayer"), cfg.icon,
				cfg.timeoutMillis);
		say(source, "command.test.sent");
		return 1;
	}
}
