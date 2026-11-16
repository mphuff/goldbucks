package com.goldbucks;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import com.goldbucks.net.NoticePayload;

/** Good news on screen: sale pings above the hotbar, and big titles for payday, level-ups and the morning news. */
public final class Notices {
	private Notices() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(NoticePayload.TYPE, NoticePayload.CODEC);
	}

	/** "Ka-ching!" wherever the owner is. The player's own settings decide the sound and the line above the hotbar. */
	public static void sale(ServerPlayer owner, String text, boolean townsfolk) {
		if (ServerPlayNetworking.canSend(owner, NoticePayload.TYPE)) {
			ServerPlayNetworking.send(owner, new NoticePayload("Ka-ching! " + text, townsfolk ? NoticePayload.TOWNSFOLK_SALE : NoticePayload.SALE));
		} else {
			owner.sendOverlayMessage(Component.literal("Ka-ching! " + text).withStyle(ChatFormatting.GOLD));
			owner.level().playSound(null, owner.blockPosition(), townsfolk ? SoundEvents.VILLAGER_YES : SoundEvents.EXPERIENCE_ORB_PICKUP,
					SoundSource.PLAYERS, 0.8f, 1.4f);
		}

		Text.tell(owner, text);
	}

	/** A big title in the middle of the screen. */
	public static void title(ServerPlayer player, String title, String subtitle) {
		player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
		player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(subtitle).withStyle(ChatFormatting.YELLOW)));
		player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(title).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
	}
}
