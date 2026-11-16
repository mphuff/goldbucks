package com.goldbucks;

import java.util.List;
import java.util.function.BiPredicate;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import com.goldbucks.economy.Account;
import com.goldbucks.economy.Economy;
import com.goldbucks.economy.Job;
import com.goldbucks.economy.Level;
import com.goldbucks.economy.Settings;
import com.goldbucks.economy.StoreKind;

/**
 * Achievements, shown as vanilla advancements in their own Goldbucks tab. Each one is earned once,
 * pops up with the usual toast, is announced in chat, and pays a small reward from the town.
 */
public final class Achievements {
	/** One achievement: its advancement id, whether it is a big one (bigger reward), and when it is earned. */
	public record Achievement(String id, boolean big, BiPredicate<Economy, Account> earned) {
	}

	private static final List<StoreKind> HOME = List.of(StoreKind.PORCH, StoreKind.GARDEN, StoreKind.BASEMENT, StoreKind.SECOND_FLOOR,
			StoreKind.STABLE, StoreKind.POOL, StoreKind.TOWER);

	public static final List<Achievement> ALL = List.of(
			new Achievement("first_job", false, (e, a) -> !e.businessesOf(a.id()).isEmpty()),
			new Achievement("open_for_business", false, (e, a) -> e.businessesOf(a.id()).stream().anyMatch(b -> !b.shopSigns().isEmpty())),
			new Achievement("first_sale", false, (e, a) -> a.stat("sales") >= 1),
			new Achievement("townsfolk_favourite", false, (e, a) -> a.stat("townsfolk_sales") >= 10),
			new Achievement("rush_hour", false, (e, a) -> a.salesToday() >= 5),
			new Achievement("order_up", false, (e, a) -> a.stat("orders") >= 1),
			new Achievement("reliable", false, (e, a) -> a.stat("orders") >= 25),
			new Achievement("payday", false, (e, a) -> totalDays(a) >= 1),
			new Achievement("steady_worker", false, (e, a) -> a.streak() >= 7),
			new Achievement("skilled", false, (e, a) -> best(a).atLeast(Level.SKILLED)),
			new Achievement("expert", false, (e, a) -> best(a).atLeast(Level.EXPERT)),
			new Achievement("master", true, (e, a) -> best(a) == Level.MASTER),
			new Achievement("jack_of_all_trades", false, (e, a) -> java.util.Arrays.stream(Job.values())
					.filter(job -> a.level(job).atLeast(Level.SKILLED)).count() >= 3),
			new Achievement("homeowner", false, (e, a) -> HOME.stream().anyMatch(k -> a.owns(k.id()))),
			new Achievement("dream_house", true, (e, a) -> HOME.stream().allMatch(k -> a.owns(k.id()))),
			new Achievement("land_baron", false, (e, a) -> a.owns(StoreKind.BIGGEST_PLOT.id())),
			new Achievement("best_friend", false, (e, a) -> a.owns(StoreKind.CAT.id()) || a.owns(StoreKind.WOLF.id())
					|| a.owns(StoreKind.PARROT.id()) || a.owns(StoreKind.AXOLOTL.id())),
			new Achievement("saver", false, (e, a) -> a.savings() >= 100),
			new Achievement("debt_free", false, (e, a) -> a.stat("been_in_debt") > 0 && a.balance() >= 0),
			new Achievement("club_500", false, (e, a) -> e.netWorth(a.id()) >= 500),
			new Achievement("club_1000", true, (e, a) -> e.netWorth(a.id()) >= 1000),
			new Achievement("tycoon", false, (e, a) -> e.businessesOf(a.id()).size() >= 2),
			new Achievement("taxi", false, (e, a) -> a.stat("passengers") >= 10),
			new Achievement("party_animal", false, (e, a) -> a.stat("events") >= 5),
			new Achievement("mayor", false, (e, a) -> a.id().equals(e.town().mayor())));

	private Achievements() {
	}

	private static int totalDays(Account account) {
		int total = 0;

		for (Job job : Job.values()) {
			total += account.daysWorked(job);
		}

		return total;
	}

	private static Level best(Account account) {
		Level best = Level.APPRENTICE;

		for (Job job : Job.values()) {
			if (account.level(job).ordinal() > best.ordinal()) {
				best = account.level(job);
			}
		}

		return best;
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (Activation.active() && server.getTickCount() % 100 == 0) {
				server.getPlayerList().getPlayers().forEach(Achievements::check);
			}
		});
	}

	/** Gives the root of the tab, so the Goldbucks tab appears. */
	public static void welcome(ServerPlayer player) {
		grantAdvancement(player, "welcome");
		check(player);
	}

	/** Checks every achievement the player has not earned yet, and awards the ones they now have. */
	public static void check(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Economy economy = EconomyService.economy(server);
		Account account = economy.account(player.getUUID());

		if (account == null) {
			return;
		}

		if (account.inDebt() && account.stat("been_in_debt") == 0) {
			economy.count(player.getUUID(), "been_in_debt", 1);
		}

		for (Achievement achievement : ALL) {
			if (account.achievements().contains(achievement.id())) {
				grantAdvancement(player, achievement.id());
				continue;
			}

			if (!achievement.earned().test(economy, account)) {
				continue;
			}

			int reward = achievement.big() ? Settings.current().bigAchievementReward : Settings.current().achievementReward;

			if (economy.award(player.getUUID(), achievement.id(), reward)) {
				EconomyService.changed(server);
				grantAdvancement(player, achievement.id());
				Fx.celebrate(player);

				if (reward > 0) {
					Text.tell(player, "Achievement reward: " + Text.money(reward) + " from the town!");
				}
			}
		}
	}

	private static void grantAdvancement(ServerPlayer player, String id) {
		AdvancementHolder holder = player.level().getServer().getAdvancements().get(GoldbucksMod.id(id));

		if (holder == null) {
			return;
		}

		AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);

		if (!progress.isDone()) {
			for (String criterion : progress.getRemainingCriteria()) {
				player.getAdvancements().award(holder, criterion);
			}
		}
	}

}
