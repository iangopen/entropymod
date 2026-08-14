package com.entropymod.entropy.quake;

import com.entropymod.EntropyMod;
import com.entropymod.entropy.AcquiredEffects;
import com.entropymod.entropy.EntropyDamageTypes;
import com.entropymod.entropy.EntropyManager;
import com.entropymod.entropy.behavior.EarthquakeBehavior;
import com.entropymod.entropy.spawn.SpawnSchedule;
import com.entropymod.network.TremorLockPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.LevelEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Earthquake's tick service: a random-interval tremor of Slowness IV, a two-second
 * blindness, two half-heart hits of true damage, and nine bursts of ground dust and
 * shattering stone.
 *
 * <p>Third shape alongside attribute and mixin, exactly like {@code GreenThumbGrowth}
 * and the two spawn effects -- the mechanic is a schedule of ours rather than a value
 * vanilla computes, so there is nothing to inject into.
 *
 * <h2>SpawnSchedule WITHOUT SafeSpawn, and that separation is the point</h2>
 *
 * <p>{@code SpawnSchedule} is reused verbatim for the cadence: 45s-3min, re-rolled on
 * every fire, one timer per player, {@code retainAll} on the live set every tick.
 * <b>Nothing here spawns anything</b>, so no position search is needed and
 * {@code SafeSpawn} is not referenced -- which the harness asserts. The two arrived
 * together for Unstable and Creeper Magnet and it would be easy to assume they are one
 * component; they are not, and the spawn cluster's own post-mortem is the reason to
 * keep them apart (every line of {@code SafeSpawn} needed a {@code ServerLevel}, which
 * is what made it the untestable half).
 *
 * <h2>The tremor window is a second, tiny piece of state</h2>
 *
 * <p>A tremor is 80 ticks long and does something on two of them, so the schedule
 * alone is not enough -- firing has to open a window that is then counted down. That
 * is {@link #ACTIVE}, an elapsed-tick counter per player, and it is transient for the
 * same reason {@code SpawnSchedule} is: a part-finished tremor describes this session,
 * not the run, and losing it on a reload costs at most one tremor. All the decisions
 * it drives ({@code isDamageTick}, {@code isFinished}) live in
 * {@link EarthquakeBehavior} as Minecraft-free statics so the harness drives them.
 *
 * <h2>Cost</h2>
 *
 * <p>O(players holding the effect) per tick: one hash lookup and one integer decrement
 * each, plus one {@code addEffect} and at most one {@code hurtServer} on the two ticks
 * of a tremor that do anything. Nothing runs at all when nobody holds it.
 */
public final class EarthquakeService {

	private static final SpawnSchedule<UUID> SCHEDULE = new SpawnSchedule<>(
			EarthquakeBehavior.MIN_INTERVAL_TICKS, EarthquakeBehavior.MAX_INTERVAL_TICKS);

	/** Ticks elapsed in each player's current tremor. Absent means no tremor running. */
	private static final Map<UUID, Integer> ACTIVE = new HashMap<>();

	private EarthquakeService() {}

	public static void tick(MinecraftServer server) {
		AcquiredEffects acquired = EntropyManager.get(server).acquired();
		if (!acquired.contains(EarthquakeBehavior.ID)) {
			if (!SCHEDULE.isEmpty() || !ACTIVE.isEmpty()) {
				reset();
			}
			return;
		}

		List<ServerPlayer> players = server.getPlayerList().getPlayers();
		Set<UUID> live = new HashSet<>(players.size());
		for (ServerPlayer player : players) {
			live.add(player.getUUID());
		}
		SCHEDULE.retainAll(live);
		ACTIVE.keySet().retainAll(live);

		for (ServerPlayer player : players) {
			// A spectator cannot be slowed or hurt and a creative player is in no
			// danger; neither is worth a tremor. Same gate the spawn effects use.
			if (player.isSpectator() || player.isCreative()) {
				continue;
			}
			UUID id = player.getUUID();

			// An already-running tremor is advanced BEFORE the schedule is ticked, so a
			// new tremor can never be started on a tick the previous one is still using.
			if (ACTIVE.containsKey(id)) {
				advanceTremor(player, id);
				continue;
			}
			if (SCHEDULE.tick(id)) {
				beginTremor(player, id);
			}
		}
	}

	/** Drops all transient state. Called on server stop. */
	public static void reset() {
		SCHEDULE.reset();
		ACTIVE.clear();
	}

	/**
	 * Whether this player is inside a tremor and therefore rooted, server-side.
	 *
	 * <p>Read by {@code PlayerImmobileMixin} through
	 * {@code EffectHooks.isMovementLocked}. This is the server's own answer; the client
	 * keeps its own countdown from {@code TremorLockPayload} and the two are read by
	 * side-scoped mixins, exactly as Slippery Grip's pair are.
	 *
	 * <p>Absent means no tremor, so a player without the effect costs one hash miss.
	 */
	public static boolean isTremorActive(UUID playerId) {
		Integer elapsed = ACTIVE.get(playerId);
		return elapsed != null && EarthquakeBehavior.isParalysed(elapsed);
	}

	// ==================================================================

	private static void beginTremor(ServerPlayer player, UUID id) {
		// Blindness for the WHOLE window, granted once here rather than re-applied per
		// tick. Its own remaining duration is what drives the client's fog lerp, so
		// refreshing it every tick would pin the remaining value and delete the
		// ease-out -- and the overshoot that keeps the closing hit at full darkness
		// (see EarthquakeBehavior.BLINDNESS_TICKS) is expressed entirely in this one
		// number. Refreshing would silently destroy it.
		player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS,
				EarthquakeBehavior.BLINDNESS_TICKS, EarthquakeBehavior.BLINDNESS_AMPLIFIER));
		// Mining Fatigue I for the window. A real MobEffectInstance for the same reason
		// as the blindness: vanilla owns the expiry, so there is no mod-side timer and
		// nothing to clean up if the player logs out mid-tremor.
		player.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE,
				EarthquakeBehavior.MINING_FATIGUE_TICKS,
				EarthquakeBehavior.MINING_FATIGUE_AMPLIFIER));
		// The movement lock. Server-side ACTIVE is what Player.isImmobile() reads here;
		// the packet is what lets the CLIENT's copy of that mixin zero the real input,
		// which is the half that actually stops the player. See TremorLockPayload for
		// why this cannot be done server-side alone.
		ServerPlayNetworking.send(player,
				new TremorLockPayload(EarthquakeBehavior.PARALYSIS_TICKS));
		ACTIVE.put(id, 0);
		// Tick 0 is a damage tick, so the opening hit lands on the same tick the
		// Slowness does -- see EarthquakeBehavior.DAMAGE_TICKS for why the hits bracket
		// the window rather than being spaced inside it.
		applyTick(player, 0);
	}

	private static void advanceTremor(ServerPlayer player, UUID id) {
		int elapsed = ACTIVE.get(id) + 1;
		applyTick(player, elapsed);
		if (EarthquakeBehavior.isFinished(elapsed)) {
			ACTIVE.remove(id);
		} else {
			ACTIVE.put(id, elapsed);
		}
	}

	private static void applyTick(ServerPlayer player, int elapsed) {
		if (EarthquakeBehavior.isParticleTick(elapsed)) {
			// ONE call for both cues, on purpose. The dust and the sound are the same
			// event and must never be able to drift into different cadences -- the way
			// to guarantee that is for there to be no second place that decides when a
			// burst happens. isParticleTick is the only schedule.
			shatterGround(player, elapsed);
		}
		if (!EarthquakeBehavior.isDamageTick(elapsed)) {
			return;
		}
		player.hurtServer(player.level(), EntropyDamageTypes.tremor(player.level()),
				EarthquakeBehavior.TREMOR_DAMAGE);
		// Logged because this effect's two hits are half a heart each and land four
		// seconds apart: "the second hit did not land" and "the second hit landed and I
		// did not notice" are indistinguishable from the player's seat, which is
		// exactly the ambiguity Double Jump and Clumsy Digger were both reported under.
		EntropyMod.LOGGER.debug("Earthquake: tremor hit {} of {} on {} at t+{}",
				elapsed == 0 ? 1 : 2, EarthquakeBehavior.DAMAGE_TICKS.length,
				player.getName().getString(), elapsed);
	}

	/**
	 * One burst of ground dust under the player.
	 *
	 * <h2>DUST_PILLAR is vanilla's own "the ground was smashed" particle</h2>
	 *
	 * <p>{@code ParticleTypes.DUST_PILLAR} is referenced by exactly one thing in the
	 * whole game: {@code ParticleUtils.spawnSmashAttackParticles}, i.e. <b>the mace's
	 * smash attack</b>. So this is not a particle chosen because its name sounded
	 * right -- it is the effect the game already uses for an impact shattering the
	 * ground, reused rather than approximated.
	 *
	 * <p>It is a {@code BlockParticleOption}, and
	 * {@code spawnSmashAttackParticles} reads {@code level.getBlockState(pos)}
	 * <em>itself</em>, so <b>the dust is textured from the real terrain</b>: grass throws
	 * grass, stone throws stone, sand throws sand, deepslate throws deepslate. Same
	 * data-driven reuse as Blight Touched taking a destroyed crop's own break particles
	 * from {@code PARTICLES_DESTROY_BLOCK}, and the reason this reads as <em>this
	 * ground</em> shattering rather than as a generic grey puff.
	 *
	 * <p>Dispatched through {@code LevelEvent.PARTICLES_SMASH_ATTACK} (2013) rather than
	 * {@code sendParticles}, which is the cheaper of the two by a wide margin: the level
	 * event is <b>one small packet</b> (id, {@code BlockPos}, int) broadcast to tracking
	 * clients, and the particles themselves are generated client-side.
	 * {@code sendParticles} would build and send a packet describing the burst instead.
	 *
	 * <h2>The data int is a PARTICLE COUNT, and the first ship of this was invisible</h2>
	 *
	 * <p>See {@link EarthquakeBehavior#PARTICLE_COUNT}. The short version: the intensity
	 * argument is very nearly a literal particle count (vanilla's mace passes <b>750</b>),
	 * this shipped passing 2, and 2 renders three particles. It was reported as "particles
	 * never render" and that report was accurate.
	 *
	 * <h2>BOTH broadcasts here take a nullable EXCLUDED player, and it must stay null</h2>
	 *
	 * <p>This is the trap worth reading before adding any other self-targeted cue.
	 * {@code ServerLevel.levelEvent} and {@code ServerLevel.playSeededSound} both end in
	 * {@code PlayerList.broadcast(Player except, x, y, z, range, dim, packet)}, whose
	 * first statement is {@code if (player == except) continue}. The argument exists
	 * because vanilla's usual caller is an action the acting client already predicted
	 * locally -- block breaking, an attack swing -- so re-sending it would double it up.
	 *
	 * <p><b>Nothing about an Earthquake tremor is client-predicted.</b> It is a
	 * server-side schedule the client has no knowledge of, so passing {@code player}
	 * there would exclude the one player the effect exists for and be structurally
	 * guaranteed to render and sound nothing, for them, forever -- while working
	 * perfectly for every bystander. Both calls below pass {@code null}, which is also
	 * what {@code MaceItem} passes. The three-argument {@code levelEvent} overload is a
	 * default method that supplies {@code null} itself.
	 *
	 * <h2>Cost, stated for completeness</h2>
	 *
	 * <p>Nine bursts per tremor, one tremor per 45-180 seconds per player: <b>at worst
	 * 0.2 packets per second per Earthquake holder</b>, plus one block-state read per
	 * burst. There is no shared or global structure here -- every burst is keyed to one
	 * player's own ground position and dispatched immediately -- so N players holding
	 * the effect cost exactly N times a very small number rather than compounding.
	 * (Singleplayer-scoped project, so N is 1 in practice; noted because "could this
	 * compound" is a fair question of anything that emits per tick.)
	 *
	 * <p>{@code getBlockPosBelowThatAffectsMyMovement()} is vanilla's own "which block
	 * am I standing on" -- the one that already answers it for step sounds and soul-sand
	 * speed -- rather than a hand-rolled {@code blockPosition().below()}, which is wrong
	 * on slabs, stairs and fences.
	 *
	 * <p>An air block below means there is no ground to shatter (flying, swimming,
	 * mid-jump over a gap), and the burst is skipped rather than dispatched to render
	 * nothing: a packet that produces invisible particles is the same kind of silent
	 * no-op as a log line that does not say why.
	 */
	private static void shatterGround(ServerPlayer player, int elapsed) {
		ServerLevel level = player.level();
		BlockPos ground = player.getBlockPosBelowThatAffectsMyMovement();
		if (level.getBlockState(ground).isAir()) {
			return;
		}
		level.levelEvent(LevelEvent.PARTICLES_SMASH_ATTACK, ground,
				EarthquakeBehavior.particleCountFor(elapsed));
		// SoundSource.BLOCKS because this is the ground breaking and there is no
		// attacker -- the mace uses the attacker's getSoundSource(), which has no
		// meaning here. It also puts the cue on the player's Blocks slider, which is
		// where someone turning down "that grinding noise" would go looking.
		level.playSound(null, ground,
				EarthquakeBehavior.isHeavySoundTick(elapsed)
						? SoundEvents.MACE_SMASH_GROUND_HEAVY
						: SoundEvents.MACE_SMASH_GROUND,
				SoundSource.BLOCKS,
				EarthquakeBehavior.soundVolumeFor(elapsed),
				EarthquakeBehavior.SOUND_PITCH);
	}
}
