package com.entropymod.entropy.status;

import com.entropymod.EntropyMod;
import com.entropymod.entropy.EntropyManager;
import com.entropymod.entropy.behavior.SunburnBehavior;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.EquipmentSlot;

/**
 * Sunburn's check. Reads the five inputs {@link SunburnBehavior#shouldIgnite} needs
 * off the world and acts on its answer -- every decision lives there, where the
 * harness can drive it.
 *
 * <p>A tick service rather than a mixin: "is this player standing in sunlight" is a
 * periodic question about the player, not a value vanilla computes for players at
 * all ({@code Mob.isSunBurnTick} is private and mob-only). Returns on one hash
 * lookup for a run that does not hold the effect.
 *
 * <p><b>Logs each ignition at DEBUG</b>, with the position, for the reason Clumsy
 * Digger and Unstable log theirs: "it never fired" and "it fired while I was under
 * a tree" look identical from the player's seat.
 */
public final class SunburnService {

	private SunburnService() {}

	public static void tick(MinecraftServer server) {
		if (server.getTickCount() % SunburnBehavior.CHECK_INTERVAL_TICKS != 0) {
			return;
		}
		if (!EntropyManager.get(server).acquired().contains(SunburnBehavior.ID)) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.isAlive()) {
				continue;
			}
			ServerLevel level = player.level();
			// Both of vanilla's daylight tests: the environment attribute is what
			// Mob.isSunBurnTick reads, and isBrightOutside is kept beside it so that a
			// dimension or datapack that leaves MONSTERS_BURN on at night cannot burn
			// the player in the dark. Requiring both can only ever burn less.
			boolean monstersBurn = level.isBrightOutside()
					&& level.environmentAttributes()
							.getValue(EnvironmentAttributes.MONSTERS_BURN, player.position());
			BlockPos eye = BlockPos.containing(player.getX(), player.getEyeY(), player.getZ());
			boolean wet = player.isInWaterOrRain() || player.isInPowderSnow || player.wasInPowderSnow;
			boolean headCovered = !player.getItemBySlot(EquipmentSlot.HEAD).isEmpty();
			boolean exempt = player.isCreative() || player.isSpectator();

			if (!SunburnBehavior.shouldIgnite(monstersBurn, level.canSeeSky(eye), wet, headCovered, exempt)) {
				continue;
			}
			player.igniteForSeconds(SunburnBehavior.IGNITE_SECONDS);
			EntropyMod.LOGGER.debug("Sunburn: ignited {} at {} for {}s",
					player.getName().getString(), eye, SunburnBehavior.IGNITE_SECONDS);
		}
	}
}
