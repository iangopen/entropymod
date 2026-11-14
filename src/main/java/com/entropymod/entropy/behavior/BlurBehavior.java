package com.entropymod.entropy.behavior;

import com.entropymod.entropy.AttributeEffectBehavior;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.List;

/**
 * Blur (GOOD / MOVEMENT, entropy 61-100) -- +40% movement speed.
 *
 * <p>Player ground speed is LINEAR in MOVEMENT_SPEED (finding 1z -- the squared rule is a mob
 * rule), so x1.4 on the attribute is x1.4 on the ground: walking 4.32 -> 6.04 b/s, sprinting
 * 5.61 -> 7.86 b/s. Sure Footing's +15% is ADD_MULTIPLIED_BASE and this is TOTAL, so together
 * they are x1.15 x 1.4 = x1.61. MOVEMENT_SPEED is client-syncable.
 */
public final class BlurBehavior extends AttributeEffectBehavior {

	public static final String ID = "blur";

	public static final double MOVEMENT_SPEED_AMOUNT = 0.4;

	public BlurBehavior() {
		super(List.of(
				new Change(Attributes.MOVEMENT_SPEED, MOVEMENT_SPEED_AMOUNT, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)));
	}
}
