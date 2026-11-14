package com.entropymod.entropy.behavior;

import com.entropymod.entropy.AttributeEffectBehavior;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.List;

/**
 * Long Arm (GOOD / UTILITY, entropy 61-100) -- +3 blocks of reach for mining, placing and attacking.
 *
 * <p>Block reach 4.5 -> 7.5 and entity reach 3.0 -> 6.0. Both attributes are bare
 * getAttributeValue reads in Player.blockInteractionRange / entityInteractionRange and both
 * are client-syncable, so the client's own target raycast extends with no correction gap. Far
 * below the 64.0 ceiling even stacked on Lethal Hook's +1.5 and Giant Size's +6.5.
 */
public final class LongArmBehavior extends AttributeEffectBehavior {

	public static final String ID = "long_arm";

	public static final double BLOCK_INTERACTION_RANGE_AMOUNT = 3.0;
	public static final double ENTITY_INTERACTION_RANGE_AMOUNT = 3.0;

	public LongArmBehavior() {
		super(List.of(
				new Change(Attributes.BLOCK_INTERACTION_RANGE, BLOCK_INTERACTION_RANGE_AMOUNT, AttributeModifier.Operation.ADD_VALUE),
				new Change(Attributes.ENTITY_INTERACTION_RANGE, ENTITY_INTERACTION_RANGE_AMOUNT, AttributeModifier.Operation.ADD_VALUE)));
	}
}
