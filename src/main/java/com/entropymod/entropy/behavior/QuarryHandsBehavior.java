package com.entropymod.entropy.behavior;

import com.entropymod.entropy.AttributeEffectBehavior;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.List;

/**
 * Quarry Hands (GOOD / TOOL, entropy 61-100) -- +100% mining speed.
 *
 * <p>Doubles block-break speed through the same client-syncable attribute Efficient Miner
 * uses, so the cracking animation matches the server. TOTAL, so it multiplies with Efficient
 * Miner (+15% BASE) and Dull Blade (-20% BASE) instead of joining their sum.
 */
public final class QuarryHandsBehavior extends AttributeEffectBehavior {

	public static final String ID = "quarry_hands";

	public static final double BLOCK_BREAK_SPEED_AMOUNT = 1.0;

	public QuarryHandsBehavior() {
		super(List.of(
				new Change(Attributes.BLOCK_BREAK_SPEED, BLOCK_BREAK_SPEED_AMOUNT, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)));
	}
}
