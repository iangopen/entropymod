package com.entropymod.entropy.companion;

import com.entropymod.EntropyMod;
import com.entropymod.entropy.EntropyManager;
import com.entropymod.entropy.behavior.EmotionalSupportLlamaBehavior;
import com.entropymod.entropy.behavior.LoyalPackBehavior;
import com.entropymod.entropy.behavior.TheAudienceBehavior;
import com.entropymod.entropy.behavior.TheEntourageBehavior;
import com.entropymod.entropy.spawn.SafeSpawn;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Movement for the companions vanilla will not do it for, plus the catch-up
 * teleport shared by all the following ones.
 *
 * <p>Loyal Pack remains absent from the <em>navigation</em> half -- wolves get
 * following and defending free from {@code FollowOwnerGoal} and the two owner
 * target goals, and a second follow loop would mean two systems writing one
 * navigation every tick. It <b>is</b> included in the catch-up half, because that
 * is a teleport rather than a path and cannot conflict with vanilla's steering.
 *
 * <h2>The crowding bug, root cause CONFIRMED rather than assumed</h2>
 *
 * <p>The first version ended with escorts standing on the player. Two independent
 * causes, both read out of bytecode rather than guessed:
 *
 * <ol>
 *   <li><b>{@code moveTo(Entity, speed)} does not respect any stop distance.</b> It
 *       is {@code createPath(entity, 1)} -> {@code createPath(Set.of(entity
 *       .blockPosition()), 1, ...)}, so the path targets the player's own block with
 *       an accuracy of <b>1</b>. {@code FOLLOW_START} only gated <em>when a new path
 *       was issued</em>; every path issued always aimed to within a block of the
 *       player.</li>
 *   <li><b>Nothing ever called {@code stop()}.</b> So a path issued at 6.1 blocks
 *       ran to completion at ~1 block, and the escort arrived on top of the player
 *       exactly as instructed. There was no stop-distance behaviour anywhere in the
 *       loop.</li>
 * </ol>
 *
 * <p>Both are fixed the same way: navigate to a computed point on a standoff
 * <em>ring</em> rather than to the player, and {@code stop()} on arrival in band.
 *
 * <h2>Cost</h2>
 *
 * <p>O(players holding a companion effect x recorded companions) every
 * {@value #TICK_INTERVAL} ticks, with a stall sample every
 * {@value CompanionMotion#STALL_SAMPLE_INTERVAL}. Each companion costs one UUID
 * lookup, one squared-distance compare, and a {@code moveTo} only when it is
 * actually out of band -- a settled escort issues no paths at all, which is a
 * strict improvement on the previous version's re-path every 5 ticks. Line of
 * sight is clipped only for Audience villagers, at most 10 per player per
 * evaluation. Nothing runs when no companion effect is held.
 */
public final class CompanionService {

	/** How often movement is re-evaluated. Four times a second. */
	public static final int TICK_INTERVAL = 5;

	/** Distance history for the catch-up teleport, keyed by entity UUID string. */
	private static final CompanionMotion.StallTracker STALLS = new CompanionMotion.StallTracker();

	/**
	 * Each audience villager's state at the last movement evaluation.
	 *
	 * <p>Two readers, which is why it is a state rather than the boolean set it used
	 * to be: {@code audience} needs {@code wasApproaching} for hysteresis, and
	 * {@code audienceLook} -- which runs on every tick, between movement evaluations --
	 * needs the whole state to ask {@link CompanionMotion#shouldWatchPlayer}. Keeping
	 * the real state means the look gate is driven by the state machine rather than by
	 * a second, parallel notion of "is it running".
	 */
	private static final Map<String, CompanionMotion.AudienceState> AUDIENCE_STATE =
			new HashMap<>();

	private CompanionService() {}

	public static void tick(MinecraftServer server) {
		EntropyManager manager = EntropyManager.get(server);
		boolean pack = manager.acquired().contains(LoyalPackBehavior.ID);
		boolean entourage = manager.acquired().contains(TheEntourageBehavior.ID);
		boolean audience = manager.acquired().contains(TheAudienceBehavior.ID);
		boolean llama = manager.acquired().contains(EmotionalSupportLlamaBehavior.ID);
		if (!pack && !entourage && !audience && !llama) {
			return;
		}

		// The movement pass is the expensive one and stays on the 5-tick cadence. The
		// Audience's look pass runs EVERY tick and is deliberately not gated by it --
		// see audienceLook for why a 1-in-5 refresh would lose the look outright.
		boolean movementPass = server.getTickCount() % TICK_INTERVAL == 0;
		boolean stallSample = server.getTickCount() % CompanionMotion.STALL_SAMPLE_INTERVAL == 0;
		CompanionRoster roster = manager.companions();

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSpectator()) {
				continue;
			}
			if (movementPass) {
				// Catch-up applies to the three FOLLOWING effects. The Audience is
				// excluded by design: it is supposed to lose track of the player and
				// wander, so teleporting it to heel would delete the effect.
				if (pack) {
					catchUp(player, roster, LoyalPackBehavior.ID, stallSample);
				}
				if (entourage) {
					catchUp(player, roster, TheEntourageBehavior.ID, stallSample);
					holdBand(player, roster, TheEntourageBehavior.ID,
							TheEntourageBehavior.MIN_HOLD, TheEntourageBehavior.MAX_HOLD,
							TheEntourageBehavior.FOLLOW_SPEED, true);
				}
				if (llama) {
					catchUp(player, roster, EmotionalSupportLlamaBehavior.ID, stallSample);
					holdBand(player, roster, EmotionalSupportLlamaBehavior.ID,
							EmotionalSupportLlamaBehavior.MIN_HOLD,
							EmotionalSupportLlamaBehavior.MAX_HOLD,
							EmotionalSupportLlamaBehavior.FOLLOW_SPEED, false);
				}
				if (audience) {
					audience(player, roster);
				}
			}
			if (audience) {
				audienceLook(player, roster);
			}
		}
	}

	/** Drops all transient movement state. Called on server stop. */
	public static void reset() {
		STALLS.reset();
		AUDIENCE_STATE.clear();
	}

	// ==================================================================

	/**
	 * Where each effect's companions are put down when they are gathered.
	 *
	 * <p>Per-effect rather than one shared ring, because "beside you" means something
	 * different for an escort than for the Audience: dropping ten villagers at 2-4
	 * blocks would violate {@link TheAudienceBehavior#MIN_DISTANCE} on the very tick
	 * they arrive, and the next evaluation would immediately BACK_OFF all ten.
	 */
	private record GatherSpec(String effectId, double minDistance, double maxDistance) {}

	private static final List<GatherSpec> GATHER_SPECS = List.of(
			new GatherSpec(LoyalPackBehavior.ID,
					CompanionMotion.TELEPORT_MIN_DISTANCE, CompanionMotion.TELEPORT_MAX_DISTANCE),
			new GatherSpec(TheEntourageBehavior.ID,
					CompanionMotion.TELEPORT_MIN_DISTANCE, CompanionMotion.TELEPORT_MAX_DISTANCE),
			new GatherSpec(EmotionalSupportLlamaBehavior.ID,
					CompanionMotion.TELEPORT_MIN_DISTANCE, CompanionMotion.TELEPORT_MAX_DISTANCE),
			// The Audience arrives at its own standoff ring, not at heel.
			new GatherSpec(TheAudienceBehavior.ID,
					TheAudienceBehavior.MIN_DISTANCE, TheAudienceBehavior.RESUME_DISTANCE));

	/**
	 * Pulls a player's companions back to them after a respawn, rejoin or dimension
	 * change. See {@link CompanionMotion#shouldGather} for why this cannot be left to
	 * the per-tick catch-up.
	 *
	 * <p><b>The Audience is included here even though it is excluded from catch-up</b>,
	 * and the distinction is real rather than an inconsistency: catch-up fires
	 * continuously on a stall, which for the Audience would delete the wandering that
	 * is the entire effect. This fires only on a discontinuity, where the alternative
	 * is not "they wander" but "they are 400 blocks away in an unloaded chunk forever".
	 *
	 * @return how many companions were actually moved
	 */
	public static int gather(MinecraftServer server, ServerPlayer player) {
		EntropyManager manager = EntropyManager.get(server);
		CompanionRoster roster = manager.companions();
		if (roster.isEmpty()) {
			return 0;
		}
		ServerLevel level = player.level();
		String owner = player.getUUID().toString();
		int moved = 0;
		int unresolved = 0;

		for (GatherSpec spec : GATHER_SPECS) {
			if (!manager.acquired().contains(spec.effectId())) {
				continue;
			}
			for (String id : roster.uuidsFor(owner, spec.effectId())) {
				// Not level.getEntity: that searches this dimension only, so a companion
				// left in the Nether by a player who died back to the overworld would
				// read as gone. This is vanilla's own cross-dimension lookup and it
				// still costs a plain map hit when the entity is in this level.
				Entity entity = level.getEntityInAnyDimension(UUID.fromString(id));
				if (entity == null) {
					// The one case gather genuinely cannot fix. Named in the log rather
					// than silent -- the spawn batch's lesson was that a failure path
					// which does not say why turns a five-minute diagnosis into a
					// world-save excavation.
					unresolved++;
					continue;
				}
				if (!(entity instanceof Mob companion) || !companion.isAlive()) {
					continue;
				}

				boolean sameLevel = companion.level() == level;
				double distance = sameLevel ? Math.sqrt(companion.distanceToSqr(player)) : -1.0;
				if (!CompanionMotion.shouldGather(sameLevel, distance)) {
					continue;
				}

				SafeSpawn.Attempt spot = SafeSpawn.findNear(level, player, companion.getType(),
						spec.minDistance(), spec.maxDistance(), level.getRandom(),
						SafeSpawn.DistanceMode.HORIZONTAL);
				if (!spot.found()) {
					EntropyMod.LOGGER.debug("Companion gather: no safe spot near {} for {}",
							player.getName().getString(), spec.effectId());
					continue;
				}
				Vec3 centre = Vec3.atBottomCenterOf(spot.pos());
				companion.getNavigation().stop();
				// teleportTo, not snapTo: snapTo cannot cross dimensions, and a companion
				// moved with it would be left registered in the level it came from.
				companion.teleportTo(level, centre.x, centre.y, centre.z, Set.of(),
						companion.getYRot(), companion.getXRot(), false);
				// Both pieces of transient state describe a distance that no longer
				// exists. Leaving them would measure the next sample against a gap from
				// before the teleport.
				STALLS.forget(id);
				AUDIENCE_STATE.remove(id);
				moved++;
			}
		}

		if (moved > 0 || unresolved > 0) {
			EntropyMod.LOGGER.info(
					"Companion gather for {}: {} moved, {} unresolved (not in a loaded chunk "
							+ "in any dimension -- see CompanionMotion.shouldGather)",
					player.getName().getString(), moved, unresolved);
		}
		return moved;
	}

	// ==================================================================

	/**
	 * The shared catch-up teleport: far away AND not closing the gap for three
	 * consecutive seconds means put it down beside the player.
	 */
	private static void catchUp(ServerPlayer player, CompanionRoster roster, String effectId,
								boolean stallSample) {
		if (!stallSample) {
			return;
		}
		ServerLevel level = player.level();
		for (String id : roster.uuidsFor(player.getUUID().toString(), effectId)) {
			Entity entity = level.getEntity(UUID.fromString(id));
			if (!(entity instanceof Mob companion) || !companion.isAlive()) {
				// Unloaded or gone. Drop the history so a companion that reappears is
				// measured afresh rather than against a distance from minutes ago.
				STALLS.forget(id);
				continue;
			}
			if (!STALLS.sample(id, Math.sqrt(companion.distanceToSqr(player)))) {
				continue;
			}

			SafeSpawn.Attempt spot = SafeSpawn.findNear(level, player, companion.getType(),
					CompanionMotion.TELEPORT_MIN_DISTANCE, CompanionMotion.TELEPORT_MAX_DISTANCE,
					level.getRandom(), SafeSpawn.DistanceMode.HORIZONTAL);
			if (!spot.found()) {
				// Same honest outcome SafeSpawn always has: nowhere valid means nothing
				// happens, and the next sample will try again.
				EntropyMod.LOGGER.debug("Companion catch-up: no safe spot near {} for {}",
						player.getName().getString(), effectId);
				continue;
			}
			Vec3 centre = Vec3.atBottomCenterOf(spot.pos());
			companion.getNavigation().stop();
			companion.snapTo(centre.x, centre.y, centre.z, companion.getYRot(), companion.getXRot());
			EntropyMod.LOGGER.debug("Companion catch-up: teleported a {} to {} for {}",
					companion.getType().toShortString(), spot.pos(), effectId);
		}
	}

	/**
	 * Distance-holding: keep companions inside [minHold, maxHold] of the player.
	 *
	 * <p>Both the too-far and too-close cases navigate to the SAME point -- the
	 * middle of the band along the line from the player through the companion -- so
	 * approach and retreat cannot drift apart, and arriving lands mid-band rather
	 * than on a boundary that could flip on the next sample.
	 */
	private static void holdBand(ServerPlayer player, CompanionRoster roster, String effectId,
								 double minHold, double maxHold, double speed, boolean defends) {
		List<String> ids = roster.uuidsFor(player.getUUID().toString(), effectId);
		if (ids.isEmpty()) {
			return;
		}
		ServerLevel level = player.level();
		LivingEntity threat = defends ? player.getLastHurtByMob() : null;
		if (threat != null && (!threat.isAlive() || threat == player)) {
			threat = null;
		}

		double ideal = CompanionMotion.idealHoldDistance(minHold, maxHold);
		for (String id : ids) {
			Entity entity = level.getEntity(UUID.fromString(id));
			if (!(entity instanceof Mob companion) || !companion.isAlive()) {
				continue;
			}

			if (threat != null && companion.getTarget() != threat) {
				companion.setTarget(threat);
			}
			// Never fight the companion's own combat pathing.
			if (companion.getTarget() != null) {
				continue;
			}

			double distance = Math.sqrt(companion.distanceToSqr(player));
			CompanionMotion.BandAction action =
					CompanionMotion.bandAction(distance, minHold, maxHold);
			if (action == CompanionMotion.BandAction.HOLD) {
				// The line the first version was missing entirely. Without it the last
				// path issued -- which aimed a block from the player -- kept running.
				if (!companion.getNavigation().isDone()) {
					companion.getNavigation().stop();
				}
				continue;
			}
			moveToRing(companion, player, ideal, speed);
		}
	}

	/** The Audience's state machine. See {@link CompanionMotion#audienceState}. */
	private static void audience(ServerPlayer player, CompanionRoster roster) {
		List<String> ids = roster.uuidsFor(player.getUUID().toString(), TheAudienceBehavior.ID);
		if (ids.isEmpty()) {
			return;
		}
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();

		for (String id : ids) {
			Entity entity = level.getEntity(UUID.fromString(id));
			if (!(entity instanceof Mob villager) || !villager.isAlive()) {
				AUDIENCE_STATE.remove(id);
				continue;
			}

			double distance = Math.sqrt(villager.distanceToSqr(player));
			// SafeSpawn's own clip, reused rather than reimplemented -- the same
			// COLLIDER/NONE ray LivingEntity.hasLineOfSight uses. Aimed at the
			// villager's eye height so a head visible over a rise counts as seen,
			// which is the same reason SafeSpawn aims up the entity's body.
			boolean seen = SafeSpawn.hasLineOfSight(level, player, eye, villager.getEyePosition());

			CompanionMotion.AudienceState state = CompanionMotion.audienceState(
					distance, seen,
					AUDIENCE_STATE.get(id) == CompanionMotion.AudienceState.APPROACHING,
					TheAudienceBehavior.MIN_DISTANCE, TheAudienceBehavior.RESUME_DISTANCE,
					TheAudienceBehavior.LOST_TRACK_DISTANCE);

			AUDIENCE_STATE.put(id, state);

			switch (state) {
				case FROZEN, IDLE -> {
					// FROZEN is an active stop, not merely "issue nothing": the villager's
					// brain would otherwise keep walking wherever it was already going,
					// and the effect is that being looked at stops them dead.
					if (!villager.getNavigation().isDone()) {
						villager.getNavigation().stop();
					}
				}
				case BACK_OFF -> {
					// The one place the brain's own walk target is overruled, and it is
					// a direct consequence of the look pass: SetWalkTargetFromLookTarget
					// is a member of the villager's IDLE RunOne (javap-verified in
					// VillagerGoalPackages.getIdlePackage), so pointing LOOK_TARGET at
					// the player occasionally makes the brain decide to WALK to within
					// 2 blocks of them. Inside the minimum distance that is unambiguously
					// wrong for this effect, and leaving it would be a visible tug of war
					// against the ring navigation issued on the next line. Scoped to
					// BACK_OFF only -- everywhere else the brain keeps its errands.
					villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
					moveToRing(villager, player, TheAudienceBehavior.MIN_DISTANCE,
							TheAudienceBehavior.FOLLOW_SPEED);
				}
				case APPROACHING ->
						moveToRing(villager, player, TheAudienceBehavior.MIN_DISTANCE,
								TheAudienceBehavior.FOLLOW_SPEED);
			}
		}
	}

	/**
	 * Points every non-approaching audience villager at the player -- the "watching
	 * you" half of the effect.
	 *
	 * <h2>LookControl does NOT persist against the brain. Verified by offset.</h2>
	 *
	 * <p>The obvious implementation is {@code villager.getLookControl().setLookAt(player)}
	 * from this service, and <b>it loses</b> -- the same class of failure as adding a
	 * follow {@code Goal} to a brain-driven mob, reached by a different route. Inside
	 * {@code Mob.serverAiStep()}, javap-verified by offset:
	 *
	 * <table border="1">
	 *   <caption>Order within one entity tick</caption>
	 *   <tr><th>offset</th><th>call</th></tr>
	 *   <tr><td>166</td><td>{@code PathNavigation.tick}</td></tr>
	 *   <tr><td><b>192</b></td><td>{@code customServerAiStep} -> {@code Brain.tick}
	 *       -> <b>{@code LookAtTargetSink}</b>, which is
	 *       {@code getLookControl().setLookAt(lookTarget.currentPosition())}</td></tr>
	 *   <tr><td>223</td><td>{@code MoveControl.tick}</td></tr>
	 *   <tr><td><b>239</b></td><td><b>{@code LookControl.tick}</b> -- the only consumer
	 *       of what was set</td></tr>
	 * </table>
	 *
	 * <p>This service runs on {@code END_SERVER_TICK}, i.e. after offset 239. So a
	 * direct {@code setLookAt} here is overwritten at offset 192 of the <em>next</em>
	 * tick, before offset 239 ever reads it, for as long as the brain holds a
	 * {@code LOOK_TARGET}. And it holds one constantly: {@code LookAtTargetSink} is
	 * registered in the villager's <b>core</b> package (so it runs in every activity),
	 * and {@code SetEntityLookTarget} / {@code SetLookAndInteract} feed it -- with ten
	 * villagers spawned as a group they are permanently within each other's 8-block
	 * look range.
	 *
	 * <p><b>So the look is written into {@code LOOK_TARGET} itself.</b> Same pattern as
	 * Green Thumb: drive the schedule yourself, perform the change through vanilla's
	 * own transition. {@code LookAtTargetSink} then does the actual rotation, on the
	 * brain's own tick, with vanilla's gradual turn rate -- there is nothing left to
	 * fight.
	 *
	 * <p><b>Every tick, not every {@value #TICK_INTERVAL}.</b> Our write lands after
	 * the brain tick, so it owns the memory for exactly the next tick before a
	 * competitor may reclaim it. Refreshing only every fifth tick would hand the look
	 * to another villager for four ticks in five, which at {@code LookControl}'s
	 * gradual turn rate is indistinguishable from the feature not working. The cost is
	 * one map lookup and one memory write per villager per tick.
	 *
	 * <p>{@code EntityTracker(player, true)} tracks the player's <em>eye</em> height,
	 * so they look at your face rather than your feet.
	 */
	private static void audienceLook(ServerPlayer player, CompanionRoster roster) {
		List<String> ids = roster.uuidsFor(player.getUUID().toString(), TheAudienceBehavior.ID);
		if (ids.isEmpty()) {
			return;
		}
		ServerLevel level = player.level();
		for (String id : ids) {
			// The gate, driven by the state machine itself rather than by a second
			// notion of "is it running": a villager mid-approach looks where it is
			// going, and FROZEN, IDLE and BACK_OFF all watch the player. A villager
			// with no recorded state yet has not been evaluated, so it watches.
			CompanionMotion.AudienceState state =
					AUDIENCE_STATE.getOrDefault(id, CompanionMotion.AudienceState.IDLE);
			if (!CompanionMotion.shouldWatchPlayer(state)) {
				continue;
			}
			Entity entity = level.getEntity(UUID.fromString(id));
			if (!(entity instanceof Mob villager) || !villager.isAlive()) {
				continue;
			}
			villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET,
					new EntityTracker(player, true));
		}
	}

	/**
	 * Navigates a companion to the point at {@code radius} from the player, along
	 * the line from the player through the companion.
	 *
	 * <p>This is the fix for the crowding bug in one method: it never targets the
	 * player, so no path can ever end on top of them, and the same call serves
	 * approach and retreat because the target depends only on which side of the
	 * ring the companion is on.
	 */
	private static void moveToRing(Mob companion, ServerPlayer player, double radius, double speed) {
		Vec3 from = player.position();
		Vec3 offset = companion.position().subtract(from);
		double horizontal = Math.sqrt(offset.x * offset.x + offset.z * offset.z);
		// Degenerate case: standing exactly on the player. Any direction will do, and
		// normalising a zero vector would give NaN.
		double dirX = horizontal < 1.0e-4 ? 1.0 : offset.x / horizontal;
		double dirZ = horizontal < 1.0e-4 ? 0.0 : offset.z / horizontal;

		double targetX = from.x + dirX * radius;
		double targetZ = from.z + dirZ * radius;
		companion.getNavigation().moveTo(targetX, from.y, targetZ, speed);
	}
}
