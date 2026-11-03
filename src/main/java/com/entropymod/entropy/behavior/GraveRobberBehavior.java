package com.entropymod.entropy.behavior;

import com.entropymod.entropy.HookEffectBehavior;

/**
 * Grave Robber (GOOD / COMBAT, Tier 2) -- mobs that die near you sometimes drop
 * one extra item from their own loot table.
 *
 * <h2>1. The hook</h2>
 *
 * <p>{@code ServerLivingEntityEvents.AFTER_DEATH(LivingEntity, DamageSource)} --
 * a real Fabric event, so no mixin. Unlike most drop effects this one does not
 * need to observe or intercept the original drops at all, which is what keeps it
 * off the death path entirely.
 *
 * <h2>2. The bonus is a SECOND ROLL of the mob's own loot table</h2>
 *
 * <p>Of the three options, this is the only one that needs no new content and
 * cannot drift out of balance on its own. A fixed item ("a gold nugget") would be
 * a lie about where it came from and would have to be re-tuned every time the
 * roster changed; extra experience would reward AFK farms specifically, which is
 * the exact thing to avoid here.
 *
 * <p><b>Crucially it is vanilla's own roll, not an imitation of one.</b>
 * {@code LivingEntity} exposes a <i>public</i>
 * {@code dropFromLootTable(ServerLevel, DamageSource, boolean, ResourceKey, Consumer)}
 * whose body builds the identical {@code LootParams} the death drop used --
 * {@code THIS_ENTITY}, {@code ORIGIN}, {@code DAMAGE_SOURCE},
 * {@code ATTACKING_ENTITY}, {@code DIRECT_ATTACKING_ENTITY} and
 * {@code getLastHurtByPlayer()}. Passing a {@code Consumer} instead of letting it
 * spawn means we get the stacks in hand rather than in the world.
 *
 * <p>So Looting, the killer's weapon, mob variant, difficulty and any datapack
 * edit to that table are all honoured for free, and the effect can never produce
 * an item the mob does not already drop. That last property is what makes it
 * <b>farm-safe</b>; see below.
 *
 * <p><b>One</b> stack from that roll, chosen uniformly, not the whole roll -- the
 * spec says "an extra item", and dropping the entire second roll would be a flat
 * doubling on every proc. An empty roll drops nothing and is not re-rolled, which
 * is honest: some mobs legitimately roll nothing.
 *
 * <h2>3. "Near" = {@value #RADIUS}, and it is vanilla's number</h2>
 *
 * <p>Not a taste pick. {@code Mob.createMobAttributes()} overrides
 * {@code FOLLOW_RANGE} to <b>16.0</b> (the registered attribute default of 32.0 is
 * not what mobs actually use -- the distinction already cost this project a bug in
 * Creeper Magnet). 16 blocks is therefore exactly the range inside which a mob
 * would have been engaging the player at all, so "near" means "close enough to
 * have been part of your fight" rather than an invented number.
 *
 * <p>Measured as a true sphere via {@code distanceToSqr}, not a box -- an
 * unfiltered AABB would leak to {@code 16 * sqrt(3) = 27.7} blocks, the same 73%
 * over-range Danger Sense and Ore Sense guard against.
 *
 * <h2>4. The player does NOT need to have dealt the kill -- and that is deliberate</h2>
 *
 * <p>Proximity alone. Three reasons:
 *
 * <ul>
 *   <li>The effect is <i>Grave Robber</i>, not <i>Executioner</i>. It is about
 *       being present at a death and helping yourself, not about claiming a kill.</li>
 *   <li>Requiring the kill would make it a strict damage-dealing bonus, which is
 *       what Looting already is. Proximity gives it a different shape: a creeper
 *       killing a zombie beside you, a mob falling into your pit, a skeleton
 *       shooting a spider -- all pay out, and those are the moments that make the
 *       effect feel like a distinct thing rather than a hidden enchantment.</li>
 *   <li>It is simpler to reason about and to test: one distance check, no
 *       attribution chain through {@code getKillCredit}.</li>
 * </ul>
 *
 * <h2>5. Farm risk: the reward's SHAPE is the protection, not the rate</h2>
 *
 * <p>A grinder keeps a player within 16 blocks of thousands of deaths an hour, so
 * a proximity-gated effect gets no protection from the trigger. The protection
 * comes from what it pays:
 *
 * <ul>
 *   <li><b>It is a percentage of what the mob already gives.</b> One stack from a
 *       second roll at {@value #CHANCE} works out at <b>+15% to +30% yield</b>.
 *       Derived rather than asserted: if a mob's death roll produces {@code k}
 *       stacks, one uniformly-chosen stack of an identical second roll is
 *       {@code 1/k} of a roll in expectation, so the multiplier is
 *       {@code 1 + CHANCE/k}. {@code k = 1} for a single-pool mob (a zombie is
 *       rotten flesh and little else) gives +30%; {@code k = 2} for a skeleton
 *       (bones and arrows) gives +15%. A Looting III sword takes a 0-2 common pool
 *       to 0-5 -- mean 1.0 to 2.5, <b>+150%</b> -- so the ceiling here is a fifth
 *       of an enchantment the player could just have. It cannot make a cheap mob
 *       worth farming, only a mob already worth farming somewhat better.</li>
 *   <li><b>It cannot introduce a new resource.</b> Nothing drops that the table
 *       does not already contain, so no farm becomes a source of something it was
 *       not already a source of.</li>
 *   <li><b>No experience.</b> XP is the one reward an AFK farm converts most
 *       efficiently, and it is deliberately not part of this.</li>
 * </ul>
 *
 * <p>Stated plainly: this <i>does</i> make grinding modestly more lucrative and
 * nothing prevents that. The judgement is that a sub-Looting-III yield bump is a
 * felt bonus rather than a farming multiplier. If it reads as too strong in play,
 * {@link #CHANCE} is the single lever.
 *
 * <p><b>The doubling from 15% to 30% did not change any of that.</b> All three
 * protections are properties of the reward's shape, not of its rate: it is still a
 * fraction of the mob's own table, it still cannot introduce a resource the mob
 * does not already drop, and it still grants no experience. The rate moves the
 * yield bump from +7.5-15% to +15-30%, which stays under the Looting III bar that
 * the judgement above rests on. <b>The shape was deliberately left alone</b> --
 * dropping the whole second roll instead of one stack of it would have doubled
 * every proc and put a 30% rate well past that bar.
 *
 * <h2>6. {@value #CHANCE} is sized for a discrete, visible event</h2>
 *
 * <p>Same class of decision as Butterfingers Extreme (10%) and Leaky Pockets (7%),
 * and the opposite risk from Clumsy Digger's invisible rate: the failure mode here
 * is "did that even happen", so it has to be common enough to be attributed.
 *
 * <table border="1">
 *   <caption>Probability of at least one proc</caption>
 *   <tr><th>chance</th><th>mean gap</th><th>in a 5-mob fight</th></tr>
 *   <tr><td>5%</td><td>20 mobs</td><td>22.6%</td></tr>
 *   <tr><td>10%</td><td>10 mobs</td><td>41.0%</td></tr>
 *   <tr><td>15% <i>(shipped first)</i></td><td>6.7 mobs</td><td>55.6%</td></tr>
 *   <tr><td>25%</td><td>4 mobs</td><td>76.3%</td></tr>
 *   <tr><td><b>30%</b></td><td><b>3.3 mobs</b></td><td><b>83.2%</b></td></tr>
 *   <tr><td>50%</td><td>2 mobs</td><td>96.9%</td></tr>
 * </table>
 *
 * <p>15% shipped first and was the lowest value that clears a coin flip per
 * ordinary fight. In play it read as too rare to attribute: at a mean gap of 6.7
 * mobs, more than one fight in three pays out nothing at all, and a blessing the
 * player cannot tell they are holding is the failure mode this effect was sized
 * against in the first place.
 *
 * <p><b>30% is the doubling, and it is the value where the mean gap falls inside a
 * single fight.</b> 3.3 mobs is shorter than an ordinary encounter, so the effect
 * is normally observed <i>within</i> the fight rather than remembered across
 * several; {@code 1 - 0.7^5 = 83.2%} of five-mob fights pay out at least once,
 * against 55.6% before. It stops short of 50%, which would make a bonus drop the
 * expected outcome of every second kill and read as the mob's own loot table
 * rather than as something the run granted.
 *
 * <p>apply()/remove() are empty by design -- see {@link HookEffectBehavior}.
 */
