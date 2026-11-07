package dev.playerjoinnotifier.core;

import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Watchlist / blocklist matching. An entry is either a player name (case-insensitive) or a UUID.
 * Rules: a blocklisted player is never notified; if the watchlist has at least one valid entry,
 * only players on it are notified; if the watchlist is empty, everyone (not blocked) is notified.
 */
public final class PlayerFilter {
	private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_.\\-]{1,32}");
	private static final Pattern UUID_FORM = Pattern.compile(
			"[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

	private PlayerFilter() {
	}

	/** Canonical comparison key (lower case) for a valid name or UUID; empty if the entry is invalid. */
	public static Optional<String> key(String raw) {
		if (raw == null) {
			return Optional.empty();
		}
		String s = raw.strip();
		if (UUID_FORM.matcher(s).matches() || NAME.matcher(s).matches()) {
			return Optional.of(s.toLowerCase(Locale.ROOT));
		}
		return Optional.empty();
	}

	/** Does this list entry refer to the given player? Invalid entries never match. */
	public static boolean matches(String entry, String name, UUID uuid) {
		Optional<String> key = key(entry);
		if (key.isEmpty()) {
			return false;
		}
		if (UUID_FORM.matcher(key.get()).matches()) {
			return uuid != null && key.get().equals(uuid.toString().toLowerCase(Locale.ROOT));
		}
		return name != null && key.get().equals(name.toLowerCase(Locale.ROOT));
	}

	public static boolean anyMatches(Collection<String> entries, String name, UUID uuid) {
		if (entries == null) {
			return false;
		}
		for (String e : entries) {
			if (matches(e, name, uuid)) {
				return true;
			}
		}
		return false;
	}

	/** True if the list has at least one valid entry. */
	public static boolean isActive(Collection<String> entries) {
		if (entries == null) {
			return false;
		}
		for (String e : entries) {
			if (key(e).isPresent()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Same as {@link #allows(Collection, Collection, String, UUID)}, but a list whose switch is off is ignored
	 * (its entries are kept in the config, they just do not apply).
	 */
	public static boolean allows(boolean watchlistEnabled, Collection<String> watchlist,
			boolean blocklistEnabled, Collection<String> blocklist, String name, UUID uuid) {
		return allows(watchlistEnabled ? watchlist : null, blocklistEnabled ? blocklist : null, name, uuid);
	}

	/** Whether a notification about this player should be shown. */
	public static boolean allows(Collection<String> watchlist, Collection<String> blocklist, String name, UUID uuid) {
		if (anyMatches(blocklist, name, uuid)) {
			return false;
		}
		return !isActive(watchlist) || anyMatches(watchlist, name, uuid);
	}
}
