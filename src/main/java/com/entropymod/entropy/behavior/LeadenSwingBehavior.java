package com.entropymod.entropy.behavior;

import com.entropymod.entropy.AttributeEffectBehavior;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.List;

/**
 * Leaden Swing (BAD / COMBAT, entropy 61-100) -- -50% attack speed.
 *
 * <p>Every cooldown doubles. ATTACK_SPEED floors at 0.0 and zero is unrecoverable (the
 * cooldown never refills), which is exactly why this is a TOTAL factor: x0.5 of anything
 * positive is positive, where a second additive penalty beside a doubled Sluggish Strikes
 * could have summed to the floor.
 */
public final class LeadenSwingBehavior extends AttributeEffectBehavior {

	public static final String ID = "leaden_swing";

	public static final double ATTACK_SPEED_AMOUNT = -0.5;

	public LeadenSwingBehavior() {
		super(List.of(
				new Change(Attributes.ATTACK_SPEED, ATTACK_SPEED_AMOUNT, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)));
	}
}