public final class GraveRobberBehavior extends HookEffectBehavior {

	public static final String ID = "grave_robber";

	/**
	 * How close the player must be to the dying mob, in blocks. Vanilla's own
	 * {@code Mob.createMobAttributes()} FOLLOW_RANGE -- see the class javadoc for
	 * why that is the principled choice and why it is NOT the attribute's
	 * registered 32.0 default.
	 */
	public static final double RADIUS = 16.0;

	/** Pre-squared, because every comparison is against {@code distanceToSqr}. */
	public static final double RADIUS_SQ = RADIUS * RADIUS;

	/** Chance per qualifying death that a bonus drop is rolled. */
	public static final float CHANCE = 0.30f;

	/**
	 * Whether a mob dying this far away (squared) counts as "near".
	 *
	 * <p>Minecraft-free so the harness can drive the boundary directly, same
	 * discipline as {@code TramplePath} and {@code DoubleJumpState}. A radius that
	 * silently leaked would be invisible in play -- an extra drop looks the same
	 * whatever the distance was.
	 */
	public static boolean isNear(double distanceSq) {
		return distanceSq <= RADIUS_SQ;
	}

	/**
	 * Rolls the proc against a uniform {@code [0,1)} sample.
	 *
	 * <p>Takes the sample rather than a random source so it is Minecraft-free and
	 * <b>deterministic</b> -- the harness drives the exact boundary and a large
	 * sweep of the real rate, neither of which is possible through a
	 * {@code RandomSource} the harness cannot construct.
	 */
	public static boolean rollsBonus(float roll) {
		return roll < CHANCE;
	}

	/**
	 * Which stack of a second loot roll to drop.
	 *
	 * <p>Uniform over the roll's results. Returns -1 for an empty roll, which the
	 * caller treats as "nothing dropped" rather than re-rolling -- a mob that
	 * genuinely rolled nothing has nothing extra to give.
	 */
	public static int chooseBonusIndex(int rolledStacks, float roll) {
		if (rolledStacks <= 0) {
			return -1;
		}
		// Math.min guards the roll == 1.0f case a float sample can technically
		// reach; without it a full roll would index one past the end.
		return Math.min(rolledStacks - 1, (int) (roll * rolledStacks));
	}
}
