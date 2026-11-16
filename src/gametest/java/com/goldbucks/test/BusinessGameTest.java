package com.goldbucks.test;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import com.goldbucks.BusinessService;
import com.goldbucks.NameTags;
import com.goldbucks.EconomyService;
import com.goldbucks.JobsService;
import com.goldbucks.LandService;
import com.goldbucks.economy.Business;
import com.goldbucks.economy.Economy;
import com.goldbucks.economy.Job;
import com.goldbucks.economy.Result;

public class BusinessGameTest {
	@GameTest(padding = 12)
	public void sellingABusinessTransfersEverything(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		Economy economy = EconomyService.economy(server);
		ServerPlayer seller = helper.makeMockServerPlayerInLevel();
		ServerPlayer buyer = helper.makeMockServerPlayerInLevel();
		JobsService.choose(seller, Job.SALESPERSON);
		JobsService.choose(buyer, Job.COOK);

		BlockPos center = helper.absolutePos(new BlockPos(1, 2, 1));
		seller.snapTo(center.getX() + 0.5, center.getY(), center.getZ() + 0.5);
		helper.assertTrue(LandService.claim(seller).ok(), "the seller has a plot");
		Business business = economy.businessesOf(seller.getUUID()).get(0);
		helper.assertValueEqual(business.price(), 50, "price of a business with no sales");

		// Without the owner's agreement nothing happens.
		helper.assertTrue(BusinessService.buy(buyer, business) == Result.NOT_FOR_SALE, "the owner must agree first");
		helper.assertTrue(business.owner().equals(seller.getUUID()), "the business is still the seller's");

		helper.assertTrue(BusinessService.agreeToSell(seller, buyer, business).ok(), "the owner agrees to sell");
		helper.assertTrue(BusinessService.buy(buyer, business).ok(), "the buyer buys it");

		// $20 - $50 puts the buyer $30 in debt; the seller receives the $50.
		helper.assertValueEqual(economy.account(buyer.getUUID()).balance(), -30, "buyer's balance");
		helper.assertValueEqual(economy.account(seller.getUUID()).balance(), 70, "seller's balance");
		helper.assertValueEqual(NameTags.labelOf(buyer).strip(), "DEBT -$30", "buyer's debt label");

		// The plot, the job and the old business all belong to the buyer now.
		helper.assertTrue(buyer.getUUID().equals(LandService.ownerAt(helper.getLevel(), center)), "the plot went with the business");
		helper.assertFalse(LandService.mayBuild(seller, helper.getLevel(), center), "the seller can no longer build there");
		helper.assertValueEqual(economy.businessesOf(buyer.getUUID()).size(), 2, "the buyer keeps their old business too");

		// The seller must pick a new job, and cannot take back the one they sold.
		helper.assertTrue(economy.needsJob(seller.getUUID()), "the seller needs a new job");
		helper.assertTrue(JobsService.choose(seller, Job.SALESPERSON) == Result.JOB_TAKEN, "the sold job is taken");
		helper.assertTrue(JobsService.choose(seller, Job.STORE_OWNER).ok(), "a shared job is still open");
		helper.succeed();
	}
}
