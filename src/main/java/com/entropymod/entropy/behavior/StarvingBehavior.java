package com.entropymod.entropy.behavior;

import com.entropymod.entropy.HookEffectBehavior;

/**
 * Starving (BAD / SURVIVAL, entropy 61-100) -- Everything you do costs three times the hunger.
 *
 * <p>Growling Stomach is x1.25. At x3 a full bar of sprinting lasts about a third as long,
 * which makes food a standing concern instead of an occasional one. Overridden completely by
 * Bottomless Stomach, whose factor is zero.
 */
public final class StarvingBehavior extends HookEffectBehavior {

	public static final String ID = "starving";

	/** Read by EffectHooks; apply()/remove() are empty by design -- see HookEffectBehavior. */
	public static final float MULTIPLIER = 3.0f;
}
