package com.goldbucks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import com.goldbucks.economy.Economy;
import com.goldbucks.economy.Settings;
import com.goldbucks.economy.TownData;
import com.goldbucks.economy.TownEvent;
import com.goldbucks.net.SaveSettingsPayload;
import com.goldbucks.net.SettingsPayload;

/** The host's tools for the town update: settings, events, rewards, contests and impeachment. */
public final class HostService {
	private HostService() {
	}

	/** Host commands may be run by the host player or from the server console. */
	static boolean isHost(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		return player == null || WorldRules.isHost(source.getServer(), player);
	}

	private interface HostCommand {
		int run(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException;
	}

	private static com.mojang.brigadier.Command<CommandSourceStack> hostOnly(HostCommand command) {
		return context -> {
			if (!isHost(context.getSource())) {
				context.getSource().sendFailure(Component.literal("Only the host can use that command."));
				return 0;
			}

			return command.run(context);
		};
	}

	private static void reply(CommandSourceStack source, String message) {
		source.sendSuccess(() -> Component.literal(message), false);
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(SettingsPayload.TYPE, SettingsPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SaveSettingsPayload.TYPE, SaveSettingsPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(SaveSettingsPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();

			if (!Activation.active()) {
				return;
			}

			if (!WorldRules.isHost(player.level().getServer(), player)) {
				Text.warn(player, "Only the host can change settings.");
				return;
			}

			saveFromScreen(player, payload.changes());
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(Commands.literal("host")
				.then(Commands.literal("settings").executes(hostOnly(context -> {
					openSettings(context.getSource().getPlayerOrException());
					return 1;
				})))
				.then(Commands.literal("get").then(Commands.argument("setting", StringArgumentType.word())
						.suggests((context, builder) -> {
							Settings.simpleNames().forEach(builder::suggest);
							return builder.buildFuture();
						})
						.executes(hostOnly(context -> {
							String name = StringArgumentType.getString(context, "setting");
							String value = Settings.current().get(name);

							if (value == null) {
								context.getSource().sendFailure(Component.literal("There is no setting called " + name + "."));
								return 0;
							}

							reply(context.getSource(), name + " = " + value);
							return 1;
						}))))
				.then(Commands.literal("set").then(Commands.argument("setting", StringArgumentType.word())
						.suggests((context, builder) -> {
							Settings.simpleNames().forEach(builder::suggest);
							return builder.buildFuture();
						})
						.then(Commands.argument("value", StringArgumentType.greedyString()).executes(hostOnly(context -> {
							String name = StringArgumentType.getString(context, "setting");
							List<String> problems = SettingsService.change(context.getSource().getServer(),
									Map.of(name, StringArgumentType.getString(context, "value")));

							if (!problems.isEmpty()) {
								problems.forEach(problem -> context.getSource().sendFailure(Component.literal(problem)));
								return 0;
							}

							reply(context.getSource(), "Saved: " + name + " = " + Settings.current().get(name));
							return 1;
						})))))
				.then(Commands.literal("event")
						.then(Commands.literal("list").executes(hostOnly(context -> {
							for (TownEvent event : TownEvent.values()) {
								reply(context.getSource(), event.id() + " - " + TownData.current().eventName(event));
							}

							return 1;
						})))
						.then(Commands.literal("stop").executes(hostOnly(context -> {
							EventService.hostStop(context.getSource().getServer());
							reply(context.getSource(), "Event stopped.");
							return 1;
						})))
						.then(Commands.literal("start").then(Commands.argument("event", StringArgumentType.word())
								.suggests((context, builder) -> {
									for (TownEvent event : TownEvent.values()) {
										builder.suggest(event.id());
									}

									return builder.buildFuture();
								})
								.executes(hostOnly(context -> {
									TownEvent event = TownEvent.parse(StringArgumentType.getString(context, "event"));

									if (event == null || event == TownEvent.CONTEST) {
										context.getSource().sendFailure(Component.literal(event == null
												? "There is no such event. Try /host event list." : "Start a contest with /host contest <theme>."));
										return 0;
									}

									EventService.hostStart(context.getSource().getServer(), event);
									return 1;
								})))))
				.then(Commands.literal("bonus").then(Commands.argument("player", EntityArgument.player())
						.then(Commands.argument("amount", IntegerArgumentType.integer(1, 10000))
								.executes(hostOnly(context -> bonus(context, "a job well done")))
								.then(Commands.argument("reason", StringArgumentType.greedyString())
										.executes(hostOnly(context -> bonus(context, StringArgumentType.getString(context, "reason"))))))))
				.then(Commands.literal("contest")
						.then(Commands.literal("winner").then(Commands.argument("player", EntityArgument.player())
								.then(Commands.argument("prize", IntegerArgumentType.integer(1, 10000)).executes(hostOnly(context -> {
									MinecraftServer server = context.getSource().getServer();
									ServerPlayer winner = EntityArgument.getPlayer(context, "player");
									int prize = IntegerArgumentType.getInteger(context, "prize");
									Economy economy = EconomyService.economy(server);
									String theme = economy.town().eventData().getOrDefault("theme", "the contest");
									economy.reward(winner.getUUID(), prize);
									economy.joinEvent(winner.getUUID());
									EconomyService.changed(server);

									for (ServerPlayer player : server.getPlayerList().getPlayers()) {
										Notices.title(player, winner.getGameProfile().name() + " wins!", theme + " - " + Text.money(prize));
									}

									Fx.celebrate(winner);
									EventService.hostStop(server);
									return 1;
								})))))
						.then(Commands.argument("theme", StringArgumentType.greedyString()).executes(hostOnly(context -> {
							EventService.startContest(context.getSource().getServer(), StringArgumentType.getString(context, "theme"));
							return 1;
						}))))
				.then(Commands.literal("impeach").executes(hostOnly(context -> {
					MinecraftServer server = context.getSource().getServer();
					EconomyService.economy(server).impeach();
					EconomyService.changed(server);
					TownSquare.updatePodium(server);
					NameTags.refresh(server);
					server.getPlayerList().getPlayers().forEach(p -> Text.tell(p, "The host has removed the Mayor. There will be an election tomorrow."));
					return 1;
				})))
				.then(Commands.literal("problems").executes(hostOnly(context -> {
					List<String> problems = SettingsService.problems();
					reply(context.getSource(), problems.isEmpty() ? "No problems with the settings or data files." : String.join("\n", problems));
					return 1;
				})))));
	}

	private static int bonus(CommandContext<CommandSourceStack> context, String reason)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		MinecraftServer server = context.getSource().getServer();
		ServerPlayer player = EntityArgument.getPlayer(context, "player");
		int amount = IntegerArgumentType.getInteger(context, "amount");
		EconomyService.economy(server).reward(player.getUUID(), amount);
		EconomyService.changed(server);
		String line = player.getGameProfile().name() + " got a " + Text.money(amount) + " bonus from the host for " + reason + "!";
		server.getPlayerList().getPlayers().forEach(p -> Text.tell(p, line));
		return 1;
	}

