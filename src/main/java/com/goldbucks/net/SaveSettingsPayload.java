package com.goldbucks.net;

import java.util.Map;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.goldbucks.GoldbucksMod;

/** Client to server: the host pressed Save on the settings screen. Only changed settings are sent. */
public record SaveSettingsPayload(Map<String, String> changes) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SaveSettingsPayload> TYPE = new CustomPacketPayload.Type<>(GoldbucksMod.id("save_settings"));

	public static final StreamCodec<ByteBuf, SaveSettingsPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.<ByteBuf, String, String, Map<String, String>>map(size -> new java.util.LinkedHashMap<>(), ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8), SaveSettingsPayload::changes,
			SaveSettingsPayload::new);

	@Override
	public Type<SaveSettingsPayload> type() {
		return TYPE;
	}
}
