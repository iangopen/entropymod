package com.entropymod.entropy.loot;

import com.entropymod.entropy.AcquiredEffects;
import com.entropymod.entropy.EntropyManager;
import com.entropymod.entropy.behavior.GraveRobberBehavior;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Grave Robber's mechanism: when a mob dies within
 * {@link GraveRobberBehavior#RADIUS} of a holder, roll that mob's own loot table
 * a second time and drop one stack of the result.
 *
 * <p>Driven from {@code ServerLivingEntityEvents.AFTER_DEATH}, so there is no
 * mixin and nothing is intercepted on the death path -- this never inspects or
 * modifies the original drops, it produces an independent second roll.
 *
 * <p>See {@link GraveRobberBehavior} for the radius, chance and
 * kill-attribution reasoning, and for why re-rolling the mob's own table is what
 * makes the effect farm-safe.
 */
public final class GraveRobberDrops {

	private static final Logger LOGGER = LoggerFactory.getLogger("entropymod");

	/**
	 * Whether the run's acquired set enables this at all.
	 *
	 * <p>Split out and free of Minecraft types so the harness can assert the
	 * regression gate directly: without Grave Robber in the set, no death is
	 * inspected and no loot table is touched.
	 */
	public static boolean isActive(AcquiredEffects acquired) {
		return acquired.contains(GraveRobberBehavior.ID);
	}

	/**
	 * Called for every living entity death on the server.
	 *
	 * <p>Returns before touching the entity when nobody holds the effect, which is
	 * the common case and must stay cheap -- this runs on every mob death in the
	 * world, holder or not.
	 */
	public static void onDeath(LivingEntity dead, DamageSource source) {
		if (!(dead.level() instanceof ServerLevel level)) {
			return;
		}
		// A player's own death is not a grave to rob, and re-rolling the player
		// loot table would duplicate their inventory.
		if (dead instanceof Player) {
			return;
		}
		if (!isActive(EntropyManager.get(level.getServer()).acquired())) {
			return;
		}
		if (!hasHolderNear(level, dead)) {
			return;
		}
		if (!GraveRobberBehavior.rollsBonus(level.getRandom().nextFloat())) {
			return;
		}
		dropBonus(level, dead, source);
	}

	// ------------------------------------------------------------------

	/**
	 * Whether any player is within {@link GraveRobberBehavior#RADIUS} of the dying
	 * mob, in the same dimension.
	 *
	 * <p>Ownership is <b>not</b> re-checked per player: {@code AcquiredEffects} is
	 * the run's single source of truth and {@link #onDeath} has already established
	 * the run holds this effect, so a second per-player test would be restating the
	 * same fact -- exactly the kind of duplicated rule this project has been burned
	 * by (1k/1q/1s). If the acquired set ever becomes per-player, this is the one
	 * place that changes.
	 *
	 * <p>Deliberately a straight scan of the player list rather than an entity
	 * query: the list is tiny (one, in this project's singleplayer scope) where a
	 * {@code getEntitiesOfClass} would build an AABB and walk entity sections for
	 * the same answer. It is also a true <b>sphere</b> via {@code distanceToSqr} --
	 * a box would reach {@code 16 * sqrt(3) = 27.7} blocks at the corners, the 73%
	 * over-range that Danger Sense and Ore Sense both guard against.
	 *
	 * <p>Spectators are excluded: a dead player ghosting past a mob farm should not
	 * collect from it, the same guard the tick services carry.
	 */
	private static boolean hasHolderNear(ServerLevel level, LivingEntity dead) {
		for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
			if (player.level() != level || player.isSpectator()) {
				continue;
			}
			if (GraveRobberBehavior.isNear(player.distanceToSqr(dead))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Rolls the dead mob's own loot table again and drops one stack of the result.
	 *
	 * <p>Uses the <b>public</b>
	 * {@code dropFromLootTable(ServerLevel, DamageSource, boolean, ResourceKey, Consumer)}
	 * overload, whose body builds exactly the {@code LootParams} the death drop
	 * used. Passing a consumer collects the stacks instead of spawning them, so we
	 * choose one rather than doubling the whole roll.
	 *
	 * <p>The {@code playerKill} flag mirrors vanilla's own: {@code dropAllDeathLoot}
	 * derives it from {@code lastHurtByPlayerMemoryTime}, and
	 * {@code getLastHurtByPlayer() != null} is the public expression of the same
	 * fact. Getting it wrong would silently change which loot pool is rolled.
	 */
	private static void dropBonus(ServerLevel level, LivingEntity dead, DamageSource source) {
		Optional<ResourceKey<LootTable>> table = dead.getLootTable();
		if (table.isEmpty()) {
			// Not a failure: plenty of entities legitimately have no loot table.
			return;
		}

		List<ItemStack> rolled = new ArrayList<>();
		boolean playerKill = dead.getLastHurtByPlayer() != null;
		dead.dropFromLootTable(level, source, playerKill, table.get(), rolled::add);

		int index = GraveRobberBehavior.chooseBonusIndex(rolled.size(), level.getRandom().nextFloat());
		if (index < 0) {
			// The second roll produced nothing. Honest outcome, not re-rolled --
			// logged because "procced but the roll was empty" and "never procced"
			// are indistinguishable from the player's seat.
			LOGGER.debug("Grave Robber: bonus roll for {} was empty", dead.getType().toShortString());
			return;
		}

		ItemStack bonus = rolled.get(index);
		if (bonus.isEmpty()) {
			return;
		}
		dead.spawnAtLocation(level, bonus);
		LOGGER.debug("Grave Robber: {} dropped a bonus {} ({} of {} rolled)",
				dead.getType().toShortString(), bonus.getItem(), index + 1, rolled.size());
	}

	private GraveRobberDrops() {}
}
