package com.entropymod.entropy.behavior;

import com.entropymod.entropy.EntropyManager;
import com.entropymod.entropy.HookEffectBehavior;

/**
 * Debt Collector (BAD / META, entropy 40-60) -- the next bad-phase pick is drawn
 * only from the most extreme tier the build defines, whatever the run's entropy
 * says you should be seeing.
 *
 * <p>One persisted one-shot flag, set in {@link #onAcquired} and cleared by
 * {@code EntropyManager.onChoiceMade} when the marked pick is answered. Nothing is
 * applied to the player.
 *
 * <h2>"The most extreme tier" is derived, not listed</h2>
 *
 * <p>{@code EffectRegistry.extremeTier(phase)} takes the highest {@code minEntropy}
 * any effect of that phase has and returns everything sitting at it. For BAD today
 * that is entropy 40: <b>Unstable and Earthquake</b>, plus Debt Collector itself,
 * which no-repeat removes -- by the time the flag is read it has necessarily been
 * acquired. When Tier 3 lands, the target moves with it and nothing here goes
 * stale.
 *
 * <p>The override replaces <b>only</b> the entropy-window eligibility test. No-repeat
 * and anti-stacking still run on the result in the usual priority order, so a forced
 * roll cannot re-offer something already taken.
 *
 * <p><b>The flag is cleared when the marked pick is ANSWERED, not when it is
 * rolled.</b> That is what makes it survive a reroll: a player who spends Second
 * Guess or a banked charge gets three more extreme-tier cards rather than escaping
 * back into the ordinary pool, which is the whole point of the effect. It also
 * means an interval that opens no pick at all does not consume it.
 *
 * <h2>Two things the entropy range is not a choice about</h2>
 *
 * <p><b>counterplay = false.</b> The extreme BAD tier is Unstable and Earthquake,
 * and both are themselves {@code counterplay = false}. An effect whose content is
 * "you will be handed one of those" cannot honestly be labelled survivable -- it is
 * labelling the delivery rather than the thing delivered, which is the failure this
 * project has already hit three times (a green assertion describing a stale
 * specification).
 *
 * <p><b>40-60.</b> CLAUDE.md Part 2 forbids a {@code counterplay = false} effect
 * below entropy 40, and this one delivers one below 40 if permitted to appear
 * there. 40-60 is Unstable's and Earthquake's own window, which is the correct
 * answer rather than a coincidence: an effect that hands you a member of a tier
 * should not be available earlier than the tier is.
 *
 * <h2>Precedence against All In, and why this one yields</h2>
 *
 * <p>If both flags are pending on the same bad pick, <b>All In's scalable pool
 * wins and Debt Collector's flag is left set</b>. That is not arbitrary: the
 * extreme tier contains no scalable effect, so letting this one win would silently
 * void All In's doubling with nothing to show for it, whereas a Debt Collector
 * flag that waits one more bad pick loses nothing at all. The rule is "consume the
 * flag that can still be honoured".
 *
 * <p>apply()/remove() are empty by design -- see {@link HookEffectBehavior}.
 */
public final class DebtCollectorBehavior extends HookEffectBehavior {

	public static final String ID = "debt_collector";

	@Override
	public void onAcquired(EntropyManager manager) {
		manager.armDebtCollector();
	}
}
