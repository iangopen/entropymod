package com.entropymod.entropy.behavior;

import com.entropymod.entropy.StatusEffectBehavior;
import net.minecraft.world.effect.MobEffects;

/**
 * Infested (BAD / DEBUFF, entropy 61-100) -- Permanently Infested -- being hurt can burst silverfish out of you.
 *
 * <p>Vanilla's Infested effect: a chance, each time the bearer takes damage, to spawn
 * silverfish at their position. Every fight gains a second front.
 */
public final class InfestedBehavior extends StatusEffectBehavior {

	public static final String ID = "infested";

	/** Zero-based, as vanilla stores it: 0 displays as level I. */
	public static final int AMPLIFIER = 0;

	public InfestedBehavior() {
		super(MobEffects.INFESTED, AMPLIFIER);
	}
}
