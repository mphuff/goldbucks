package com.goldbucks.test;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.FilteredText;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import com.goldbucks.NameTags;
import com.goldbucks.EconomyService;
import com.goldbucks.JobsService;
import com.goldbucks.LandService;
import com.goldbucks.ShopService;
import com.goldbucks.economy.Business;
import com.goldbucks.economy.Economy;
import com.goldbucks.economy.Job;

public class ShopGameTest {
	/** Tests sit close together; this keeps each test's 16x16 plot clear of its neighbours. */
	private static final int PLOT_PADDING = 12;

	/** A store owner with a plot, a stocked chest and a shop sign on top of it. */
	private static final class Shop {
		ServerPlayer owner;
		ServerPlayer buyer;
		BlockPos chest;
		BlockPos sign;
		Container stock;
		Economy economy;
		Business business;
	}

	private static Shop open(GameTestHelper helper, String priceLine, ItemStack... stock) {
		ServerLevel level = helper.getLevel();
		Shop shop = new Shop();
		shop.owner = helper.makeMockServerPlayerInLevel();
		shop.buyer = helper.makeMockServerPlayerInLevel();
		shop.economy = EconomyService.economy(level.getServer());
		JobsService.choose(shop.owner, Job.STORE_OWNER);

		shop.chest = helper.absolutePos(new BlockPos(1, 2, 1));
		shop.sign = shop.chest.above();
		shop.owner.snapTo(shop.chest.getX() + 0.5, shop.chest.getY(), shop.chest.getZ() + 0.5);
		helper.assertTrue(LandService.claim(shop.owner).ok(), "the owner claims a plot for the shop");
		shop.business = shop.economy.businessesOf(shop.owner.getUUID()).get(0);
		shop.owner.snapTo(shop.chest.getX() + 4.5, shop.chest.getY(), shop.chest.getZ() + 4.5);
		shop.buyer.snapTo(shop.chest.getX() - 2.5, shop.chest.getY(), shop.chest.getZ() + 0.5);

		level.setBlockAndUpdate(shop.chest, Blocks.CHEST.defaultBlockState());
		level.setBlockAndUpdate(shop.sign, Blocks.OAK_SIGN.defaultBlockState());
		shop.stock = (Container) level.getBlockEntity(shop.chest);

		for (int i = 0; i < stock.length; i++) {
			shop.stock.setItem(i, stock[i]);
		}

		SignBlockEntity sign = (SignBlockEntity) level.getBlockEntity(shop.sign);
		sign.setText(shopText(priceLine), SignTextSlot.FRONT);
		return shop;
	}

	private static SignText shopText(String priceLine) {
		List<Component> lines = List.of(Component.literal("[Shop]"), Component.literal(priceLine), Component.empty(), Component.empty());
		return new SignText(lines, lines, DyeColor.BLACK, false);
	}

