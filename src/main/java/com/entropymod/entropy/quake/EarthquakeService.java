package com.entropymod.entropy.quake;

import com.entropymod.EntropyMod;
import com.entropymod.entropy.AcquiredEffects;
import com.entropymod.entropy.EntropyDamageTypes;
import com.entropymod.entropy.EntropyManager;
import com.entropymod.entropy.behavior.EarthquakeBehavior;
import com.entropymod.entropy.spawn.SpawnSchedule;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Earthquake's tick service: a random-interval tremor of Slowness II plus two
 * half-heart hits of true damage.
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

	// ==================================================================

	private static void beginTremor(ServerPlayer player, UUID id) {
		// Slowness for exactly the tremor, granted as a real MobEffectInstance so it
		// expires on vanilla's own schedule with nothing mod-side to clean up -- the
		// same reason Phoenix Chambered Heart grants real effects instead of imitating
		// them. showParticles is left on: the player should be able to see why they
		// are slow.
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,
				EarthquakeBehavior.TREMOR_TICKS, EarthquakeBehavior.SLOWNESS_AMPLIFIER));
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
}
