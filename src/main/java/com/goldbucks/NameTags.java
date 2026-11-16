package com.goldbucks;

import java.util.Optional;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.TeamColor;

import com.goldbucks.economy.Account;
import com.goldbucks.economy.Business;
import com.goldbucks.economy.Economy;
import com.goldbucks.economy.Level;

/**
 * What shows around a player's name in the world and the tab list: their title in front
 * ("Expert Cook", "Mayor"), their name colour, and a red debt label after it.
 */
public final class NameTags {
	private static final String TEAM_PREFIX = "gbname_";

	private NameTags() {
	}

	static String teamName(ServerPlayer player) {
		return TEAM_PREFIX + player.getUUID().toString().substring(0, 8);
	}

	/** The debt label a player currently wears, or an empty string. */
	public static String labelOf(ServerPlayer player) {
		PlayerTeam team = player.level().getServer().getScoreboard().getPlayerTeam(teamName(player));
		return team == null ? "" : team.getPlayerSuffix().getString();
	}

	/** The title in front of a player's name, or an empty string. */
	public static String titleOf(ServerPlayer player) {
		PlayerTeam team = player.level().getServer().getScoreboard().getPlayerTeam(teamName(player));
		return team == null ? "" : team.getPlayerPrefix().getString().strip();
	}

	/** "Expert Cook", "Mayor" (and the job title too), or "" for a player with no job. */
	public static String title(Economy economy, java.util.UUID player) {
		Account account = economy.account(player);
		String job = "";

		for (Business business : economy.businessesOf(player)) {
			Level level = account.level(business.job());
			job = (level == Level.APPRENTICE ? "" : level.title() + " ") + business.job().title();
			break;
		}

		if (player.equals(economy.town().mayor())) {
			return job.isEmpty() ? "Mayor" : "Mayor & " + job;
		}

		return job;
	}

	public static void refresh(MinecraftServer server) {
		Scoreboard scoreboard = server.getScoreboard();
		Economy economy = EconomyService.economy(server);

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Account account = economy.account(player.getUUID());

			if (account == null) {
				continue;
			}

			String name = teamName(player);
			PlayerTeam team = scoreboard.getPlayerTeam(name);

			if (team == null) {
				team = scoreboard.addPlayerTeam(name);
			}

			if (scoreboard.getPlayersTeam(player.getScoreboardName()) != team) {
				scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
			}

			String title = title(economy, player.getUUID());
			MutableComponent prefix = Component.literal(title.isEmpty() ? "" : title + " ").withStyle(
					player.getUUID().equals(economy.town().mayor()) ? ChatFormatting.GOLD : ChatFormatting.GRAY);
			String debt = account.inDebt() ? " DEBT " + Text.money(account.balance()) : "";

			if (!team.getPlayerPrefix().getString().equals(prefix.getString())) {
				team.setPlayerPrefix(prefix);
			}

			if (!team.getPlayerSuffix().getString().equals(debt)) {
				team.setPlayerSuffix(Component.literal(debt).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
			}

			Optional<TeamColor> colour = Optional.ofNullable(account.nameColour() == null ? null : TeamColor.byName(account.nameColour()));

			if (!team.getColor().equals(colour)) {
				team.setColor(colour);
			}
		}
	}

	public static void register() {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (Activation.active() && server.getTickCount() % 20 == 0) {
				refresh(server);
			}
		});
	}
}
