package com.entropymod.entropy.behavior;

import com.entropymod.entropy.AttributeEffectBehavior;
import com.entropymod.entropy.EffectContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.List;

/**
 * Withered Frame (BAD / SURVIVAL, entropy 61-100) -- Your maximum health is halved.
 *
 * <p>ADD_MULTIPLIED_TOTAL, not a flat subtraction, and that is the safety property: a product
 * can never reach zero, so however many health curses a long run has stacked this leaves half
 * of whatever they left. On a plain player 20 -> 10; under Brittle Bones and Glass Cannon Pact
 * (14) -> 7. MAX_HEALTH floors at 1.0 regardless. Current health is clamped on apply for the
 * reason Brittle Bones records.
 */
public final class WitheredFrameBehavior extends AttributeEffectBehavior {

	public static final String ID = "withered_frame";

	public static final double MAX_HEALTH_AMOUNT = -0.5;

	public WitheredFrameBehavior() {
		super(List.of(
				new Change(Attributes.MAX_HEALTH, MAX_HEALTH_AMOUNT, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)));
	}

	/** Lowering max health does not lower current health -- see BrittleBonesBehavior. */
	@Override
	protected void afterApply(EffectContext ctx) {
		ServerPlayer player = ctx.target();
		float max = player.getMaxHealth();
		if (player.getHealth() > max) {
			player.setHealth(max);
		}
	}
}
