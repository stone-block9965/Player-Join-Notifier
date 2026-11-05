package dev.playerjoinnotifier.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * </ul>
 */
public final class JoinDetector {
	/** 通知対象となる参加イベント。 */
	public record Joined(UUID uuid, String name) {
	}

	/** プレイヤーリストが一度も届かない場合に、基準線確定を諦めるまでのティック数。 */
	private static final int MAX_WAIT_FOR_FIRST_LIST_TICKS = 200;

	private final int graceTicks;
	private final int suppressTicks;

	private final Set<UUID> known = new HashSet<>();
	private final Map<UUID, Long> lastNotifiedTick = new HashMap<>();
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
		lastNotifiedTick.clear();
		tick = 0;
		baselineStartTick = -1;
		ready = false;
	}

	public boolean isReady() {
		return ready;
	}

	/**
	 * @param current 現在のプレイヤーリスト（UUID → 名前）
	 * @param self    自分自身の UUID（通知しない）。null 可。
	 * @return このティックで新たに参加したプレイヤー
	 */
	public List<Joined> update(Map<UUID, String> current, UUID self) {
		tick++;

		if (!ready) {
			if (baselineStartTick < 0 && !current.isEmpty()) {
				baselineStartTick = tick;
			}
			// 初期同期中に届いた全員を既知として取り込む
			known.clear();
			known.addAll(current.keySet());
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
			if (!known.add(id)) {
				continue; // すでに知っている UUID = 新規参加ではない
			}
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

		// 退出した UUID を忘れる（再参加で再通知できるように）
		known.retainAll(current.keySet());
		lastNotifiedTick.values().removeIf(t -> tick - t >= suppressTicks);
		return joined;
	}
}
