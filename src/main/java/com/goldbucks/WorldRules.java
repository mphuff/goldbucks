package com.goldbucks;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.GameType;

import com.goldbucks.economy.Settings;

/**
 * The house rules of the world: everyone plays in survival style (no flying, no all-items menu),
 * there are no monsters, nobody can be hurt or die, and only the host may use cheat commands.
 */
public final class WorldRules {
	/** Commands anyone may use. Everything else is host-only. */
	private static final Set<String> OPEN_COMMANDS = ConcurrentHashMap.newKeySet();
	/** The mod's own commands, which only work in a Goldbucks world. */
	private static final Set<String> MOD_COMMANDS = ConcurrentHashMap.newKeySet();

	static {
		MOD_COMMANDS.add("host");
	}

	static {
		OPEN_COMMANDS.addAll(Set.of("msg", "tell", "w", "me", "help", "list", "teammsg", "tm", "trigger"));
	}

	private WorldRules() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(WorldRules::onServerStarted);
		ServerTickEvents.END_SERVER_TICK.register(WorldRules::tick);

		// No monsters: remove anything hostile the moment it appears.
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Enemy && Activation.active() && Settings.current().noMonsters) {
				entity.discard();
			}
		});

		// Players cannot be hurt, so they cannot die.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(entity instanceof ServerPlayer) || !Activation.active() || !Settings.current().noDeath);
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer player && Activation.active() && Settings.current().noDeath) {
				player.setHealth(player.getMaxHealth());
				return false;
			}

			return true;
		});
	}

	/** Lets the mod's own commands be used by everyone. */
	public static void openCommand(String name) {
		OPEN_COMMANDS.add(name);
		MOD_COMMANDS.add(name);
	}

	private static void onServerStarted(MinecraftServer server) {
		if (Activation.active()) {
			applySettings(server);
		}
	}

	/** Applies the world-rule settings that are not checked every moment. */
	public static void applySettings(MinecraftServer server) {
		// Peaceful difficulty would switch hunger off.
		if (server.overworld().getDifficulty() == Difficulty.PEACEFUL) {
			server.setDifficulty(Difficulty.EASY, true);
		}

		if (Settings.current().survivalOnly) {
			server.setDefaultGameType(GameType.SURVIVAL);
		}
	}

	public static void onJoin(ServerPlayer player) {
		if (Settings.current().survivalOnly) {
			enforceGameMode(player);
		}
	}

	private static void tick(MinecraftServer server) {
		if (!Activation.active()) {
			return;
		}

		if (server.getTickCount() % 20 != 0) {
			return;
		}

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!isHost(server, player) && Settings.current().survivalOnly) {
				enforceGameMode(player);
			}

			rescueFromVoid(player);
		}
	}

	private static void enforceGameMode(ServerPlayer player) {
		if (player.gameMode.getGameModeForPlayer() != GameType.SURVIVAL) {
			player.setGameMode(GameType.SURVIVAL);
		}
	}

	private static void rescueFromVoid(ServerPlayer player) {
		ServerLevel level = player.level();

		if (player.getY() < level.getMinY() - 16) {
			BlockPos spawn = level.getRespawnData().pos();
			player.teleportTo(spawn.getX() + 0.5, spawn.getY() + 1, spawn.getZ() + 0.5);
			player.fallDistance = 0;
		}
	}

	/** The host is the player whose computer opened the world. */
	public static boolean isHost(MinecraftServer server, ServerPlayer player) {
		return server.isSingleplayerOwner(player.nameAndId());
	}

	/** True when this command must be refused: a cheat command typed by a player who is not the host. */
	public static boolean isBlocked(CommandSourceStack source, String command) {
		ServerPlayer player = source.getPlayer();

		if (player == null || !Activation.active() || isHost(source.getServer(), player) || !Settings.current().cheatsHostOnly) {
			return false;
		}

		String root = rootOf(command);

		// A command that does not exist gets the game's normal "unknown command" answer, not "host only".
		if (source.getServer().getCommands().getDispatcher().getRoot().getChild(root) == null) {
			return false;
		}

		return !OPEN_COMMANDS.contains(root);
	}

	/**
	 * True when this is one of the mod's commands typed in a world where Goldbucks is off.
	 * Only /goldbucks on, off and status still work there.
	 */
	public static boolean isOffInThisWorld(String command) {
		if (Activation.active()) {
			return false;
		}

		String root = rootOf(command);
		String rest = command.strip().replaceFirst("^/", "").toLowerCase(Locale.ROOT);
		boolean switching = root.equals("goldbucks") && (rest.endsWith(" on") || rest.endsWith(" off") || rest.endsWith(" status") || rest.endsWith(" settings"));
		return MOD_COMMANDS.contains(root) && !switching;
	}

	static String rootOf(String command) {
		String trimmed = command.strip();

		if (trimmed.startsWith("/")) {
			trimmed = trimmed.substring(1);
		}

		int space = trimmed.indexOf(' ');
		String root = space < 0 ? trimmed : trimmed.substring(0, space);
		int colon = root.indexOf(':');
		return (colon < 0 ? root : root.substring(colon + 1)).toLowerCase(Locale.ROOT);
	}
}
