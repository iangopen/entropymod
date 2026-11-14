package com.entropymod.entropy.behavior;

import com.entropymod.entropy.StatusEffectBehavior;
import net.minecraft.world.effect.MobEffects;

/**
 * Stoneskin (GOOD / GEAR, entropy 61-100) -- Permanent Resistance II -- 40% less damage from almost everything.
 *
 * <p>Vanilla's Resistance removes 20% per level in getDamageAfterMagicAbsorb, AFTER armour, so
 * it composes with armour and with the mod's own damage hook rather than replacing either. It
 * does not apply to #bypasses_resistance (the void and /kill), and it DOES apply to
 * Earthquake's tremor damage, which bypasses armour and enchantments but deliberately not
 * Resistance (finding 1v).
 */
public final class StoneskinBehavior extends StatusEffectBehavior {

	public static final String ID = "stoneskin";

	/** Zero-based, as vanilla stores it: 1 displays as level II. */
	public static final int AMPLIFIER = 1;

	public StoneskinBehavior() {
		super(MobEffects.RESISTANCE, AMPLIFIER);
	}
}
