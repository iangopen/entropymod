package com.entropymod.entropy.behavior;

import com.entropymod.entropy.EntropyManager;
import com.entropymod.entropy.HookEffectBehavior;

/**
 * All In (BAD / META, entropy 25-50) -- two banked rerolls now, and the next curse
 * you take is drawn from the scalable subset and lands at <b>double strength</b>.
 *
 * <p>Two pieces of run state, both set once in {@link #onAcquired}:
 * {@code rerollCharges += 2} and a one-shot {@code doubleNextCursePending} flag.
 * Nothing is applied to the player, and nothing here is idempotent-sensitive,
 * because {@code onAcquired} runs exactly once per run from the acquisition path --
 * unlike {@code apply}, which runs again on every respawn and would bank two more
 * charges each time a player died.
 *
 * <h2>Why it defers rather than rolling a curse itself</h2>
 *
 * <p>The alternative shape is "All In immediately rolls a scalable curse and
 * applies it". This one instead marks the <em>next</em> bad-phase pick: that roll
 * draws only from {@code EffectRegistry.SCALABLE_SEVERITY_IDS}, and whichever of
 * the three cards the player takes is the one that gets doubled. Three reasons,
 * in order of weight:
 *
 * <ul>
 *   <li><b>The player still chooses.</b> A pick that silently hands you a curse
 *       you never saw is the one shape this project's whole design rules out
 *       ("picking is mandatory" is about choosing, not about being assigned).</li>
 *   <li>It reuses Debt Collector's one-shot roll-restriction mechanism exactly --
 *       two effects, one {@code PoolOverride} -- rather than adding a second way
 *       for an effect to acquire another effect.</li>
 *   <li>It makes the charges read correctly. Two rerolls banked <em>before</em> the
 *       doubled curse arrives are compensation the player can actually spend on
 *       it; two banked after it has already landed would be compensation for
 *       nothing.</li>
 * </ul>
 *
 * <p><b>All In is itself BAD, so the marked pick is two intervals away</b> -- the
 * phase alternates strictly, so BAD (this) then GOOD then BAD (the doubled one).
 * That gap is what makes the banked charges useful rather than academic.
 *
 * <h2>The pool, and why it is only eight effects</h2>
 *
 * <p>"Doubled severity" has no generic meaning across 35 curses. It has an exact
 * one for an {@code AttributeEffectBehavior} -- multiply every {@code Change}
 * amount by two -- and none at all for a curse whose magnitude is a constant read
 * by a mixin. The verified subset, the clamp check behind each member, and the
 * reason each excluded effect is excluded all live on
 * {@code EffectRegistry.SCALABLE_SEVERITY_IDS}. <b>Read that before adding to it.</b>
 *
 * <p>The doubling itself is one multiplication in
 * {@code AttributeEffectBehavior.apply}, reading
 * {@code EffectContext.severityMultiplier()}. Because that is derived from
 * persisted run state rather than stored on the modifier, re-application after a
 * respawn recomputes the same doubled amount and replaces rather than stacks.
 *
 * <p>counterplay = true. Every member of the subset is a Tier 1 attribute curse
 * whose doubled value was checked against the attribute's real clamp, and none
 * reaches one: max health bottoms out at six hearts against a floor of 1.0,
 * movement speed at 3.02 b/s, attack speed at 2.4. The two harshest --
 * {@code stone_feet} and {@code leaden_legs} at 2x -- leave the player unable to
 * clear a one-block ledge, but {@code STEP_HEIGHT} is untouched at 0.6, so slabs
 * and stairs still auto-step and the world stays traversable by ramps. That is the
 * same argument the {@code entropy-effects} skill records for their 1x values.
 *
 * <p>apply()/remove() are empty by design -- see {@link HookEffectBehavior}.
 */
public final class AllInBehavior extends HookEffectBehavior {

	public static final String ID = "all_in";

	/**
	 * Rerolls banked when this is taken.
	 *
	 * <p>Two, not one, because the marked curse is chosen from three cards: one
	 * reroll replaces the offer, and the second is what makes it a real hedge
	 * rather than a single re-draw. They are ordinary {@code rerollCharges} --
	 * a separate counter from Second Guess's one-shot, spendable whether or not the
	 * player has ever held Second Guess, and killed retroactively by No Do-Overs.
	 */
	public static final int GRANTED_CHARGES = 2;

	/** Severity applied to the marked curse. */
	public static final double SEVERITY_MULTIPLIER = 2.0;

	@Override
	public void onAcquired(EntropyManager manager) {
		manager.armAllIn(GRANTED_CHARGES);
	}
}
