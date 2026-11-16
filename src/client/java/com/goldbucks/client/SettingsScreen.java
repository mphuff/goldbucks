package com.goldbucks.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import com.goldbucks.net.SaveSettingsPayload;
import com.goldbucks.net.SettingsPayload;

/**
 * The host's settings screen. Settings are grouped in tabs; numbers are typed, switches are clicked.
 * Save sends only what changed, and the game checks everything together before using any of it.
 */
public class SettingsScreen extends Screen {
	private static final int ROW = 24;
	private static final int WIDTH = 360;
	private final List<SettingsPayload.Entry> entries;
	private final List<String> problems;
	private final Map<String, String> values = new LinkedHashMap<>();
	private final List<String> tabs = new ArrayList<>();
	private String tab;
	private int scroll;

	public SettingsScreen(SettingsPayload payload) {
		super(Component.literal("World settings"));
		this.entries = payload.entries();
		this.problems = payload.problems();
		Set<String> seen = new LinkedHashSet<>();

		for (SettingsPayload.Entry entry : entries) {
			values.put(entry.name(), entry.value());
			seen.add(entry.tab());
		}

		tabs.addAll(seen);
		tab = tabs.isEmpty() ? "" : tabs.get(0);
	}

	/** Every setting whose value was changed on this screen. */
	public Map<String, String> changes() {
		Map<String, String> changes = new LinkedHashMap<>();

		for (SettingsPayload.Entry entry : entries) {
			String now = values.get(entry.name());

			if (!now.equals(entry.value())) {
				changes.put(entry.name(), now);
			}
		}

		return changes;
	}

	public void setValue(String name, String value) {
		values.put(name, value);
	}

	public void selectTab(String name) {
		tab = name;
		scroll = 0;
		rebuildWidgets();
	}

	private List<SettingsPayload.Entry> inTab() {
		return entries.stream().filter(entry -> entry.tab().equals(tab)).toList();
	}

	private int top() {
		return 58;
	}

	private int bottom() {
		return height - 36 - Math.min(3, problems.size()) * 10;
	}

	private int visibleRows() {
		return Math.max(1, (bottom() - top()) / ROW);
	}

	@Override
	protected void init() {
		int tabWidth = Math.min(80, (WIDTH - 4 * (tabs.size() - 1)) / Math.max(1, tabs.size()));
		int x = width / 2 - (tabWidth * tabs.size() + 4 * (tabs.size() - 1)) / 2;

		for (String name : tabs) {
			Button button = Button.builder(Component.literal(name), pressed -> selectTab(name)).bounds(x, 28, tabWidth, 20).build();
			button.active = !name.equals(tab);
			addRenderableWidget(button);
			x += tabWidth + 4;
		}

		int left = width / 2 - WIDTH / 2;
		int y = top();
		List<SettingsPayload.Entry> shown = inTab();

		for (int i = scroll; i < shown.size() && i < scroll + visibleRows(); i++) {
			SettingsPayload.Entry entry = shown.get(i);
			int fieldX = left + WIDTH - 90;

			if (entry.toggle()) {
				Button toggle = Button.builder(Component.literal(onOff(values.get(entry.name()))), pressed -> {
					values.put(entry.name(), values.get(entry.name()).equals("true") ? "false" : "true");
					pressed.setMessage(Component.literal(onOff(values.get(entry.name()))));
				}).bounds(fieldX, y, 90, 20).build();
				toggle.setTooltip(Tooltip.create(Component.literal(entry.name())));
				addRenderableWidget(toggle);
			} else {
				EditBox box = new EditBox(font, fieldX, y, 90, 20, Component.literal(entry.description()));
				box.setMaxLength(entry.name().equals("disabledEvents") ? 400 : 9);
				box.setValue(values.get(entry.name()));
				box.setResponder(text -> values.put(entry.name(), text));
				box.setTooltip(Tooltip.create(Component.literal(entry.name())));
				addRenderableWidget(box);
			}

			y += ROW;
		}

		addRenderableWidget(Button.builder(Component.literal("Save"), pressed -> save()).bounds(width / 2 - 104, height - 28, 100, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Cancel"), pressed -> onClose()).bounds(width / 2 + 4, height - 28, 100, 20).build());
	}

	private static String onOff(String value) {
		return value.equals("true") ? "On" : "Off";
	}

	private void save() {
		ClientPlayNetworking.send(new SaveSettingsPayload(changes()));
		onClose();
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		int most = Math.max(0, inTab().size() - visibleRows());
		int next = Math.max(0, Math.min(most, scroll - (int) Math.signum(vertical)));

		if (next != scroll) {
			scroll = next;
			rebuildWidgets();
		}

		return true;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		int left = width / 2 - WIDTH / 2;
		graphics.centeredText(font, "World settings (host only)", width / 2, 10, 0xFFFFD700);
		List<SettingsPayload.Entry> shown = inTab();
		int y = top();

		for (int i = scroll; i < shown.size() && i < scroll + visibleRows(); i++) {
			graphics.text(font, font.plainSubstrByWidth(shown.get(i).description(), WIDTH - 96), left, y + 6, 0xFFFFFFFF, true);
			y += ROW;
		}

		int py = bottom() + 2;

		for (int i = 0; i < Math.min(3, problems.size()); i++) {
			graphics.centeredText(font, font.plainSubstrByWidth(problems.get(i), WIDTH + 60), width / 2, py, 0xFFFF5555);
			py += 10;
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
