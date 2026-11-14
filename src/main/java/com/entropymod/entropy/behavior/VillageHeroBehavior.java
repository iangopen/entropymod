package com.entropymod.entropy.behavior;

import com.entropymod.entropy.StatusEffectBehavior;
import net.minecraft.world.effect.MobEffects;

/**
 * Village Hero (GOOD / UTILITY, entropy 61-100) -- Permanent Hero of the Village -- villagers discount and gift you.
 *
 * <p>Feeds the same Villager.updateSpecialPrices pass Bad Reputation's surcharge is added at
 * the tail of, so a run holding both gets vanilla's discount and then the curse's surcharge on
 * the discounted price.
 */
public final class VillageHeroBehavior extends StatusEffectBehavior {

	public static final String ID = "village_hero";

	/** Zero-based, as vanilla stores it: 0 displays as level I. */
	public static final int AMPLIFIER = 0;

	public VillageHeroBehavior() {
		super(MobEffects.HERO_OF_THE_VILLAGE, AMPLIFIER);
	}
}
