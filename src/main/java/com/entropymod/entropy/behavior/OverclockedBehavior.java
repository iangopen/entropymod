package com.entropymod.entropy.behavior;

import com.entropymod.entropy.StatusEffectBehavior;
import net.minecraft.world.effect.MobEffects;

/**
 * Overclocked (GOOD / TOOL, entropy 61-100) -- Permanent Haste II.
 *
 * <p>Haste II: +40% mining speed and +20% attack speed, both vanilla's own numbers. Stacks
 * with Quarry Hands because that one is the BLOCK_BREAK_SPEED attribute and this is the potion
 * term in Player.getDestroySpeed -- two different factors.
 */
public final class OverclockedBehavior extends StatusEffectBehavior {

	public static final String ID = "overclocked";

	/** Zero-based, as vanilla stores it: 1 displays as level II. */
	public static final int AMPLIFIER = 1;

	public OverclockedBehavior() {
		super(MobEffects.HASTE, AMPLIFIER);
	}
}
