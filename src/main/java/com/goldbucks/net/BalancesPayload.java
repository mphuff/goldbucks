package com.goldbucks.net;

import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.goldbucks.GoldbucksMod;

/** Server to client: who is online, their cash and net worth, for the side bar. Colour is an RGB name colour, or -1. */
public record BalancesPayload(List<Entry> entries) implements CustomPacketPayload {
	public record Entry(String name, int cash, int netWorth, int colour) {
		public static final StreamCodec<ByteBuf, Entry> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, Entry::name,
				ByteBufCodecs.INT, Entry::cash,
				ByteBufCodecs.INT, Entry::netWorth,
				ByteBufCodecs.INT, Entry::colour,
				Entry::new);
	}

	public static final CustomPacketPayload.Type<BalancesPayload> TYPE =
			new CustomPacketPayload.Type<>(GoldbucksMod.id("balances"));

	public static final StreamCodec<ByteBuf, BalancesPayload> CODEC = StreamCodec.composite(
			Entry.CODEC.apply(ByteBufCodecs.list()), BalancesPayload::entries,
			BalancesPayload::new);

	@Override
	public Type<BalancesPayload> type() {
		return TYPE;
	}
}
