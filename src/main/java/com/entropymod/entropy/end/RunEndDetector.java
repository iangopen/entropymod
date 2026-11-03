package com.entropymod.entropy.end;

import com.entropymod.EntropyMod;
import com.entropymod.entropy.EntropyManager;
import com.entropymod.entropy.RunOutcome;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.dimension.end.EnderDragonFight;

/**
 * Death-driven run endings: the Ender Dragon win, and the run-wide death counter.
 *
 * <p>Both ride one hook, {@code ServerLivingEntityEvents.AFTER_DEATH}, because
 * both are the same question asked of different entities.
 *
 * <h2>Why the Fabric event and not a mixin -- this is the load-bearing part</h2>
 *
 * <p>The obvious hand-rolled version is an inject on {@code LivingEntity.die}.
 * <b>It would have counted zero player deaths while applying perfectly</b>:
 * {@code ServerPlayer.die} is a complete reimplementation and <b>never calls
 * {@code super.die}</b> -- verified, there is not a single {@code .die:(}
 * reference anywhere in {@code ServerPlayer}. That is CLAUDE.md failure mode 1,
 * and it is invisible to the build.
 *
 * <p>Fabric already absorbed it. Its {@code LivingEntityMixin} fires
 * {@code AFTER_DEATH} from {@code LivingEntity.die}, and a <em>second</em>
 * injection in {@code ServerPlayerMixin} fires the same event from
 * {@code ServerPlayer.die}. So the event reaches us exactly once for a player and
 * exactly once for the dragon, by two different routes we do not have to know
 * about. This is the argument for preferring a real event over a mixin restated
 * with a concrete near-miss.
 *
 * <p>It also lands in the right place relative to the mod's own death saves:
 * Phoenix Chambered Heart and vanilla totems both cancel inside
 * {@code checkTotemDeathProtection}, which runs <em>before</em> {@code die}. A
 * rescued player never reaches this and is correctly not counted.
 */
public final class RunEndDetector {

	/**
	 * Every death in the world, filtered to the two that matter.
	 *
	 * <p>Ordered dragon-first only for readability; the two branches are mutually
	 * exclusive by type.
	 */
	public static void onDeath(LivingEntity entity, DamageSource source) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}
		MinecraftServer server = level.getServer();
		if (server == null) {
			return;
		}

		if (entity instanceof EnderDragon dragon) {
			onDragonDeath(server, level, dragon);
			return;
		}
		if (entity instanceof ServerPlayer player) {
			int deaths = EntropyManager.get(server).recordDeath();
			EntropyMod.LOGGER.info("Death #{} this run: {} ({}).", deaths,
					player.getName().getString(), source.getMsgId());
		}
	}

	/**
	 * Path 2 of the three into {@code ENDED}: the dragon is dead, the run is won.
	 *
	 * <h2>"First kill only" is VANILLA'S record, not a parallel one</h2>
	 *
	 * <p>Vanilla already tracks whether this world's dragon has ever been defeated
	 * -- it is what decides whether a dragon egg drops -- and it exposes it:
	 * {@code EnderDragonFight.hasPreviouslyKilledDragon()} is public, and
	 * {@code EnderDragonFight} is a {@code SavedData}, so it survives a reload. No
	 * tracking of our own is needed or wanted.
	 *
	 * <p><b>The timing is the crux, and it goes the right way.</b> Read in bytecode
	 * rather than assumed:
	 *
	 * <ul>
	 *   <li>{@code setDragonKilled} sets {@code hasPreviouslyKilledDragon = true} at
	 *       its offset <b>79</b>.</li>
	 *   <li>Its caller is {@code EnderDragon.tickDeath} at offset <b>409</b> -- the
	 *       <em>end</em> of the roughly 200-tick death animation.</li>
	 *   <li>{@code die()}, and therefore this event, fires ~200 ticks
	 *       <em>earlier</em>.</li>
	 * </ul>
	 *
	 * <p>So at this moment the flag still holds its <b>pre-kill</b> value, and
	 * reading it answers exactly "had the dragon ever been killed before this one".
	 * A re-fight of a resurrected dragon reads {@code true} and is declined.
	 *
	 * <p><b>Known gap, stated rather than hidden:</b> {@code EnderDragon} overrides
	 * {@code kill(ServerLevel)} to call {@code remove} and {@code setDragonKilled}
	 * directly, never routing through {@code hurtServer}/{@code die}. So
	 * {@code /kill} on a dragon does not reach this at all. That is a testing
	 * concern rather than a play one -- a dragon killed by damage always goes
	 * through {@code die} -- and {@code /entropyforcewin} exists for the testing
	 * case.
	 */
	private static void onDragonDeath(MinecraftServer server, ServerLevel level, EnderDragon dragon) {
		EnderDragonFight fight = level.getDragonFight();
		if (fight == null) {
			// A dragon outside a managed fight -- summoned in the overworld, or a
			// datapack dimension with no fight. It is not "the" dragon, so it does not
			// win the run. Logged because a silent decline here would be hard to tell
			// from a broken listener.
			EntropyMod.LOGGER.info("An Ender Dragon died in {} with no dragon fight attached "
					+ "-- not a run win.", level.dimension().identifier());
			return;
		}
		if (fight.hasPreviouslyKilledDragon()) {
			EntropyMod.LOGGER.info("Ender Dragon killed, but this world's dragon had already been "
					+ "defeated before -- a re-fight does not win the run again.");
			return;
		}

		EntropyManager manager = EntropyManager.get(server);
		if (manager.endRun(server, RunOutcome.WIN_DRAGON)) {
			EntropyMod.LOGGER.info("First Ender Dragon kill in this world -- run WON at entropy {}.",
					manager.getEntropy());
		}
	}

	private RunEndDetector() {}
}
