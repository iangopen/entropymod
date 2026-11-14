package com.entropymod.entropy.behavior;

import com.entropymod.entropy.StatusEffectBehavior;
import net.minecraft.world.effect.MobEffects;

/**
 * Second Wind (GOOD / SURVIVAL, entropy 61-100) -- Permanent Regeneration -- a heart every five seconds.
 *
 * <p>Regeneration I heals 1.0 HP every 50 ticks: twice per 100-tick refresh, with no drift,
 * because 600 and 100 are both multiples of 50. Unlike natural regeneration it costs no food
 * and does not need a full hunger bar.
 */
public final class SecondWindBehavior extends StatusEffectBehavior {

	public static final String ID = "second_wind";

	/** Zero-based, as vanilla stores it: 0 displays as level I. */
	public static final int AMPLIFIER = 0;

	public SecondWindBehavior() {
		super(MobEffects.REGENERATION, AMPLIFIER);
	}
}
