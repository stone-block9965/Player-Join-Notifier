package dev.playerjoinnotifier;

import com.mojang.brigadier.CommandDispatcher;
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
				.then(ClientCommands.literal("reload").executes(c -> reload(c.getSource(), mod)))
				.then(ClientCommands.literal("test").executes(c -> test(c.getSource(), mod))));
	}

	private static void say(FabricClientCommandSource source, String key, Object... args) {
		source.sendFeedback(Component.literal("[JoinNotify] ").append(Texts.tr(key, args)));
	}

	private static Component state(boolean on) {
		return Texts.tr(on ? "command.state.on" : "command.state.off");
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
