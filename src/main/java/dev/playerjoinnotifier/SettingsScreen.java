package dev.playerjoinnotifier;

import dev.playerjoinnotifier.ConfigManager.PlayerList;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Function;

/**
 * Config screen opened from Mod Menu. Every click is applied and saved to the config file immediately
 * (the same file the /joinnotify commands use), so changes are permanent.
 * Left column: on/off settings and language. Right column: servers, watchlist and blocklist (each list has an ON/OFF switch and an editor).
 */
final class SettingsScreen extends Screen {
	private static final int BUTTON_HEIGHT = 20;
	private static final int ROW = 24;
	private static final int GAP = 8;

	private final Screen parent;

	SettingsScreen(Screen parent) {
		super(Texts.tr("gui.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		PlayerJoinNotifierMod mod = PlayerJoinNotifierMod.get();
		if (mod == null) {
			return;
		}
		int colWidth = Math.min(200, (this.width - 20 - GAP) / 2);
		int leftX = this.width / 2 - GAP / 2 - colWidth;
		int rightX = this.width / 2 + GAP / 2;
		int top = 40;

		// Left column: toggles
		addToggle(mod, leftX, top, colWidth, "gui.enabled", c -> c.enabled, (c, v) -> c.enabled = v, true);
		addToggle(mod, leftX, top + ROW, colWidth, "gui.only_unfocused", c -> c.onlyWhenUnfocused,
				(c, v) -> c.onlyWhenUnfocused = v, false);
		addToggle(mod, leftX, top + ROW * 2, colWidth, "gui.notify_on_leave", c -> c.notifyOnLeave,
				(c, v) -> c.notifyOnLeave = v, false);
		addToggle(mod, leftX, top + ROW * 3, colWidth, "gui.sound", c -> c.sound, (c, v) -> c.sound = v, false);

		this.addRenderableWidget(Button.builder(languageLabel(mod), button -> {
			String next = switch (mod.config().get().language) {
				case "auto" -> "en";
				case "en" -> "ja";
				default -> "auto";
			};
			mod.config().update(c -> c.language = next);
			// Every label changes with the language, so reopen this screen
			this.minecraft.gui.setScreen(new SettingsScreen(this.parent));
		}).pos(leftX, top + ROW * 4).size(colWidth, BUTTON_HEIGHT).build());

		// Right column: lists
		this.addRenderableWidget(Button.builder(Texts.tr("gui.servers"),
				button -> this.minecraft.gui.setScreen(new ListScreen(this, ListScreen.servers(mod), 0, "", "")))
				.pos(rightX, top).size(colWidth, BUTTON_HEIGHT).build());
		addToggle(mod, rightX, top + ROW, colWidth, "gui.watchlist_enabled", c -> c.watchlistEnabled,
				(c, v) -> c.watchlistEnabled = v, false);
		this.addRenderableWidget(Button.builder(Texts.tr("gui.watchlist"),
				button -> this.minecraft.gui.setScreen(
						new ListScreen(this, ListScreen.players(mod, PlayerList.WATCH), 0, "", "")))
				.pos(rightX, top + ROW * 2).size(colWidth, BUTTON_HEIGHT).build());
		addToggle(mod, rightX, top + ROW * 3, colWidth, "gui.blocklist_enabled", c -> c.blocklistEnabled,
				(c, v) -> c.blocklistEnabled = v, false);
		this.addRenderableWidget(Button.builder(Texts.tr("gui.blocklist"),
				button -> this.minecraft.gui.setScreen(
						new ListScreen(this, ListScreen.players(mod, PlayerList.BLOCK), 0, "", "")))
				.pos(rightX, top + ROW * 4).size(colWidth, BUTTON_HEIGHT).build());

		this.addRenderableWidget(Button.builder(Texts.tr("gui.done"), button -> onClose())
				.pos(this.width / 2 - 100, this.height - 28).size(200, BUTTON_HEIGHT).build());
	}

	/** An ON/OFF button that flips the setting and saves it right away. */
	private void addToggle(PlayerJoinNotifierMod mod, int x, int y, int width, String labelKey,
			Function<NotifierConfig, Boolean> get, java.util.function.BiConsumer<NotifierConfig, Boolean> set,
			boolean restartSession) {
		this.addRenderableWidget(Button.builder(label(labelKey, get.apply(mod.config().get())), button -> {
			boolean next = !get.apply(mod.config().get());
			mod.config().update(c -> set.accept(c, next));
			if (restartSession) {
				mod.requestRestart();
			}
			button.setMessage(label(labelKey, next));
		}).pos(x, y).size(width, BUTTON_HEIGHT).build());
	}

	private static Component label(String key, boolean on) {
		return Texts.tr(key, Texts.tr(on ? "gui.on" : "gui.off"));
	}

	private static Component languageLabel(PlayerJoinNotifierMod mod) {
		return Texts.tr("gui.language", JoinNotifyCommand.languageLabel(mod.config().get().language));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.centeredText(this.font, this.title, this.width / 2, 16, 0xFFFFFFFF);
		graphics.centeredText(this.font, Texts.tr("gui.hint"), this.width / 2, 40 + ROW * 5 + 6, 0xFFAAAAAA);
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}
}
