package com.goldbucks.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.item.ItemStack;

import com.goldbucks.ItemKinds;
import com.goldbucks.JobsService;
import com.goldbucks.economy.ItemKind;
import com.goldbucks.economy.Job;

/** Taking cooked food out of a furnace or smoker counts as a cook's daily task, and gets their perks. */
@Mixin(FurnaceResultSlot.class)
public class FurnaceResultSlotMixin {
	@Shadow
	@Final
	private Player player;

	@Inject(method = "checkTakeAchievements", at = @At("HEAD"))
	private void goldbucks$cookedFood(ItemStack stack, CallbackInfo ci) {
		if (com.goldbucks.Activation.active() && player instanceof ServerPlayer serverPlayer && ItemKinds.of(stack) == ItemKind.FOOD) {
			JobsService.completeTask(serverPlayer, Job.COOK);
			JobsService.cookPerks(serverPlayer, stack);
		}
	}
}
