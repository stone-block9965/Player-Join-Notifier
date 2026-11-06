package dev.playerjoinnotifier;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Config screen opened from Mod Menu. Every click is applied and saved to the config file immediately
 * (the same file the /joinnotify commands use), so changes are permanent.
 */
final class SettingsScreen extends Screen {
	private static final int BUTTON_WIDTH = 220;
	private static final int BUTTON_HEIGHT = 20;
	private static final int ROW = 24;

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
		int x = this.width / 2 - BUTTON_WIDTH / 2;
		int y = this.height / 4 + 8;

		this.addRenderableWidget(Button.builder(enabledLabel(mod), button -> {
			mod.config().update(c -> c.enabled = !c.enabled);
			mod.requestRestart();
			button.setMessage(enabledLabel(mod));
		}).pos(x, y).size(BUTTON_WIDTH, BUTTON_HEIGHT).build());

		this.addRenderableWidget(Button.builder(unfocusedLabel(mod), button -> {
			mod.config().update(c -> c.onlyWhenUnfocused = !c.onlyWhenUnfocused);
			button.setMessage(unfocusedLabel(mod));
		}).pos(x, y + ROW).size(BUTTON_WIDTH, BUTTON_HEIGHT).build());

		this.addRenderableWidget(Button.builder(languageLabel(mod), button -> {
			String next = switch (mod.config().get().language) {
				case "auto" -> "en";
				case "en" -> "ja";
				default -> "auto";
			};
			mod.config().update(c -> c.language = next);
			// Every label changes with the language, so reopen this screen
			this.minecraft.gui.setScreen(new SettingsScreen(this.parent));
		}).pos(x, y + ROW * 2).size(BUTTON_WIDTH, BUTTON_HEIGHT).build());

		this.addRenderableWidget(Button.builder(Texts.tr("gui.servers"),
				button -> this.minecraft.gui.setScreen(new ServersScreen(this, 0, "", "")))
				.pos(x, y + ROW * 3).size(BUTTON_WIDTH, BUTTON_HEIGHT).build());

		this.addRenderableWidget(Button.builder(Texts.tr("gui.done"), button -> onClose())
				.pos(x, y + ROW * 5).size(BUTTON_WIDTH, BUTTON_HEIGHT).build());
	}

	private static Component onOff(boolean on) {
		return Texts.tr(on ? "gui.on" : "gui.off");
	}

	private static Component enabledLabel(PlayerJoinNotifierMod mod) {
		return Texts.tr("gui.enabled", onOff(mod.config().get().enabled));
	}

	private static Component unfocusedLabel(PlayerJoinNotifierMod mod) {
		return Texts.tr("gui.only_unfocused", onOff(mod.config().get().onlyWhenUnfocused));
	}

	private static Component languageLabel(PlayerJoinNotifierMod mod) {
		return Texts.tr("gui.language", JoinNotifyCommand.languageLabel(mod.config().get().language));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.centeredText(this.font, this.title, this.width / 2, this.height / 4 - 14, 0xFFFFFFFF);
		graphics.centeredText(this.font, Texts.tr("gui.hint"), this.width / 2, this.height / 4 + 8 + ROW * 4 + 4,
				0xFFAAAAAA);
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}
}
