package com.goldbucks.mixin;

import com.mojang.brigadier.ParseResults;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import com.goldbucks.WorldRules;

/** Stops cheat commands typed by anyone other than the host. */
@Mixin(Commands.class)
public class CommandsMixin {
	@Inject(method = "performCommand", at = @At("HEAD"), cancellable = true)
	private void goldbucks$blockCheats(ParseResults<CommandSourceStack> parseResults, String command, CallbackInfo ci) {
		CommandSourceStack source = parseResults.getContext().getSource();

		if (WorldRules.isOffInThisWorld(command)) {
			source.sendFailure(Component.literal("Goldbucks is off in this world. The host can turn it on with /goldbucks on."));
			ci.cancel();
			return;
		}

		if (WorldRules.isBlocked(source, command)) {
			source.sendFailure(Component.literal("Only the host can use that command."));
			ci.cancel();
		}
	}
}
