package com.entropymod.entropy.behavior;

import com.entropymod.entropy.AttributeEffectBehavior;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.List;

/**
 * Executioner (GOOD / COMBAT, entropy 61-100) -- +100% attack damage.
 *
 * <p>Doubles every hit. ADD_MULTIPLIED_TOTAL so it composes as a product with the other damage
 * effects (Glass Cannon Pact x1.5, Human Machinegun x0.4) rather than summing against a shared
 * base -- see finding 1bb for why the stacking behaviour is the only real difference between
 * the two multiplied operations. ATTACK_DAMAGE is not client-syncable, so the tooltip will not
 * show it; the same known gap Steady Hands has.
 */
public final class ExecutionerBehavior extends AttributeEffectBehavior {

	public static final String ID = "executioner";

	public static final double ATTACK_DAMAGE_AMOUNT = 1.0;

	public ExecutionerBehavior() {
		super(List.of(
				new Change(Attributes.ATTACK_DAMAGE, ATTACK_DAMAGE_AMOUNT, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)));
	}
}
