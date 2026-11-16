package com.goldbucks;

import java.util.List;
import java.util.Set;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;

import com.goldbucks.economy.Account;
import com.goldbucks.economy.Business;
import com.goldbucks.economy.Economy;
import com.goldbucks.economy.ItemKind;
import com.goldbucks.economy.Job;
import com.goldbucks.economy.Result;
import com.goldbucks.economy.Rules;

/**
 * Shops in the running game. A shop is a sign on a business plot whose first line is [Shop]
 * and whose second line is a price, placed on or above a chest. The chest holds the stock;
 * clicking the sign buys one item from it (or a bundle, for an Expert Store Owner who writes x2 to x4 on line 3).
 */
public final class ShopService {
	public static final String SHOP_TAG = "[shop]";
	public static final String EXPIRES_KEY = "goldbucks_expires";

	private ShopService() {
	}

	/** Reads a price like "$5" or "5". Returns -1 if the text is not a price. */
	public static int parsePrice(String text) {
		String digits = text.strip().replace("$", "").strip();

		if (digits.isEmpty() || digits.length() > 6 || !digits.chars().allMatch(Character::isDigit)) {
			return -1;
		}

		return Integer.parseInt(digits);
	}

	/** Reads a bundle size like "x3" from line 3. Returns 1 when there is none. */
	public static int parseBundle(String text) {
		String t = text.strip().toLowerCase(java.util.Locale.ROOT);

		if (t.length() == 2 && t.charAt(0) == 'x' && Character.isDigit(t.charAt(1))) {
			return t.charAt(1) - '0';
		}

		return 1;
	}

	static boolean isShopSign(SignBlockEntity sign) {
		List<Component> lines = sign.getText(SignTextSlot.FRONT).getMessages(false);
		return !lines.isEmpty() && lines.get(0).getString().strip().equalsIgnoreCase(SHOP_TAG);
	}

	/** The chest (or barrel) a sign uses: the block it hangs on, or the block beneath it. */
	static Container stockOf(Level level, BlockPos signPos) {
		BlockState state = level.getBlockState(signPos);

		if (state.hasProperty(WallSignBlock.FACING)) {
			BlockPos behind = signPos.relative(state.getValue(WallSignBlock.FACING).getOpposite());

			if (level.getBlockEntity(behind) instanceof Container container) {
				return container;
			}
		}

		return level.getBlockEntity(signPos.below()) instanceof Container container ? container : null;
	}

	static int firstStockedSlot(Container stock) {
		for (int slot = 0; slot < stock.getContainerSize(); slot++) {
			if (!stock.getItem(slot).isEmpty()) {
				return slot;
			}
		}

		return -1;
	}

	/** Everything about one shop sign, as read from the world. */
	public record Shop(Business business, BlockPos sign, int price, int bundle, Container stock, int slot, ItemStack sample) {
		public boolean inStock() {
			return slot >= 0 && sample.getCount() >= bundle;
		}

		public String itemId() {
			return sample.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(sample.getItem()).toString();
		}

		public String itemName() {
			return sample.isEmpty() ? "" : sample.getHoverName().getString();
		}
	}

	/** Reads a shop sign. Returns null if it is not a working shop with a price and a chest on a business plot. */
	public static Shop readShop(Level level, BlockPos pos) {
		if (level.dimension() != Level.OVERWORLD || !(level.getBlockEntity(pos) instanceof SignBlockEntity sign) || !isShopSign(sign)) {
			return null;
		}

		Business business = EconomyService.economy(level.getServer()).businessAt(pos.getX(), pos.getZ());
		List<Component> lines = sign.getText(SignTextSlot.FRONT).getMessages(false);
		int price = lines.size() > 1 ? parsePrice(lines.get(1).getString()) : -1;
		Container stock = stockOf(level, pos);

		if (business == null || price < 1 || stock == null) {
			return null;
		}

		int bundle = lines.size() > 2 ? parseBundle(lines.get(2).getString()) : 1;
		Account owner = EconomyService.economy(level.getServer()).account(business.owner());
		boolean bundles = business.job() == Job.STORE_OWNER && owner != null
				&& owner.level(Job.STORE_OWNER).atLeast(com.goldbucks.economy.Level.EXPERT);
		bundle = bundles ? Math.max(1, Math.min(4, bundle)) : 1;
		int slot = firstStockedSlot(stock);
		ItemStack sample = slot < 0 ? ItemStack.EMPTY : stock.getItem(slot);
		return new Shop(business, pos, price, bundle, stock, slot, sample);
	}

