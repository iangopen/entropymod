package com.entropymod.entropy.behavior;

import com.entropymod.entropy.AttributeEffectBehavior;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.List;

/**
 * Glass Bones (BAD / MOVEMENT, entropy 61-100) -- Fall damage is tripled, and a two-block drop already hurts.
 *
 * <p>Both fall mechanics at once, in the opposite direction to Embrace the Moon: the threshold
 * drops 3 -> 1 block and what is left is multiplied by 3. A 2-block drop costs 3 HP, 4 blocks
 * 9 HP, and 8 blocks 21 HP -- lethal from full, where vanilla charges 5.
 */
public final class GlassBonesBehavior extends AttributeEffectBehavior {

	public static final String ID = "glass_bones";

	public static final double FALL_DAMAGE_MULTIPLIER_AMOUNT = 2.0;
	public static final double SAFE_FALL_DISTANCE_AMOUNT = -2.0;

	public GlassBonesBehavior() {
		super(List.of(
				new Change(Attributes.FALL_DAMAGE_MULTIPLIER, FALL_DAMAGE_MULTIPLIER_AMOUNT, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
				new Change(Attributes.SAFE_FALL_DISTANCE, SAFE_FALL_DISTANCE_AMOUNT, AttributeModifier.Operation.ADD_VALUE)));
	}
}
