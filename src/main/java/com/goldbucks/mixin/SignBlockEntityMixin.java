package com.goldbucks.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.FilteredText;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignTextSlot;

import com.goldbucks.ShopService;

/** When a player finishes writing a sign, tell them whether it works as a shop. */
@Mixin(SignBlockEntity.class)
public class SignBlockEntityMixin {
	@Inject(method = "updateSignText", at = @At("TAIL"))
	private void goldbucks$signWritten(Player player, SignTextSlot slot, List<FilteredText> lines, CallbackInfo ci) {
		SignBlockEntity sign = (SignBlockEntity) (Object) this;

		if (com.goldbucks.Activation.active() && player instanceof ServerPlayer serverPlayer && sign.getLevel() != null) {
			ShopService.onSignEdited(serverPlayer, sign.getLevel(), sign.getBlockPos());
		}
	}
}
