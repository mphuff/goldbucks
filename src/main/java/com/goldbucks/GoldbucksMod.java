package com.goldbucks;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.goldbucks.net.BalancesPayload;

public class GoldbucksMod implements ModInitializer {
	public static final String MOD_ID = "goldbucks";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.clientboundPlay().register(BalancesPayload.TYPE, BalancesPayload.CODEC);

		// Decides first whether this world is a Goldbucks world; everything else does nothing if not.
		Activation.register();
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (Activation.active()) {
				onJoin(server, handler.getPlayer());
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			if (Activation.active()) {
				EconomyService.changed(server);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(EconomyService::tick);

		SettingsService.register();
		Menus.register();
		Notices.register();
		WorldRules.register();
		JobsService.register();
		LandService.register();
		ShopService.register();
		NameTags.register();
		BusinessService.register();
		GuideService.register();
		StoreService.register();
		BoardService.register();
		BankService.register();
		HeraldService.register();
		MayorService.register();
		EventService.register();
		HostService.register();
		Townspeople.register();
		UpgradeBuilder.register();
		Achievements.register();

		LOGGER.info("Goldbucks loaded");
	}

	/** Everything that happens when a player arrives in a Goldbucks world (or when Goldbucks is switched on). */
	static void onJoin(net.minecraft.server.MinecraftServer server, net.minecraft.server.level.ServerPlayer player) {
		WorldRules.onJoin(player);
		EconomyService.onJoin(player);
		LandService.onJoin(player);
		GuideService.giveFarmKitOnce(player);
		JobsService.onJoin(player);
		HeraldService.loginSummary(player);
		Achievements.welcome(player);
		NameTags.refresh(server);

		if (WorldRules.isHost(server, player)) {
			SettingsService.tellHost(server);
		}
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
