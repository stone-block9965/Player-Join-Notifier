package dev.playerjoinnotifier;

import dev.playerjoinnotifier.core.ServerAddress;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Registered-server editor (opened from {@link SettingsScreen}). Adding or removing a server is saved to
 * the config file immediately. The list is paged so it fits any window size.
 */
final class ServersScreen extends Screen {
	private static final int ROW = 22;
	private static final int LIST_TOP = 38;
	private static final int BOTTOM_RESERVED = 100;
	private static final int LIST_WIDTH = 300;

	private final Screen parent;
	private final int page;
	private final String status;
	private final String boxText;

	private EditBox addBox;
	private List<String> servers = List.of();
	private int pageSize = 5;
	private int pageCount = 1;

	ServersScreen(Screen parent, int page, String status, String boxText) {
		super(Texts.tr("gui.servers.title"));
		this.parent = parent;
		this.page = page;
		this.status = status;
		this.boxText = boxText;
	}

	@Override
	protected void init() {
		PlayerJoinNotifierMod mod = PlayerJoinNotifierMod.get();
		if (mod == null) {
			return;
		}
		servers = List.copyOf(mod.config().get().servers);
		pageSize = Math.max(1, Math.min(10, (this.height - LIST_TOP - BOTTOM_RESERVED) / ROW));
		pageCount = Math.max(1, (servers.size() + pageSize - 1) / pageSize);
		int current = Math.max(0, Math.min(page, pageCount - 1));

		int left = this.width / 2 - LIST_WIDTH / 2;
		int first = current * pageSize;
		int last = Math.min(servers.size(), first + pageSize);
		for (int i = first; i < last; i++) {
			String entry = servers.get(i);
			int rowY = LIST_TOP + (i - first) * ROW;
			this.addRenderableWidget(Button.builder(Texts.tr("gui.servers.remove"), button -> {
				mod.config().removeServer(entry);
				mod.requestRestart();
				this.minecraft.gui.setScreen(new ServersScreen(this.parent, current, "", this.addBox.getValue()));
			}).pos(left + LIST_WIDTH - 60, rowY).size(60, 20).build());
		}

		// Pager
		int pagerY = this.height - BOTTOM_RESERVED + 6;
		Button prev = Button.builder(Component.literal("<"),
				button -> this.minecraft.gui.setScreen(new ServersScreen(this.parent, current - 1, "", this.addBox.getValue())))
				.pos(this.width / 2 - 70, pagerY).size(20, 20).build();
		prev.active = current > 0;
		this.addRenderableWidget(prev);
		Button next = Button.builder(Component.literal(">"),
				button -> this.minecraft.gui.setScreen(new ServersScreen(this.parent, current + 1, "", this.addBox.getValue())))
				.pos(this.width / 2 + 50, pagerY).size(20, 20).build();
		next.active = current < pageCount - 1;
		this.addRenderableWidget(next);

		// Add box
		int boxY = this.height - 54;
		this.addBox = new EditBox(this.font, left, boxY, LIST_WIDTH - 66, 20, null, Component.literal(""));
		this.addBox.setMaxLength(255);
		this.addBox.setValue(boxText);
		this.addRenderableWidget(this.addBox);
		this.addRenderableWidget(Button.builder(Texts.tr("gui.servers.add"), button -> {
			String raw = this.addBox.getValue();
			switch (mod.config().addServer(raw)) {
				case ADDED -> {
					mod.requestRestart();
					int lastPage = Math.max(0, (mod.config().get().servers.size() - 1) / pageSize);
					this.minecraft.gui.setScreen(new ServersScreen(this.parent, lastPage, "", ""));
				}
				case DUPLICATE -> this.minecraft.gui.setScreen(new ServersScreen(this.parent, current,
						Texts.str("command.add.duplicate", raw.strip()), raw));
				case INVALID -> this.minecraft.gui.setScreen(new ServersScreen(this.parent, current,
						Texts.str("command.add.invalid", raw), raw));
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
		if (servers.isEmpty()) {
			graphics.centeredText(this.font, Texts.tr("gui.servers.empty"), this.width / 2, LIST_TOP + 6, 0xFFAAAAAA);
		}
		int first = current * pageSize;
		int last = Math.min(servers.size(), first + pageSize);
		for (int i = first; i < last; i++) {
			String entry = servers.get(i);
			String label = fit(entry, LIST_WIDTH - 70);
			boolean valid = ServerAddress.normalize(entry).isPresent();
			Component text = valid ? Component.literal(label) : Component.literal(label + " " + Texts.str("gui.servers.invalid"));
			graphics.text(this.font, text, left, LIST_TOP + (i - first) * ROW + 6, valid ? 0xFFFFFFFF : 0xFFFF5555);
		}

		graphics.centeredText(this.font, Texts.tr("gui.servers.page", current + 1, pageCount), this.width / 2,
				this.height - BOTTOM_RESERVED + 12, 0xFFAAAAAA);
		graphics.text(this.font, Texts.tr("gui.servers.input_label"), left, this.height - 54 - 12, 0xFFAAAAAA);
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
