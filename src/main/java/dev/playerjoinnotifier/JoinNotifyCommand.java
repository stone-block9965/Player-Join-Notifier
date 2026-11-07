package dev.playerjoinnotifier;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.playerjoinnotifier.ConfigManager.PlayerList;
import dev.playerjoinnotifier.core.NotificationText;
import dev.playerjoinnotifier.core.PlayerFilter;
import dev.playerjoinnotifier.core.ServerAddress;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * クライアントサイドコマンド {@code /joinnotify}（サーバーには送信されない）。
 * 設定ファイルを直接編集する代わりに、ゲーム内からホワイトリストを操作できる。
 */
final class JoinNotifyCommand {
	private JoinNotifyCommand() {
	}

	/** A true/false setting that can be shown and changed with /joinnotify <name> [true|false]. */
	private record BoolSetting(String name, Function<NotifierConfig, Boolean> get,
			BiConsumer<NotifierConfig, Boolean> set, boolean restartSession) {
	}

	private static final List<BoolSetting> BOOL_SETTINGS = List.of(
			new BoolSetting("enabled", c -> c.enabled, (c, v) -> c.enabled = v, true),
			new BoolSetting("onlyWhenUnfocused", c -> c.onlyWhenUnfocused, (c, v) -> c.onlyWhenUnfocused = v, false),
			new BoolSetting("notifyOnLeave", c -> c.notifyOnLeave, (c, v) -> c.notifyOnLeave = v, false),
			new BoolSetting("sound", c -> c.sound, (c, v) -> c.sound = v, false),
			new BoolSetting("watchlistEnabled", c -> c.watchlistEnabled, (c, v) -> c.watchlistEnabled = v, false),
			new BoolSetting("blocklistEnabled", c -> c.blocklistEnabled, (c, v) -> c.blocklistEnabled = v, false));

