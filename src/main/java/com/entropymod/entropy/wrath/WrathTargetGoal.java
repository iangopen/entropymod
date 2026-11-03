package com.entropymod.entropy.wrath;

import com.entropymod.entropy.EffectHooks;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;

/**
 * The target goal Beast Wrath installs on every eligible animal -- a stock
 * {@link NearestAttackableTargetGoal} that <b>re-checks its own selector on
 * continuation</b>.
 *
 * <h2>Why the subclass exists: vanilla does not re-test the Selector</h2>
 *
 * <p>{@code TargetGoal.canContinueToUse} was read in bytecode and it checks
 * exactly five things -- the target still exists, {@code Mob.canAttack(target)},
 * team membership, follow distance, and line of sight. <b>It never consults the
 * {@code TargetingConditions.Selector} that chose the target in the first
 * place.</b> Selection is a one-time gate; retention is a completely different
 * set of rules.
 *
 * <p>For the land-only version of the effect that was harmless: the predicate
 * ("is this player holding Beast Wrath") could only change when the run itself
 * changed. The tameable expansion makes it reachable and sharp:
 *
 * <blockquote>a wild wolf acquires the player as a target, the player then tames
 * it -- with a bone, or because Loyal Pack granted them a pack -- and vanilla's
 * retention rules know nothing about ownership, so <b>the freshly-tamed wolf
 * keeps attacking its new owner</b> until it happens to lose line of sight.</blockquote>
 *
 * <p>Overriding {@code canContinueToUse} to re-ask the same question closes that
 * by construction rather than by timing. The mob drops the target on the very
 * next tick after ownership changes, which is the behaviour "your own pack never
 * turns on you" actually names.
 *
 * <p><b>This is deliberately the same predicate, not a second copy of it.</b>
 * {@link EffectHooks#attractsAnimalAggression} is called with the identical
 * arguments the selector passes, so selection and retention cannot drift into
 * disagreeing -- the failure this project has recorded three times (1k, 1q, 1s)
 * when two statements of one rule were maintained separately.
 *
 * <p>{@code super.canContinueToUse()} is evaluated first and short-circuits, so
 * every vanilla retention rule still applies unchanged and the extra predicate
 * only ever <i>removes</i> a target, never keeps one alive.
 */
public final class WrathTargetGoal extends NearestAttackableTargetGoal<Player> {

	/**
	 * @param mob the animal being armed; captured so the ownership test has both
	 *            halves of the (attacker, candidate) pair
	 */
	public WrathTargetGoal(Mob mob, int randomInterval) {
		super(mob,
				Player.class,
				randomInterval,
				true,   // mustSee -- line of sight, so walls and darkness still hide you
				false,  // mustReach -- do not require a completed path before targeting
				(candidate, serverLevel) -> EffectHooks.attractsAnimalAggression(mob, candidate));
	}

	@Override
	public boolean canContinueToUse() {
		return super.canContinueToUse()
				&& EffectHooks.attractsAnimalAggression(this.mob, this.mob.getTarget());
	}
}
