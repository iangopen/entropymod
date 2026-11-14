package com.entropymod.entropy.behavior;

import com.entropymod.entropy.AttributeEffectBehavior;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.List;

/**
 * Tinderbox (BAD / SURVIVAL, entropy 61-100) -- Once alight, you burn four times as long.
 *
 * <p>LivingEntity.igniteForTicks is ceil(ticks x BURNING_TIME) before it calls up to Entity,
 * so the attribute scales how long every ignition lasts. x4 turns stepping out of a fire from
 * two hearts into most of a health bar unless the player reaches water. It lengthens burning;
 * it does not start it -- Sunburn does.
 */
public final class TinderboxBehavior extends AttributeEffectBehavior {

	public static final String ID = "tinderbox";

	public static final double BURNING_TIME_AMOUNT = 3.0;

	public TinderboxBehavior() {
		super(List.of(
				new Change(Attributes.BURNING_TIME, BURNING_TIME_AMOUNT, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)));
	}
}
