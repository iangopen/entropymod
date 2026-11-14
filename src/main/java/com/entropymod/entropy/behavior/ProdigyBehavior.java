package com.entropymod.entropy.behavior;

import com.entropymod.entropy.HookEffectBehavior;

/**
 * Prodigy (GOOD / META, entropy 61-100) -- Triple experience from every source.
 *
 * <p>Fast Learner is x1.5; this is x3 through the same Player.giveExperiencePoints hook and
 * the same rounding scaleAmount. The two multiply to x4.5 when held together.
 */
public final class ProdigyBehavior extends HookEffectBehavior {

	public static final String ID = "prodigy";

	/** Read by EffectHooks; apply()/remove() are empty by design -- see HookEffectBehavior. */
	public static final float MULTIPLIER = 3.0f;
}
