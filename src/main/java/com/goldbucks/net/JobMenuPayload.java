package com.goldbucks.net;

import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.goldbucks.GoldbucksMod;

/** Server to client: open the job menu. Lists the jobs (by position in the Job list) that are still free. */
public record JobMenuPayload(List<Integer> availableJobs, String message) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<JobMenuPayload> TYPE =
			new CustomPacketPayload.Type<>(GoldbucksMod.id("job_menu"));

	public static final StreamCodec<ByteBuf, JobMenuPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), JobMenuPayload::availableJobs,
			ByteBufCodecs.STRING_UTF8, JobMenuPayload::message,
			JobMenuPayload::new);

	@Override
	public Type<JobMenuPayload> type() {
		return TYPE;
	}
}
