package com.goldbucks.client;

import java.util.List;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import com.goldbucks.net.MenuActionPayload;
import com.goldbucks.net.MenuPayload;

/**
 * A menu sent by the server: the Town Store, the Town Board, the bank or the ballot.
 * Each row is a line of text with an optional button on the right. Scroll with the mouse wheel.
 */
public class MenuScreen extends Screen {
	private static final int ROW = 22;
	private static final int WIDTH = 340;
	private final MenuPayload menu;
	private int scroll;

	public MenuScreen(MenuPayload menu) {
		super(Component.literal(menu.title()));
		this.menu = menu;
	}

	public MenuPayload menu() {
		return menu;
	}

	private int top() {
		return 44;
	}

	private int visibleRows() {
		return Math.max(1, (height - top() - 34) / ROW);
	}

	@Override
	protected void init() {
		List<MenuPayload.Row> rows = menu.rows();
		int left = width / 2 - WIDTH / 2;
		int y = top();

		for (int i = scroll; i < rows.size() && i < scroll + visibleRows(); i++) {
			MenuPayload.Row row = rows.get(i);

			if (!row.button().isEmpty()) {
				Button button = Button.builder(Component.literal(row.button()), pressed -> {
					ClientPlayNetworking.send(new MenuActionPayload(row.command()));
				}).bounds(left + WIDTH - 70, y, 70, 20).build();
				button.active = row.enabled();

				if (!row.tooltip().isEmpty()) {
					button.setTooltip(Tooltip.create(Component.literal(row.tooltip())));
				}

				addRenderableWidget(button);
			}

			y += ROW;
		}

		addRenderableWidget(Button.builder(Component.literal("Close"), pressed -> onClose())
				.bounds(width / 2 - 50, height - 28, 100, 20).build());
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		int most = Math.max(0, menu.rows().size() - visibleRows());
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
		graphics.centeredText(font, menu.title(), width / 2, 12, 0xFFFFD700);
		graphics.centeredText(font, menu.subtitle(), width / 2, 26, 0xFFFFFFFF);
		List<MenuPayload.Row> rows = menu.rows();
		int y = top();

		for (int i = scroll; i < rows.size() && i < scroll + visibleRows(); i++) {
			MenuPayload.Row row = rows.get(i);
			boolean heading = row.button().isEmpty() && row.text().startsWith("--");
			int colour = heading ? 0xFFFFD700 : row.button().isEmpty() || row.enabled() ? 0xFFFFFFFF : 0xFFAAAAAA;
			String text = font.plainSubstrByWidth(row.text(), WIDTH - (row.button().isEmpty() ? 0 : 76));
			graphics.text(font, text, left, y + 6, colour, true);
			y += ROW;
		}

		if (rows.size() > visibleRows()) {
			graphics.centeredText(font, "Scroll for more (" + (scroll + 1) + "-" + Math.min(rows.size(), scroll + visibleRows()) + " of "
					+ rows.size() + ")", width / 2, height - 40, 0xFFAAAAAA);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