	/** The highest price this shop may charge for what it is selling. */
	static int capFor(Economy economy, Shop shop) {
		return economy.priceCapFor(shop.business(), ItemKinds.of(shop.sample()), shop.sample().is(Items.ENCHANTED_BOOK));
	}

	/** What state a shop sign is in. */
	public enum SignStatus {
		NOT_A_SHOP,
		OPEN,
		NOT_ON_YOUR_PLOT,
		NO_PRICE,
		PRICE_TOO_HIGH,
		NO_CHEST,
		EMPTY_CHEST;

		public String message() {
			return switch (this) {
				case NOT_A_SHOP -> "";
				case OPEN -> "Shop open!";
				case NOT_ON_YOUR_PLOT -> "A shop sign only works on your own business plot. Claim one with /plot claim.";
				case NO_PRICE -> "Write a price such as $5 on the second line of the sign.";
				case PRICE_TOO_HIGH -> "Nothing may cost more than $" + Rules.priceCap() + ". Lower the price on the sign.";
				case NO_CHEST -> "Put the sign on top of a chest, or on its side. The chest holds what you sell.";
				case EMPTY_CHEST -> "Shop ready, but the chest is empty. Put the items you want to sell in it.";
			};
		}
	}

	/** Works out whether a sign is a working shop for this player, and if not, what is missing. */
	public static SignStatus checkSign(ServerPlayer owner, Level level, BlockPos pos) {
		if (!(level.getBlockEntity(pos) instanceof SignBlockEntity sign) || !isShopSign(sign)) {
			return SignStatus.NOT_A_SHOP;
		}

		Economy economy = EconomyService.economy(level.getServer());
		Business business = level.dimension() == Level.OVERWORLD ? economy.businessAt(pos.getX(), pos.getZ()) : null;

		if (business == null || !business.owner().equals(owner.getUUID())) {
			return SignStatus.NOT_ON_YOUR_PLOT;
		}

		List<Component> lines = sign.getText(SignTextSlot.FRONT).getMessages(false);
		int price = lines.size() > 1 ? parsePrice(lines.get(1).getString()) : -1;

		if (price < 1) {
			return SignStatus.NO_PRICE;
		}

		if (stockOf(level, pos) == null) {
			return SignStatus.NO_CHEST;
		}

		Shop shop = readShop(level, pos);

		if (price > capFor(economy, shop)) {
			return SignStatus.PRICE_TOO_HIGH;
		}

		return shop.slot() < 0 ? SignStatus.EMPTY_CHEST : SignStatus.OPEN;
	}

