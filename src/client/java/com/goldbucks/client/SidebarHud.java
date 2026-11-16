package com.goldbucks.client;

import java.util.List;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import com.goldbucks.GoldbucksMod;
import com.goldbucks.net.BalancesPayload;

/** Draws the money side bar on the right edge of the screen. */
public final class SidebarHud {
	private static final Identifier GOLDBUCK = GoldbucksMod.id("textures/gui/goldbuck.png");
	private static final int ICON = 10;
	private static final int ROW = 11;
	private static final int PAD = 3;
	private static final int BACKGROUND = 0x66000000;
	private static final int TITLE_BACKGROUND = 0x99000000;
	private static final int WHITE = 0xFFFFFFFF;
	private static final int GREEN = 0xFF55FF55;
	private static final int RED = 0xFFFF5555;
	private static final int YELLOW = 0xFFFFD700;

	private SidebarHud() {
	}

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		List<BalancesPayload.Entry> entries = ClientBalances.entries();
		Minecraft minecraft = Minecraft.getInstance();

		if (entries.isEmpty() || !ClientSettings.get().showSidebar) {
			return;
		}

		Font font = minecraft.font;
		// Two columns: Cash is what you can spend now; Worth adds savings, property and businesses.
		String cashTitle = "Cash";
		String worthTitle = "Worth";
		int nameWidth = 0;
		int cashWidth = font.width(cashTitle);
		int worthWidth = font.width(worthTitle);

		for (BalancesPayload.Entry entry : entries) {
			nameWidth = Math.max(nameWidth, font.width(entry.name()));
			cashWidth = Math.max(cashWidth, font.width(money(entry.cash())));
			worthWidth = Math.max(worthWidth, font.width(money(entry.netWorth())));
		}

		int gap = 8;
		int width = PAD + ICON + 2 + nameWidth + gap + cashWidth + gap + worthWidth + PAD;
		int height = ROW * (entries.size() + 1) + PAD;
		boolean onLeft = ClientSettings.get().sidebarOnLeft;
		int right = onLeft ? 2 + width : graphics.guiWidth() - 2;
		int left = right - width;
		int top = graphics.guiHeight() / 2 - height / 2;
		int worthRight = right - PAD;
		int cashRight = worthRight - worthWidth - gap;

		graphics.fill(left, top, right, top + ROW, TITLE_BACKGROUND);
		graphics.fill(left, top + ROW, right, top + height, BACKGROUND);
		icon(graphics, left + PAD, top);
		graphics.text(font, cashTitle, cashRight - font.width(cashTitle), top + 2, YELLOW, false);
		graphics.text(font, worthTitle, worthRight - font.width(worthTitle), top + 2, WHITE, false);

		int y = top + ROW + 1;

		for (BalancesPayload.Entry entry : entries) {
			String cash = money(entry.cash());
			String worth = money(entry.netWorth());
			graphics.text(font, entry.name(), left + PAD + ICON + 2, y + 1, entry.colour() < 0 ? WHITE : 0xFF000000 | entry.colour(), false);
			graphics.text(font, cash, cashRight - font.width(cash), y + 1, entry.cash() < 0 ? RED : YELLOW, false);
			graphics.text(font, worth, worthRight - font.width(worth), y + 1, entry.netWorth() < 0 ? RED : GREEN, false);
			y += ROW;
		}
	}

	private static void icon(GuiGraphicsExtractor graphics, int x, int y) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, GOLDBUCK, x, y, 0, 0, ICON, ICON, 64, 64, 64, 64);
	}

	static String money(int amount) {
		return amount < 0 ? "-$" + (-amount) : "$" + amount;
	}
}
