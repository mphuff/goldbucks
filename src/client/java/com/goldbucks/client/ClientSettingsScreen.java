package com.goldbucks.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** This player's own settings: sale sound, sale message, and where the side bar goes. */
public class ClientSettingsScreen extends Screen {
	private final Screen parent;

	public ClientSettingsScreen(Screen parent) {
		super(Component.literal("Goldbucks settings (just for you)"));
		this.parent = parent;
	}

	private static String onOff(boolean value) {
		return value ? "On" : "Off";
	}

	@Override
	protected void init() {
		ClientSettings settings = ClientSettings.get();
		int x = width / 2 - 110;
		int y = height / 2 - 50;

		addRenderableWidget(Button.builder(Component.literal("Sale sound: " + onOff(settings.saleSound)), b -> {
			settings.saleSound = !settings.saleSound;
			b.setMessage(Component.literal("Sale sound: " + onOff(settings.saleSound)));
		}).bounds(x, y, 220, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Sale message above hotbar: " + onOff(settings.saleMessage)), b -> {
			settings.saleMessage = !settings.saleMessage;
			b.setMessage(Component.literal("Sale message above hotbar: " + onOff(settings.saleMessage)));
		}).bounds(x, y + 24, 220, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Side bar: " + onOff(settings.showSidebar)), b -> {
			settings.showSidebar = !settings.showSidebar;
			b.setMessage(Component.literal("Side bar: " + onOff(settings.showSidebar)));
		}).bounds(x, y + 48, 220, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Side bar on the " + (settings.sidebarOnLeft ? "left" : "right")), b -> {
			settings.sidebarOnLeft = !settings.sidebarOnLeft;
			b.setMessage(Component.literal("Side bar on the " + (settings.sidebarOnLeft ? "left" : "right")));
		}).bounds(x, y + 72, 220, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(x, y + 104, 220, 20).build());
	}

	@Override
	public void onClose() {
		ClientSettings.get().save();
		minecraft.gui.setScreen(parent);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.centeredText(font, title.getString(), width / 2, height / 2 - 72, 0xFFFFD700);
	}
}
