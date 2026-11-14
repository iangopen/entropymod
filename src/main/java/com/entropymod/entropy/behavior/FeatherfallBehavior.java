package com.entropymod.entropy.behavior;

import com.entropymod.entropy.StatusEffectBehavior;
import net.minecraft.world.effect.MobEffects;

/**
 * Featherfall (GOOD / MOVEMENT, entropy 61-100) -- Permanent Slow Falling -- you drift down and never take fall damage.
 *
 * <p>LivingEntity.aiStep calls resetFallDistance() on every tick the entity has Slow Falling,
 * so fall distance never accumulates and no height deals damage. The same reset means fall
 * distance is never available to anything else that reads it.
 */
public final class FeatherfallBehavior extends StatusEffectBehavior {

	public static final String ID = "featherfall";

	/** Zero-based, as vanilla stores it: 0 displays as level I. */
	public static final int AMPLIFIER = 0;

	public FeatherfallBehavior() {
		super(MobEffects.SLOW_FALLING, AMPLIFIER);
	}
}
