package com.entropymod.entropy.behavior;

import com.entropymod.entropy.StatusEffectBehavior;
import net.minecraft.world.effect.MobEffects;

/**
 * Gills (GOOD / UTILITY, entropy 61-100) -- Permanent Water Breathing.
 *
 * <p>The air bar never drains. Says nothing about swim speed or underwater mining, which are
 * separate attributes this effect leaves alone.
 */
public final class GillsBehavior extends StatusEffectBehavior {

	public static final String ID = "gills";

	/** Zero-based, as vanilla stores it: 0 displays as level I. */
	public static final int AMPLIFIER = 0;

	public GillsBehavior() {
		super(MobEffects.WATER_BREATHING, AMPLIFIER);
	}
}
