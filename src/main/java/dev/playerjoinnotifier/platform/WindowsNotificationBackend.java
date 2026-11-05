package dev.playerjoinnotifier.platform;

import java.awt.Color;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Windows: JDK 標準の {@link TrayIcon#displayMessage}（Windows 10/11 ではトースト通知として表示される）。
 * 追加ライブラリ・外部コマンドは使わない。
 *
 * <p>通知を出すにはトレイアイコンが必要なため、通知のたびに（未登録なら）登録し、
 * 最後の通知から {@link #ICON_LINGER_SECONDS} 秒たったら外す。表示時間は Windows の設定に従い、
 * {@code timeoutMillis} は使われない。
 */
public final class WindowsNotificationBackend implements NotificationBackend {
	private static final int ICON_LINGER_SECONDS = 30;
	// Windows のバルーン通知の上限（タイトル 63 文字・本文 255 文字）
	private static final int MAX_TITLE = 63;
	private static final int MAX_BODY = 255;

	private final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "PlayerJoinNotifier-TrayCleanup");
		t.setDaemon(true);
		return t;
	});

	private TrayIcon trayIcon;
	private ScheduledFuture<?> pendingRemoval;
	private boolean shutdownHookInstalled;

	@Override
	public String name() {
		return "Windows (system tray notification)";
	}

	@Override
	public synchronized void send(String title, String body, String icon, int timeoutMillis) throws Exception {
		if (GraphicsEnvironment.isHeadless() || !SystemTray.isSupported()) {
			throw new IOException("System tray notifications are unavailable here (java.awt.headless=true, or no tray support)");
		}
		SystemTray tray = SystemTray.getSystemTray();
		if (trayIcon == null) {
			trayIcon = new TrayIcon(createIconImage(), "Minecraft");
			trayIcon.setImageAutoSize(true);
		}
		if (!Arrays.asList(tray.getTrayIcons()).contains(trayIcon)) {
			tray.add(trayIcon);
		}
		installShutdownHook();

		trayIcon.displayMessage(truncate(title, MAX_TITLE), truncate(body, MAX_BODY), TrayIcon.MessageType.INFO);
		scheduleRemoval();
	}

	private void scheduleRemoval() {
		if (pendingRemoval != null) {
			pendingRemoval.cancel(false);
		}
		pendingRemoval = cleaner.schedule(this::removeIcon, ICON_LINGER_SECONDS, TimeUnit.SECONDS);
	}

	private synchronized void removeIcon() {
		try {
			if (trayIcon != null && !GraphicsEnvironment.isHeadless() && SystemTray.isSupported()) {
				SystemTray.getSystemTray().remove(trayIcon);
			}
		} catch (RuntimeException ignored) {
			// 片付けの失敗は無視（次回の通知で再登録される）
		}
	}

	private void installShutdownHook() {
		if (!shutdownHookInstalled) {
			shutdownHookInstalled = true;
			Runtime.getRuntime().addShutdownHook(new Thread(this::removeIcon, "PlayerJoinNotifier-TrayShutdown"));
		}
	}

	public static String truncate(String s, int max) {
		if (s == null) {
			return "";
		}
		if (s.length() <= max) {
			return s;
		}
		int end = max - 1;
		// サロゲートペアの途中で切らない
		if (Character.isHighSurrogate(s.charAt(end - 1))) {
			end--;
		}
		return s.substring(0, end) + "…";
	}

	/** 外部のアセットを使わず、草ブロック風の 16x16 アイコンをコードで描く。 */
	private static Image createIconImage() {
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				boolean grass = y < 5 || (y < 7 && ((x * 7 + y * 3) % 4 == 0));
				Color c = grass ? new Color(0x5D9B3A) : new Color(0x8B5A2B);
				if (x == 0 || y == 0 || x == 15 || y == 15) {
					c = c.darker();
				}
				img.setRGB(x, y, c.getRGB());
			}
		}
		return img;
	}
}
