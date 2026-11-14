package com.entropymod.entropy.behavior;

import com.entropymod.entropy.AttributeEffectBehavior;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.List;

/**
 * Short Arms (BAD / UTILITY, entropy 61-100) -- Your reach is cut to 2.5 blocks, and 1.5 for anything alive.
 *
 * <p>Block reach 4.5 -> 2.5, entity reach 3.0 -> 1.5. Both are measured from the eye, 1.62
 * above the feet, so 2.5 still reaches the block the player stands on and the ones beside it
 * -- the world stays buildable. 1.5 for entities means trading blows at the range mobs hit
 * from, which is the point. Both attributes floor at 0.0.
 */
public final class ShortArmsBehavior extends AttributeEffectBehavior {

	public static final String ID = "short_arms";

	public static final double BLOCK_INTERACTION_RANGE_AMOUNT = -2.0;
	public static final double ENTITY_INTERACTION_RANGE_AMOUNT = -1.5;

	public ShortArmsBehavior() {
		super(List.of(
				new Change(Attributes.BLOCK_INTERACTION_RANGE, BLOCK_INTERACTION_RANGE_AMOUNT, AttributeModifier.Operation.ADD_VALUE),
				new Change(Attributes.ENTITY_INTERACTION_RANGE, ENTITY_INTERACTION_RANGE_AMOUNT, AttributeModifier.Operation.ADD_VALUE)));
	}
}
