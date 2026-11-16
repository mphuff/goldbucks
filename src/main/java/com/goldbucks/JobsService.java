package com.goldbucks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.ConsumeEffect;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.goldbucks.economy.Account;
import com.goldbucks.economy.Business;
import com.goldbucks.economy.Economy;
import com.goldbucks.economy.Job;
import com.goldbucks.economy.Level;
import com.goldbucks.economy.Plot;
import com.goldbucks.economy.Result;
import com.goldbucks.economy.Rules;
import com.goldbucks.economy.Town;
import com.goldbucks.economy.TownEvent;
import com.goldbucks.net.ChooseJobPayload;
import com.goldbucks.net.JobMenuPayload;

/** Jobs in the running game: the menu, the day clock (payday, then a new day), spotting daily tasks, and perks. */
public final class JobsService {
	private static final String COUNTED_BOOK = "goldbucks_counted";
	private static final String COOK_PERK = "goldbucks_cook_perk";
	private static final Random RANDOM = new Random();

	private static final Map<UUID, Vec3> lastRidePosition = new HashMap<>();
	private static final Map<UUID, Double> rideDistance = new HashMap<>();
	/** Whether it thundered since the last dawn, for Storm Repairs. */
	private static boolean thunderedToday;

	private JobsService() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(JobMenuPayload.TYPE, JobMenuPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ChooseJobPayload.TYPE, ChooseJobPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(ChooseJobPayload.TYPE, (payload, context) -> {
			Job[] jobs = Job.values();

			if (!Activation.active()) {
				return;
			}

			if (payload.job() >= 0 && payload.job() < jobs.length) {
				choose(context.player(), jobs[payload.job()]);
			}
		});

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			lastRidePosition.clear();
			rideDistance.clear();
			thunderedToday = false;
		});
		ServerTickEvents.END_SERVER_TICK.register(JobsService::tick);

		WorldRules.openCommand("job");
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
				Commands.literal("job")
						.executes(context -> {
							showStatus(context.getSource().getPlayerOrException());
							return 1;
						})
						.then(Commands.literal("choose")
								.then(Commands.argument("job", StringArgumentType.word())
										.suggests((context, builder) -> {
											for (Job job : Job.values()) {
												builder.suggest(job.name().toLowerCase(Locale.ROOT));
											}

											return builder.buildFuture();
										})
										.executes(context -> {
											ServerPlayer player = context.getSource().getPlayerOrException();
											Job job = parseJob(StringArgumentType.getString(context, "job"));

											if (job == null) {
												Text.warn(player, "There is no such job.");
												return 0;
											}

											return choose(player, job).ok() ? 1 : 0;
										})))));
	}

	static Job parseJob(String name) {
		return Job.parse(name);
	}

	// ---- choosing ----

	public static void onJoin(ServerPlayer player) {
		if (EconomyService.economy(player.level().getServer()).needsJob(player.getUUID())) {
			offerMenu(player, "Welcome! Pick your job.");
		}
	}

	/** Opens the job menu on the player's screen, with a chat version as a fallback. */
	public static void offerMenu(ServerPlayer player, String message) {
		Economy economy = EconomyService.economy(player.level().getServer());
		List<Integer> available = new ArrayList<>();
		MutableComponent chat = Component.literal(message + " ");

		for (Job job : Job.values()) {
			if (economy.jobAvailable(job)) {
				available.add(job.ordinal());
				chat.append(Text.button(job.title() + " $" + job.dailyPay(), "/job choose " + job.name().toLowerCase(Locale.ROOT)))
						.append(" ");
			}
		}

		Text.tell(player, chat);

		if (ServerPlayNetworking.canSend(player, JobMenuPayload.TYPE)) {
			ServerPlayNetworking.send(player, new JobMenuPayload(available, message));
		}
	}

	public static Result choose(ServerPlayer player, Job job) {
		MinecraftServer server = player.level().getServer();
		Result result = EconomyService.economy(server).chooseJob(player.getUUID(), job);

		if (!result.ok()) {
			Text.warn(player, result.message());

			if (result == Result.JOB_TAKEN || result == Result.JOB_CLOSED) {
				offerMenu(player, "Pick a different job.");
			}

			return result;
		}

		EconomyService.changed(server);
		Text.tell(player, "You are now the " + job.title() + ". You earn $" + job.dailyPay()
				+ " each day you do your task: " + job.task() + ".");
		Events.JOB_CHOSEN.forEach(listener -> listener.accept(player));
		NameTags.refresh(server);
		Achievements.check(player);
		return result;
	}

	private static void showStatus(ServerPlayer player) {
		Economy economy = EconomyService.economy(player.level().getServer());
		List<Business> owned = economy.businessesOf(player.getUUID());
		Account account = economy.account(player.getUUID());

		if (owned.isEmpty()) {
			offerMenu(player, "You have no job. Pick one.");
			return;
		}

		for (Business business : owned) {
			Job job = business.job();
			Level level = account.level(job);
			int pay = job.dailyPay() + level.bonus();
			Text.tell(player, level.title() + " " + job.title() + ": " + Text.money(pay) + " a day. Today's task (" + job.task() + ") is "
					+ (business.taskDoneToday() ? "done." : "not done yet."));
			int next = level.nextAt();
			Text.tell(player, "Days worked: " + account.daysWorked(job) + (next < 0 ? ". You are a Master!"
					: ". " + (next - account.daysWorked(job)) + " more to reach the next level."));
		}

		long ticksLeft = Rules.ticksPerDay() - economy.clockTicks() % Rules.ticksPerDay();
		Text.tell(player, "Payday in " + Math.max(1, ticksLeft / 1200) + " min. Tax day in " + economy.daysUntilTax() + " day(s).");
	}

	// ---- tasks ----

	/** Records that the player did the daily task for a job, and tells them the first time each day. */
	public static void completeTask(ServerPlayer player, Job job) {
		MinecraftServer server = player.level().getServer();
		Economy economy = EconomyService.economy(server);
		boolean wasPending = economy.taskPending(player.getUUID(), job);

		if (economy.completeTask(player.getUUID(), job) && wasPending) {
			EconomyService.changed(server);
			Text.tell(player, job.title() + " task done! You will be paid at the end of the day.");
		}
	}

	/** Which of the owner's businesses still have today's task to do. Call around a sale. */
	public static Set<Integer> pendingTasks(Economy economy, UUID owner) {
		Set<Integer> pending = new HashSet<>();

		for (Business business : economy.businessesOf(owner)) {
			if (!business.taskDoneToday()) {
				pending.add(business.id());
			}
		}

		return pending;
	}

	/** A Skilled Cook's food fills one more hunger point; a Master's also gives a burst of speed. */
	public static void cookPerks(ServerPlayer player, ItemStack stack) {
		Economy economy = EconomyService.economy(player.level().getServer());

		if (!economy.holdsJob(player.getUUID(), Job.COOK)) {
			return;
		}

		Level level = economy.account(player.getUUID()).level(Job.COOK);
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);

		if (!level.atLeast(Level.SKILLED) || (data != null && data.copyTag().contains(COOK_PERK))) {
			return;
		}

		FoodProperties food = stack.get(DataComponents.FOOD);

		if (food != null) {
			stack.set(DataComponents.FOOD, new FoodProperties(food.nutrition() + 1, food.saturation(), food.canAlwaysEat()));
		}

		Consumable consumable = stack.get(DataComponents.CONSUMABLE);

		if (level == Level.MASTER && consumable != null) {
			List<ConsumeEffect> effects = new ArrayList<>(consumable.onConsumeEffects());
			effects.add(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.SPEED, 200, 0)));
			stack.set(DataComponents.CONSUMABLE, new Consumable(consumable.consumeSeconds(), consumable.animation(), consumable.sound(),
					consumable.hasConsumeParticles(), effects));
		}

		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(COOK_PERK, true));
	}

	private static boolean owns(Economy economy, UUID player, Job job) {
		return economy.holdsJob(player, job);
	}

	// ---- the clock ----

	private static void tick(MinecraftServer server) {
		if (!Activation.active()) {
			return;
		}

		Economy economy = EconomyService.economy(server);
		List<ServerPlayer> players = server.getPlayerList().getPlayers();
		Set<UUID> online = new HashSet<>();

		for (ServerPlayer player : players) {
			online.add(player.getUUID());
		}

		if (!economy.dayStarted()) {
			startNewDay(server, null);
		}

		if (server.overworld().isThundering()) {
			thunderedToday = true;
		}

		if (server.getTickCount() % 20 == 0) {
			for (ServerPlayer player : players) {
				if (owns(economy, player.getUUID(), Job.LIBRARIAN)) {
					checkForSignedBook(player);
				}

				if (owns(economy, player.getUUID(), Job.TAXI_DRIVER)) {
					checkTaxiRide(player);
				}
			}
		}

		if (server.getTickCount() % 400 == 0) {
			farmerPerks(server);
		}

		boolean dayEnds = (economy.clockTicks() + 1) % Rules.ticksPerDay() == 0;
		HeraldService.TopSeller topSeller = dayEnds ? HeraldService.TopSeller.of(economy) : null;
		Economy.DayReport report = economy.tick(online);

		if (report != null) {
			announceDay(server, economy, report);
			rideDistance.clear();
			startNewDay(server, topSeller);
		}

		if (server.getTickCount() % 100 == 0 || report != null) {
			EconomyService.changed(server);
		}
	}

	/** Runs payday right now, and starts the next day. Used by tests and the host. */
	public static Economy.DayReport endDayNow(MinecraftServer server) {
		Economy economy = EconomyService.economy(server);
		Set<UUID> online = new HashSet<>();

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			online.add(player.getUUID());
		}

		HeraldService.TopSeller topSeller = HeraldService.TopSeller.of(economy);
		Economy.DayReport report = economy.endDay(online, RANDOM);
		announceDay(server, economy, report);
		rideDistance.clear();
		startNewDay(server, topSeller);
		EconomyService.changed(server);
		return report;
	}

	/** Dawn: the day's event, fresh orders, townsfolk plans and the morning news. */
	static void startNewDay(MinecraftServer server, HeraldService.TopSeller topSeller) {
		Economy economy = EconomyService.economy(server);
		ServerLevel level = server.overworld();
		TownSquare.ensureBuilt(server);
		BlockPos square = TownSquare.center(economy.town());
		boolean fullMoon = level.getMoonBrightness(square) >= 0.99f;
		Town.Conditions conditions = new Town.Conditions(level.isRaining(), thunderedToday, fullMoon);
		thunderedToday = false;
		EventService.cleanUp(server);
		TownEvent started = economy.startDay(conditions, RANDOM);
		Townspeople.chooseDestinations(server);
		Townspeople.planDay(server);

		if (started != null) {
			EventService.started(server, started);
		} else {
			EventService.updateBar(server);
		}

		HeraldService.announce(server, topSeller);
		TownSquare.updatePodium(server);
		NameTags.refresh(server);
		EconomyService.changed(server);
	}

	private static void announceDay(MinecraftServer server, Economy economy, Economy.DayReport report) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID id = player.getUUID();
			Account account = economy.account(id);

			if (account == null) {
				continue;
			}

			Integer pay = report.pay().get(id);

			if (pay != null) {
				Notices.title(player, "Payday! +" + Text.money(pay), "Balance: " + Text.money(account.balance()));
				Fx.payday(player);
				Text.tell(player, "Payday! You earned " + Text.money(pay) + ". " + String.join("; ", report.breakdown().getOrDefault(id, List.of()))
						+ ". Balance: " + Text.money(account.balance()) + ".");
			} else if (!economy.businessesOf(id).isEmpty()) {
				Text.warn(player, "Payday, but you did not do your task today, so no pay.");
			}

			Integer tax = report.tax().get(id);

			if (tax != null) {
				Text.warn(player, "Tax day! The government took " + Text.money(tax) + " (" + economy.taxRate() + "% of money over $"
						+ Rules.taxFreeAmount() + "). Balance: " + Text.money(account.balance()) + ".");
			}

			Integer interest = report.interest().get(id);

			if (interest != null) {
				Text.tell(player, "Your savings earned " + Text.money(interest) + " interest. Saved: " + Text.money(account.savings()) + ".");
			}

			if (account.inDebt()) {
				Text.warn(player, "You are " + Text.money(account.debt()) + " in debt.");
			}
		}

		for (Economy.LevelUp levelUp : report.levelUps()) {
			ServerPlayer player = server.getPlayerList().getPlayer(levelUp.player());
			String name = economy.account(levelUp.player()).name();
			String title = levelUp.level().title() + " " + levelUp.job().title();

			if (player != null) {
				Notices.title(player, "Level up!", "You are now " + (levelUp.level() == Level.EXPERT ? "an " : "a ") + title + "!");
				Fx.celebrate(player);
				Text.tell(player, "New perk unlocked! " + Perks.describe(levelUp.job(), levelUp.level()));
			}

			server.getPlayerList().getPlayers().forEach(p -> Text.tell(p, name + " is now " + (levelUp.level() == Level.EXPERT ? "an " : "a ") + title + "!"));
		}

		if (report.feastWinner() != null) {
			String name = economy.account(report.feastWinner()).name();
			server.getPlayerList().getPlayers().forEach(p -> Text.tell(p, name + " filled the most feast orders and wins $10!"));
		}

		if (report.election() != null) {
			MayorService.announceResult(server, report.election());
		}

		server.getPlayerList().getPlayers().forEach(Achievements::check);
	}

	private static void checkForSignedBook(ServerPlayer player) {
		String name = player.getGameProfile().name();

		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);

			if (!stack.is(Items.WRITTEN_BOOK)) {
				continue;
			}

			WrittenBookContent content = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
			CustomData data = stack.get(DataComponents.CUSTOM_DATA);
			boolean counted = data != null && data.copyTag().contains(COUNTED_BOOK);

			if (content != null && name.equals(content.author()) && !counted) {
				CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(COUNTED_BOOK, true));
				completeTask(player, Job.LIBRARIAN);
			}
		}
	}

	private static void checkTaxiRide(ServerPlayer driver) {
		UUID id = driver.getUUID();
		Entity vehicle = driver.getVehicle();
		boolean carrying = false;

		if (vehicle != null) {
			for (Entity passenger : vehicle.getRootVehicle().getPassengers()) {
				if (passenger instanceof ServerPlayer other && other != driver) {
					carrying = true;
				}
			}
		}

		if (!carrying) {
			lastRidePosition.remove(id);
			return;
		}

		Vec3 now = driver.position();
		Vec3 before = lastRidePosition.put(id, now);

		if (before != null) {
			double previous = rideDistance.getOrDefault(id, 0.0);
			double total = rideDistance.merge(id, before.distanceTo(now), Double::sum);

			if (total >= Rules.TAXI_RIDE_BLOCKS) {
				if (previous < Rules.TAXI_RIDE_BLOCKS) {
					EconomyService.economy(driver.level().getServer()).count(id, "passengers", 1);
				}

				completeTask(driver, Job.TAXI_DRIVER);
			}
		}
	}

	/** Expert Farmers' crops grow faster on their plot; Masters' animals are ready to breed again sooner. */
	private static void farmerPerks(MinecraftServer server) {
		Economy economy = EconomyService.economy(server);
		ServerLevel level = server.overworld();

		for (Business business : economy.businesses()) {
			Account owner = economy.account(business.owner());
			Plot plot = business.plot();

			if (business.job() != Job.FARMER || plot == null || owner == null || !owner.level(Job.FARMER).atLeast(Level.EXPERT)
					|| !level.isLoaded(new BlockPos(plot.centerX(), 64, plot.centerZ()))) {
				continue;
			}

			for (int x = plot.minX(); x < plot.minX() + plot.size(); x++) {
				for (int z = plot.minZ(); z < plot.minZ() + plot.depth(); z++) {
					int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);

					for (int y = top - 2; y <= top; y++) {
						BlockPos pos = new BlockPos(x, y, z);
						BlockState state = level.getBlockState(pos);

						// About a quarter of a normal growth step each time, on top of normal growth.
						if (state.getBlock() instanceof CropBlock crop && !crop.isMaxAge(state) && RANDOM.nextInt(16) == 0) {
							level.setBlock(pos, crop.getStateForAge(crop.getAge(state) + 1), 2);
						}
					}
				}
			}

			if (owner.level(Job.FARMER) == Level.MASTER) {
				AABB box = new AABB(plot.minX(), level.getMinY(), plot.minZ(), plot.minX() + plot.size(), level.getMaxY(), plot.minZ() + plot.depth());

				for (Animal animal : level.getEntitiesOfClass(Animal.class, box)) {
					if (animal.getAge() > 1200) {
						animal.setAge(animal.getAge() - 1200);
					}
				}
			}
		}
	}
}
