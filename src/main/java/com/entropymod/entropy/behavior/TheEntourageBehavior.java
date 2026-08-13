package com.entropymod.entropy.behavior;

import com.entropymod.entropy.EffectBehavior;
import com.entropymod.entropy.EffectContext;
import com.entropymod.entropy.EntropyManager;
import com.entropymod.entropy.companion.CompanionBuffs;
import com.entropymod.entropy.companion.CompanionSpawner;
import net.minecraft.world.entity.EntityType;

import java.util.List;

/**
 * The Entourage (GOOD / COMPANION, Tier 2) -- {@value #ESCORT_SIZE} iron golems
 * that follow you and attack anything that attacks you.
 *
 * <h2>Species: iron golems, and the reasoning is a real constraint</h2>
 *
 * <p>The brief asked for "passive mobs". Taken literally -- cows, pigs, sheep --
 * the effect cannot exist: <b>a passive animal has no attack goal at all</b>, so
 * {@code setTarget} on a cow does precisely nothing and "attacks anything that
 * attacks the player" would need a melee goal, a damage attribute and an attack
 * animation invented for a species that has none.
 *
 * <p>Iron golems are the honest reading of the requirement and satisfy every part
 * of it that matters:
 *
 * <ul>
 *   <li><b>Passive toward the player.</b> {@code IronGolem} is a
 *       {@code NeutralMob} -- it never targets a player unprovoked, so it is not a
 *       hostile escort in disguise.</li>
 *   <li><b>Genuinely combat-capable.</b> It already has a {@code MeleeAttackGoal},
 *       which is what makes {@code setTarget} immediately actionable and means
 *       <b>no new Goal class is written for this effect at all</b>.</li>
 *   <li><b>Not wolves</b>, so it stays visually and mechanically distinct from
 *       Loyal Pack, and it carries no armour.</li>
 * </ul>
 *
 * <p><b>Count is {@value #ESCORT_SIZE}, deliberately small.</b> An iron golem
 * deals 7.5-21.5 damage and has 100 HP; four of them is already overwhelming
 * against anything Tier 2 will meet, and they are large enough (1.4 x 2.7) that
 * more would be a mobility problem in ordinary terrain. Four also keeps this
 * clearly below Loyal Pack's fifteen, which is the intended contrast between the
 * two effects.
 *
 * <h2>Defending needed no new Goal, and could not have reused vanilla's</h2>
 *
 * <p>Vanilla's {@code OwnerHurtByTargetGoal} does exactly what this effect wants,
 * and <b>it cannot be used</b>: its constructor takes a {@code TamableAnimal}, and
 * an iron golem is not one. That typing -- rather than the broader
 * {@code OwnableEntity} interface -- is what rules out reusing any of vanilla's
 * three owner goals for every companion effect except Loyal Pack.
 *
 * <p>But the goal's <em>content</em> is one line: read the owner's
 * {@code getLastHurtByMob()} and target it. That accessor is public and vanilla
 * maintains it, so {@code CompanionService} reproduces the behaviour without
 * reproducing the class. Everything after the target choice -- pathing, swinging,
 * knockback, losing interest -- is the golem's own vanilla AI.
 *
 * <h2>DO NOT ADD {@code setInvulnerable(true)} BACK. It is not a damage decision.</h2>
 *
 * <p>It was added, shipped for one session, and removed again. The damage half worked
 * exactly as intended -- {@code Entity.isInvulnerableToBase} blocks everything except
 * {@code #bypasses_invulnerability} ({@code out_of_world}, {@code generic_kill}) and a
 * Creative-mode player. <b>The problem is the half that is not about damage at all:</b>
 *
 * <pre>
 * LivingEntity.canBeSeenAsEnemy() = !isInvulnerable() &amp;&amp; canBeSeenByAnyone()
 * LivingEntity.canAttack(target)  = ... : target.canBeSeenAsEnemy()   // Mob delegates
 * TargetingConditions.test(...)   = if (isCombat &amp;&amp; !attacker.canAttack(target)) false
 * </pre>
 *
 * <p>{@code NearestAttackableTargetGoal} uses {@code forCombat()}, and so does
 * {@code HurtByTargetGoal} ({@code forCombat().ignoreLineOfSight()
 * .ignoreInvisibilityTesting()}). So an invulnerable golem <b>cannot be acquired as a
 * target by any hostile mob, and a mob it punches cannot retaliate.</b> Zombies walked
 * past the escort to the player, the escort could never pull aggro, and this effect's
 * own registry description -- "attack anything that attacks you" -- described something
 * that could not happen. It also made {@link #HEALTH_MULTIPLIER} unreachable.
 *
 * <p><b>The honest cost of removing it, stated rather than buried:</b> the golems are
 * genuinely killable now, by mobs and by the player, and {@code CompanionRoster}'s "a
 * companion that dies stays dead" therefore <em>can</em> arise for this effect -- it is
 * one of two invulnerable companions now, not three. {@link #HEALTH_MULTIPLIER} is the
 * replacement for the guarantee, not an equal of it.
 *
 * <p>See {@code CompanionRoster} for why {@code apply} is gated on
 * {@link EffectContext#isFreshPick()} rather than counting live entities.
 */
public final class TheEntourageBehavior implements EffectBehavior {

	public static final String ID = "the_entourage";