	static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, PlayerJoinNotifierMod mod) {
		LiteralArgumentBuilder<FabricClientCommandSource> root = ClientCommands.literal("joinnotify")
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
				.then(playerListNode("watchlist", PlayerList.WATCH, mod))
				.then(playerListNode("blocklist", PlayerList.BLOCK, mod))
				.then(ClientCommands.literal("reload").executes(c -> reload(c.getSource(), mod)))
				.then(ClientCommands.literal("test").executes(c -> test(c.getSource(), mod)));

		// Each setting works both as /joinnotify <name> [value] and as /joinnotify settings <name> [value]
		LiteralArgumentBuilder<FabricClientCommandSource> settings = ClientCommands.literal("settings")
				.executes(c -> showSettings(c.getSource(), mod));
		for (BoolSetting setting : BOOL_SETTINGS) {
			root.then(boolNode(setting, mod));
			settings.then(boolNode(setting, mod));
		}
		root.then(volumeNode(mod));
		settings.then(volumeNode(mod));
		root.then(settings);

		dispatcher.register(root);
	}

	// ------------------------------------------------------------------ 設定（true/false など）

	private static LiteralArgumentBuilder<FabricClientCommandSource> boolNode(BoolSetting setting,
			PlayerJoinNotifierMod mod) {
		return boolNode(setting, setting.name(), mod);
	}

	/** Same, but the command word may differ from the setting name (e.g. "watchlist enabled"). */
	private static LiteralArgumentBuilder<FabricClientCommandSource> boolNode(BoolSetting setting, String word,
			PlayerJoinNotifierMod mod) {
		return ClientCommands.literal(word)
				.executes(c -> {
					boolean current = setting.get().apply(mod.config().get());
					say(c.getSource(), "command.setting.value", setting.name(), String.valueOf(current));
					return current ? 1 : 0;
				})
				.then(ClientCommands.argument("value", BoolArgumentType.bool()).executes(c -> {
					boolean value = BoolArgumentType.getBool(c, "value");
					mod.config().update(cfg -> setting.set().accept(cfg, value));
					if (setting.restartSession()) {
						mod.requestRestart();
					}
					say(c.getSource(), "command.settings.changed", setting.name(), String.valueOf(value));
					return 1;
				}));
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> volumeNode(PlayerJoinNotifierMod mod) {
		return ClientCommands.literal("soundVolume")
				.executes(c -> {
					int current = mod.config().get().soundVolume;
					say(c.getSource(), "command.setting.value", "soundVolume", String.valueOf(current));
					return current;
				})
				.then(ClientCommands.argument("value", IntegerArgumentType.integer(0, 100)).executes(c -> {
					int value = IntegerArgumentType.getInteger(c, "value");
					mod.config().update(cfg -> cfg.soundVolume = value);
					say(c.getSource(), "command.settings.changed", "soundVolume", String.valueOf(value));
					return 1;
				}));
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
		for (BoolSetting setting : BOOL_SETTINGS) {
			say(source, "command.settings.line", setting.name(), String.valueOf(setting.get().apply(cfg)));
		}
		say(source, "command.settings.line", "soundVolume", String.valueOf(cfg.soundVolume));
		say(source, "command.settings.line", "language", languageLabel(cfg.language));
		say(source, "command.settings.line", "watchlist", String.valueOf(cfg.watchlist.size()));
		say(source, "command.settings.line", "blocklist", String.valueOf(cfg.blocklist.size()));
		return 1;
	}

	// ------------------------------------------------------------------ ウォッチリスト / ブロックリスト

	private static LiteralArgumentBuilder<FabricClientCommandSource> playerListNode(String name, PlayerList which,
			PlayerJoinNotifierMod mod) {
		BoolSetting switchSetting = BOOL_SETTINGS.stream()
				.filter(s -> s.name().equals(name + "Enabled")).findFirst().orElseThrow();
		return ClientCommands.literal(name)
				.executes(c -> showPlayers(c.getSource(), mod, which))
				.then(boolNode(switchSetting, "enabled", mod))
				.then(ClientCommands.literal("add")
						.then(ClientCommands.argument("player", StringArgumentType.word())
								.executes(c -> addPlayer(c.getSource(), mod, which, StringArgumentType.getString(c, "player")))))
				.then(ClientCommands.literal("remove")
						.then(ClientCommands.argument("player", StringArgumentType.word())
								.executes(c -> removePlayer(c.getSource(), mod, which, StringArgumentType.getString(c, "player")))));
	}

	private static String listName(PlayerList which) {
		return Texts.str(which == PlayerList.WATCH ? "command.list_name.watch" : "command.list_name.block");
	}

	private static List<String> entries(PlayerJoinNotifierMod mod, PlayerList which) {
		NotifierConfig cfg = mod.config().get();
		return which == PlayerList.WATCH ? cfg.watchlist : cfg.blocklist;
	}

	private static int showPlayers(FabricClientCommandSource source, PlayerJoinNotifierMod mod, PlayerList which) {
		List<String> list = entries(mod, which);
		boolean watch = which == PlayerList.WATCH;
		NotifierConfig cfg = mod.config().get();
		if (!(watch ? cfg.watchlistEnabled : cfg.blocklistEnabled)) {
			say(source, watch ? "command.watchlist.disabled" : "command.blocklist.disabled");
		}
		if (list.isEmpty()) {
			say(source, watch ? "command.watchlist.empty" : "command.blocklist.empty");
		} else {
			say(source, watch ? "command.watchlist.header" : "command.blocklist.header", list.size());
			for (String entry : list) {
				say(source, PlayerFilter.key(entry).isPresent() ? "command.player.entry" : "command.player.entry_invalid", entry);
			}
		}
		return list.size();
	}

	private static int addPlayer(FabricClientCommandSource source, PlayerJoinNotifierMod mod, PlayerList which,
			String player) {
		switch (mod.config().addPlayer(which, player)) {
			case ADDED -> {
				say(source, "command.player.added", listName(which), player.strip());
				if (which == PlayerList.WATCH && entries(mod, which).size() == 1) {
					say(source, "command.watchlist.active_note");
				}
			}
			case DUPLICATE -> say(source, "command.player.duplicate", listName(which), player.strip());
			case INVALID -> say(source, "command.player.invalid", player);
		}
		return 1;
	}

	private static int removePlayer(FabricClientCommandSource source, PlayerJoinNotifierMod mod, PlayerList which,
			String player) {
		int removed = mod.config().removePlayer(which, player);
		say(source, removed > 0 ? "command.player.removed" : "command.player.not_found", listName(which), player.strip());
		return removed;
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
		if (cfg.sound) {
			mod.notifier().playChime(true, cfg.soundVolume);
		}
		say(source, "command.test.sent");
		return 1;
	}
}
