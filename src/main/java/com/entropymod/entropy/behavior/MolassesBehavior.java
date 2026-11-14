package com.entropymod.entropy.behavior;

import com.entropymod.entropy.AttributeEffectBehavior;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.List;

/**
 * Molasses (BAD / MOVEMENT, entropy 61-100) -- -40% movement speed.
 *
 * <p>Walking 4.32 -> 2.59 b/s, sprinting 5.61 -> 3.37 b/s. The number is chosen against a
 * threshold rather than for size: a creeper chases at 2.75 b/s (finding 1z), so walking no
 * longer outruns one and sprinting still does. Counterplay is therefore one held key, which is
 * what keeps this at counterplay = true.
 */
public final class MolassesBehavior extends AttributeEffectBehavior {

	public static final String ID = "molasses";

	public static final double MOVEMENT_SPEED_AMOUNT = -0.4;

	public MolassesBehavior() {
		super(List.of(
				new Change(Attributes.MOVEMENT_SPEED, MOVEMENT_SPEED_AMOUNT, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)));
	}
}
