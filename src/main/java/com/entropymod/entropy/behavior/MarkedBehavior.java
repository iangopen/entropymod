package com.entropymod.entropy.behavior;

import com.entropymod.entropy.StatusEffectBehavior;
import net.minecraft.world.effect.MobEffects;

/**
 * Marked (BAD / DEBUFF, entropy 61-100) -- Permanent Bad Omen -- every village you enter is raided.
 *
 * <p>Vanilla converts Bad Omen into a Raid Omen, and then a raid, when the bearer steps into a
 * village; the refresh hands Bad Omen back, so it happens at the next village too. Nothing
 * about the raid is this mod's. Counterplay is total: stay out of villages, or win the raid.
 */
public final class MarkedBehavior extends StatusEffectBehavior {

	public static final String ID = "marked";

	/** Zero-based, as vanilla stores it: 0 displays as level I. */
	public static final int AMPLIFIER = 0;

	public MarkedBehavior() {
		super(MobEffects.BAD_OMEN, AMPLIFIER);
	}
}
