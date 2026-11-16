package com.goldbucks.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import com.goldbucks.GoldbucksMod;
import com.goldbucks.net.BalancesPayload;
import com.goldbucks.net.JobMenuPayload;
import com.goldbucks.net.MenuPayload;
import com.goldbucks.net.NoticePayload;
import com.goldbucks.net.SettingsPayload;

public class GoldbucksModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(BalancesPayload.TYPE,
				(payload, context) -> ClientBalances.set(payload.entries()));
		ClientPlayNetworking.registerGlobalReceiver(JobMenuPayload.TYPE,
				(payload, context) -> context.client().gui.setScreen(new JobScreen(payload.availableJobs(), payload.message())));
		ClientPlayNetworking.registerGlobalReceiver(MenuPayload.TYPE,
				(payload, context) -> context.client().gui.setScreen(new MenuScreen(payload)));
		ClientPlayNetworking.registerGlobalReceiver(SettingsPayload.TYPE,
				(payload, context) -> context.client().gui.setScreen(new SettingsScreen(payload)));
		ClientPlayNetworking.registerGlobalReceiver(NoticePayload.TYPE, (payload, context) -> notice(context.client(), payload));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientBalances.clear());

		// /goldbucks settings is a server command (so it does not hide the other /goldbucks commands); it asks for this screen.
		ClientPlayNetworking.registerGlobalReceiver(com.goldbucks.net.OpenClientSettingsPayload.TYPE,
				(payload, context) -> context.client().gui.setScreen(new ClientSettingsScreen(null)));

		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, GoldbucksMod.id("sidebar"), SidebarHud::extract);
	}

	/** A sale: a line above the hotbar and a sound, as this player's own settings say. */
	static void notice(Minecraft client, NoticePayload payload) {
		ClientSettings settings = ClientSettings.get();

		if (settings.saleMessage && client.player != null) {
			client.player.sendOverlayMessage(Component.literal(payload.text()).withStyle(net.minecraft.ChatFormatting.GOLD));
		}

		if (settings.saleSound) {
			client.getSoundManager().play(payload.sound() == NoticePayload.TOWNSFOLK_SALE
					? SimpleSoundInstance.forUI(SoundEvents.VILLAGER_YES, 1.0f, 0.6f)
					: SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.4f, 0.8f));
		}
	}
}
