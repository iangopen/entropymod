package com.entropymod.entropy.behavior;

import com.entropymod.entropy.HookEffectBehavior;

/**
 * Paper Skin (BAD / GEAR, entropy 61-100) -- All damage taken is doubled.
 *
 * <p>Fragile is x1.25; this is x2 on the same hook, applied before armour, so armour and
 * Resistance still take their share of the doubled number.
 */
public final class PaperSkinBehavior extends HookEffectBehavior {

	public static final String ID = "paper_skin";

	/** Read by EffectHooks; apply()/remove() are empty by design -- see HookEffectBehavior. */
	public static final float MULTIPLIER = 2.0f;
}