	/** Called when a player finishes writing a sign: tells them whether their shop works, and remembers it for townsfolk. */
	public static void onSignEdited(ServerPlayer player, Level level, BlockPos pos) {
		if (BankService.onSignEdited(player, level, pos)) {
			return;
		}

		SignStatus status = checkSign(player, level, pos);

		if (status == SignStatus.NOT_A_SHOP) {
			return;
		}

		MinecraftServer server = level.getServer();
		Economy economy = EconomyService.economy(server);
		Business business = economy.businessAt(pos.getX(), pos.getZ());

		if (status == SignStatus.OPEN || status == SignStatus.EMPTY_CHEST) {
			boolean first = economy.registerShop(business.id(), pos.asLong());
			EconomyService.changed(server);

			if (first) {
				Text.tell(player, "Your first shop! Tomorrow is its Grand Opening, with extra townsfolk customers.");
			}
		}

		if (status == SignStatus.OPEN) {
			Shop shop = readShop(level, pos);
			String what = shop.bundle() > 1 ? shop.bundle() + " " + shop.itemName() : "one " + shop.itemName();
			Text.tell(player, "Shop open! Customers who right-click this sign buy " + what + " for " + Text.money(shop.price())
					+ ". Items sell from the first chest slot onwards.");
			double value = com.goldbucks.economy.TownData.current().valueOf(shop.itemId()) * shop.bundle();

			if (value <= 0) {
				Text.tell(player, "Townsfolk do not buy " + shop.itemName() + ", but players can.");
			} else {
				int most = (int) Math.floor(value * com.goldbucks.economy.Settings.current().fairPricePercent / 100.0);
				Text.tell(player, "Townsfolk think " + shop.itemName() + " is worth about " + Text.money((int) Math.max(1, Math.round(value)))
						+ " and will pay up to " + Text.money(Math.max(1, most)) + ".");
			}
		} else if (status == SignStatus.EMPTY_CHEST) {
			Text.tell(player, status.message());
		} else {
			Text.warn(player, status.message());
		}
	}

	/** The steps for opening a shop, as shown by /shop and after a plot is claimed. */
	public static void explain(ServerPlayer player) {
		Text.tell(player, "How to sell:");
		Text.tell(player, "1. On your plot, place a chest and put the items you want to sell in it.");
		Text.tell(player, "2. Put a sign on top of the chest, or on its side.");
		Text.tell(player, "3. Write [Shop] on the first line and a price such as $5 on the second. $" + Rules.priceCap() + " is the most.");
		Text.tell(player, "4. Players and townsfolk buy from it. The money comes to you.");
	}

	/** Handles a click on a shop sign. Returns null when the click is not a purchase. */
	public static InteractionResult onUseBlock(ServerPlayer player, Level level, InteractionHand hand, BlockPos pos) {
		BlockEntity blockEntity = level.getBlockEntity(pos);

		if (!(blockEntity instanceof SignBlockEntity sign) || !isShopSign(sign) || level.dimension() != Level.OVERWORLD) {
			return null;
		}

		MinecraftServer server = level.getServer();
		Economy economy = EconomyService.economy(server);
		Business business = economy.businessAt(pos.getX(), pos.getZ());

		if (business == null) {
			Text.warn(player, "A shop sign only works on a business plot.");
			return InteractionResult.FAIL;
		}

		// The owner clicking their own sign edits it, as usual.
		if (business.owner().equals(player.getUUID())) {
			return null;
		}

		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.FAIL;
		}

