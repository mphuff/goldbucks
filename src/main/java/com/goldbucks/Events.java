package com.goldbucks;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import net.minecraft.server.level.ServerPlayer;

/** Hooks that let one part of the mod react to another without depending on it. */
public final class Events {
	/** Fired after a player successfully picks a job. */
	public static final List<Consumer<ServerPlayer>> JOB_CHOSEN = new CopyOnWriteArrayList<>();

	private Events() {
	}
}
