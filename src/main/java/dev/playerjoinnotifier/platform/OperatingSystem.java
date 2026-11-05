package dev.playerjoinnotifier.platform;

import java.util.Locale;

/** 通知方式の切り替えに使う OS 種別。 */
public enum OperatingSystem {
	WINDOWS, MACOS, LINUX;

	/** 現在の JVM が動いている OS。 */
	public static OperatingSystem current() {
		return detect(System.getProperty("os.name", ""));
	}

	/**
	 * {@code os.name} から OS を判定する。Windows・macOS 以外（Linux・BSD など）は
	 * D-Bus 通知を使う {@link #LINUX} として扱う。
	 */
	public static OperatingSystem detect(String osName) {
		String name = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
		// "darwin" には "win" が含まれるため、mac の判定を先に行う
		if (name.contains("mac") || name.contains("darwin")) {
			return MACOS;
		}
		if (name.contains("win")) {
			return WINDOWS;
		}
		return LINUX;
	}
}
