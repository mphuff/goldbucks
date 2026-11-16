package com.goldbucks.test;

import net.fabricmc.api.ModInitializer;

import com.goldbucks.Activation;
import com.goldbucks.LandService;
import com.goldbucks.SettingsService;
import com.goldbucks.TownSquare;
import com.goldbucks.UpgradeBuilder;

/**
 * Test-only settings. The test server runs its tests right at the world spawn, so houses and the
 * town square are built well away from it, and the town stays quiet (no random events, no townsfolk)
 * unless a test asks for them.
 */
public class TestSetup implements ModInitializer {
	@Override
	public void onInitialize() {
		LandService.setNeighborhoodOffset(8, 400);
		TownSquare.setOffset(8, 300);
		UpgradeBuilder.setInstant(true);
		Activation.setForceOnForTests(true);
		SettingsService.setTestOverride(settings -> {
			settings.eventsEnabled = false;
			settings.townsfolkEnabled = false;
			// Achievement rewards would change the balances other tests check; the achievement tests turn them back on.
			settings.achievementReward = 0;
			settings.bigAchievementReward = 0;
		});
	}
}
