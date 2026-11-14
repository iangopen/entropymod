package com.entropymod.entropy.behavior;

import com.entropymod.entropy.AttributeEffectBehavior;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.List;

/**
 * Grounded (BAD / MOVEMENT, entropy 61-100) -- You can no longer jump.
 *
 * <p>JUMP_STRENGTH x 0 = 0.0, the attribute's floor: LivingEntity.jumpFromGround returns early
 * when getJumpPower() <= 1e-5, so nothing happens at all, including for Random Jump and Double
 * Jump, which both go through the same method. The world stays traversable because STEP_HEIGHT
 * is untouched at 0.6 -- slabs, stairs, ladders, scaffolding and water all still work -- which
 * is the argument that kept Stone Feet at counterplay = true, at the extreme end of the same
 * scale. Swimming upward is jumpInLiquid, a different branch, and is unaffected.
 */
public final class GroundedBehavior extends AttributeEffectBehavior {

	public static final String ID = "grounded";

	public static final double JUMP_STRENGTH_AMOUNT = -1.0;

	public GroundedBehavior() {
		super(List.of(
				new Change(Attributes.JUMP_STRENGTH, JUMP_STRENGTH_AMOUNT, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)));
	}
}
