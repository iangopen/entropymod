package com.entropymod.entropy.behavior;

import com.entropymod.entropy.AttributeEffectBehavior;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.List;

/**
 * Mountain Goat (GOOD / MOVEMENT, entropy 61-100) -- Walk straight up full blocks, and fall 10 blocks unharmed.
 *
 * <p>STEP_HEIGHT 0.6 -> 1.25: LivingEntity.maxUpStep() is literally the attribute, so a full
 * block is walked up like a slab. 1.25 rather than 1.0 so a block with a carpet or a snow
 * layer on it still steps. SAFE_FALL_DISTANCE 3 -> 10 moves the fall-damage THRESHOLD
 * (calculateFallPower subtracts it), which is a different mechanic from the multiplier
 * Featherlight scales. This is also the answer to every jump curse in the registry -- Stone
 * Feet, Leaden Legs, Crushing Gravity and Grounded all leave STEP_HEIGHT alone.
 */
public final class MountainGoatBehavior extends AttributeEffectBehavior {

	public static final String ID = "mountain_goat";

	public static final double STEP_HEIGHT_AMOUNT = 0.65;
	public static final double SAFE_FALL_DISTANCE_AMOUNT = 7.0;

	public MountainGoatBehavior() {
		super(List.of(
				new Change(Attributes.STEP_HEIGHT, STEP_HEIGHT_AMOUNT, AttributeModifier.Operation.ADD_VALUE),
				new Change(Attributes.SAFE_FALL_DISTANCE, SAFE_FALL_DISTANCE_AMOUNT, AttributeModifier.Operation.ADD_VALUE)));
	}
}
