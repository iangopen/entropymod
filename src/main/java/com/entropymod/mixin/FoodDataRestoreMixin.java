package com.entropymod.mixin;

import com.entropymod.entropy.EffectHooks;
import com.entropymod.entropy.FoodDataOwner;
import com.entropymod.entropy.behavior.GluttonBehavior;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Cuts what any food source restores, for Glutton.
 *
 * <h2>The private {@code add} is the one funnel, and that is the whole reason for
 * this target</h2>
 *
 * <p>Every {@code FoodData.eat} call site in the shipped jar was enumerated -- there
 * are three, {@code FoodProperties.onConsume} (every food item, since food is a data
 * component), {@code CakeBlock.eat}, and {@code SaturationMobEffect.applyEffectTick}
 * -- and both {@code eat} overloads are one-line delegations to
 * {@code add(int, float)}. Hooking {@code add} therefore makes "all food sources,
 * uniformly" a <b>structural</b> property rather than a list of three callers that
 * a version bump or another mod can quietly extend. Same reasoning as Clumsy Digger
 * being gated on a tag instead of an item list.
 *
 * <p>The reduction lands <b>before</b> vanilla's own clamps
 * ({@code Mth.clamp(nutrition + foodLevel, 0, 20)} and saturation clamped to the new
 * food level), so nothing here can push either field outside its normal range and
 * the {@code saturation <= foodLevel} invariant still holds.
 *
 * <h2>Two variables, and the int one rounds</h2>
 *
 * <p>{@code foodLevel} is an {@code int}, so truncation would zero every small food:
 * {@code (int)(2 * 0.25f)} is 0. That is the trap {@code EffectHooks.scaleAmount}
 * exists for on the XP side, and {@link GluttonBehavior#reduceNutrition} rounds for
 * the same reason. Saturation is a {@code float} and is scaled exactly.
 *
 * <p>Both handlers are kept to a lookup and a call; the arithmetic and its rounding
 * table live in {@link GluttonBehavior} where the harness can drive them without a
 * live player.
 *
 * <p><b>An unstamped {@code FoodData} passes the value through untouched.</b> The
 * owner is set from {@code Player}'s constructor, so in practice it is always
 * present for a player's own bar; anything else -- and the client, where
 * {@code EffectHooks} answers "no effect" by design -- degrades to vanilla.
 */
@Mixin(FoodData.class)
public abstract class FoodDataRestoreMixin {

	@ModifyVariable(method = "add", at = @At("HEAD"), argsOnly = true, index = 1)
	private int entropymod$gluttonNutrition(int nutrition) {
		return entropymod$reduced() ? GluttonBehavior.reduceNutrition(nutrition) : nutrition;
	}

	@ModifyVariable(method = "add", at = @At("HEAD"), argsOnly = true, index = 2)
	private float entropymod$gluttonSaturation(float saturation) {
		return entropymod$reduced() ? GluttonBehavior.reduceSaturation(saturation) : saturation;
	}

	@Unique
	private boolean entropymod$reduced() {
		Player owner = ((FoodDataOwner) this).entropymod$owner();
		return owner != null
				&& EffectHooks.foodRestoreMultiplier(owner) != 1.0f;
	}
}
