package dev.playerjoinnotifier;

import dev.playerjoinnotifier.core.Translator;

import java.util.ArrayList;
import java.util.List;

/** 設定ファイル（JSON）の内容。フィールド名がそのまま JSON のキーになる。 */
public final class NotifierConfig {
	/** false にすると全サーバーで通知しない。 */
	public boolean enabled = true;

	/** 通知対象サーバーのアドレス（サーバー一覧に入力しているものと同じ書き方）。空なら何も通知しない。 */
	public List<String> servers = new ArrayList<>();

	/** Language of the mod's own text: "auto" (follow the game language), "en" or "ja". */
	public String language = "auto";

	/** If true, also notify when a player leaves. Default off. */
	public boolean notifyOnLeave = false;

	/** Play a short chime with each notification. */
	public boolean sound = true;

	/** Chime volume, 0-100. */
	public int soundVolume = 40;

	/** Master switch for the watchlist. false = the watchlist is ignored (its entries are kept). */
	public boolean watchlistEnabled = true;

	/** Master switch for the blocklist. false = the blocklist is ignored (its entries are kept). */
	public boolean blocklistEnabled = true;

	/** Only these players are notified (names, case-insensitive, or UUIDs). Empty = everyone. */
	public List<String> watchlist = new ArrayList<>();

	/** These players are never notified (names or UUIDs). Wins over the watchlist. */
	public List<String> blocklist = new ArrayList<>();

	/** If true, only notify while the Minecraft window is not focused. Default false = always notify. */
	public boolean onlyWhenUnfocused = false;

	/** Notification title. Empty = localized default that follows the game language. */
	public String title = "";

	/** Title of the "player left" notification. Empty = localized default. */
	public String leaveTitle = "";

	/** Body of the "player left" notification; {player} is replaced by the player name. Empty = localized default. */
	public String leaveBody = "";

	/** Notification body; {player} is replaced by the player name. Empty = localized default that follows the game language. */
	public String body = "";

	/** 通知アイコン名（freedesktop のアイコン名）。Linux でのみ使われ、Windows / macOS では無視される。 */
	public String icon = "applications-games";

	/** 通知の表示時間（ミリ秒）。-1 でデスクトップ既定、0 で自動では消えない。Linux でのみ使われ、Windows / macOS では OS の設定に従う。 */
	public int timeoutMillis = 5000;

	/** 接続直後、プレイヤーリストが届いてからこのティック数（20 = 1 秒）は通知しない。初期同期の誤検知防止。 */
	public int initialSyncGraceTicks = 40;

	/** 同じプレイヤーへの再通知を抑える秒数。タブリストの一瞬の作り直しによる二重通知を防ぐ。 */
	public int duplicateSuppressSeconds = 3;

	/** 手編集で壊れた値（null・範囲外）を安全な値に直す。 */
	void sanitize() {
		if (servers == null) {
			servers = new ArrayList<>();
		}
		servers.removeIf(s -> s == null);
		if (watchlist == null) {
			watchlist = new ArrayList<>();
		}
		watchlist.removeIf(s -> s == null || s.isBlank());
		if (blocklist == null) {
			blocklist = new ArrayList<>();
		}
		blocklist.removeIf(s -> s == null || s.isBlank());
		soundVolume = Math.max(0, Math.min(soundVolume, 100));
		if (leaveTitle == null) {
			leaveTitle = "";
		}
		if (leaveBody == null) {
			leaveBody = "";
		}
		NotifierConfig d = new NotifierConfig();
		String lang = Translator.normalizeSetting(language);
		language = lang == null ? d.language : lang;
		if (title == null || title.isBlank()) {
			title = d.title;
		}
		if (body == null || body.isBlank()) {
			body = d.body;
		}
		if (icon == null) {
			icon = d.icon;
		}
		if (timeoutMillis < -1) {
			timeoutMillis = d.timeoutMillis;
		}
		initialSyncGraceTicks = Math.max(0, Math.min(initialSyncGraceTicks, 20 * 60));
		duplicateSuppressSeconds = Math.max(0, Math.min(duplicateSuppressSeconds, 600));
	}
}
