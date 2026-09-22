package com.entropymod.entropy.behavior;

import com.entropymod.entropy.EntropyManager;
import com.entropymod.entropy.HookEffectBehavior;

/**
 * No Do-Overs (BAD / META, entropy 25-55) -- no reroll of any kind ever works
 * again for the rest of the run.
 *
 * <p>One persisted boolean, {@code rerollsForbidden}, set in {@link #onAcquired}.
 * It is never cleared: this is the only run flag in the project that is
 * deliberately one-way.
 *
 * <h2>It is retroactive, and that is the requirement rather than a side effect</h2>
 *
 * <p>The flag is checked <b>first</b> in both {@code EntropyManager.rerollState}
 * (what the button draws) and {@code EntropyManager.requestReroll} (whether the
 * request is honoured), ahead of every other term. So it kills, in one place:
 *
 * <ul>
 *   <li>Second Guess's one-shot, whether or not it has been spent;</li>
 *   <li>banked {@code rerollCharges}, <b>including charges banked by All In
 *       before this was picked</b> -- they are not refunded, not spendable, and
 *       not decremented; they simply stop being reachable;</li>
 *   <li>any future source of either.</li>
 * </ul>
 *
 * <p>Checking the flag rather than zeroing the counter is what makes that
 * retroactive claim structural. Draining {@code rerollCharges} to 0 on acquisition
 * would look equivalent and would leave the run unable to distinguish "never had
 * charges" from "had them taken", which is exactly the distinction
 * {@code RerollState} exists to preserve on the Second Guess side.
 *
 * <p>The client is told via {@code RerollState.FORBIDDEN}, so the button is drawn
 * disabled with its own wording rather than vanishing -- a player who owned Second
 * Guess and watched the button silently disappear would read it as the effect
 * having stopped existing. The state is <em>not</em> authorisation:
 * {@code requestReroll} re-checks server-side, so a client sent anything else and
 * asking anyway is still refused.
 *
 * <p><b>counterplay = true</b>, and it means it. This effect takes away a safety
 * net; it does nothing to the player. A run that never held Second Guess and never
 * met All In loses literally nothing. That is a strictly weaker claim than most
 * curses make, and it is why this sits at 25-55 rather than in the extreme band.
 *
 * <p>apply()/remove() are empty by design -- see {@link HookEffectBehavior}.
 */
public final class NoDoOversBehavior extends HookEffectBehavior {

	public static final String ID = "no_do_overs";

	@Override
	public void onAcquired(EntropyManager manager) {
		manager.forbidRerolls();
	}
}
