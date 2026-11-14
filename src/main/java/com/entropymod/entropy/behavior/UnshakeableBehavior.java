package com.entropymod.entropy.behavior;

import com.entropymod.entropy.AttributeEffectBehavior;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.List;

/**
 * Unshakeable (GOOD / COMBAT, entropy 61-100) -- Nothing can knock you back -- not mobs, not explosions.
 *
 * <p>LivingEntity.knockback scales its strength by (1 - KNOCKBACK_RESISTANCE) and returns when
 * the result is <= 0, so 1.0 is total immunity rather than a large reduction. The explosion
 * shove is a separate path that reads EXPLOSION_KNOCKBACK_RESISTANCE, which is why both are
 * granted -- Iron Will's finding that explosions never pass through knockback() applies here
 * too. Supersedes Iron Will rather than conflicting with it.
 */
public final class UnshakeableBehavior extends AttributeEffectBehavior {

	public static final String ID = "unshakeable";

	public static final double KNOCKBACK_RESISTANCE_AMOUNT = 1.0;
	public static final double EXPLOSION_KNOCKBACK_RESISTANCE_AMOUNT = 1.0;

	public UnshakeableBehavior() {
		super(List.of(
				new Change(Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE_AMOUNT, AttributeModifier.Operation.ADD_VALUE),
				new Change(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, EXPLOSION_KNOCKBACK_RESISTANCE_AMOUNT, AttributeModifier.Operation.ADD_VALUE)));
	}
}
