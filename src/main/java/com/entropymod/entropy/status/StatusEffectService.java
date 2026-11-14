package com.entropymod.entropy.status;

import com.entropymod.entropy.AcquiredEffects;
import com.entropymod.entropy.EffectBehavior;
import com.entropymod.entropy.EffectBehaviors;
import com.entropymod.entropy.EntropyManager;
import com.entropymod.entropy.StatusEffectBehavior;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Renews every status-effect effect the run holds. See {@link StatusEffectBehavior}
 * for why these are refreshed rather than granted once with an infinite duration.
 *
 * <p>Generic on purpose: it asks each acquired id's behavior whether it is a
 * {@link StatusEffectBehavior} rather than naming the effects, so adding another
 * one is still exactly the three registration steps and never a fourth edit here.
 *
 * <p>Cost: every {@value StatusEffectBehavior#REFRESH_INTERVAL_TICKS} ticks, one map
 * lookup per acquired id per player, and one {@code addEffect} per status effect
 * actually held. Returns on the first check for an empty run.
 *
 * <p>Not gated on the run state, like every other effect service -- see CLAUDE.md
 * "ENDED stops the LOOP, and deliberately nothing else".
 */
public final class StatusEffectService {

	private StatusEffectService() {}

	public static void tick(MinecraftServer server) {
		if (!isRefreshTick(server.getTickCount())) {
			return;
		}
		AcquiredEffects acquired = EntropyManager.get(server).acquired();
		if (acquired.isEmpty()) {
			return;
		}
		for (String id : acquired.ids()) {
			EffectBehavior behavior = EffectBehaviors.get(id);
			if (!(behavior instanceof StatusEffectBehavior status)) {
				continue;
			}
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				// A spectator has no body for a status to act on, and a dead player's
				// effects are restored by reapplyAll on respawn.
				if (player.isSpectator() || !player.isAlive()) {
					continue;
				}
				status.grant(player);
			}
		}
	}

	/** Minecraft-free so the harness can drive the cadence. */
	public static boolean isRefreshTick(int tickCount) {
		return tickCount % StatusEffectBehavior.REFRESH_INTERVAL_TICKS == 0;
	}
}
