package com.entropymod.entropy.behavior;

import com.entropymod.entropy.AttributeEffectBehavior;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.List;

/**
 * Crushing Gravity (BAD / MOVEMENT, entropy 61-100) -- Gravity is doubled -- you cannot jump a full block, and you fall twice as fast.
 *
 * <p>0.08 -> 0.16. The per-tick integration the harness validates against vanilla's
 * 1.2522-block jump gives an apex of 0.77 blocks here, so a full block is out of reach and
 * STEP_HEIGHT (untouched) is the way up, as with Leaden Legs. GRAVITY's clamp is [-1, 1] and
 * this multiplies upward, so the dangerous floor is not in play; stacked on a doubled Leaden
 * Legs it is 0.288, still far under 1.0. ADD_MULTIPLIED_TOTAL per the rule that every gravity
 * effect after Moon Walker uses it.
 */
public final class CrushingGravityBehavior extends AttributeEffectBehavior {

	public static final String ID = "crushing_gravity";

	public static final double GRAVITY_AMOUNT = 1.0;

	public CrushingGravityBehavior() {
		super(List.of(
				new Change(Attributes.GRAVITY, GRAVITY_AMOUNT, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)));
	}
}
