package com.goldbucks.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;

import com.goldbucks.LandService;

/** Tells the mod when a player places a block, for Builders' tasks and events. */
@Mixin(BlockItem.class)
public class BlockItemMixin {
	@Inject(method = "place", at = @At("RETURN"))
	private void goldbucks$placed(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
		if (com.goldbucks.Activation.active() && cir.getReturnValue().consumesAction() && context.getPlayer() instanceof ServerPlayer player) {
			LandService.onPlaced(player, context.getClickedPos(), context.getLevel().getBlockState(context.getClickedPos()));
		}
	}
}
