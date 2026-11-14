package com.entropymod.entropy.behavior;

import com.entropymod.entropy.AttributeEffectBehavior;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.List;

/**
 * Titan Hide (GOOD / SURVIVAL, entropy 61-100) -- +10 hearts of maximum health.
 *
 * <p>Five times Thick Hide's +2 hearts. ADD_VALUE, the same pool and operation as Thick Hide,
 * Brittle Bones and Glass Cannon Pact, so the four sum and no modifier can clobber another --
 * each carries its own Identifier. Raising the ceiling does not heal: the player fills the new
 * hearts by regenerating, exactly as with Health Boost.
 */
public final class TitanHideBehavior extends AttributeEffectBehavior {

	public static final String ID = "titan_hide";

	public static final double MAX_HEALTH_AMOUNT = 20.0;

	public TitanHideBehavior() {
		super(List.of(
				new Change(Attributes.MAX_HEALTH, MAX_HEALTH_AMOUNT, AttributeModifier.Operation.ADD_VALUE)));
	}
}
