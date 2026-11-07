package dev.playerjoinnotifier.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * プレイヤーリスト（UUID → 名前）のスナップショットを毎ティック渡すと、
 * 「新規参加」だけを返す状態機械。Minecraft には依存しない。
 *
 * <ul>
 *   <li>UUID の集合の差分だけを見る。Ping・表示名・ゲームモード等の更新では集合が変わらないので無視される。</li>
 *   <li>接続直後の初期同期は「基準線」として取り込み、猶予ティックが経過するまで通知しない。</li>
 *   <li>退出した UUID は集合から消えるので、再参加すれば再び通知される。</li>
 *   <li>サーバーがタブリストを一瞬作り直す場合（remove→add）の誤通知を抑える短い抑制窓を持つ。</li>
 *   <li>退出は {@link #LEAVE_DEBOUNCE_TICKS} ティック消えたままだったときだけ確定する（タブリストの作り直しを退出と誤認しない）。
 *       確定した退出は {@link #left()} で取得する。</li>
 * </ul>
 */
public final class JoinDetector {
	/** 通知対象となる参加イベント。 */
	public record Joined(UUID uuid, String name) {
	}

	/** 退出イベント（{@link #left()}）。 */
	public record Left(UUID uuid, String name) {
	}

	/** 消えたプレイヤーを「退出」と確定するまでの待ち（20 ティック = 1 秒）。この間に戻ってきたら何も起きなかったことにする。 */
	public static final int LEAVE_DEBOUNCE_TICKS = 10;

	/** プレイヤーリストが一度も届かない場合に、基準線確定を諦めるまでのティック数。 */
	private static final int MAX_WAIT_FOR_FIRST_LIST_TICKS = 200;

	private final int graceTicks;
	private final int suppressTicks;

	/** 既知のプレイヤー（UUID → 最後に見えた名前）。 */
	private final Map<UUID, String> known = new HashMap<>();
	/** リストから消えたが、まだ退出と確定していないプレイヤー（UUID → 消えたティック）。 */
	private final Map<UUID, Long> pendingGone = new HashMap<>();
	private final Map<UUID, Long> lastNotifiedTick = new HashMap<>();
	private List<Left> left = List.of();
	private long tick;
	private long baselineStartTick = -1;
	private boolean ready;

	/**
	 * @param graceTicks    プレイヤーリストが最初に届いてから通知を始めるまでの猶予（20 ティック = 1 秒）
	 * @param suppressTicks 同一 UUID への再通知を抑える期間（ティック）。0 で無効。
	 */
	public JoinDetector(int graceTicks, int suppressTicks) {
		this.graceTicks = Math.max(0, graceTicks);
		this.suppressTicks = Math.max(0, suppressTicks);
	}

	/** 新しい接続を始めるときに呼ぶ。 */
	public void reset() {
		known.clear();
		pendingGone.clear();
		lastNotifiedTick.clear();
		left = List.of();
		tick = 0;
		baselineStartTick = -1;
		ready = false;
	}

	public boolean isReady() {
		return ready;
	}

	/** 直近の {@link #update} で退出が確定したプレイヤー（自分自身は含まない）。次の update で置き換わる。 */
	public List<Left> left() {
		return left;
	}

	/**
	 * @param current 現在のプレイヤーリスト（UUID → 名前）
	 * @param self    自分自身の UUID（通知しない）。null 可。
	 * @return このティックで新たに参加したプレイヤー
	 */
	public List<Joined> update(Map<UUID, String> current, UUID self) {
		tick++;
		left = List.of();

		if (!ready) {
			if (baselineStartTick < 0 && !current.isEmpty()) {
				baselineStartTick = tick;
			}
			// 初期同期中に届いた全員を既知として取り込む
			known.clear();
			pendingGone.clear();
			known.putAll(current);
			boolean graceElapsed = baselineStartTick >= 0 && tick - baselineStartTick >= graceTicks;
			boolean gaveUp = baselineStartTick < 0 && tick >= MAX_WAIT_FOR_FIRST_LIST_TICKS;
			if (graceElapsed || gaveUp) {
				ready = true;
			}
			return List.of();
		}

		List<Joined> joined = new ArrayList<>();
		for (Map.Entry<UUID, String> e : current.entrySet()) {
			UUID id = e.getKey();
			if (pendingGone.remove(id) != null) {
				// 一瞬消えて戻ってきた（タブリストの作り直し）。参加でも退出でもない
				known.put(id, e.getValue());
				continue;
			}
			if (known.containsKey(id)) {
				known.put(id, e.getValue()); // すでに知っている UUID = 新規参加ではない（名前だけ更新）
				continue;
			}
			known.put(id, e.getValue());
			if (id.equals(self)) {
				continue;
			}
			Long last = lastNotifiedTick.get(id);
			if (last != null && tick - last < suppressTicks) {
				continue;
			}
			lastNotifiedTick.put(id, tick);
			joined.add(new Joined(id, e.getValue()));
		}

		// 消えた UUID は、一定ティック戻らなければ退出として確定し、忘れる（再参加で再通知できるように）
		for (UUID id : known.keySet()) {
			if (!current.containsKey(id)) {
				pendingGone.putIfAbsent(id, tick);
			}
		}
		List<Left> confirmed = new ArrayList<>();
		for (Iterator<Map.Entry<UUID, Long>> it = pendingGone.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Long> g = it.next();
			if (tick - g.getValue() >= LEAVE_DEBOUNCE_TICKS) {
				it.remove();
				String name = known.remove(g.getKey());
				if (!g.getKey().equals(self)) {
					confirmed.add(new Left(g.getKey(), name));
				}
			}
		}
		left = confirmed;
		lastNotifiedTick.values().removeIf(t -> tick - t >= suppressTicks);
		return joined;
	}
}