	/** Right-clicks a block the way a real player would, through the game's own interaction path. */
	private static void click(ServerPlayer player, ServerLevel level, BlockPos pos) {
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.WEST, pos, false);
		player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
	}

	private static int count(ServerPlayer player, net.minecraft.world.item.Item item) {
		int total = 0;

		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);

			if (stack.is(item)) {
				total += stack.getCount();
			}
		}

		return total;
	}

	@GameTest(padding = PLOT_PADDING)
	public void ownerStocksShop(GameTestHelper helper) {
		Shop shop = open(helper, "$12", new ItemStack(Items.BREAD, 3));

		// The owner clicking their own sign is not a purchase; they can edit it and reach the chest.
		helper.assertTrue(ShopService.onUseBlock(shop.owner, helper.getLevel(), InteractionHand.MAIN_HAND, shop.sign) == null,
				"the owner's click on their own sign is left alone");
		click(shop.owner, helper.getLevel(), shop.sign);
		helper.assertValueEqual(shop.stock.getItem(0).getCount(), 3, "stock after the owner clicks the sign");
		helper.assertValueEqual(shop.economy.account(shop.owner.getUUID()).balance(), 20, "owner's balance");
		helper.assertTrue(LandService.mayBuild(shop.owner, helper.getLevel(), shop.chest), "the owner can reach the chest");
		helper.assertFalse(LandService.mayBuild(shop.buyer, helper.getLevel(), shop.chest), "a customer cannot open the chest");

		// Prices follow the rules.
		helper.assertValueEqual(ShopService.parsePrice("$12"), 12, "price with a dollar sign");
		helper.assertValueEqual(ShopService.parsePrice(" 7 "), 7, "price without one");
		helper.assertValueEqual(ShopService.parsePrice("cheap"), -1, "not a price");
		helper.succeed();
	}

	@GameTest(padding = PLOT_PADDING)
	public void buyerGetsItemOwnerGetsMoney(GameTestHelper helper) {
		Shop shop = open(helper, "$12", new ItemStack(Items.BREAD, 3));

		click(shop.buyer, helper.getLevel(), shop.sign);

		helper.assertValueEqual(count(shop.buyer, Items.BREAD), 1, "bread in the buyer's inventory");
		helper.assertValueEqual(shop.stock.getItem(0).getCount(), 2, "bread left in the shop");
		helper.assertValueEqual(shop.economy.account(shop.buyer.getUUID()).balance(), 8, "buyer's balance");
		helper.assertValueEqual(shop.economy.account(shop.owner.getUUID()).balance(), 32, "owner's balance");
		helper.assertValueEqual(shop.business.lifetimeSales(), 12, "the shop's sales history");
		helper.assertTrue(shop.business.taskDoneToday(), "a sale does the store owner's daily task");
		helper.succeed();
	}

	@GameTest(padding = PLOT_PADDING)
	public void priceCapAndSoldOut(GameTestHelper helper) {
		Shop pricey = open(helper, "$41", new ItemStack(Items.BREAD, 1));
		click(pricey.buyer, helper.getLevel(), pricey.sign);
		helper.assertValueEqual(count(pricey.buyer, Items.BREAD), 0, "nothing can be sold for more than $40");
		helper.assertValueEqual(pricey.economy.account(pricey.buyer.getUUID()).balance(), 20, "buyer's balance after a refused sale");
		helper.assertValueEqual(pricey.stock.getItem(0).getCount(), 1, "stock after a refused sale");

		// Empty the chest: nothing to buy, nothing charged.
		pricey.stock.setItem(0, ItemStack.EMPTY);
		SignBlockEntity sign = (SignBlockEntity) helper.getLevel().getBlockEntity(pricey.sign);
		sign.setText(shopText("$5"), SignTextSlot.FRONT);
		click(pricey.buyer, helper.getLevel(), pricey.sign);
		helper.assertValueEqual(pricey.economy.account(pricey.buyer.getUUID()).balance(), 20, "buyer's balance when sold out");
		helper.succeed();
	}

	@GameTest(padding = PLOT_PADDING)
	public void debtorIsLabelled(GameTestHelper helper) {
		Shop shop = open(helper, "$40", new ItemStack(Items.BREAD, 8));
		helper.assertValueEqual(NameTags.labelOf(shop.buyer), "", "no label while in credit");

		// $20 -> -$20 -> -$60 -> -$100, then the credit limit stops the fourth purchase.
		click(shop.buyer, helper.getLevel(), shop.sign);
		helper.assertValueEqual(shop.economy.account(shop.buyer.getUUID()).balance(), -20, "balance after buying on credit");
		helper.assertValueEqual(NameTags.labelOf(shop.buyer).strip(), "DEBT -$20", "debt label on the name tag");

		click(shop.buyer, helper.getLevel(), shop.sign);
		click(shop.buyer, helper.getLevel(), shop.sign);
		click(shop.buyer, helper.getLevel(), shop.sign);
		helper.assertValueEqual(shop.economy.account(shop.buyer.getUUID()).balance(), -100, "credit stops at -$100");
		helper.assertValueEqual(count(shop.buyer, Items.BREAD), 3, "the fourth purchase was refused");
		helper.assertValueEqual(NameTags.labelOf(shop.buyer).strip(), "DEBT -$100", "label follows the debt");

		shop.economy.forgiveDebt(shop.buyer.getUUID());
		NameTags.refresh(helper.getLevel().getServer());
		helper.assertValueEqual(NameTags.labelOf(shop.buyer), "", "the label goes when the debt is cleared");
		helper.succeed();
	}

	@GameTest(padding = PLOT_PADDING)
	public void boughtItemsExpireButBlocksAndFoodDoNot(GameTestHelper helper) {
		Shop shop = open(helper, "$1", new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.STONE), new ItemStack(Items.BREAD));
		int today = shop.economy.day();

		click(shop.buyer, helper.getLevel(), shop.sign);
		click(shop.buyer, helper.getLevel(), shop.sign);
		click(shop.buyer, helper.getLevel(), shop.sign);

		ItemStack pickaxe = null;

		for (int slot = 0; slot < shop.buyer.getInventory().getContainerSize(); slot++) {
			ItemStack stack = shop.buyer.getInventory().getItem(slot);

			if (stack.is(Items.IRON_PICKAXE)) {
				pickaxe = stack;
			} else if (!stack.isEmpty()) {
				helper.assertValueEqual(ShopService.expiryDay(stack), -1, "blocks and food never expire");
			}
		}

		helper.assertTrue(pickaxe != null, "the buyer has the pickaxe");
		int expires = ShopService.expiryDay(pickaxe);
		helper.assertTrue(expires >= today + 5 && expires <= today + 7, "a bought tool expires 5 to 7 days later, got " + (expires - today));

		// Nothing is removed before its day.
		helper.assertValueEqual(ShopService.removeExpired(shop.buyer), 0, "items removed before they expire");
		helper.assertValueEqual(count(shop.buyer, Items.IRON_PICKAXE), 1, "pickaxes before expiry");

		// Pretend its day has come.
		CustomData.update(DataComponents.CUSTOM_DATA, pickaxe, tag -> tag.putInt(ShopService.EXPIRES_KEY, today));
		helper.assertValueEqual(ShopService.removeExpired(shop.buyer), 1, "items removed on their expiry day");
		helper.assertValueEqual(count(shop.buyer, Items.IRON_PICKAXE), 0, "pickaxes after expiry");
		helper.assertValueEqual(count(shop.buyer, Items.STONE), 1, "blocks are kept");
		helper.assertValueEqual(count(shop.buyer, Items.BREAD), 1, "food is kept");
		helper.succeed();
	}

	@GameTest(padding = PLOT_PADDING)
	public void boughtFoodIsSingleUse(GameTestHelper helper) {
		Shop shop = open(helper, "$1", new ItemStack(Items.BREAD, 2));
		click(shop.buyer, helper.getLevel(), shop.sign);

		ItemStack bread = ItemStack.EMPTY;

		for (int slot = 0; slot < shop.buyer.getInventory().getContainerSize(); slot++) {
			if (shop.buyer.getInventory().getItem(slot).is(Items.BREAD)) {
				bread = shop.buyer.getInventory().getItem(slot);
			}
		}

		helper.assertTrue(bread.is(Items.BREAD) && bread.getCount() == 1, "the buyer holds one bread");
		shop.buyer.getFoodData().setFoodLevel(10);
		ItemStack after = bread.finishUsingItem(helper.getLevel(), shop.buyer);

		helper.assertTrue(after.isEmpty(), "eating the bread uses it up");
		helper.assertTrue(shop.buyer.getFoodData().getFoodLevel() > 10, "and it fills the hunger bar");
		helper.succeed();
	}

	/** Issue 1: writing a shop sign tells the owner whether the shop works, and what is missing if not. */
	@GameTest(padding = PLOT_PADDING)
	public void signTellsOwnerWhatIsWrong(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Shop shop = open(helper, "$12", new ItemStack(Items.BREAD, 3));
		SignBlockEntity sign = (SignBlockEntity) level.getBlockEntity(shop.sign);

		helper.assertTrue(ShopService.checkSign(shop.owner, level, shop.sign) == ShopService.SignStatus.OPEN, "a correct sign is an open shop");
		helper.assertTrue(ShopService.checkSign(shop.buyer, level, shop.sign) == ShopService.SignStatus.NOT_ON_YOUR_PLOT,
				"a sign on someone else's plot is not your shop");

		sign.setText(shopText("$41"), SignTextSlot.FRONT);
		helper.assertTrue(ShopService.checkSign(shop.owner, level, shop.sign) == ShopService.SignStatus.PRICE_TOO_HIGH, "price over the cap");

		sign.setText(shopText("cheap"), SignTextSlot.FRONT);
		helper.assertTrue(ShopService.checkSign(shop.owner, level, shop.sign) == ShopService.SignStatus.NO_PRICE, "no price");

		sign.setText(shopText("$5"), SignTextSlot.FRONT);
		shop.stock.setItem(0, ItemStack.EMPTY);
		helper.assertTrue(ShopService.checkSign(shop.owner, level, shop.sign) == ShopService.SignStatus.EMPTY_CHEST, "empty chest");

		level.setBlockAndUpdate(shop.chest, Blocks.STONE.defaultBlockState());
		helper.assertTrue(ShopService.checkSign(shop.owner, level, shop.sign) == ShopService.SignStatus.NO_CHEST, "no chest under the sign");

		List<Component> plain = List.of(Component.literal("Hello"), Component.empty(), Component.empty(), Component.empty());
		sign.setText(new SignText(plain, plain, DyeColor.BLACK, false), SignTextSlot.FRONT);
		helper.assertTrue(ShopService.checkSign(shop.owner, level, shop.sign) == ShopService.SignStatus.NOT_A_SHOP, "an ordinary sign");

		// Writing a sign the way a player does runs the same check without trouble.
		sign.setAllowedPlayerEditor(shop.owner.getUUID());
		sign.updateSignText(shop.owner, SignTextSlot.FRONT, List.of(FilteredText.passThrough("[Shop]"), FilteredText.passThrough("$7"),
				FilteredText.passThrough(""), FilteredText.passThrough("")));
		helper.assertTrue(ShopService.checkSign(shop.owner, level, shop.sign) == ShopService.SignStatus.NO_CHEST,
				"the sign the player wrote is read as a shop sign");
		helper.succeed();
	}

	/** Issue 1: the sign can also hang on the side of the chest. */
	@GameTest(padding = PLOT_PADDING)
	public void signOnTheSideOfTheChestWorks(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Shop shop = open(helper, "$3", new ItemStack(Items.BREAD, 2));
		level.setBlockAndUpdate(shop.sign, Blocks.AIR.defaultBlockState());

		BlockPos side = shop.chest.west();
		level.setBlockAndUpdate(side, Blocks.OAK_WALL_SIGN.defaultBlockState()
				.setValue(net.minecraft.world.level.block.WallSignBlock.FACING, Direction.WEST));
		((SignBlockEntity) level.getBlockEntity(side)).setText(shopText("$3"), SignTextSlot.FRONT);

		helper.assertTrue(ShopService.checkSign(shop.owner, level, side) == ShopService.SignStatus.OPEN, "a wall sign on the chest is a shop");
		click(shop.buyer, level, side);
		helper.assertValueEqual(count(shop.buyer, Items.BREAD), 1, "bread bought from the wall sign");
		helper.assertValueEqual(shop.economy.account(shop.buyer.getUUID()).balance(), 17, "buyer's balance");
		helper.succeed();
	}
}
