package com.entropymod.entropy.behavior;

import com.entropymod.entropy.AttributeEffectBehavior;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.List;

/**
 * Whirlwind (GOOD / COMBAT, entropy 61-100) -- +100% attack speed.
 *
 * <p>Halves every weapon's cooldown: a sword 1.6 -> 3.2 swings a second, an axe 1.0 -> 2.0.
 * TOTAL, so it is a factor in the same product as Human Machinegun's x1.9 and Leaden Swing's
 * x0.5 and cannot be summed to zero with them.
 */
public final class WhirlwindBehavior extends AttributeEffectBehavior {

	public static final String ID = "whirlwind";

	public static final double ATTACK_SPEED_AMOUNT = 1.0;

	public WhirlwindBehavior() {
		super(List.of(
				new Change(Attributes.ATTACK_SPEED, ATTACK_SPEED_AMOUNT, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)));
	}
}