		buy(player, level, pos, sign, business);
		return InteractionResult.SUCCESS;
	}

	/** Tries to buy from the shop sign. Returns what happened. */
	public static Result buy(ServerPlayer buyer, Level level, BlockPos signPos, SignBlockEntity sign, Business business) {
		MinecraftServer server = level.getServer();
		Economy economy = EconomyService.economy(server);
		List<Component> lines = sign.getText(SignTextSlot.FRONT).getMessages(false);
		int price = lines.size() > 1 ? parsePrice(lines.get(1).getString()) : -1;

		if (price < 0) {
			Text.warn(buyer, "This shop has no price yet. The second line of the sign must be a price like $5.");
			return Result.BAD_PRICE;
		}

		Shop shop = readShop(level, signPos);

		if (shop == null) {
			Text.warn(buyer, "This shop has no chest. The sign must be on or above a chest.");
			return Result.NO_BUSINESS;
		}

		if (!shop.inStock()) {
			Text.warn(buyer, "Sold out!");
			return Result.NO_BUSINESS;
		}

		Result check = economy.canPurchase(buyer.getUUID(), business.id(), price, capFor(economy, shop));

		if (!check.ok()) {
			Text.warn(buyer, check.message());
			return check;
		}

		if (buyer.getInventory().getFreeSlot() < 0 && buyer.getInventory().getSlotWithRemainingSpace(shop.sample()) < 0) {
			Text.warn(buyer, "Your inventory is full.");
			return Result.NO_BUSINESS;
		}

		Set<Integer> tasksBefore = JobsService.pendingTasks(economy, business.owner());
		ItemStack bought = shop.stock().removeItem(shop.slot(), shop.bundle());
		shop.stock().setChanged();
		ItemKind kind = ItemKinds.of(bought);
		String itemName = bought.getHoverName().getString();
		markBought(bought, economy);
		economy.purchase(buyer.getUUID(), business.id(), price, kind, capFor(economy, shop));
		buyer.getInventory().add(bought);
		EconomyService.changed(server);
		NameTags.refresh(server);

		Account buyerAccount = economy.account(buyer.getUUID());
		Text.tell(buyer, "You bought " + (shop.bundle() > 1 ? shop.bundle() + " " : "") + itemName + " for " + Text.money(price)
				+ ". Balance: " + Text.money(buyerAccount.balance()) + ".");

		if (buyerAccount.inDebt()) {
			Text.warn(buyer, "You are " + Text.money(buyerAccount.debt()) + " in debt. It comes out of your pay.");
		}

		saleNotice(server, business, buyerAccount.name(), itemName, price, false, tasksBefore);
		Achievements.check(buyer);
		return Result.OK;
	}

	/** Tells a shop owner about a sale, wherever they are, or saves it for their login summary. */
	static void saleNotice(MinecraftServer server, Business business, String who, String itemName, int price, boolean townsfolk,
			Set<Integer> tasksBefore) {
		Economy economy = EconomyService.economy(server);
		ServerPlayer owner = server.getPlayerList().getPlayer(business.owner());

		if (owner == null) {
			economy.recordAwaySale(business.owner(), price);
			return;
		}

		Notices.sale(owner, who + " bought " + itemName + " for " + Text.money(price) + ".", townsfolk);

		if (tasksBefore.contains(business.id()) && business.taskDoneToday()) {
			Text.tell(owner, business.job().title() + " task done! You will be paid at the end of the day.");
		}

		Achievements.check(owner);
	}

	/**
	 * Stamps a bought item with the day it expires. Building blocks never expire,
	 * and food is simply used up when eaten.
	 */
	static void markBought(ItemStack stack, Economy economy) {
		if (stack.getItem() instanceof BlockItem || stack.has(DataComponents.FOOD)) {
			return;
		}

		int expires = economy.rollExpiryDay(new java.util.Random());
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(EXPIRES_KEY, expires));
		ItemLore lore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
		stack.set(DataComponents.LORE, lore.withLineAdded(
				Component.literal("Shop-bought: expires on day " + expires).withStyle(ChatFormatting.RED)));
	}

	/** The day a bought item expires, or -1 if it never does. */
	public static int expiryDay(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? -1 : data.copyTag().getIntOr(EXPIRES_KEY, -1);
	}

	/** Removes expired shop-bought items from a player's inventory. Returns how many stacks went. */
	public static int removeExpired(ServerPlayer player) {
		Economy economy = EconomyService.economy(player.level().getServer());
		int removed = 0;

		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			int expires = stack.isEmpty() ? -1 : expiryDay(stack);

			if (expires >= 0 && economy.isExpired(expires)) {
				Text.warn(player, "Your " + stack.getHoverName().getString() + " has expired.");
				player.getInventory().setItem(slot, ItemStack.EMPTY);
				removed++;
			}
		}

		return removed;
	}

	public static void register() {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (!Activation.active() || server.getTickCount() % 100 != 0) {
				return;
			}

			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				removeExpired(player);
			}
		});
	}
}
