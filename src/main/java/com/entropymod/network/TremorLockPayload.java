package com.entropymod.network;

import com.entropymod.EntropyMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server -&gt; Client. "An Earthquake tremor has started; you are rooted for this many
 * ticks."
 *
 * <h2>Why a payload at all, when everything else about the tremor is server-side</h2>
 *
 * <p>Earthquake's paralysis is enforced by zeroing {@code xxa} / {@code zza} /
 * {@code jumping} through {@code Player.isImmobile()} -- vanilla's own movement lock.
 * <b>Those three fields only mean anything on the side that SIMULATES the movement, and
 * for a player that is the client:</b> {@code ServerPlayer} never writes any of them
 * (zero {@code putfield}s), because a player's position arrives as movement packets
 * rather than being integrated server-side.
 *
 * <p>Vanilla's own immobilisation confirms the shape rather than contradicting it.
 * {@code ServerGamePacketListenerImpl.handleMovePlayer}, offsets 333-378:
 *
 * <pre>
 * if (this.player.isSleeping()) {
 *     if (distSqr &gt; 1.0) this.teleport(player.getX(), player.getY(), player.getZ(), yRot, xRot);
 *     return;
 * }
 * </pre>
 *
 * <p>a <b>deliberately generous</b> server-side backstop -- it tolerates a full block of
 * slop -- which only works because the client-side input lock has already stopped the
 * player from trying. There is no server-only route to this.
 *
 * <p>So the client has to be told, and it cannot derive it: the tremor schedule is a
 * server-side {@code SpawnSchedule} re-rolled in 900-3600 ticks per fire. This is the
 * smallest thing that carries it.
 *
 * <h2>It carries a DURATION, not a flag, and that is the failure-mode decision</h2>
 *
 * <p>The obvious alternative is two packets -- {@code locked=true} at tick 0 and
 * {@code locked=false} at tick 80. <b>A dropped or reordered "false" would then paralyse
 * the player permanently, with no way out short of a relog.</b> A duration expires by
 * itself: the worst a lost packet can do is fail to start a lock, which costs one tremor
 * and is invisible. The client counts it down in {@code ClientRunState} and
 * {@code reset()} clears it on disconnect.
 *
 * <p>The cost of that choice is that client and server countdowns can drift by the
 * latency of the one packet. For a movement lock that means unlocking a tick or two
 * early or late, which is not observable -- and it is bounded, because the client's
 * countdown is set fresh by every tremor rather than accumulating.
 *
 * <p><b>Sent to the affected player only</b>, via {@code ServerPlayNetworking.send},
 * because a tremor is one player's event. Deliberately not ridden along on
 * {@code ClientEffectsPayload}: that one answers "which effects do you hold", which
 * changes on pick/grant/join, and conflating a per-tremor pulse with it would mean
 * re-sending the whole acquired set every 45-180 seconds.
 *
 * @param ticks how many more ticks the player is rooted for; a fresh tremor sends
 *              {@code EarthquakeBehavior.PARALYSIS_TICKS}
 */
public record TremorLockPayload(int ticks) implements CustomPacketPayload {

	public static final Identifier ID = EntropyMod.id("tremor_lock");
	public static final CustomPacketPayload.Type<TremorLockPayload> TYPE =
			new CustomPacketPayload.Type<>(ID);

	// One component, one codec line. VAR_INT rather than INT because the value is a
	// small positive tick count -- see OpenChoicePayload for the general rule that
	// every record component must have a matching line here.
	public static final StreamCodec<RegistryFriendlyByteBuf, TremorLockPayload> CODEC =
			StreamCodec.composite(
					ByteBufCodecs.VAR_INT, TremorLockPayload::ticks,
					TremorLockPayload::new
			);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
