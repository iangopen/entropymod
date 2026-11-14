package com.entropymod.entropy.behavior;

import com.entropymod.entropy.StatusEffectBehavior;
import net.minecraft.world.effect.MobEffects;

/**
 * Night Eyes (GOOD / UTILITY, entropy 61-100) -- Permanent Night Vision.
 *
 * <p>The reason the refresh floor is 500 ticks: the client blinks Night Vision once fewer than
 * 200 remain. Cancels Shadow Blind's gloom only partly -- Darkness is a separate fog effect
 * and both render.
 */
public final class NightEyesBehavior extends StatusEffectBehavior {

	public static final String ID = "night_eyes";

	/** Zero-based, as vanilla stores it: 0 displays as level I. */
	public static final int AMPLIFIER = 0;

	public NightEyesBehavior() {
		super(MobEffects.NIGHT_VISION, AMPLIFIER);
	}
}
