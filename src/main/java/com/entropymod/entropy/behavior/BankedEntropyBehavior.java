package com.entropymod.entropy.behavior;

import com.entropymod.entropy.HookEffectBehavior;

/**
 * Banked Entropy (GOOD / META, entropy 45-60) -- entropy rises half as fast for
 * the rest of the run.
 *
 * <p>Applies nothing to the player. Its entire content is one persisted boolean on
 * the run, {@code halfEntropyPending}, consulted by
 * {@code EntropyManager.onChoiceMade} on <b>every</b> pick once this effect is
 * held: flag clear means the pick adds 0 entropy and sets the flag, flag set means
 * it adds the full 1 and clears it. Net, exactly +1 per 2 picks -- half, expressed
 * entirely in the integer entropy the run already has, with no change to how
 * entropy is represented or displayed.
 *
 * <h2>Why it is run-wide, and not "this pick only"</h2>
 *
 * <p><b>The single-pick version of this design cannot work, and the reason is
 * no-repeat.</b> The original shape was "the first Banked Entropy pick adds 0 and
 * sets the flag; a second Banked Entropy pick adds 1 and clears it", so that two
 * half-points sum to one. But no-repeat is a hard rule: an effect picked once is
 * removed from the pool for the rest of the run, so <em>there is no second Banked
 * Entropy pick</em>. The clearing branch is unreachable, the flag is set forever
 * and never read again, and what actually ships is "this one pick was free" with a
 * dead boolean persisted alongside it.
 *
 * <p>Consulting the flag on every pick is the smallest change that makes the same
 * carry mechanic reachable. The arithmetic is unchanged -- it is still 0.5 + 0.5
 * with integers -- only the question of <em>which</em> picks participate.
 *
 * <p>The pick that acquires this effect is itself free: by the time
 * {@code onChoiceMade} reaches the entropy step the id is already in
 * {@code AcquiredEffects}, so the flag is set on that pick and the first +1 lands
 * on the next one.
 *
 * <h2>The window is the balance lever, and it is the only one</h2>
 *
 * <p>Halving entropy gain doubles the remaining length of the run, so this is
 * worth far more at entropy 10 than at entropy 55. {@code EffectRegistry} has no
 * rarity weight -- the roll is a uniform shuffle of the eligible pool -- so the
 * entropy window is the only control available, and <b>45-60</b> is chosen so the
 * effect can only ever extend the last part of a run rather than double the whole
 * of one.
 *
 * <p>Note the consequence of having no weights: narrowing a window makes an effect
 * rarer <em>across a run</em> but <em>more</em> likely per pick inside its band,
 * because the eligible pool is smaller there. Widening the window downward is the
 * wrong lever if this reads as too strong; lowering the ceiling is not available
 * either, since above 50 the GOOD pool collapses to Glass Cannon Pact alone. The
 * real fix for both is Tier 3 content.
 *
 * <p>apply()/remove() are empty by design -- see {@link HookEffectBehavior}.
 * Membership in the acquired set <em>is</em> the effect, which also means it
 * survives respawn, rejoin and reload with no re-application to get right.
 */
public final class BankedEntropyBehavior extends HookEffectBehavior {

	public static final String ID = "banked_entropy";
}
