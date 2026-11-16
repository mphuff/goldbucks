package com.goldbucks;

import java.util.Map;
import java.util.UUID;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import com.goldbucks.economy.Account;
import com.goldbucks.economy.Economy;
import com.goldbucks.economy.Result;
import com.goldbucks.economy.Town;
import com.goldbucks.economy.TownData;
import com.goldbucks.economy.TownEvent;

/** Mayor elections and the Mayor's powers. */
public final class MayorService {
	private MayorService() {
	}

	public static void register() {
		WorldRules.openCommand("mayor");
		WorldRules.openCommand("vote");

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(Commands.literal("mayor")
					.executes(context -> {
						status(context.getSource().getPlayerOrException());
						return 1;
					})
					.then(Commands.literal("run")
							.executes(context -> run(context.getSource().getPlayerOrException(), ""))
							.then(Commands.argument("promise", StringArgumentType.greedyString())
									.executes(context -> run(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "promise")))))
					.then(Commands.literal("tax").then(Commands.argument("percent", IntegerArgumentType.integer(0, 100))
							.executes(context -> power(context.getSource().getPlayerOrException(), "tax",
									economy -> economy.setMayorTax(context.getSource().getPlayerOrException().getUUID(),
											IntegerArgumentType.getInteger(context, "percent"))))))
					.then(Commands.literal("market").then(Commands.argument("event", StringArgumentType.word())
							.suggests((context, builder) -> {
								EconomyService.economy(context.getSource().getServer()).town().marketChoices().forEach(e -> builder.suggest(e.id()));
								return builder.buildFuture();
							})
							.executes(context -> power(context.getSource().getPlayerOrException(), "market",
									economy -> economy.pickMarketDay(context.getSource().getPlayerOrException().getUUID(),
											TownEvent.parse(StringArgumentType.getString(context, "event")))))))
					.then(Commands.literal("holiday").executes(context -> power(context.getSource().getPlayerOrException(), "holiday",
							economy -> economy.declareHoliday(context.getSource().getPlayerOrException().getUUID()))))
					.then(Commands.literal("name").then(Commands.argument("name", StringArgumentType.greedyString())
							.executes(context -> power(context.getSource().getPlayerOrException(), "name",
									economy -> economy.nameTown(context.getSource().getPlayerOrException().getUUID(),
											trim(StringArgumentType.getString(context, "name"), 24))))))
					.then(Commands.literal("announce").then(Commands.argument("message", StringArgumentType.greedyString())
							.executes(context -> power(context.getSource().getPlayerOrException(), "announce",
									economy -> economy.announce(context.getSource().getPlayerOrException().getUUID(),
											trim(StringArgumentType.getString(context, "message"), 80)))))));

