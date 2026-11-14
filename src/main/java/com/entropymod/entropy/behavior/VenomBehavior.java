package com.entropymod.entropy.behavior;

import com.entropymod.entropy.StatusEffectBehavior;
import net.minecraft.world.effect.MobEffects;

/**
 * Venom (BAD / SURVIVAL, entropy 81-100) -- Permanently poisoned -- it cannot kill you, and it never lets you heal.
 *
 * <p>Poison I deals 1.0 magic damage every 25 ticks while health is above 1.0
 * (PoisonMobEffect: getHealth() > 1.0f, interval 25 >> amplifier) -- four times per 100-tick
 * refresh, with no drift. Natural regeneration returns 1 HP per 80 ticks at best, so the
 * player is held at half a heart for the rest of the run. counterplay = FALSE: milk clears it
 * for at most five seconds. The answers are other effects -- Second Wind's regeneration slows
 * it, nothing stops it.
 */
public final class VenomBehavior extends StatusEffectBehavior {

	public static final String ID = "venom";

	/** Zero-based, as vanilla stores it: 0 displays as level I. */
	public static final int AMPLIFIER = 0;

	public VenomBehavior() {
		super(MobEffects.POISON, AMPLIFIER);
	}
}
