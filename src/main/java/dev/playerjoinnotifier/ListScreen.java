package dev.playerjoinnotifier;

import dev.playerjoinnotifier.ConfigManager.AddResult;
import dev.playerjoinnotifier.ConfigManager.PlayerList;
import dev.playerjoinnotifier.core.PlayerFilter;
import dev.playerjoinnotifier.core.ServerAddress;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Editor for one of the mod's lists (registered servers, watchlist, blocklist), opened from
 * {@link SettingsScreen}. Adding or removing an entry is saved to the config file immediately.
 * The list is paged so it fits any window size.
 */
final class ListScreen extends Screen {
	/** What the screen edits. */
	interface Source {
		String title();

		String emptyText();

		String inputLabel();

		List<String> entries();

		/** False for entries that cannot be used (hand-edited typos); shown in red. */
		boolean isValid(String entry);

		AddResult add(String raw);

		void remove(String entry);

		String duplicateMessage(String raw);

		String invalidMessage(String raw);
	}

	static Source servers(PlayerJoinNotifierMod mod) {
		return new Source() {
			public String title() {
				return Texts.str("gui.servers.title");
			}

			public String emptyText() {
				return Texts.str("gui.servers.empty");
			}

			public String inputLabel() {
				return Texts.str("gui.servers.input_label");
			}

			public List<String> entries() {
				return List.copyOf(mod.config().get().servers);
			}

			public boolean isValid(String entry) {
				return ServerAddress.normalize(entry).isPresent();
			}

			public AddResult add(String raw) {
				AddResult result = mod.config().addServer(raw);
				if (result == AddResult.ADDED) {
					mod.requestRestart();
				}
				return result;
			}

			public void remove(String entry) {
				mod.config().removeServer(entry);
				mod.requestRestart();
			}

			public String duplicateMessage(String raw) {
				return Texts.str("command.add.duplicate", raw.strip());
			}

			public String invalidMessage(String raw) {
				return Texts.str("command.add.invalid", raw);
			}
		};
	}

	static Source players(PlayerJoinNotifierMod mod, PlayerList which) {
		boolean watch = which == PlayerList.WATCH;
		String noun = Texts.str(watch ? "command.list_name.watch" : "command.list_name.block");
		return new Source() {
			public String title() {
				return Texts.str(watch ? "gui.watchlist.title" : "gui.blocklist.title");
			}

			public String emptyText() {
				return Texts.str(watch ? "gui.watchlist.empty" : "gui.blocklist.empty");
			}

			public String inputLabel() {
				return Texts.str(watch ? "gui.watchlist.input_label" : "gui.blocklist.input_label");
			}

			public List<String> entries() {
				NotifierConfig cfg = mod.config().get();
				return List.copyOf(watch ? cfg.watchlist : cfg.blocklist);
			}

			public boolean isValid(String entry) {
				return PlayerFilter.key(entry).isPresent();
			}

			public AddResult add(String raw) {
				return mod.config().addPlayer(which, raw);
			}

			public void remove(String entry) {
				mod.config().removePlayer(which, entry);
			}

			public String duplicateMessage(String raw) {
				return Texts.str("command.player.duplicate", noun, raw.strip());
			}

			public String invalidMessage(String raw) {
				return Texts.str("command.player.invalid", raw);
			}
		};
	}

	private static final int ROW = 22;
	private static final int LIST_TOP = 38;
	private static final int BOTTOM_RESERVED = 100;
	private static final int LIST_WIDTH = 300;

	private final Screen parent;
	private final Source source;
	private final int page;
	private final String status;
	private final String boxText;

	private EditBox addBox;
	private List<String> entries = List.of();
	private int pageSize = 5;
	private int pageCount = 1;

	ListScreen(Screen parent, Source source, int page, String status, String boxText) {
		super(Component.literal(source.title()));
		this.parent = parent;
		this.source = source;
		this.page = page;
		this.status = status;
		this.boxText = boxText;
	}

	private void reopen(int toPage, String message, String text) {
		this.minecraft.gui.setScreen(new ListScreen(this.parent, this.source, toPage, message, text));
	}

