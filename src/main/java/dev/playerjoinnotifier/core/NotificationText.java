package dev.playerjoinnotifier.core;

/** 通知本文の整形。プレイヤー名はサーバーが決める文字列なので、必ずエスケープ・サニタイズして埋め込む。 */
public final class NotificationText {
	private static final int MAX_NAME_LENGTH = 64;

	private NotificationText() {
	}

	/** テンプレートの {@code {player}} をプレイヤー名で置き換える。 */
	public static String body(String template, String playerName) {
		return template.replace("{player}", escapeMarkup(sanitize(playerName)));
	}

	/** 制御文字を除去し、長すぎる名前を切り詰める。 */
	static String sanitize(String s) {
		if (s == null) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		s.codePoints().filter(cp -> !Character.isISOControl(cp)).limit(MAX_NAME_LENGTH).forEach(sb::appendCodePoint);
		return sb.toString();
	}

	/** 通知サーバー（KDE / deepin など）が本文を簡易マークアップとして解釈しても崩れないようにする。 */
	static String escapeMarkup(String s) {
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
