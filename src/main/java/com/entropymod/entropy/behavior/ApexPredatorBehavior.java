package com.entropymod.entropy.behavior;

import com.entropymod.entropy.AttributeEffectBehavior;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.List;

/**
 * Apex Predator (GOOD / COMBAT, entropy 81-100) -- +200% attack damage and +50% attack speed.
 *
 * <p>x3 damage and x1.5 swing rate: x4.5 DPS for every weapon, uniformly, because both are
 * single factors in a product. With Executioner the damage is x3 x 2 = x6.
 */
public final class ApexPredatorBehavior extends AttributeEffectBehavior {

	public static final String ID = "apex_predator";

	public static final double ATTACK_DAMAGE_AMOUNT = 2.0;
	public static final double ATTACK_SPEED_AMOUNT = 0.5;

	public ApexPredatorBehavior() {
		super(List.of(
				new Change(Attributes.ATTACK_DAMAGE, ATTACK_DAMAGE_AMOUNT, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
				new Change(Attributes.ATTACK_SPEED, ATTACK_SPEED_AMOUNT, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)));
	}
}
