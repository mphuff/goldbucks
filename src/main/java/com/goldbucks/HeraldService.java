package com.goldbucks;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.entity.LecternBlockEntity;

import com.goldbucks.economy.Account;
import com.goldbucks.economy.Economy;
import com.goldbucks.economy.Town;
import com.goldbucks.economy.TownData;
import com.goldbucks.economy.TownEvent;

/** The Goldbucks Herald: the morning news, read out at dawn on the mod's clock and kept in the lectern. */
public final class HeraldService {
	/** Marks a Herald ad that was paid for but not written yet. */
	public static final String UNWRITTEN_AD = "\u0000unwritten";
	private static List<String> lastHerald = new ArrayList<>();
	private static String lastHeadline = "";

	private HeraldService() {
	}

	public static void register() {
		WorldRules.openCommand("news");
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(Commands.literal("news")
				.executes(context -> {
					ServerPlayer player = context.getSource().getPlayerOrException();

					if (lastHerald.isEmpty()) {
						compose(player.level().getServer(), null);
					}

					read(player);
					return 1;
				})));
	}

	public static List<String> lastHerald() {
		return List.copyOf(lastHerald);
	}

	/**
	 * Writes today's Herald. {@code topSeller} is yesterday's best seller and how many sales, or null.
	 */
	public static List<String> compose(MinecraftServer server, TopSeller topSeller) {
		Economy economy = EconomyService.economy(server);
		Town town = economy.town();
		List<String> lines = new ArrayList<>();
		TownEvent event = town.event();

		if (event != null) {
			lines.add("Today: " + TownData.current().eventName(event) + "! " + TownData.current().eventDescription(event));
			lastHeadline = TownData.current().eventName(event) + "!";
		} else {
			lastHeadline = "Good morning, " + town.townName() + "!";
		}

		String clue = EventService.clue(server);

		if (clue != null) {
			lines.add(clue);
		}

		if (topSeller != null && topSeller.sales() > 0) {
			lines.add(topSeller.name() + " sold " + topSeller.sales() + " thing" + (topSeller.sales() == 1 ? "" : "s") + " yesterday!");
		}

		long open = town.orders().stream().filter(Town.Order::open).count();
		lines.add(open == 0 ? "No orders on the Town Board today." : open + " orders are on the Town Board.");

		if (town.mayorAnnouncement() != null) {
			lines.add("The Mayor says: " + town.mayorAnnouncement());
		}

		for (Account account : economy.accounts()) {
			if (account.heraldAd() != null && !account.heraldAd().equals(UNWRITTEN_AD)) {
				lines.add("Ad - " + account.heraldAd());
				economy.setHeraldAd(account.id(), null);
			} else if (UNWRITTEN_AD.equals(account.heraldAd())) {
				economy.setHeraldAd(account.id(), null);
			}
		}

		lastHerald = lines.size() > 6 ? new ArrayList<>(lines.subList(0, 6)) : lines;
		putInLectern(server);
		EconomyService.changed(server);
		return lastHerald;
	}

	public record TopSeller(String name, int sales) {
		public static TopSeller of(Economy economy) {
			Account best = null;

			for (Account account : economy.accounts()) {
				if (best == null || account.salesToday() > best.salesToday()) {
					best = account;
				}
			}

			return best == null || best.salesToday() == 0 ? null : new TopSeller(best.name(), best.salesToday());
		}
	}

	/** Reads the morning news out to everyone online: the bell, a title, and the Herald in chat. */
	public static void announce(MinecraftServer server, TopSeller topSeller) {
		Economy economy = EconomyService.economy(server);
		compose(server, topSeller);

		if (economy.town().squareBuilt) {
			Fx.bell(server.overworld(), TownSquare.board(economy.town()));
		}

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Notices.title(player, "Day " + economy.day(), lastHeadline);
			read(player);
		}
	}

	static void read(ServerPlayer player) {
		Economy economy = EconomyService.economy(player.level().getServer());
		player.sendSystemMessage(Component.literal("~ The " + economy.town().townName() + " Herald, day " + economy.day() + " ~")
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

		for (String line : lastHerald) {
			player.sendSystemMessage(Component.literal(" " + line).withStyle(ChatFormatting.YELLOW));
		}
	}

	private static void putInLectern(MinecraftServer server) {
		Town town = EconomyService.economy(server).town();

		if (!town.squareBuilt) {
			return;
		}

		ServerLevel level = server.overworld();

		if (!(level.getBlockEntity(TownSquare.board(town)) instanceof LecternBlockEntity lectern)) {
			return;
		}

		List<Filterable<Component>> pages = new ArrayList<>();
		StringBuilder page = new StringBuilder("THE HERALD\nDay " + EconomyService.economy(server).day() + "\n\n");

		for (String line : lastHerald) {
			if (page.length() + line.length() > 230) {
				pages.add(Filterable.passThrough(Component.literal(page.toString())));
				page = new StringBuilder();
			}

			page.append(line).append("\n\n");
		}

		page.append("Orders: /board");
		pages.add(Filterable.passThrough(Component.literal(page.toString())));
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("The Herald"), "The Town", 0, pages, true));
		lectern.setBook(book);
		lectern.setChanged();
	}

	/** Shows a returning player what their shops did while they were away. */
	public static void loginSummary(ServerPlayer player) {
		Economy economy = EconomyService.economy(player.level().getServer());
		Account account = economy.account(player.getUUID());
		UUID id = player.getUUID();

		if (account == null || (account.awaySales() == 0 && !account.awayEmptyShop())) {
			return;
		}

		String summary = "While you were away: " + account.awaySales() + " sale" + (account.awaySales() == 1 ? "" : "s")
				+ ", +" + Text.money(account.awayMoney()) + "." + (account.awayEmptyShop() ? " One of your shops is empty." : "");
		Text.tell(player, summary);
		economy.clearAway(id);
		EconomyService.changed(player.level().getServer());
	}
}
