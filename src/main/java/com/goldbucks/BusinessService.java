package com.goldbucks;

import java.util.List;
import java.util.Locale;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import com.goldbucks.economy.Account;
import com.goldbucks.economy.Business;
import com.goldbucks.economy.Economy;
import com.goldbucks.economy.Job;
import com.goldbucks.economy.Result;

/** Buying and selling whole businesses, and the host's admin powers. */
public final class BusinessService {
	private BusinessService() {
	}

	// ---- selling a business ----

	/** The seller agrees to sell one of their businesses to the buyer at the game's price. */
	public static Result agreeToSell(ServerPlayer seller, ServerPlayer buyer, Business business) {
		MinecraftServer server = seller.level().getServer();
		Result result = EconomyService.economy(server).agreeToSell(seller.getUUID(), business.id(), buyer.getUUID());

		if (!result.ok()) {
			Text.warn(seller, result == Result.NO_ACCOUNT ? "You cannot sell to that player." : result.message());
			return result;
		}

		EconomyService.changed(server);
		String name = seller.getGameProfile().name();
		Text.tell(seller, "You offered your " + business.job().title() + " business to " + buyer.getGameProfile().name()
				+ " for " + Text.money(business.price()) + ".");
		Text.tell(buyer, Component.literal(name + " will sell you their " + business.job().title() + " business for "
				+ Text.money(business.price()) + ". ")
				.append(Text.button("Buy it", "/business buy " + name + " " + business.job().name().toLowerCase(Locale.ROOT))));
		return result;
	}

	/** The buyer takes over a business its owner agreed to sell them. */
	public static Result buy(ServerPlayer buyer, Business business) {
		MinecraftServer server = buyer.level().getServer();
		Economy economy = EconomyService.economy(server);
		int price = business.price();
		java.util.UUID sellerId = business.owner();
		Result result = economy.buyBusiness(buyer.getUUID(), business.id());

		if (!result.ok()) {
			Text.warn(buyer, result.message());
			return result;
		}

		EconomyService.changed(server);
		NameTags.refresh(server);
		Text.tell(buyer, "You bought the " + business.job().title() + " business for " + Text.money(price)
				+ ". Its plot, shop and job are yours.");
		ServerPlayer seller = server.getPlayerList().getPlayer(sellerId);

		if (seller != null) {
			Text.tell(seller, buyer.getGameProfile().name() + " bought your " + business.job().title() + " business for "
					+ Text.money(price) + ".");

			if (economy.needsJob(sellerId)) {
				JobsService.offerMenu(seller, "You sold your business. Pick a new job.");
			}
		}

		return result;
	}

	// ---- host powers ----

	public static Result fire(MinecraftServer server, ServerPlayer player) {
		Result result = EconomyService.economy(server).fire(player.getUUID());

		if (result.ok()) {
			EconomyService.changed(server);
			Text.warn(player, "You have been fired!");
			JobsService.offerMenu(player, "Pick a new job.");
		}

		return result;
	}

	public static Result setBalance(MinecraftServer server, ServerPlayer player, int amount) {
		Result result = EconomyService.economy(server).setBalance(player.getUUID(), amount);

		if (result.ok()) {
			EconomyService.changed(server);
			NameTags.refresh(server);
			Text.tell(player, "The host set your balance to " + Text.money(amount) + ".");
		}

		return result;
	}

	public static Result forgive(MinecraftServer server, ServerPlayer player) {
		Result result = EconomyService.economy(server).forgiveDebt(player.getUUID());

		if (result.ok()) {
			EconomyService.changed(server);
			NameTags.refresh(server);
			Text.tell(player, "The host forgave your debt.");
		}

		return result;
	}

	/** Host commands may be run by the host player or from the server console. */
	private static boolean isHost(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		return player == null || WorldRules.isHost(source.getServer(), player);
	}

	// ---- commands ----

	private static Business pick(ServerPlayer owner, Economy economy, String jobName) {
		List<Business> owned = economy.businessesOf(owner.getUUID());

		if (jobName == null) {
			return owned.size() == 1 ? owned.get(0) : null;
		}

		Job job = JobsService.parseJob(jobName);

		for (Business business : owned) {
			if (business.job() == job) {
				return business;
			}
		}

		return null;
	}

