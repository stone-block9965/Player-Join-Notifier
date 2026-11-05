package dev.playerjoinnotifier.platform;

import dev.playerjoinnotifier.dbus.DBusNotificationClient;

/** Linux: D-Bus の {@code org.freedesktop.Notifications} に直接 Notify を送る。 */
public final class LinuxNotificationBackend implements NotificationBackend {
	@Override
	public String name() {
		return "Linux (D-Bus org.freedesktop.Notifications)";
	}

	@Override
	public void send(String title, String body, String icon, int timeoutMillis) throws Exception {
		DBusNotificationClient.notify("Minecraft", icon, title, body, timeoutMillis);
	}
}
