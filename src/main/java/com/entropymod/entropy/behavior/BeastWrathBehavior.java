package com.entropymod.entropy.behavior;

import com.entropymod.entropy.HookEffectBehavior;

/**
 * Beast Wrath (BAD / COMBAT, Tier 2) -- normally passive animals attack you.
 *
 * <h2>Why this is NOT the same shape as Beast Whisperer</h2>
 *
 * <p>{@link BeastWhispererBehavior} was the obvious precedent and it does
 * <b>not</b> transfer. That effect works by <i>vetoing a goal that already
 * exists and has already resolved a target</i>: {@code PanicGoal.shouldPanic}
 * and {@code AvoidEntityGoal.canUse} both run on a cow every tick anyway, and
 * both hand you the entity they are reacting to, so a one-line gate on the
 * resolved target is the whole implementation.
 *
 * <p>Aggression has no such goal to veto. A cow's {@code targetSelector} is
 * <b>empty</b> and its {@code goalSelector} holds Float/Panic/Breed/Tempt/
 * FollowParent/Stroll/LookAt/RandomLook -- there is no attack goal and no target
 * goal to intercept. Suppressing behaviour is subtractive and needs only a
 * predicate; adding behaviour is additive and needs the goals to exist in the
 * first place. So this is a structurally different problem, and the Beast
 * Whisperer pattern was not reused.
 *
 * <p>What <em>did</em> carry over is the discipline, not the mechanism: the gate
 * is still on a <b>resolved target</b> rather than on the goal's existence, so
 * exactly like Beast Whisperer the change is scoped to one player and needs no
 * per-species code.
 *
 * <h2>1. The goals -- constructible, and not gated like TamableAnimal's</h2>
 *
 * <p>The worry in the brief was that these would be typed narrowly the way
 * {@code TamableAnimal}'s owner goals are. They are not:
 *
 * <ul>
 *   <li>{@code MeleeAttackGoal(PathfinderMob, double, boolean)}</li>
 *   <li>{@code NearestAttackableTargetGoal(Mob, Class&lt;T&gt;, int, boolean, boolean,
 *       TargetingConditions.Selector)}</li>
 * </ul>
 *
 * <p>Every animal is a {@code PathfinderMob} ({@code Cow -> AbstractCow ->
 * Animal -> AgeableMob -> PathfinderMob -> Mob}), so both construct for a cow
 * exactly as they do for a zombie. <b>Nothing in either class consults
 * hostility.</b> Vanilla's own killer bunny is the precedent: an ordinary
 * {@code Animal} carrying stock attack goals.
 *
 * <h2>2. The attribute -- the one real obstacle, and why it needed a mixin</h2>
 *
 * <p>{@code ATTACK_DAMAGE} is <b>not</b> in {@code LivingEntity.createLivingAttributes()}
 * (21 attributes, verified; {@code ATTACK_KNOCKBACK} is there, {@code ATTACK_DAMAGE}
 * is not), nor in {@code Mob.createMobAttributes()} (adds only {@code FOLLOW_RANGE}),
 * nor in {@code Animal.createAnimalAttributes()} (adds only {@code TEMPT_RANGE}).
 * A cow therefore has <b>no {@code ATTACK_DAMAGE} attribute instance at all</b>,
 * and that is not a value of zero -- it is an absence, with two hard consequences:
 *
 * <ul>
 *   <li>{@code cow.getAttribute(ATTACK_DAMAGE)} returns <b>null</b>.
 *       {@code AttributeMap.getInstance} is
 *       {@code computeIfAbsent(holder, supplier::createInstance)} and
 *       {@code AttributeSupplier.createInstance} returns null for an attribute it
 *       does not hold. <b>So the project's usual
 *       {@code addOrUpdateTransientModifier} route is simply unavailable</b> --
 *       there is no instance to add a modifier to. This is the single finding
 *       that shaped the implementation.</li>
 *   <li>{@code cow.getAttributeValue(ATTACK_DAMAGE)} <b>throws</b>.
 *       {@code AttributeMap.getValue} falls back to
 *       {@code AttributeSupplier.getValue}, which calls the private
 *       {@code getAttributeInstance} -- and that throws
 *       {@code IllegalArgumentException} on a miss rather than returning a
 *       default. {@code Mob.doHurtTarget} reads {@code ATTACK_DAMAGE} on its
 *       very first instruction, so <b>a cow with an attack goal and no
 *       attribute crashes the server tick the instant it lands a hit.</b>
 *       Giving the attribute is not a tuning nicety; without it the effect is
 *       a crash.</li>
 * </ul>
 *
 * <p>The attribute must therefore exist in the <i>supplier</i>, which is built
 * once per {@code EntityType} at bootstrap and is immutable afterwards. The
 * clean single point is {@code Animal.createAnimalAttributes()}: Cow (via
 * AbstractCow), Pig, Chicken, Sheep and Rabbit all chain through it, so one
 * injection covers every animal with no species list. <b>It is safe for animals
 * that already define their own</b> -- {@code AttributeSupplier.Builder.build()}
 * uses Guava's {@code buildKeepingLast()}, so a wolf's or rabbit's later
 * {@code add(ATTACK_DAMAGE, ...)} overwrites ours and vanilla values are
 * untouched. See {@code AnimalAttackDamageMixin}.
 *
 * <p>This mirrors vanilla exactly: {@code Rabbit.createAttributes} carries
 * {@code ATTACK_DAMAGE} 3.0 on <i>every</i> rabbit, and only the killer bunny
 * variant ever has a goal that reads it. An attribute nothing attacks with is
 * inert.
 *
 * <h2>3. Scoping to one player</h2>
 *
 * <p>{@code NearestAttackableTargetGoal}'s {@code TargetingConditions.Selector}
 * is {@code (LivingEntity, ServerLevel) -> boolean}, evaluated per candidate.
 * Ours returns true only for a {@code Player} holding this effect, so an animal
 * <b>never selects a non-holder as a target in the first place</b> -- the
 * scoping is in target <i>selection</i>, not in a post-hoc check on an
 * already-chosen victim. A second player in the world is invisible to the goal.
 *
 * <p>The goals themselves are added to every eligible animal unconditionally,
 * and are <b>inert with no holder nearby</b>: the target goal selects nothing,
 * {@code MeleeAttackGoal.canUse()} returns false on its {@code getTarget() ==
 * null} branch, and the animal is byte-for-byte vanilla. That is why nothing
 * needs adding or removing when the effect is picked or lost -- the same reason
 * {@code apply}/{@code remove} are empty here as for every other
 * {@link HookEffectBehavior}.
 *
 * <h2>4. The damage number: {@value #ATTACK_DAMAGE}</h2>
 *
 * <p>Vanilla anchors: silverfish 1.0, spider 2.0, killer bunny 3.0, zombie 3.0.
 * The brief asked for a swarm nuisance that is felt but is not individually a
 * real hostile mob, which brackets it between spider and zombie.
 *
 * <p><b>2.0 -- one full heart per hit, exactly a spider.</b> Three reasons it is
 * not lower and not higher:
 *
 * <ul>
 *   <li>A full heart clears the project's perceptual-threshold bar (CLAUDE.md's
 *       third mixin failure mode -- Magnetic Boots shipped a real change nobody
 *       could feel). Half a heart from a chicken would read as a bug.</li>
 *   <li>It is strictly below zombie/killer-bunny 3.0, so an individual cow is
 *       measurably less dangerous than the weakest real hostile mob.</li>
 *   <li><b>A swarm does not multiply, so the per-hit number carries the whole
 *       weight.</b> {@code LivingEntity.INVULNERABLE_DURATION} gives the player
 *       20 i-frames and a same-window hit only applies the amount <i>above</i>
 *       the last one, so eight cows do not deal 16.0 -- they deal roughly 2.0
 *       per second between them. Sizing this as if a herd stacked would have
 *       produced a number far too small to notice alone.</li>
 * </ul>
 *
 * <p>So the felt effect is about one heart per second while a herd has you,
 * unarmoured: escapable, survivable, and impossible to ignore.
 *
 * <p>{@link #SPEED_MODIFIER} 1.2 puts a cow's pursuit at 0.2 * 1.2 = 0.24,
 * a shade over a zombie's 0.23 -- fast enough to chase down a walking player,
 * slower than a sprinting one, so running away always works.
 *
 * <h2>5. Farming and breeding are untouched</h2>
 *
 * <p>Checked rather than assumed: neither {@code Cow.mobInteract} nor
 * {@code Animal.mobInteract} contains a single reference to {@code getTarget()}.
 * Hostility lives entirely in goal selection and interaction lives entirely in
 * the use path, and the two never meet -- <b>a cow chasing you can still be
 * milked and still be fed into love mode, mid-pursuit.</b>
 *
 * <p>The one real interaction is goal <i>priority</i>. The attack goal is added
 * at priority 2, which beats {@code TemptGoal} (3) and {@code FollowParentGoal}
 * (4) -- deliberately, so holding wheat does not pacify a hostile animal -- and
 * loses to {@code PanicGoal} (1) and {@code FloatGoal} (0), so a burning cow
 * still runs for water and none of them drown while angry. Breeding still
 * completes: it needs the animal fed and in range, which is exactly where an
 * attacking animal already is.
 *
 * <p>apply()/remove() are empty by design -- see {@link HookEffectBehavior}.
 */
public final class BeastWrathBehavior extends HookEffectBehavior {

	public static final String ID = "beast_wrath";

	/**
	 * Attack damage granted to every {@code Animal} lacking its own, in half-hearts.
	 * One full heart per hit -- a spider. See the class javadoc for why a swarm's
	 * i-frames mean this single number carries the whole difficulty of the effect.
	 */
	public static final double ATTACK_DAMAGE = 2.0;

	/**
	 * Pathing speed multiplier for the attack goal. 1.2 puts a cow at 0.24 against
	 * a zombie's 0.23 -- catches a walking player, never a sprinting one.
	 */
	public static final double SPEED_MODIFIER = 1.2;

	/**
	 * Priority for the melee goal in the animal's {@code goalSelector}. Above
	 * TemptGoal (3) so wheat does not pacify it, below PanicGoal (1) and FloatGoal
	 * (0) so fire and water still win. See the class javadoc.
	 */
	public static final int ATTACK_GOAL_PRIORITY = 2;

	/** Priority for the target goal in the animal's (otherwise empty) {@code targetSelector}. */
	public static final int TARGET_GOAL_PRIORITY = 1;
}
