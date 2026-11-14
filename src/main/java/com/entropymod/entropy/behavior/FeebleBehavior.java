package com.entropymod.entropy.behavior;

import com.entropymod.entropy.AttributeEffectBehavior;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.List;

/**
 * Feeble (BAD / COMBAT, entropy 61-100) -- -60% attack damage.
 *
 * <p>x0.4 on every hit, three times Weak Grip's bite on an iron sword and felt bare-handed
 * too. TOTAL, so stacked on Human Machinegun it is x0.16 and never the 0.0 an additive pool
 * would clamp to -- ATTACK_DAMAGE floors at 0.0 and anti-stacking is a soft rule.
 */
public final class FeebleBehavior extends AttributeEffectBehavior {

	public static final String ID = "feeble";

	public static final double ATTACK_DAMAGE_AMOUNT = -0.6;

	public FeebleBehavior() {
		super(List.of(
				new Change(Attributes.ATTACK_DAMAGE, ATTACK_DAMAGE_AMOUNT, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)));
	}
}
