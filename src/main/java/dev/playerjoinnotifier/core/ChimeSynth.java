package dev.playerjoinnotifier.core;

/**
 * Generates the short notification chime as raw PCM (16-bit signed little-endian, mono) in code,
 * so no sound files or libraries are needed. Join = rising two-note chime, leave = falling.
 */
public final class ChimeSynth {
	public static final int SAMPLE_RATE = 44_100;
	private static final double NOTE_SECONDS = 0.11;
	private static final double LOW_HZ = 784.0;   // G5
	private static final double HIGH_HZ = 1174.7; // D6
	/** Full-scale amplitude at volume 100 (kept below clipping so it is never harsh). */
	private static final double PEAK = 0.6;

	private ChimeSynth() {
	}

	/**
	 * @param rising        true: low→high (join), false: high→low (leave)
	 * @param volumePercent 0–100 (values outside are clamped). 0 returns silence.
	 */
	public static byte[] render(boolean rising, int volumePercent) {
		double volume = Math.max(0, Math.min(100, volumePercent)) / 100.0;
		int perNote = (int) (SAMPLE_RATE * NOTE_SECONDS);
		byte[] out = new byte[perNote * 2 * 2];
		double first = rising ? LOW_HZ : HIGH_HZ;
		double second = rising ? HIGH_HZ : LOW_HZ;
		for (int note = 0; note < 2; note++) {
			double hz = note == 0 ? first : second;
			for (int i = 0; i < perNote; i++) {
				double t = i / (double) SAMPLE_RATE;
				double attack = Math.min(1.0, i / (SAMPLE_RATE * 0.006));      // 6 ms fade-in (no click)
				double decay = Math.exp(-t * 22.0);                           // soft decay
				double wave = Math.sin(2 * Math.PI * hz * t) + 0.25 * Math.sin(4 * Math.PI * hz * t);
				double sample = wave / 1.25 * attack * decay * PEAK * volume;
				short v = (short) Math.round(sample * Short.MAX_VALUE);
				int idx = (note * perNote + i) * 2;
				out[idx] = (byte) (v & 0xFF);
				out[idx + 1] = (byte) ((v >> 8) & 0xFF);
			}
		}
		return out;
	}
}
