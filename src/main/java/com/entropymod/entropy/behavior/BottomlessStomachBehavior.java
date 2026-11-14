package com.entropymod.entropy.behavior;

import com.entropymod.entropy.HookEffectBehavior;

/**
 * Bottomless Stomach (GOOD / SURVIVAL, entropy 61-100) -- Sprinting, jumping, mining and fighting no longer cost hunger.
 *
 * <p>Iron Stomach's hook taken to its end: Player.causeFoodExhaustion is multiplied by zero.
 * Stated precisely because it is narrower than "never hungry": natural regeneration spends
 * food through FoodData.addExhaustion directly, which that mixin does not see (the reason
 * PlayerExhaustionMixin targets Player in the first place), so healing still costs food and
 * the Hunger effect still drains. Multiplicative with the other two exhaustion effects, and
 * zero times anything is zero -- this overrides Starving and Growling Stomach when held
 * together.
 */
public final class BottomlessStomachBehavior extends HookEffectBehavior {

	public static final String ID = "bottomless_stomach";

	/** Read by EffectHooks; apply()/remove() are empty by design -- see HookEffectBehavior. */
	public static final float MULTIPLIER = 0.0f;
}
