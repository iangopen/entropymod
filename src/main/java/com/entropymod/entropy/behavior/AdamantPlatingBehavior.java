package com.entropymod.entropy.behavior;

import com.entropymod.entropy.AttributeEffectBehavior;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.List;

/**
 * Adamant Plating (GOOD / GEAR, entropy 61-100) -- +10 armor and +6 armor toughness, with nothing worn.
 *
 * <p>Half a full bar of armor that no curse can take off and nothing can break. ARMOR is
 * capped at 30.0 and ARMOR_TOUGHNESS at 20.0 by their RangedAttributes, so over a full
 * netherite set (20 armor, 12 toughness) this saturates the armor bar and still adds the
 * toughness -- it is never wasted, only capped.
 */
public final class AdamantPlatingBehavior extends AttributeEffectBehavior {

	public static final String ID = "adamant_plating";

	public static final double ARMOR_AMOUNT = 10.0;
	public static final double ARMOR_TOUGHNESS_AMOUNT = 6.0;

	public AdamantPlatingBehavior() {
		super(List.of(
				new Change(Attributes.ARMOR, ARMOR_AMOUNT, AttributeModifier.Operation.ADD_VALUE),
				new Change(Attributes.ARMOR_TOUGHNESS, ARMOR_TOUGHNESS_AMOUNT, AttributeModifier.Operation.ADD_VALUE)));
	}
}