	@Override
	protected void init() {
		entries = source.entries();
		pageSize = Math.max(1, Math.min(10, (this.height - LIST_TOP - BOTTOM_RESERVED) / ROW));
		pageCount = Math.max(1, (entries.size() + pageSize - 1) / pageSize);
		int current = Math.max(0, Math.min(page, pageCount - 1));

		int left = this.width / 2 - LIST_WIDTH / 2;
		int first = current * pageSize;
		int last = Math.min(entries.size(), first + pageSize);
		for (int i = first; i < last; i++) {
			String entry = entries.get(i);
			int rowY = LIST_TOP + (i - first) * ROW;
			this.addRenderableWidget(Button.builder(Texts.tr("gui.list.remove"), button -> {
				source.remove(entry);
				reopen(current, "", this.addBox.getValue());
			}).pos(left + LIST_WIDTH - 60, rowY).size(60, 20).build());
		}

		// Pager
		int pagerY = this.height - BOTTOM_RESERVED + 6;
		Button prev = Button.builder(Component.literal("<"),
				button -> reopen(current - 1, "", this.addBox.getValue()))
				.pos(this.width / 2 - 70, pagerY).size(20, 20).build();
		prev.active = current > 0;
		this.addRenderableWidget(prev);
		Button next = Button.builder(Component.literal(">"),
				button -> reopen(current + 1, "", this.addBox.getValue()))
				.pos(this.width / 2 + 50, pagerY).size(20, 20).build();
		next.active = current < pageCount - 1;
		this.addRenderableWidget(next);

		// Add box
		int boxY = this.height - 54;
		this.addBox = new EditBox(this.font, left, boxY, LIST_WIDTH - 66, 20, null, Component.literal(""));
		this.addBox.setMaxLength(255);
		this.addBox.setValue(boxText);
		this.addRenderableWidget(this.addBox);
		this.addRenderableWidget(Button.builder(Texts.tr("gui.list.add"), button -> {
			String raw = this.addBox.getValue();
			switch (source.add(raw)) {
				case ADDED -> reopen(Math.max(0, (source.entries().size() - 1) / pageSize), "", "");
				case DUPLICATE -> reopen(current, source.duplicateMessage(raw), raw);
				case INVALID -> reopen(current, source.invalidMessage(raw), raw);
			}
		}).pos(left + LIST_WIDTH - 60, boxY).size(60, 20).build());

		this.addRenderableWidget(Button.builder(Texts.tr("gui.back"), button -> onClose())
				.pos(this.width / 2 - 100, this.height - 28).size(200, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.centeredText(this.font, this.title, this.width / 2, 14, 0xFFFFFFFF);

		int left = this.width / 2 - LIST_WIDTH / 2;
		int current = Math.max(0, Math.min(page, pageCount - 1));
		if (entries.isEmpty()) {
			graphics.centeredText(this.font, Component.literal(source.emptyText()), this.width / 2, LIST_TOP + 6, 0xFFAAAAAA);
		}
		int first = current * pageSize;
		int last = Math.min(entries.size(), first + pageSize);
		for (int i = first; i < last; i++) {
			String entry = entries.get(i);
			String label = fit(entry, LIST_WIDTH - 70);
			boolean valid = source.isValid(entry);
			Component text = valid ? Component.literal(label) : Component.literal(label + " " + Texts.str("gui.list.invalid"));
			graphics.text(this.font, text, left, LIST_TOP + (i - first) * ROW + 6, valid ? 0xFFFFFFFF : 0xFFFF5555);
		}

		graphics.centeredText(this.font, Texts.tr("gui.list.page", current + 1, pageCount), this.width / 2,
				this.height - BOTTOM_RESERVED + 12, 0xFFAAAAAA);
		graphics.text(this.font, Component.literal(source.inputLabel()), left, this.height - 54 - 12, 0xFFAAAAAA);
		if (!status.isEmpty()) {
			graphics.centeredText(this.font, Component.literal(status), this.width / 2, this.height - 54 + 24, 0xFFFF5555);
		}
	}

	/** Shortens text with "..." so it fits in the given pixel width. */
	private String fit(String s, int maxWidth) {
		if (this.font.width(Component.literal(s)) <= maxWidth) {
			return s;
		}
		String t = s;
		while (t.length() > 1 && this.font.width(Component.literal(t + "...")) > maxWidth) {
			t = t.substring(0, t.length() - 1);
		}
		return t + "...";
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}
}