	/** Small on purpose -- see the class javadoc. */
	public static final int ESCORT_SIZE = 4;

	/**
	 * Closest an escort will stand. Below this it backs away.
	 *
	 * <p>Three blocks is set by the golem's own size rather than by taste: an iron
	 * golem is 1.4 blocks wide and 2.7 tall, so at 3 blocks four of them ring the
	 * player without their bounding boxes overlapping each other or the player's,
	 * and the player can still see past them. Any less and they occlude the view and
	 * push each other, which is the crowding this replaced.
	 */
	public static final double MIN_HOLD = 3.0;

	/**
	 * Furthest an escort will drift before closing back in.
	 *
	 * <p>Five keeps them inside melee reach of anything attacking the player, which
	 * is the whole point of an escort -- a golem holding at 8 would arrive after the
	 * hit landed. The 2-block band between {@link #MIN_HOLD} and this is wide enough
	 * that ordinary player movement does not constantly re-trigger pathing.
	 */
	public static final double MAX_HOLD = 5.0;

	/** Navigation speed multiplier. 1.0 is a golem's flat-out speed, whatever it is. */
	public static final double FOLLOW_SPEED = 1.0;

	/**
	 * Real movement speed as a multiple of a stock iron golem's: <b>+25%</b>.
	 *
	 * <p>A golem's {@code MOVEMENT_SPEED} is 0.25, which at navigation modifier 1.0
	 * gives ground acceleration {@code 0.25^2 = 0.0625} and <b>2.70 blocks/second</b>.
	 * The target is <b>3.375 b/s</b>, needing acceleration {@code 0.078125}, i.e. an
	 * effective attribute of {@code sqrt(0.078125) = 0.2795085}.
	 *
	 * <p><b>That is 0.25 x 1.118034, not 0.25 x 1.25.</b> Real speed goes as the
	 * SQUARE of the attribute (CLAUDE.md 1e), so the naive multiply would have
	 * delivered accel {@code 0.3125^2 = 0.09766} -> 4.22 b/s, <b>+56.3%</b> -- more
	 * than double the buff asked for, and undetectable without a stopwatch. The
	 * derivation lives once, in {@code CompanionBuffs.speedModifierAmount}.
	 *
	 * <p>Note the buff is on the <em>attribute</em>, not on {@link #FOLLOW_SPEED}: the
	 * navigation modifier only applies to the paths this mod issues, while the
	 * attribute also covers the golem's own {@code MeleeAttackGoal} pursuit, which is
	 * where an escort's speed matters most.
	 */
	public static final double SPEED_MULTIPLIER = 1.25;

	/**
	 * Maximum health as a multiple of a stock iron golem's: <b>+400%, i.e. 5x</b>.
	 *
	 * <p>An iron golem's {@code MAX_HEALTH} is <b>100.0</b> (50 hearts), so this ships
	 * <b>500.0</b> -- comfortably inside the attribute's 1024.0 ceiling, so nothing is
	 * clamped away. {@code CompanionBuffs.applyMaxHealth} refills the bar, because
	 * raising the ceiling does not raise current health.
	 *
	 * <p><b>This is now the golems' actual survivability, and for one session it was
	 * not.</b> It shipped alongside {@code setInvulnerable(true)} and was flagged then
	 * as "presently unreachable" -- two changes that contradicted each other, which is
	 * exactly what the flag's removal resolves. The "+400%" reading is the one asked
	 * for (new total = 5x); the other candidate reading, +400 flat, would give 500.0 as
	 * well by coincidence of the base being 100, so no interpretation is at risk.
	 *
	 * <p>For scale, against what Tier 2 actually throws: a creeper's point-blank blast
	 * is ~40 damage and a zombie's hit 3-4, so a golem absorbs a dozen explosions or
	 * well over a hundred zombie hits. It is not immortal, and that is the trade named
	 * below.
	 */
	public static final double HEALTH_MULTIPLIER = 5.0;

	@Override
	public void apply(EffectContext ctx) {
		if (!ctx.isFreshPick()) {
			return;
		}
		EntropyManager manager = EntropyManager.get(ctx.server());
		List<String> spawned = CompanionSpawner.spawnGroup(ctx.target(), EntityType.IRON_GOLEM,
				ESCORT_SIZE, golem -> {
					// A golem built by a player is "player-created", which is what stops
					// it ever turning on villagers or wandering off to defend a village
					// instead of its owner.
					golem.setPlayerCreated(true);
					// NO setInvulnerable HERE, AND THAT IS THE POINT -- see the class
					// javadoc. It made the golems untargetable by every hostile mob and
					// unable to be retaliated against, which deleted the half of this
					// effect its own description promises. 500 HP is the replacement.
					// PERMANENT modifiers, not transient: this apply() runs once and
					// never again (isFreshPick), so a transient modifier would vanish
					// on the first chunk unload with nothing to re-derive it from.
					// See CompanionBuffs for that inversion of the player-side rule,
					// and for why the speed amount is sqrt-derived.
					CompanionBuffs.applySpeed(golem, ID, SPEED_MULTIPLIER);
					CompanionBuffs.applyMaxHealth(golem, ID, HEALTH_MULTIPLIER);
				});
		manager.recordCompanions(ctx.target(), ID, spawned);
	}

	@Override
	public void remove(EffectContext ctx) {
		// Permanent; nothing calls this, and the escort is left standing.
	}
}
