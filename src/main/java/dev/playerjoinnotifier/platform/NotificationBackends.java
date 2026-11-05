package dev.playerjoinnotifier.platform;

/** 実行中の OS に合った {@link NotificationBackend} を選ぶ。 */
public final class NotificationBackends {
	private NotificationBackends() {
	}

	public static NotificationBackend forCurrentOs() {
		return forOs(OperatingSystem.current());
	}

	public static NotificationBackend forOs(OperatingSystem os) {
		return switch (os) {
			case WINDOWS -> new WindowsNotificationBackend();
			case MACOS -> new MacNotificationBackend();
			case LINUX -> new LinuxNotificationBackend();
		};
	}
}
