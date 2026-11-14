package com.entropymod.entropy.behavior;

import com.entropymod.entropy.AttributeEffectBehavior;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.List;

/**
 * Stormstride (GOOD / MOVEMENT, entropy 81-100) -- +80% movement speed, and you never take fall damage.
 *
 * <p>Walking 4.32 -> 7.77 b/s and sprinting 5.61 -> 10.10 b/s (linear, finding 1z). The fall
 * half is FALL_DAMAGE_MULTIPLIER x (1 - 1.0) = 0: the attribute floors at 0.0, which is simply
 * full immunity, so unlike GRAVITY there is no dangerous side of that clamp. TOTAL, so Glass
 * Jaw's and Glass Bones' increases are multiplied by zero too.
 */
public final class StormstrideBehavior extends AttributeEffectBehavior {

	public static final String ID = "stormstride";

	public static final double MOVEMENT_SPEED_AMOUNT = 0.8;
	public static final double FALL_DAMAGE_MULTIPLIER_AMOUNT = -1.0;

	public StormstrideBehavior() {
		super(List.of(
				new Change(Attributes.MOVEMENT_SPEED, MOVEMENT_SPEED_AMOUNT, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
				new Change(Attributes.FALL_DAMAGE_MULTIPLIER, FALL_DAMAGE_MULTIPLIER_AMOUNT, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)));
	}
}
