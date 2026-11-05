package dev.playerjoinnotifier;

import dev.playerjoinnotifier.platform.NotificationBackend;
import dev.playerjoinnotifier.platform.NotificationBackends;
import org.slf4j.Logger;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 通知を別スレッドで送る。ゲームのメインスレッドは通知の I/O で待たされない。
 * 実際の送信は OS ごとの {@link NotificationBackend}（Linux: D-Bus / Windows: トレイ通知 / macOS: osascript）が行う。
 * どんな失敗（通知サービスなし・拒否・タイムアウト）もログに残すだけで、Minecraft 本体には伝播させない。
 */
public final class DesktopNotifier {
	private static final long WARN_INTERVAL_MILLIS = 60_000;

	private final Logger log;
	private final NotificationBackend backend;
	private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
			new ArrayBlockingQueue<>(16), r -> {
				Thread t = new Thread(r, "PlayerJoinNotifier-Notify");
				t.setDaemon(true);
				return t;
			}, new ThreadPoolExecutor.DiscardPolicy()); // 詰まったら古い通知を溜め込まず捨てる

	private volatile long lastWarnMillis;

	public DesktopNotifier(Logger log) {
		this(log, NotificationBackends.forCurrentOs());
	}

	public DesktopNotifier(Logger log, NotificationBackend backend) {
		this.log = log;
		this.backend = backend;
	}

	/** 使用中の通知方式の名前（ログ用）。 */
	public String backendName() {
		return backend.name();
	}

	public void send(String title, String body, String icon, int timeoutMillis) {
		try {
			executor.execute(() -> deliver(title, body, icon, timeoutMillis));
		} catch (RuntimeException e) {
			warnThrottled(e);
		}
	}

	private void deliver(String title, String body, String icon, int timeoutMillis) {
		try {
			backend.send(title, body, icon, timeoutMillis);
		} catch (Exception e) {
			warnThrottled(e);
		}
	}

	private void warnThrottled(Exception e) {
		long now = System.currentTimeMillis();
		if (now - lastWarnMillis >= WARN_INTERVAL_MILLIS) {
			lastWarnMillis = now;
			log.warn("Could not send the desktop notification [{}] (similar warnings are suppressed for 1 minute): {}", backend.name(), e.toString());
		} else {
			log.debug("Desktop notification failed", e);
		}
	}
}
