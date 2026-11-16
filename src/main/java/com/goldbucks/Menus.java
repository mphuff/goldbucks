package com.goldbucks;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import com.goldbucks.net.MenuActionPayload;
import com.goldbucks.net.MenuPayload;

/**
 * Menus the server shows on a player's screen: the store, the town board, the bank and the ballot.
 * A button runs a normal command as the player, so a menu can never do more than typing could.
 * Players without the screen get the same menu in chat, with clickable buttons.
 */
public final class Menus {
	/** Commands a menu button may run. */
	private static final Set<String> ALLOWED = Set.of("store", "board", "bank", "vote", "mayor", "pet", "job", "host", "bill", "news");

	private Menus() {
	}

	/** Builds a menu one row at a time. */
	public static final class Menu {
		final String title;
		String subtitle = "";
		final List<MenuPayload.Row> rows = new ArrayList<>();

		public Menu(String title) {
			this.title = title;
		}

		public Menu subtitle(String subtitle) {
			this.subtitle = subtitle;
			return this;
		}

		public Menu text(String text) {
			rows.add(new MenuPayload.Row(text, "", "", "", false));
			return this;
		}

		public Menu button(String text, String tooltip, String button, String command, boolean enabled) {
			rows.add(new MenuPayload.Row(text, tooltip == null ? "" : tooltip, button, command, enabled));
			return this;
		}

		public List<MenuPayload.Row> rows() {
			return rows;
		}

		public String title() {
			return title;
		}
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(MenuPayload.TYPE, MenuPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(MenuActionPayload.TYPE, MenuActionPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(MenuActionPayload.TYPE, (payload, context) -> {
			if (Activation.active()) {
				run(context.player(), payload.command());
			}
		});
	}

	/** Runs a menu button's command as the player, if it is one menus may run. */
	static void run(ServerPlayer player, String command) {
		String root = WorldRules.rootOf(command);

		if (!ALLOWED.contains(root)) {
			return;
		}

		player.level().getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
	}

	/** The last menu opened for each player, for the tests. */
	private static final java.util.Map<java.util.UUID, Menu> lastOpened = new java.util.concurrent.ConcurrentHashMap<>();

	public static Menu lastOpened(ServerPlayer player) {
		return lastOpened.get(player.getUUID());
	}

	public static void open(ServerPlayer player, Menu menu) {
		lastOpened.put(player.getUUID(), menu);

		if (ServerPlayNetworking.canSend(player, MenuPayload.TYPE)) {
			ServerPlayNetworking.send(player, new MenuPayload(menu.title, menu.subtitle, menu.rows));
			return;
		}

		Text.tell(player, menu.title + (menu.subtitle.isEmpty() ? "" : " - " + menu.subtitle));

		for (MenuPayload.Row row : menu.rows) {
			MutableComponent line = Component.literal(row.text() + " ");

			if (!row.button().isEmpty() && row.enabled()) {
				line.append(Text.button(row.button(), "/" + row.command()));
			}

			player.sendSystemMessage(line);
		}
	}
}
