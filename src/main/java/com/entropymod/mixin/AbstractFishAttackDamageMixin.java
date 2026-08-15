package com.entropymod.mixin;

import com.entropymod.entropy.behavior.BeastWrathBehavior;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives every {@link AbstractFish} an {@code ATTACK_DAMAGE} attribute -- the
 * aquatic counterpart to {@link AnimalAttackDamageMixin}, and a <b>second</b>
 * injection point rather than a widening of the first.
 *
 * <h2>Why the existing Animal injection does not reach a cod</h2>
 *
 * <p>The obvious assumption is that fish are animals. <b>They are not, in the
 * type system that matters here.</b> The real chain is
 * {@code Cod -> AbstractSchoolingFish -> AbstractFish -> WaterAnimal ->
 * PathfinderMob -> Mob} -- <i>{@code Animal} appears nowhere in it</i>. So
 * {@code Animal.createAnimalAttributes} is never called for a fish and the
 * original mixin cannot reach one.
 *
 * <p>Confirmed the same way the land species were, by reading what each
 * {@code createAttributes} actually invokes:
 *
 * <ul>
 *   <li>{@code AbstractFish.createAttributes()} = {@code Mob.createMobAttributes()}
 *       plus {@code MAX_HEALTH}. <b>No {@code ATTACK_DAMAGE}.</b></li>
 *   <li>Cod, Salmon, Pufferfish and TropicalFish declare <b>no</b>
 *       {@code createAttributes} of their own, so all four inherit exactly that
 *       -- which is what makes this single injection cover the whole family.</li>
 * </ul>
 *
 * <p>That is the identical <b>absence, not a zero</b> finding recorded for cows:
 * {@code getAttribute} returns null and {@code getAttributeValue} throws, and
 * {@code Mob.doHurtTarget} reads it on its first instruction. Without this mixin
 * an aggressive cod takes the server tick down on its first landed bite -- the
 * exact crash the land build was written to avoid, reached by a class hierarchy
 * that the land build's injection does not touch.
 *
 * <p><b>Tadpole is the odd one out and needs nothing here.</b> It extends
 * {@code AbstractFish} but its own {@code createAttributes} calls
 * {@code Animal.createAnimalAttributes} -- so it is already covered by
 * {@link AnimalAttackDamageMixin}, through a superclass it does not extend.
 * Worth knowing rather than deducing from the hierarchy, which points the other
 * way.
 *
 * <p>Safety is by the same mechanism as the land mixin:
 * {@code AttributeSupplier.Builder.build()} ends in Guava's
 * {@code buildKeepingLast()}, so any fish that later adds its own value wins.
 * None currently does.
 */
@Mixin(AbstractFish.class)
public abstract class AbstractFishAttackDamageMixin {

	@Inject(method = "createAttributes", at = @At("RETURN"))
	private static void entropymod$giveFishAttackDamage(
			CallbackInfoReturnable<AttributeSupplier.Builder> cir) {
		// add() mutates the builder and returns it, so there is nothing to
		// setReturnValue -- the caller already holds this instance.
		cir.getReturnValue().add(Attributes.ATTACK_DAMAGE, BeastWrathBehavior.ATTACK_DAMAGE);
	}
}
