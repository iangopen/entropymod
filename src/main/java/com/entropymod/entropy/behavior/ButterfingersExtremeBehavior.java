package com.entropymod.entropy.behavior;

import com.entropymod.entropy.HookEffectBehavior;

/**
 * Butterfingers Extreme (BAD / COMBAT, Tier 2) -- attacking has a chance to fling
 * whatever you are holding out of your hand.
 *
 * <h2>The fling is vanilla's own Q-key throw, not a hand-rolled velocity</h2>
 *
 * <p>{@code Player.drop(ItemStack, boolean)} is
 * {@code drop(stack, dropAround = false, includeThrowerName = arg)}, and
 * {@code LivingEntity.createItemStackToDrop} has two profiles, both javap-verified:
 *
 * <table border="1">
 *   <caption>vanilla's two drop profiles</caption>
 *   <tr><th>{@code dropAround}</th><th>velocity</th><th>used by</th></tr>
 *   <tr><td>true</td><td>random bearing, horizontal speed {@code nextFloat() * 0.5},
 *       fixed {@code +0.2} up</td><td>death drops, scatter</td></tr>
 *   <tr><td><b>false</b></td><td><b>{@code 0.3} along the look vector</b> (pitch and
 *       yaw), plus up to {@code 0.02} of random scatter</td><td>the Q key</td></tr>
 * </table>
 *
 * <p><b>This effect uses the second.</b> The player is mid-swing and therefore facing
 * their target, so a look-directed throw at 0.3 blocks/tick sends the item forward
 * past the mob -- roughly 3-4 blocks before it settles -- which reads as the weapon
 * being torn out of the swing rather than as a random scatter. Going through
 * {@code Player.drop} rather than constructing an {@code ItemEntity} by hand is the
 * same "perform the change through vanilla's own transition" rule Green Thumb
 * follows, and it brings three things with it for free: the spawn point at
 * {@code getEyeY() - 0.3} rather than at the feet, {@code setThrower(player)} so the
 * item is attributed, and the pickup delay so it cannot be re-collected on the same
 * tick it leaves.
 *
 * <p><b>It is a real dropped entity, deliberately.</b> Deleting the stack or moving
 * it to another slot would both be cheaper and both would be a different effect: the
 * whole counterplay here is that the item is lying on the ground a few blocks away,
 * in the middle of a fight, and you have to decide whether to go and get it.
 *
 * <h2>Scope: anything in the main hand, not "weapons"</h2>
 *
 * <p>Same rule and the same reason as {@link BehemothGauntletsBehavior}: at the point
 * this fires there is no principled definition of "weapon" available -- tools are
 * data-driven and there is no {@code SwordItem} hierarchy left -- and "whatever you
 * are holding" is legible in a way "whatever the game considers a weapon" is not.
 * Punching a mob with a pickaxe in hand and losing the pickaxe is exactly as much of
 * a curse as losing a sword.
 *
 * <p><b>An empty hand rolls nothing and consumes nothing.</b> There is no item to
 * fling, so the roll is not taken at all -- which also means bare-handed combat is a
 * complete, if painful, counterplay.
 *
 * <p>apply()/remove() are empty by design -- see {@link HookEffectBehavior}.
 */
public final class ButterfingersExtremeBehavior extends HookEffectBehavior {

	public static final String ID = "butterfingers_extreme";

	/**
	 * Chance per landed attack that the held item is flung.
	 *
	 * <h2>Why 10% rather than Clumsy Digger's 8%, and what it means in fights</h2>
	 *
	 * <p>The two effects are tuned against opposite failure modes, so copying the
	 * number would have been the wrong move even though the roll shape is shared.
	 * Clumsy Digger is <em>invisible</em> -- its whole observable size is a durability
	 * bar moving faster -- so its chance is one factor in a product that has to add up
	 * to something detectable. This effect's outcome is a <b>discrete, unmissable
	 * event</b>: the thing in your hand is suddenly on the floor. The project's
	 * recorded failure mode for a visible event is the opposite one, "is it even on?",
	 * which is what retuned Leaky Pockets from 4% to 7%.
	 *
	 * <p>Per-fight arithmetic, against a 4-hit fight (a 20 HP zombie against an iron
	 * sword is about four swings):
	 *
	 * <table border="1">
	 *   <caption>chance of at least one fling per fight</caption>
	 *   <tr><th>chance</th><th>mean attacks between flings</th><th>P(fling in a 4-hit fight)</th></tr>
	 *   <tr><td>3%</td><td>33.3</td><td>11.5% -- one fight in nine</td></tr>
	 *   <tr><td>6%</td><td>16.7</td><td>21.9%</td></tr>
	 *   <tr><td><b>10%</b></td><td><b>10.0</b></td><td><b>34.4% -- about one fight in three</b></td></tr>
	 *   <tr><td>15%</td><td>6.7</td><td>47.8% -- every other fight</td></tr>
	 * </table>
	 *
	 * <p>10% puts an incident in roughly one fight in three: frequent enough that it
	 * is unambiguously working and has to be planned around (a spare sword on the
	 * hotbar becomes a real decision), and rare enough that two fights in three are
	 * still ordinary. 15% was rejected as making combat mostly about item recovery.
	 *
	 * <p><b>The counterplay is total, which is what licenses the high end of the
	 * range:</b> nothing is destroyed. The item is three or four blocks away and can
	 * be picked straight back up, so the cost is tempo and position in a fight rather
	 * than the item.
	 *
	 * <p>One constant. If it reads as too much in play, 6% is the next stop down.
	 */
	public static final float CHANCE = 0.10f;
}
