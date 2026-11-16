package com.goldbucks;

import java.util.List;

import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignTextSlot;

import com.goldbucks.economy.Account;
import com.goldbucks.economy.Business;
import com.goldbucks.economy.Economy;
import com.goldbucks.economy.Job;
import com.goldbucks.economy.Result;

/**
 * The bank. The Banker writes [Bank] on a sign on their plot; that is the bank counter.
 * With no Banker, the Town Clerk keeps savings, but they earn no interest.
 */
public final class BankService {
	public static final String BANK_TAG = "[bank]";
	/** Means "the Town Clerk" rather than a Banker's counter. */
	public static final int CLERK = -1;

	private BankService() {
	}

	public static void register() {
		WorldRules.openCommand("bank");
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(Commands.literal("bank")
				.executes(context -> {
					open(context.getSource().getPlayerOrException());
					return 1;
				})
				.then(Commands.literal("deposit").then(Commands.argument("amount", StringArgumentType.word())
						.executes(context -> move(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "amount"), true) ? 1 : 0)))
				.then(Commands.literal("withdraw").then(Commands.argument("amount", StringArgumentType.word())
						.executes(context -> move(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "amount"), false) ? 1 : 0)))));
	}

	static boolean isBankSign(SignBlockEntity sign) {
		List<Component> lines = sign.getText(SignTextSlot.FRONT).getMessages(false);
		return !lines.isEmpty() && lines.get(0).getString().strip().equalsIgnoreCase(BANK_TAG);
	}

	/** Called when a sign is written. Returns true if it was a bank sign (handled here). */
	static boolean onSignEdited(ServerPlayer player, Level level, BlockPos pos) {
		if (!(level.getBlockEntity(pos) instanceof SignBlockEntity sign) || !isBankSign(sign)) {
			return false;
		}

		Business business = EconomyService.economy(level.getServer()).businessAt(pos.getX(), pos.getZ());

		if (business == null || !business.owner().equals(player.getUUID()) || business.job() != Job.BANKER) {
			Text.warn(player, "Only the Banker can put a [Bank] sign up, on their own plot.");
		} else {
			Text.tell(player, "Bank open! Players right-click this sign to save or take out money. You earn a fee each time.");
		}

		return true;
	}

	/** A click on a bank sign opens the bank. Returns null when the click is not on a bank sign. */
	static InteractionResult onUseBlock(ServerPlayer player, Level level, BlockPos pos) {
		if (!(level.getBlockEntity(pos) instanceof SignBlockEntity sign) || !isBankSign(sign)) {
			return null;
		}

		Business business = EconomyService.economy(level.getServer()).businessAt(pos.getX(), pos.getZ());

		if (business == null || business.job() != Job.BANKER || business.owner().equals(player.getUUID())) {
			return null;
		}

		open(player);
		return InteractionResult.SUCCESS;
	}

	/**
	 * The bank the player is standing at: a Banker's counter's business id, {@link #CLERK} at the Town Clerk
	 * when there is no Banker, or null when they are not at a bank.
	 */
	public static Integer bankHere(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Economy economy = EconomyService.economy(server);
		Level level = player.level();

		if (level.dimension() == Level.OVERWORLD) {
			for (BlockPos pos : BlockPos.betweenClosed(player.blockPosition().offset(-5, -3, -5), player.blockPosition().offset(5, 3, 5))) {
				if (level.getBlockEntity(pos) instanceof SignBlockEntity sign && isBankSign(sign)) {
					Business business = economy.businessAt(pos.getX(), pos.getZ());

					if (business != null && business.job() == Job.BANKER) {
						return business.id();
					}
				}
			}
		}

		boolean nearClerk = economy.town().squareBuilt
				&& player.blockPosition().distSqr(TownSquare.clerkSpot(economy.town())) < 8 * 8;
		return nearClerk && economy.holderOf(Job.BANKER) == null ? CLERK : null;
	}

	public static void open(ServerPlayer player) {
		Economy economy = EconomyService.economy(player.level().getServer());
		Account account = economy.account(player.getUUID());
		Integer bank = bankHere(player);

		if (bank == null) {
			java.util.UUID banker = economy.holderOf(Job.BANKER);
			Text.tell(player, banker == null
					? "The bank is at the Town Clerk's stall on the town square."
					: "Go to " + economy.account(banker).name() + "'s bank and right-click the [Bank] sign.");
			Text.tell(player, "You have " + Text.money(account.savings()) + " saved.");
			return;
		}

		int interest = economy.interestFor(player.getUUID());
		Menus.Menu menu = new Menus.Menu(bank == CLERK ? "Town Clerk's Savings" : economy.account(economy.business(bank).owner()).name() + "'s Bank")
				.subtitle("Cash " + Text.money(account.balance()) + ", saved " + Text.money(account.savings())
						+ (bank == CLERK ? ". No Banker, so no interest." : ". Interest next week: about " + Text.money(interest) + "."));
		menu.text("Saved money is safe from tax, but you cannot spend it until you take it out.");

		for (int amount : new int[] {5, 10, 25, 50}) {
			menu.button("Save " + Text.money(amount), "", "Deposit", "bank deposit " + amount, !account.inDebt() && account.balance() >= amount);
		}

		menu.button("Save everything", "", "Deposit", "bank deposit all", !account.inDebt() && account.balance() > 0);

		for (int amount : new int[] {5, 10, 25, 50}) {
			menu.button("Take out " + Text.money(amount), "", "Withdraw", "bank withdraw " + amount, account.savings() >= amount);
		}

		menu.button("Take out everything", "", "Withdraw", "bank withdraw all", account.savings() > 0);
		Menus.open(player, menu);
	}

	static boolean move(ServerPlayer player, String amountText, boolean deposit) {
		MinecraftServer server = player.level().getServer();
		Economy economy = EconomyService.economy(server);
		Account account = economy.account(player.getUUID());
		Integer bank = bankHere(player);

		if (bank == null) {
			open(player);
			return false;
		}

		int amount;

		if (amountText.equalsIgnoreCase("all")) {
			amount = deposit ? account.balance() : account.savings();
		} else {
			try {
				amount = Integer.parseInt(amountText.replace("$", ""));
			} catch (NumberFormatException notANumber) {
				Text.warn(player, "Say how much, like /bank deposit 10.");
				return false;
			}
		}

		Result result = deposit ? economy.deposit(player.getUUID(), amount, bank) : economy.withdraw(player.getUUID(), amount, bank);

		if (!result.ok()) {
			Text.warn(player, result.message());
			return false;
		}

		EconomyService.changed(server);
		Text.tell(player, (deposit ? "Saved " : "Took out ") + Text.money(amount) + ". Cash " + Text.money(account.balance())
				+ ", saved " + Text.money(account.savings()) + ".");

		if (bank != CLERK) {
			ServerPlayer banker = server.getPlayerList().getPlayer(economy.business(bank).owner());

			if (banker != null) {
				Notices.sale(banker, player.getGameProfile().name() + " used your bank.", false);
			}
		}

		Achievements.check(player);
		open(player);
		return true;
	}
}
