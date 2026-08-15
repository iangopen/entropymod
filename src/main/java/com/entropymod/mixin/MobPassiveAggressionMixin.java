package com.entropymod.mixin;

import com.entropymod.entropy.behavior.BeastWrathBehavior;
import com.entropymod.entropy.wrath.WrathTargetGoal;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives normally passive animals the ability to attack -- the goal half of Beast
 * Wrath. Pairs with {@link AnimalAttackDamageMixin}, which supplies the damage
 * value without which this crashes.
 *
 * <h2>Why the constructor, and not {@code registerGoals}</h2>
 *
 * <p>{@code Mob.registerGoals} is the obvious target and it is <b>the first
 * failure mode in CLAUDE.md</b>: a subclass overrides the method you targeted.
 * {@code Animal}, {@code AgeableMob} and {@code PathfinderMob} were each checked
 * and <b>none of them declares {@code registerGoals}</b> -- Cow, Pig, Chicken,
 * Sheep and Rabbit override {@code Mob}'s empty base directly, and an override of
 * an empty base has no reason to call {@code super}. A mixin there would apply
 * cleanly and never run for a single animal.
 *
 * <p>{@code Mob}'s constructor cannot be overridden, and it calls
 * {@code registerGoals()} at offset 146 behind an
 * {@code instanceof ServerLevel} check, immediately before its {@code return} at
 * 149. Injecting at TAIL therefore runs <b>after the mob's own goals are already
 * registered</b>, which is what makes the eligibility test below possible. The
 * server-side check is repeated here because TAIL fires either way.
 *
 * <h2>Which animals, without a species list</h2>
 *
 * <p>The test is {@code Animal} with an <b>empty {@code targetSelector}</b>, and
 * that is a definition rather than a heuristic: an entity with no target-selection
 * goal has no way to acquire a target, and therefore no way to attack anything.
 * It is exactly the property "normally passive" names. Cow, pig, chicken, sheep
 * and rabbit all pass -- Rabbit's {@code registerGoals} was read in bytecode and
 * registers Float/Panic/Breed/Tempt/Stroll/RaidGarden/Avoid/LookAt and no target
 * goal at all. Wolves, polar bears, llamas and every other already-dangerous
 * animal carry a {@code HurtByTargetGoal} and are excluded without being named.
 *
 * <h2>Scoping, and why nothing is added or removed when the effect changes</h2>
 *
 * <p>The goals go on every eligible animal unconditionally and are <b>inert</b>
 * without a holder: {@link EffectHooks#attractsAnimalAggression} gates target
 * <i>selection</i>, so with no holder nearby the target goal selects nothing and
 * {@code MeleeAttackGoal.canUse()} returns false on its {@code getTarget() ==
 * null} branch. The animal is vanilla until the moment someone holds Beast Wrath,
 * and vanilla again the moment they do not.
 *
 * <p>That is deliberate and is the reason this needs no bookkeeping. Adding the
 * goals in {@code apply()} instead would have meant finding every loaded animal,
 * handling ones that spawn or load later, and removing them again -- the
 * entity-state idempotency trap recorded in CLAUDE.md 1h. Here the acquired set
 * stays the only source of truth and the behaviour is derived from it every tick.
 *
 * <p><b>Other players are never targeted.</b> The selector runs per candidate, so
 * a player without the effect is not merely ignored after selection -- it is
 * never selected.
 */
@Mixin(Mob.class)
public abstract class MobPassiveAggressionMixin {

	@Shadow
	@org.spongepowered.asm.mixin.Final
	protected GoalSelector goalSelector;

	@Shadow
	@org.spongepowered.asm.mixin.Final
	protected GoalSelector targetSelector;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void entropymod$armPassiveAnimals(EntityType<? extends Mob> type, Level level, CallbackInfo ci) {
		if (!(level instanceof ServerLevel)) {
			return;
		}
		Object self = this;
		// The eligible universe: land animals and fish. Both are PathfinderMob, so
		// both drive the stock goals below. Squid, glow squid and dolphin are
		// AgeableWaterCreature and fall outside this test -- see the class javadoc
		// for why that exclusion is a decision rather than an oversight.
		if (!(self instanceof Animal || self instanceof AbstractFish)) {
			return;
		}
		PathfinderMob mob = (PathfinderMob) self;

		// Two INDEPENDENT questions, which used to be one and could not stay one.
		//
		//   canAlreadyFight -- does this mob already own an attack goal?
		//   eligible        -- should it turn on the holder at all?
		//
		// A wolf answers yes to both: it can already fight, and (when wild) it is
		// in scope. Conflating them is what excluded every tameable species.
		boolean canAlreadyFight = !this.targetSelector.getAvailableGoals().isEmpty();
		boolean eligible = !canAlreadyFight || self instanceof TamableAnimal;
		if (!eligible) {
			return;
		}

		this.targetSelector.addGoal(
				BeastWrathBehavior.TARGET_GOAL_PRIORITY,
				new WrathTargetGoal(mob, BeastWrathBehavior.TARGET_SCAN_INTERVAL));

		// Only teach a mob to fight if it cannot already. A wolf and a cat each
		// carry their own MeleeAttackGoal (javap-verified), so adding a second at a
		// priority of our choosing would quietly retune how a WILD wolf chases a
		// skeleton -- a change to an animal that has nothing to do with this effect.
		if (!canAlreadyFight) {
			this.goalSelector.addGoal(
					BeastWrathBehavior.ATTACK_GOAL_PRIORITY,
					new MeleeAttackGoal(mob, BeastWrathBehavior.SPEED_MODIFIER, false));
		}
	}
}
