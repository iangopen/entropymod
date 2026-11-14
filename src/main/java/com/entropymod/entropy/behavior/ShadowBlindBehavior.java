package com.entropymod.entropy.behavior;

import com.entropymod.entropy.StatusEffectBehavior;
import net.minecraft.world.effect.MobEffects;

/**
 * Shadow Blind (BAD / DEBUFF, entropy 61-100) -- Permanent Darkness -- the world pulses black around you.
 *
 * <p>The Warden's own effect: a pulsing loss of light rather than Blindness's fog wall, so the
 * player can still see nearby and can still sprint. Chosen over Blindness for exactly that --
 * permanent Blindness would also forbid sprinting.
 */
public final class ShadowBlindBehavior extends StatusEffectBehavior {

	public static final String ID = "shadow_blind";

	/** Zero-based, as vanilla stores it: 0 displays as level I. */
	public static final int AMPLIFIER = 0;

	public ShadowBlindBehavior() {
		super(MobEffects.DARKNESS, AMPLIFIER);
	}
}
