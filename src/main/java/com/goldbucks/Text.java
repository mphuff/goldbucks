package com.goldbucks;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/** Small helpers for the chat messages the mod sends. */
public final class Text {
	private Text() {
	}

	public static String money(int amount) {
		return amount < 0 ? "-$" + (-amount) : "$" + amount;
	}

	public static void tell(ServerPlayer player, String message) {
		player.sendSystemMessage(Component.literal("[Goldbucks] ").withStyle(ChatFormatting.GOLD)
				.append(Component.literal(message).withStyle(ChatFormatting.WHITE)));
	}

	public static void tell(ServerPlayer player, Component message) {
		player.sendSystemMessage(Component.literal("[Goldbucks] ").withStyle(ChatFormatting.GOLD).append(message));
	}

	public static void warn(ServerPlayer player, String message) {
		player.sendSystemMessage(Component.literal("[Goldbucks] ").withStyle(ChatFormatting.GOLD)
				.append(Component.literal(message).withStyle(ChatFormatting.RED)));
	}

	/** Chat text that runs a command when clicked. */
	public static MutableComponent button(String label, String command) {
		return Component.literal("[" + label + "]").withStyle(style -> style
				.withColor(ChatFormatting.GREEN)
				.withUnderlined(true)
				.withClickEvent(new ClickEvent.RunCommand(command)));
	}
}