			dispatcher.register(Commands.literal("vote")
					.executes(context -> {
						ballot(context.getSource().getPlayerOrException());
						return 1;
					})
					.then(Commands.argument("player", EntityArgument.player()).executes(context ->
							vote(context.getSource().getPlayerOrException(), EntityArgument.getPlayer(context, "player")))));
		});
	}

	private static String trim(String text, int most) {
		return text.length() > most ? text.substring(0, most) : text;
	}

	private interface Power {
		Result apply(Economy economy) throws com.mojang.brigadier.exceptions.CommandSyntaxException;
	}

	private static int power(ServerPlayer player, String what, Power power) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		MinecraftServer server = player.level().getServer();
		Economy economy = EconomyService.economy(server);
		Result result = power.apply(economy);

		if (!result.ok()) {
			Text.warn(player, result == Result.OUT_OF_RANGE && what.equals("tax")
					? "The tax rate must be between " + com.goldbucks.economy.Settings.current().mayorTaxMin + "% and "
							+ com.goldbucks.economy.Settings.current().mayorTaxMax + "%."
					: result == Result.OUT_OF_RANGE ? "Pick one of the three Market Day choices." : result.message());
			return 0;
		}

		EconomyService.changed(server);
		Town town = economy.town();
		String name = player.getGameProfile().name();
		String news = switch (what) {
			case "tax" -> "Mayor " + name + " set the weekly tax to " + economy.taxRate() + "%.";
			case "market" -> "Mayor " + name + " picked " + TownData.current().eventName(town.marketPick()) + " for the next Market Day!";
			case "holiday" -> "Mayor " + name + " declared a Holiday for tomorrow!";
			case "name" -> "Mayor " + name + " named the town " + town.townName() + "!";
			default -> "The Mayor's message will be in tomorrow's Herald.";
		};

		if (what.equals("announce")) {
			Text.tell(player, news);
		} else {
			server.getPlayerList().getPlayers().forEach(p -> Text.tell(p, news));
		}

		TownSquare.updatePodium(server);
		return 1;
	}

	static void status(ServerPlayer player) {
		Economy economy = EconomyService.economy(player.level().getServer());
		Town town = economy.town();
		Account mayor = town.mayor() == null ? null : economy.account(town.mayor());
		Text.tell(player, mayor == null ? "There is no Mayor yet." : "The Mayor is " + mayor.name() + ". Weekly tax: " + economy.taxRate() + "%.");
		Text.tell(player, town.electionOpen() ? "It is Election Day! Run with /mayor run <promise>, vote with /vote."
				: "Next election: day " + town.nextElectionDay() + ".");

		if (player.getUUID().equals(town.mayor())) {
			Text.tell(player, "Your powers: /mayor tax <percent>, /mayor holiday" + (town.holidayUsed() ? " (used)" : "")
					+ ", /mayor name <town name>, /mayor announce <message>.");

			if (!town.marketChoices().isEmpty()) {
				StringBuilder choices = new StringBuilder("Pick the next Market Day: ");

				for (TownEvent choice : town.marketChoices()) {
					choices.append("/mayor market ").append(choice.id()).append("  ");
				}

				Text.tell(player, choices.toString());
			}
		}
	}

	static int run(ServerPlayer player, String promise) {
		MinecraftServer server = player.level().getServer();
		Result result = EconomyService.economy(server).runForMayor(player.getUUID(), trim(promise, 60));

		if (!result.ok()) {
			Text.warn(player, result == Result.NO_ELECTION ? "You can only run for Mayor on Election Day." : result.message());
			return 0;
		}

		EconomyService.changed(server);
		String line = player.getGameProfile().name() + " is running for Mayor!" + (promise.isBlank() ? "" : " Promise: \"" + trim(promise, 60) + "\"");
		server.getPlayerList().getPlayers().forEach(p -> Text.tell(p, line));
		return 1;
	}

	/** The ballot screen: one button per candidate. */
	static void ballot(ServerPlayer player) {
		Economy economy = EconomyService.economy(player.level().getServer());
		Town town = economy.town();

		if (!town.electionOpen()) {
			status(player);
			return;
		}

		Menus.Menu menu = new Menus.Menu("Election Day").subtitle(town.votes().containsKey(player.getUUID())
				? "You have voted. Results at the end of the day." : "Vote for anyone but yourself.");

		if (town.candidates().isEmpty()) {
			menu.text("Nobody is running yet. Type /mayor run <promise> to run!");
		}

		for (Map.Entry<UUID, String> candidate : town.candidates().entrySet()) {
			Account account = economy.account(candidate.getKey());
			boolean can = !candidate.getKey().equals(player.getUUID()) && !town.votes().containsKey(player.getUUID());
			menu.button(account.name() + (candidate.getValue().isBlank() ? "" : ": \"" + candidate.getValue() + "\""),
					candidate.getKey().equals(player.getUUID()) ? "You cannot vote for yourself." : "",
					"Vote", "vote " + account.name(), can);
		}

		Menus.open(player, menu);
	}

	static int vote(ServerPlayer voter, ServerPlayer candidate) {
		MinecraftServer server = voter.level().getServer();
		Result result = EconomyService.economy(server).vote(voter.getUUID(), candidate.getUUID());

		if (!result.ok()) {
			Text.warn(voter, result.message());
			return 0;
		}

		EconomyService.changed(server);
		Text.tell(voter, "You voted for " + candidate.getGameProfile().name() + ". Results at the end of the day!");
		EconomyService.economy(server).joinEvent(voter.getUUID());
		return 1;
	}

	/** Announces the result of an election at the podium. */
	public static void announceResult(MinecraftServer server, Town.ElectionResult result) {
		Economy economy = EconomyService.economy(server);

		if (result.winner() == null) {
			server.getPlayerList().getPlayers().forEach(p -> Text.tell(p, "Nobody ran for Mayor, so there is no new Mayor this time."));
			return;
		}

		String name = economy.account(result.winner()).name();
		String how = result.coinFlip() ? " It was a tie, so a coin flip decided it!" : "";

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Notices.title(player, "Mayor " + name + "!", "The town has a new Mayor." + (result.coinFlip() ? " (coin flip)" : ""));
			Text.tell(player, name + " is the new Mayor!" + how);
		}

		if (economy.town().squareBuilt) {
			Fx.fireworksShow(server.overworld(), TownSquare.podiumSign(economy.town()).above(2));
		}

		ServerPlayer winner = server.getPlayerList().getPlayer(result.winner());

		if (winner != null) {
			Achievements.check(winner);
			Text.tell(winner, "As Mayor you earn $" + com.goldbucks.economy.Settings.current().mayorPay + " a day. Type /mayor to see your powers.");
		}

		TownSquare.updatePodium(server);
		NameTags.refresh(server);
	}
}
