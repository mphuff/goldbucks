package com.goldbucks;


import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleEvents;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.scores.PlayerTeam;


/**
 * Whether Goldbucks is on in the world that is open. It is a game rule, "Goldbucks", so it can be
 * switched on in the Create World screen (More > Game Rules), or later by the host with /goldbucks on.
 * In every other world the mod does nothing at all: no rules, no houses, no files, no commands.
 */
public final class Activation {
	public static GameRule<Boolean> RULE;
	private static volatile boolean active;
	/** The automated tests always play in a Goldbucks world. */
	private static boolean forceOn;

	private Activation() {
	}

	public static boolean active() {
		return active;
	}

	public static void setForceOnForTests(boolean value) {
		forceOn = value;
	}

	public static void register() {
		RULE = GameRuleBuilder.forBoolean(false).category(GameRuleCategory.MISC).buildAndRegister(GoldbucksMod.id("goldbucks"));

		GameRuleEvents.changeCallback(RULE).register((value, server) -> {
			if (value && !active) {
				turnOn(server);
			} else if (!value && active) {
				turnOff(server);
			}
		});

		net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay()
				.register(com.goldbucks.net.OpenClientSettingsPayload.TYPE, com.goldbucks.net.OpenClientSettingsPayload.CODEC);
		ServerLifecycleEvents.SERVER_STARTED.register(Activation::decide);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> active = false);

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(Commands.literal("goldbucks")
				.then(Commands.literal("on").executes(context -> set(context.getSource(), true)))
				.then(Commands.literal("off").executes(context -> set(context.getSource(), false)))
				.then(Commands.literal("settings").executes(context -> {
					ServerPlayer player = context.getSource().getPlayerOrException();

					if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player, com.goldbucks.net.OpenClientSettingsPayload.TYPE)) {
						net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new com.goldbucks.net.OpenClientSettingsPayload());
					}

					return 1;
				}))
				.then(Commands.literal("status").executes(context -> {
					context.getSource().sendSuccess(() -> Component.literal(active
							? "Goldbucks is on in this world." : "Goldbucks is off in this world. The host can type /goldbucks on."), false);
					return 1;
				}))));
	}

	private static int set(CommandSourceStack source, boolean on) {
		ServerPlayer player = source.getPlayer();

		if (player != null && !WorldRules.isHost(source.getServer(), player)) {
			source.sendFailure(Component.literal("Only the host can switch Goldbucks on or off."));
			return 0;
		}

		if (on == active) {
			source.sendSuccess(() -> Component.literal("Goldbucks is already " + (on ? "on" : "off") + " in this world."), false);
			return 1;
		}

		source.getServer().getGameRules().set(RULE, on, source.getServer());
		return 1;
	}

	/** When a world opens: on if the game rule says so, or if it was a Goldbucks world before the rule existed. */
	/** When a world opens: Goldbucks runs only if its game rule is on. */
	static void decide(MinecraftServer server) {
		active = false;

		if (server.getGameRules().get(RULE)) {
			turnOn(server);
		} else if (forceOn) {
			server.getGameRules().set(RULE, true, server);
		}
	}

	static void turnOn(MinecraftServer server) {
		active = true;
		GoldbucksMod.LOGGER.info("Goldbucks is on in this world");
		SettingsService.load(server);
		EconomyService.reset();
		TownSquare.ensureBuilt(server);
		WorldRules.applySettings(server);

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			GoldbucksMod.onJoin(server, player);
			Text.tell(player, "Goldbucks is on in this world!");
		}
	}

	static void turnOff(MinecraftServer server) {
		active = false;
		GoldbucksMod.LOGGER.info("Goldbucks is off in this world");
		EventService.hideBar();

		// Take the titles, colours and debt labels off everyone's names.
		for (PlayerTeam team : java.util.List.copyOf(server.getScoreboard().getPlayerTeams())) {
			if (team.getName().startsWith("gbname_")) {
				server.getScoreboard().removePlayerTeam(team);
			}
		}

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Text.tell(player, "Goldbucks is off in this world. Everything is kept for if it is turned on again.");
		}
	}
}
