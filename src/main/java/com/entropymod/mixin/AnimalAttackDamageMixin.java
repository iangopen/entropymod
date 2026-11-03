package com.entropymod.mixin;

import com.entropymod.entropy.behavior.BeastWrathBehavior;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives every {@link Animal} an {@code ATTACK_DAMAGE} attribute -- the half of
 * Beast Wrath that {@code MobPassiveAggressionMixin} cannot do.
 *
 * <p><b>This is not tuning, it is the difference between the effect working and
 * the server crashing.</b> {@code ATTACK_DAMAGE} is absent from
 * {@code LivingEntity.createLivingAttributes()}, {@code Mob.createMobAttributes()}
 * and {@code Animal.createAnimalAttributes()} alike, so a cow has no attribute
 * <i>instance</i> -- an absence, not a zero. {@code Mob.doHurtTarget} reads it on
 * its first instruction via {@code getAttributeValue}, which falls through
 * {@code AttributeMap.getValue} to {@code AttributeSupplier.getValue} to the
 * private {@code getAttributeInstance} -- and that <b>throws
 * {@code IllegalArgumentException}</b> on a miss. An attacking cow without this
 * mixin takes the server tick down on its first landed hit.
 *
 * <p>It also rules out the project's usual route:
 * {@code AttributeMap.getInstance} is
 * {@code computeIfAbsent(holder, supplier::createInstance)} and
 * {@code createInstance} returns null for an attribute the supplier lacks, so
 * {@code cow.getAttribute(ATTACK_DAMAGE)} is null and
 * {@code addOrUpdateTransientModifier} has nothing to attach to. The value has
 * to come from the supplier, which is built once per {@code EntityType} at
 * bootstrap.
 *
 * <p><b>Why this one method covers every species with no list.</b> Cow (via
 * {@code AbstractCow}), Pig, Chicken, Sheep and Rabbit were each checked in
 * bytecode and all open {@code createAttributes} with
 * {@code invokestatic Animal.createAnimalAttributes} -- same discovery as
 * {@code AvoidEntityGoal} being one parameterised class rather than a per-species
 * reimplementation. Any animal added later inherits this for free.
 *
 * <p><b>Why it cannot corrupt an animal that already has attack damage.</b>
 * {@code AttributeSupplier.Builder.build()} finishes with Guava's
 * {@code buildKeepingLast()}, not {@code build()} -- duplicate keys are legal and
 * the <i>last</i> put wins. We add at the bottom of the chain, so a wolf's or a
 * rabbit's own {@code add(ATTACK_DAMAGE, ...)} lands afterwards and overwrites
 * ours. Every vanilla value survives untouched; only animals that defined none
 * end up with {@value BeastWrathBehavior#ATTACK_DAMAGE}.
 *
 * <p><b>Why an always-on attribute is not an always-on effect.</b> An attribute
 * is inert unless something attacks with it, and only
 * {@code MobPassiveAggressionMixin}'s goals ever do -- which in turn only fire
 * for a player holding Beast Wrath. This is vanilla's own pattern:
 * {@code Rabbit.createAttributes} carries {@code ATTACK_DAMAGE} 3.0 on every
 * rabbit alive, and only the killer bunny has a goal that reads it.
 */
@Mixin(Animal.class)
public abstract class AnimalAttackDamageMixin {

	@Inject(method = "createAnimalAttributes", at = @At("RETURN"))
	private static void entropymod$giveAnimalsAttackDamage(
			CallbackInfoReturnable<AttributeSupplier.Builder> cir) {
		// add() mutates the builder and returns it, so there is nothing to
		// setReturnValue -- the caller already holds this instance.
		cir.getReturnValue().add(Attributes.ATTACK_DAMAGE, BeastWrathBehavior.ATTACK_DAMAGE);
	}
}
