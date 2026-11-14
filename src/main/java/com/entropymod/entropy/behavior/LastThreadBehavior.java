package com.entropymod.entropy.behavior;

import com.entropymod.entropy.AttributeEffectBehavior;
import com.entropymod.entropy.EffectContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.List;

/**
 * Last Thread (BAD / SURVIVAL, entropy 81-100) -- Your maximum health is cut to a tenth -- one heart.
 *
 * <p>20 -> 2.0. counterplay = FALSE: nothing the player does gets the hearts back, and almost
 * any hit is the last one. ADD_MULTIPLIED_TOTAL for Withered Frame's reason, and the two
 * compose to 1.0 -- MAX_HEALTH's floor -- rather than to zero. A run holding Titan Hide keeps
 * a tenth of 40, so that blessing is worth exactly one more heart here. Current health is
 * clamped on apply.
 */
public final class LastThreadBehavior extends AttributeEffectBehavior {

	public static final String ID = "last_thread";

	public static final double MAX_HEALTH_AMOUNT = -0.9;

	public LastThreadBehavior() {
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