	private static String optionalJob(CommandContext<CommandSourceStack> context) {
		try {
			return StringArgumentType.getString(context, "job");
		} catch (IllegalArgumentException noSuchArgument) {
			return null;
		}
	}

	private static int sell(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer seller = context.getSource().getPlayerOrException();
		ServerPlayer buyer = EntityArgument.getPlayer(context, "player");
		Business business = pick(seller, EconomyService.economy(context.getSource().getServer()), optionalJob(context));

		if (business == null) {
			Text.warn(seller, "Say which business: /business sell <player> <job>.");
			return 0;
		}

		return agreeToSell(seller, buyer, business).ok() ? 1 : 0;
	}

	private static int buyCommand(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer buyer = context.getSource().getPlayerOrException();
		ServerPlayer seller = EntityArgument.getPlayer(context, "player");
		Economy economy = EconomyService.economy(context.getSource().getServer());
		String jobName = optionalJob(context);
		Business chosen = null;

		for (Business business : economy.businessesOf(seller.getUUID())) {
			boolean jobMatches = jobName == null || business.job() == JobsService.parseJob(jobName);

			if (jobMatches && buyer.getUUID().equals(economy.agreedBuyer(business.id()))) {
				chosen = business;
			}
		}

		if (chosen == null) {
			Text.warn(buyer, Result.NOT_FOR_SALE.message());
			return 0;
		}

		return buy(buyer, chosen).ok() ? 1 : 0;
	}

	private static int list(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		Economy economy = EconomyService.economy(context.getSource().getServer());

		if (economy.businesses().isEmpty()) {
			Text.tell(player, "There are no businesses yet.");
		}

		for (Business business : economy.businesses()) {
			Account owner = economy.account(business.owner());
			Text.tell(player, business.job().title() + " - owner " + (owner == null ? "?" : owner.name()) + ", sold "
					+ Text.money(business.lifetimeSales()) + " so far, worth " + Text.money(business.price()) + ".");
		}

		return 1;
	}

	private interface HostAction {
		Result run(MinecraftServer server, ServerPlayer target) throws CommandSyntaxException;
	}

	private static int host(CommandContext<CommandSourceStack> context, String done, HostAction action) throws CommandSyntaxException {
		CommandSourceStack source = context.getSource();

		if (!isHost(source)) {
			source.sendFailure(Component.literal("Only the host can use that command."));
			return 0;
		}

		ServerPlayer target = EntityArgument.getPlayer(context, "player");
		Result result = action.run(source.getServer(), target);

		if (!result.ok()) {
			source.sendFailure(Component.literal(result.message()));
			return 0;
		}

		source.sendSuccess(() -> Component.literal(done + " " + target.getGameProfile().name() + "."), false);
		return 1;
	}

	public static void register() {
		WorldRules.openCommand("business");

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(Commands.literal("business")
					.executes(BusinessService::list)
					.then(Commands.literal("sell")
							.then(Commands.argument("player", EntityArgument.player())
									.executes(BusinessService::sell)
									.then(Commands.argument("job", StringArgumentType.word()).executes(BusinessService::sell))))
					.then(Commands.literal("buy")
							.then(Commands.argument("player", EntityArgument.player())
									.executes(BusinessService::buyCommand)
									.then(Commands.argument("job", StringArgumentType.word()).executes(BusinessService::buyCommand)))));

			dispatcher.register(Commands.literal("host")
					.then(Commands.literal("fire")
							.then(Commands.argument("player", EntityArgument.player())
									.executes(context -> host(context, "Fired", BusinessService::fire))))
					.then(Commands.literal("forgive")
							.then(Commands.argument("player", EntityArgument.player())
									.executes(context -> host(context, "Forgave the debt of", BusinessService::forgive))))
					.then(Commands.literal("setbalance")
							.then(Commands.argument("player", EntityArgument.player())
									.then(Commands.argument("amount", IntegerArgumentType.integer(-100000, 100000))
											.executes(context -> host(context, "Set the balance of", (server, target) ->
													setBalance(server, target, IntegerArgumentType.getInteger(context, "amount")))))))
					.then(Commands.literal("payday").executes(context -> {
						if (!isHost(context.getSource())) {
							context.getSource().sendFailure(Component.literal("Only the host can use that command."));
							return 0;
						}

						JobsService.endDayNow(context.getSource().getServer());
						return 1;
					})));
		});
	}
}
