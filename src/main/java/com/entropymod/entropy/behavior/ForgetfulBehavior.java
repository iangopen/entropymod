package com.entropymod.entropy.behavior;

import com.entropymod.entropy.HookEffectBehavior;

/**
 * Forgetful (BAD / META, entropy 61-100) -- You no longer gain experience.
 *
 * <p>Slow Learner halves experience; this multiplies it by zero through the same hook. Levels
 * already held are kept and can still be spent -- nothing is taken away, the supply is cut
 * off. Zero times Prodigy is still zero.
 */
public final class ForgetfulBehavior extends HookEffectBehavior {

	public static final String ID = "forgetful";

	/** Read by EffectHooks; apply()/remove() are empty by design -- see HookEffectBehavior. */
	public static final float MULTIPLIER = 0.0f;
}