	/** Every setting and its current value, for the settings screen. */
	public static SettingsPayload settingsPayload() {
		Settings settings = Settings.current();
		List<SettingsPayload.Entry> entries = new ArrayList<>();

		for (String name : Settings.simpleNames()) {
			Settings.Info info = Settings.info(name);
			String value = settings.get(name);
			entries.add(new SettingsPayload.Entry(name, info.tab(), info.text(), value, value.equals("true") || value.equals("false")));
		}

		for (com.goldbucks.economy.Job job : com.goldbucks.economy.Job.values()) {
			String id = job.name().toLowerCase(java.util.Locale.ROOT);
			entries.add(new SettingsPayload.Entry("pay." + id, "Jobs", job.title() + ": pay per day", settings.get("pay." + id), false));
			entries.add(new SettingsPayload.Entry("job." + id, "Jobs", job.title() + ": can be picked", settings.get("job." + id), true));
		}

		entries.add(new SettingsPayload.Entry("disabledEvents", "Events", "Events that never start by themselves (ids, separated by commas)",
				settings.get("disabledEvents"), false));
		return new SettingsPayload(entries, SettingsService.problems());
	}

	public static void openSettings(ServerPlayer player) {
		if (ServerPlayNetworking.canSend(player, SettingsPayload.TYPE)) {
			ServerPlayNetworking.send(player, settingsPayload());
		} else {
			Text.tell(player, "Use /host get <setting> and /host set <setting> <value>.");
		}
	}

	static void saveFromScreen(ServerPlayer player, Map<String, String> changes) {
		if (changes.isEmpty()) {
			Text.tell(player, "Nothing changed.");
			return;
		}

		List<String> problems = SettingsService.change(player.level().getServer(), changes);

		if (problems.isEmpty()) {
			Text.tell(player, "Settings saved (" + changes.size() + " changed).");
			EconomyService.resendSidebar();
			return;
		}

		Text.warn(player, "Nothing was saved, because:");
		problems.forEach(problem -> Text.warn(player, problem));
		ServerPlayNetworking.send(player, new SettingsPayload(settingsPayload().entries(), problems));
	}
}
