package com.entropymod.entropy;

import net.minecraft.world.entity.player.Player;

/**
 * Gives a {@code FoodData} a way back to the player it belongs to.
 *
 * <p><b>Why this exists at all.</b> {@code FoodData} holds no reference to its
 * owner -- the same gap that made {@code PlayerExhaustionMixin} target
 * {@code Player.causeFoodExhaustion} rather than {@code FoodData.addExhaustion}.
 * For the drain side there was a caller high enough up that still knew whose
 * hunger it was. For the <em>restore</em> side there is not: the three sources
 * ({@code FoodProperties.onConsume}, {@code CakeBlock.eat},
 * {@code SaturationMobEffect.applyEffectTick}) all converge on the private
 * {@code FoodData.add(int, float)}, and hooking the three callers instead would
 * make Glutton's "all food sources" claim a list that goes stale rather than a
 * structural property.
 *
 * <p>{@code FoodDataOwnerMixin} implements this on {@code FoodData} with a single
 * {@code @Unique} field, and {@code PlayerFoodDataOwnerMixin} stamps it at the TAIL
 * of {@code Player}'s one constructor. Reading it is then an ordinary cast, which is
 * the standard Mixin duck-typing shape.
 *
 * <p><b>An unstamped {@code FoodData} reports null</b>, and every reader must treat
 * that as "no effect" rather than as an error -- a {@code FoodData} built outside a
 * player, or read before the constructor finishes, must degrade to vanilla
 * behaviour and never throw inside vanilla's own call stack.
 */
public interface FoodDataOwner {

	/** The player this hunger bar belongs to, or null if it was never stamped. */
	Player entropymod$owner();

	/** Called once, from the TAIL of {@code Player}'s constructor. */
	void entropymod$setOwner(Player player);
}
