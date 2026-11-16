package com.goldbucks.client;

import java.util.List;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import com.goldbucks.economy.Job;
import com.goldbucks.net.ChooseJobPayload;

/** The job menu shown on first login, and again whenever a player has no job. */
public class JobScreen extends Screen {
	private static final int BUTTON_WIDTH = 220;
	private static final int BUTTON_HEIGHT = 20;
	private static final int GAP = 2;

	private final List<Integer> availableJobs;
	private final String message;

	public JobScreen(List<Integer> availableJobs, String message) {
		super(Component.literal("Choose your job"));
		this.availableJobs = availableJobs;
		this.message = message;
	}

	@Override
	protected void init() {
		Job[] jobs = Job.values();
		int y = top();

		for (Job job : jobs) {
			boolean available = availableJobs.contains(job.ordinal());
			String label = job.title() + "  -  $" + job.dailyPay() + " a day" + (available ? "" : "  (taken)");
			Button button = Button.builder(Component.literal(label), pressed -> choose(job))
					.bounds(width / 2 - BUTTON_WIDTH / 2, y, BUTTON_WIDTH, BUTTON_HEIGHT)
					.tooltip(Tooltip.create(Component.literal("Daily task: " + job.task())))
					.build();
			button.active = available;
			addRenderableWidget(button);
			y += BUTTON_HEIGHT + GAP;
		}
	}

	/** Where the first button goes: centred, but always leaving room for the title above. */
	private int top() {
		int totalHeight = Job.values().length * (BUTTON_HEIGHT + GAP);
		return Math.max(30, height / 2 - totalHeight / 2 + 12);
	}

	private void choose(Job job) {
		ClientPlayNetworking.send(new ChooseJobPayload(job.ordinal()));
		minecraft.gui.setScreen(null);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		int top = top();
		graphics.centeredText(font, title, width / 2, top - 26, 0xFFFFFFFF);
		graphics.centeredText(font, message, width / 2, top - 14, 0xFFFFD700);
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
