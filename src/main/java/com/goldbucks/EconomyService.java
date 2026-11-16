package com.goldbucks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import com.goldbucks.economy.Account;
import com.goldbucks.economy.Economy;
import com.goldbucks.net.BalancesPayload;

/** Connects the rule engine to the running game. */
public final class EconomyService {
	private static List<BalancesPayload.Entry> lastSent = List.of();
	private static boolean resend = true;

	private EconomyService() {
	}

	public static Economy economy(MinecraftServer server) {
		return EconomyState.get(server).economy();
	}

	/** Call after anything changes a record so it is saved and shown. */
	public static void changed(MinecraftServer server) {
		EconomyState.get(server).setDirty();
	}

	public static Account onJoin(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Economy economy = economy(server);
		boolean firstVisit = !economy.hasAccount(player.getUUID());
		Account account = economy.join(player.getUUID(), player.getGameProfile().name());
		changed(server);
		resend = true;

		if (firstVisit) {
			GoldbucksMod.LOGGER.info("Opened an account for {} with ${}", account.name(), account.balance());
		}

		return account;
	}

	/** The side bar's contents: online players, richest first. */
	public static List<BalancesPayload.Entry> sidebar(MinecraftServer server) {
		Economy economy = economy(server);
		List<BalancesPayload.Entry> entries = new ArrayList<>();

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Account account = economy.account(player.getUUID());

			if (account != null) {
				net.minecraft.world.scores.TeamColor colour = account.nameColour() == null ? null
						: net.minecraft.world.scores.TeamColor.byName(account.nameColour());
				entries.add(new BalancesPayload.Entry(account.name(), account.balance(), economy.netWorth(account.id()), colour == null ? -1 : colour.rgb()));
			}
		}

		entries.sort(Comparator.comparingInt(BalancesPayload.Entry::netWorth).reversed()
				.thenComparing(BalancesPayload.Entry::name));
		return entries;
	}

	/** Runs every server tick; sends the side bar when it has changed. */
	public static void tick(MinecraftServer server) {
		if (!Activation.active()) {
			return;
		}

		if (!resend && server.getTickCount() % 20 != 0) {
			return;
		}

		List<BalancesPayload.Entry> entries = sidebar(server);

		if (!resend && entries.equals(lastSent)) {
			return;
		}

		resend = false;
		lastSent = entries;
		BalancesPayload payload = new BalancesPayload(entries);

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (ServerPlayNetworking.canSend(player, BalancesPayload.TYPE)) {
				ServerPlayNetworking.send(player, payload);
			}
		}
	}

	/** Sends the side bar again on the next tick, for example after a name colour changes. */
	public static void resendSidebar() {
		resend = true;
	}

	static void reset() {
		lastSent = List.of();
		resend = true;
	}
}
