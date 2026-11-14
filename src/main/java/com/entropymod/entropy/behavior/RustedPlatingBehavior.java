package com.entropymod.entropy.behavior;

import com.entropymod.entropy.AttributeEffectBehavior;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.List;

/**
 * Rusted Plating (BAD / GEAR, entropy 61-100) -- Your armor counts for 10 points less, and loses 8 toughness.
 *
 * <p>A full iron set (15) is worth 5; full diamond (20, toughness 8) is worth 10 with no
 * toughness. Both attributes floor at 0.0, so an unarmoured player loses nothing -- the curse
 * taxes the gear, and wearing better gear is the answer.
 */
public final class RustedPlatingBehavior extends AttributeEffectBehavior {

	public static final String ID = "rusted_plating";

	public static final double ARMOR_AMOUNT = -10.0;
	public static final double ARMOR_TOUGHNESS_AMOUNT = -8.0;

	public RustedPlatingBehavior() {
		super(List.of(
				new Change(Attributes.ARMOR, ARMOR_AMOUNT, AttributeModifier.Operation.ADD_VALUE),
				new Change(Attributes.ARMOR_TOUGHNESS, ARMOR_TOUGHNESS_AMOUNT, AttributeModifier.Operation.ADD_VALUE)));
	}
}
