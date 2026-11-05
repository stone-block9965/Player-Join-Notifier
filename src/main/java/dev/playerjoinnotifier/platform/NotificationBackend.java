package dev.playerjoinnotifier.platform;

/** OS ごとの通知送信方法。呼び出しは専用スレッドから行われる。失敗は例外で知らせる。 */
public interface NotificationBackend {
	/** ログ用の名前。 */
	String name();

	/**
	 * @param icon          通知アイコン名（freedesktop のアイコン名）。Linux 以外では無視される
	 * @param timeoutMillis 表示時間（ミリ秒）。Linux 以外では OS 側の設定が使われ、無視される
	 */
	void send(String title, String body, String icon, int timeoutMillis) throws Exception;
}
