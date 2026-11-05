package dev.playerjoinnotifier;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import dev.playerjoinnotifier.core.ServerAddress;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** 設定ファイルの読み書きと、サーバーホワイトリストの管理。 */
public final class ConfigManager {
	public enum AddResult { ADDED, DUPLICATE, INVALID }

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private final Path file;
	private final Logger log;
	private volatile NotifierConfig config = new NotifierConfig();
	private volatile Set<String> normalizedServers = Set.of();

	public ConfigManager(Path file, Logger log) {
		this.file = file;
		this.log = log;
	}

	public Path file() {
		return file;
	}

	public NotifierConfig get() {
		return config;
	}

	/** 正規化済み（host:port）のホワイトリスト。 */
	public Set<String> normalizedServers() {
		return normalizedServers;
	}

	/**
	 * 設定ファイルを読み込む。ファイルが無ければ既定値で作成する。
	 * JSON が壊れている場合は元のファイルを上書きせず、直前の（または既定の）設定を使い続ける。
	 *
	 * @return 読み込みに成功したか
	 */
	public synchronized boolean load() {
		if (!Files.exists(file)) {
			apply(new NotifierConfig());
			save();
			return true;
		}
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			NotifierConfig loaded = GSON.fromJson(reader, NotifierConfig.class);
			if (loaded == null) {
				loaded = new NotifierConfig();
			}
			apply(loaded);
			return true;
		} catch (IOException | JsonParseException e) {
			log.error("Could not read the config file (the original file is left untouched): {}", file, e);
			return false;
		}
	}

	private void apply(NotifierConfig loaded) {
		loaded.sanitize();
		for (String s : loaded.servers) {
			if (ServerAddress.normalize(s).isEmpty()) {
				log.warn("Invalid address in \"servers\" (ignored): \"{}\"", s);
			}
		}
		this.config = loaded;
		this.normalizedServers = ServerAddress.normalizeAll(loaded.servers);
	}

	public synchronized AddResult addServer(String raw) {
		var key = ServerAddress.normalize(raw);
		if (key.isEmpty()) {
			return AddResult.INVALID;
		}
		if (normalizedServers.contains(key.get())) {
			return AddResult.DUPLICATE;
		}
		List<String> list = new ArrayList<>(config.servers);
		list.add(raw.strip());
		config.servers = list;
		apply(config);
		save();
		return AddResult.ADDED;
	}

	/** @return 削除した件数 */
	public synchronized int removeServer(String raw) {
		var key = ServerAddress.normalize(raw);
		String stripped = raw == null ? "" : raw.strip();
		List<String> kept = new ArrayList<>();
		int removed = 0;
		for (String s : config.servers) {
			boolean same = s.strip().equals(stripped)
					|| (key.isPresent() && ServerAddress.normalize(s).map(key.get()::equals).orElse(false));
			if (same) {
				removed++;
			} else {
				kept.add(s);
			}
		}
		if (removed > 0) {
			config.servers = kept;
			apply(config);
			save();
		}
		return removed;
	}

	/** 一時ファイルに書いてから置き換える（書き込み途中でクラッシュしても設定が壊れない）。 */
	private void save() {
		try {
			Path dir = file.toAbsolutePath().getParent();
			if (dir != null) {
				Files.createDirectories(dir);
			}
			Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
			try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
				GSON.toJson(config, w);
			}
			try {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			log.error("Could not save the config file: {}", file, e);
		}
	}
}
