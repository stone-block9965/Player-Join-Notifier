package dev.playerjoinnotifier;

import com.mojang.authlib.GameProfile;
import dev.playerjoinnotifier.core.JoinDetector;
import dev.playerjoinnotifier.core.NotificationText;
import dev.playerjoinnotifier.core.PlayerFilter;
import dev.playerjoinnotifier.core.ServerAddress;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * クライアント専用 Mod 本体。
 *
 * <p>検知方式: 毎ティック、クライアントが保持しているプレイヤーリスト（タブリスト）の UUID 集合を
 * 前回と比較する。Mixin でパケットを直接フックしない理由は、(1) Ping や表示名の更新のように
 * 「集合が変わらない更新」を新規参加と取り違える余地がない、(2) バージョン間で変わりやすい
 * パケットクラス名に依存しない、ため。ティック処理はメインスレッドで軽い集合比較だけを行い、
 * 通知の送信（OS ごとの通知 I/O）は {@link DesktopNotifier} の別スレッドで行う。
 */
public final class PlayerJoinNotifierMod implements ClientModInitializer {
	public static final String MOD_ID = "player_join_notifier";
	private static final Logger LOGGER = LoggerFactory.getLogger("PlayerJoinNotifier");

	private static PlayerJoinNotifierMod instance;

	private ConfigManager configManager;
	private DesktopNotifier notifier;
	private JoinDetector detector = new JoinDetector(0, 0);

	private ClientPacketListener lastConnection;
	private boolean sessionActive;
	private String sessionAddress;
	private boolean restartRequested;

	@Override
	public void onInitializeClient() {
		configManager = new ConfigManager(
				FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".json"), LOGGER);
		configManager.load();
		Texts.bind(configManager);
		instance = this;
		notifier = new DesktopNotifier(LOGGER);

		ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
		ClientCommandRegistrationCallback.EVENT.register(
				(dispatcher, registryAccess) -> JoinNotifyCommand.register(dispatcher, this));

		LOGGER.info("Player Join Notifier initialized (backend: {} / config: {} / {} registered server(s))",
				notifier.backendName(), configManager.file(), configManager.normalizedServers().size());
	}

	// ------------------------------------------------------------------ ティック処理

	private void onTick(Minecraft mc) {
		ClientPacketListener connection = mc.getConnection();
		if (connection == null || mc.player == null) {
			if (lastConnection != null) {
				lastConnection = null;
				sessionActive = false;
				sessionAddress = null;
			}
			return;
		}

		// 新しい接続（サーバー切替・再接続を含む）、または設定変更の直後は基準線から取り直す
		if (connection != lastConnection || restartRequested) {
			restartRequested = false;
			startSession(mc, connection);
		}
		if (!sessionActive) {
			return;
		}

		Map<UUID, String> current = new LinkedHashMap<>();
		for (PlayerInfo info : connection.getOnlinePlayers()) {
			GameProfile profile = info.getProfile();
			if (profile != null && profile.id() != null) {
				current.put(profile.id(), profile.name());
			}
		}

		// Always run the detector so joins/leaves are recorded even when no notification is shown
		var joinedPlayers = detector.update(current, mc.player.getUUID());
		var leftPlayers = detector.left();
		NotifierConfig cfg = configManager.get();
		if (cfg.onlyWhenUnfocused && mc.isWindowActive()) {
			return; // the game window is focused: skip desktop notifications and sounds
		}
		for (JoinDetector.Joined joined : joinedPlayers) {
			notifyPlayer(cfg, true, joined.uuid(), joined.name());
		}
		if (cfg.notifyOnLeave) {
			for (JoinDetector.Left gone : leftPlayers) {
				notifyPlayer(cfg, false, gone.uuid(), gone.name());
			}
		}
	}

	/** Applies the watchlist / blocklist, then shows the notification (and the chime). */
	private void notifyPlayer(NotifierConfig cfg, boolean join, UUID uuid, String name) {
		if (!PlayerFilter.allows(cfg.watchlistEnabled, cfg.watchlist, cfg.blocklistEnabled, cfg.blocklist, name, uuid)) {
			return;
		}
		String shown = (name == null || name.isBlank()) ? uuid.toString() : name;
		String title = join ? Texts.title(cfg) : Texts.leaveTitle(cfg);
		String template = join ? Texts.bodyTemplate(cfg) : Texts.leaveBodyTemplate(cfg);
		notifier.send(title, NotificationText.body(template, shown), cfg.icon, cfg.timeoutMillis);
		if (cfg.sound) {
			notifier.playChime(join, cfg.soundVolume);
		}
	}

	private void startSession(Minecraft mc, ClientPacketListener connection) {
		lastConnection = connection;
		NotifierConfig cfg = configManager.get();
		detector = new JoinDetector(cfg.initialSyncGraceTicks, cfg.duplicateSuppressSeconds * 20);
		detector.reset();

		sessionAddress = CurrentServer.address(mc);
		sessionActive = cfg.enabled && sessionAddress != null
				&& ServerAddress.matches(configManager.normalizedServers(), sessionAddress);

		LOGGER.info("Connected to: {} -> join notifications: {}", sessionAddress == null ? "(singleplayer/unknown)" : sessionAddress,
				sessionActive ? "enabled" : "disabled (server not whitelisted, or the mod is disabled)");
	}

	// ------------------------------------------------------------------ コマンドからの利用

	/** The running mod instance (for the Mod Menu screens). Null before initialization. */
	static PlayerJoinNotifierMod get() {
		return instance;
	}

	ConfigManager config() {
		return configManager;
	}

	DesktopNotifier notifier() {
		return notifier;
	}

	/** 現在の接続を基準線から取り直す（設定変更の反映用）。メインスレッドから呼ぶ。 */
	void requestRestart() {
		restartRequested = true;
	}

	boolean isSessionActive() {
		return sessionActive;
	}

	String sessionAddress() {
		return sessionAddress;
	}

	/** 実行中の接続のアドレス（コマンド入力時点で取得）。 */
	String currentAddress() {
		return CurrentServer.address(Minecraft.getInstance());
	}
}
