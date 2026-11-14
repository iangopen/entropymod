package com.entropymod.entropy.behavior;

import com.entropymod.entropy.AttributeEffectBehavior;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.List;

/**
 * Stone Hands (BAD / TOOL, entropy 61-100) -- -60% mining speed.
 *
 * <p>Every block takes 2.5x as long. Three times Dull Blade, and a product with it (x0.8 x 0.4
 * = x0.32) rather than a sum. Client-syncable, so the crack animation is honest about how long
 * it will take.
 */
public final class StoneHandsBehavior extends AttributeEffectBehavior {

	public static final String ID = "stone_hands";

	public static final double BLOCK_BREAK_SPEED_AMOUNT = -0.6;

	public StoneHandsBehavior() {
		super(List.of(
				new Change(Attributes.BLOCK_BREAK_SPEED, BLOCK_BREAK_SPEED_AMOUNT, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)));
	}
}
