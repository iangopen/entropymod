package com.entropymod.entropy.behavior;

import com.entropymod.entropy.HookEffectBehavior;

/**
 * Juggernaut (GOOD / GEAR, entropy 81-100) -- All damage taken reduced by 60%.
 *
 * <p>Iron Skin is x0.8; this is x0.4 on the same LivingEntity.hurtServer hook, before armour
 * and enchantments. The terms multiply: with Iron Skin x0.32, against Paper Skin x0.8, and
 * never zero.
 */
public final class JuggernautBehavior extends HookEffectBehavior {

	public static final String ID = "juggernaut";

	/** Read by EffectHooks; apply()/remove() are empty by design -- see HookEffectBehavior. */
	public static final float MULTIPLIER = 0.4f;
}
