package com.entropymod.entropy.behavior;

import com.entropymod.entropy.AttributeEffectBehavior;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.List;

/**
 * Human Machinegun (BAD / COMBAT, entropy 25-50) -- swing 90% faster, hit for a
 * quarter as much.
 *
 * <h2>The operation choice is about STACKING, not about a single modifier</h2>
 *
 * <p>The intuitive story -- "{@code ADD_MULTIPLIED_BASE} works off the raw base, so
 * on a weapon it would barely scratch the total" -- is <b>wrong</b>, and
 * {@code AttributeInstance.calculateValue}'s bytecode says so:
 *
 * <pre>
 *   double d = getBaseValue();
 *   for (m : ADD_VALUE)             d += m.amount();       // the weapon lands here
 *   double e = d;
 *   for (m : ADD_MULTIPLIED_BASE)   e += d * m.amount();   // <-- d, NOT getBaseValue()
 *   for (m : ADD_MULTIPLIED_TOTAL)  e *= 1.0 + m.amount();
 * </pre>
 *
 * <p><b>The "base" that {@code ADD_MULTIPLIED_BASE} multiplies is base + the
 * {@code ADD_VALUE} pass -- the weapon is already included.</b> So held alone the
 * two operations are numerically identical, and both would deliver exactly 25%.
 * (This also means Glass Cannon Pact's +0.50 {@code ADD_MULTIPLIED_BASE} is +50% of
 * the weapon-inclusive total, not a flat +0.5.)
 *
 * <p>What separates them is what happens when two effects write the same attribute,
 * and the argument is the one Embrace the Moon records for {@code GRAVITY}:
 *
 * <ul>
 *   <li>{@code ADD_MULTIPLIED_BASE} accumulates <b>additively</b> against a fixed
 *       {@code d}. Two -0.75 modifiers give {@code d + d(-0.75) + d(-0.75) = -0.5d}
 *       -- <b>negative damage, clamped to 0.0</b>, a player who can never hurt
 *       anything again.</li>
 *   <li>{@code ADD_MULTIPLIED_TOTAL} composes as a <b>product</b>:
 *       {@code 0.25 x 0.25 = 0.0625}, and for any amount above -1 it can never
 *       reach zero however many stack.</li>
 * </ul>
 *
 * <p>{@code ATTACK_DAMAGE} floors at <b>0.0</b> -- unlike {@code MAX_HEALTH}, whose
 * 1.0 floor makes over-stacking bottom out harmlessly -- and anti-stacking is a
 * <b>soft</b> rule dropped first when the pool empties, so a second damage-scaling
 * COMBAT effect is a genuinely reachable pairing rather than a hypothetical. Both
 * numbers are asserted by the harness so the reasoning cannot be lost to a later
 * "these two operations are the same, simplify it" pass.
 *
 * <h2>The derived values, not the input percentages</h2>
 *
 * <p>Vanilla bases: {@code ATTACK_SPEED} 4.0, {@code ATTACK_DAMAGE} 1.0. A weapon
 * adds {@code ADD_VALUE} to both -- an iron sword is {@code +5.0} damage and
 * {@code -2.4} speed, netting 6.0 and 1.6.
 *
 * <table border="1">
 *   <caption>exact resulting values ({@code TOTAL} factors 1.90 and 0.25)</caption>
 *   <tr><th></th><th>speed, normal</th><th>speed, cursed</th>
 *       <th>cooldown</th><th>damage, normal</th><th>damage, cursed</th>
 *       <th>DPS, normal</th><th>DPS, cursed</th></tr>
 *   <tr><td>bare hand</td><td>4.00/s</td><td><b>7.60/s</b></td><td>20 -&gt; 2.63 ticks</td>
 *       <td>1.0</td><td><b>0.25</b></td><td>4.00</td><td><b>1.90</b></td></tr>
 *   <tr><td>iron sword</td><td>1.60/s</td><td><b>3.04/s</b></td><td>12.5 -&gt; 6.58 ticks</td>
 *       <td>6.0</td><td><b>1.50</b></td><td>9.60</td><td><b>4.56</b></td></tr>
 *   <tr><td>netherite axe</td><td>1.00/s</td><td><b>1.90/s</b></td><td>20 -&gt; 10.53 ticks</td>
 *       <td>10.0</td><td><b>2.50</b></td><td>10.00</td><td><b>4.75</b></td></tr>
 * </table>
 *
 * <p>Every row lands on exactly the stated percentages -- speed x1.90, damage x0.25
 * -- because {@code TOTAL} is a product and is therefore independent of whatever
 * else is in the pool. <b>Net DPS is x0.475 uniformly</b> ({@code 1.90 * 0.25}), and
 * that number is what settles the phase: an effect that more than halves damage
 * output is a curse, so it is registered <b>BAD</b> despite the attack-speed half
 * reading as a buff.
 *
 * <h2>Both clamps checked, neither reachable</h2>
 *
 * <p>{@code ATTACK_SPEED} floors at <b>0.0</b> and 0 would be unrecoverable (the
 * cooldown could never refill) -- this effect only ever raises it. {@code ATTACK_DAMAGE}
 * floors at <b>0.0</b>, and {@code TOTAL} at any amount greater than -1 can never
 * reach zero however many stack, so the bare-handed 0.25 is a genuine (if derisory)
 * number rather than a clamp. Composed with Weak Grip's {@code -2.0 ADD_VALUE} the
 * bare-handed total is already 0 before this applies, which is that effect's own
 * documented outcome and not a new one.
 *
 * <p>Held with Glass Cannon Pact (+0.50 {@code ADD_MULTIPLIED_BASE}) an iron sword
 * is {@code (6.0 + 0.5) * 0.25 = 1.625} -- the two compose in different passes and
 * neither clobbers the other, since every modifier carries its own {@code Identifier}.
 *
 * <p><b>{@code ATTACK_DAMAGE} is not client-syncable</b>, so the item tooltip will
 * not show the reduction; {@code ATTACK_SPEED} is, so the cooldown sweep on the
 * crosshair does reflect the faster swing. That split is vanilla's and is the same
 * known cosmetic gap Steady Hands and Glass Cannon Pact have.
 */
public final class HumanMachinegunBehavior extends AttributeEffectBehavior {

	public static final String ID = "human_machinegun";

	/** +90% attack speed. {@code ADD_MULTIPLIED_TOTAL}, so the factor is x1.90. */
	public static final double ATTACK_SPEED_BONUS = 0.90;

	/** Damage down to 25% of normal. {@code ADD_MULTIPLIED_TOTAL}, so x0.25. */
	public static final double ATTACK_DAMAGE_PENALTY = -0.75;

	public HumanMachinegunBehavior() {
		super(List.of(
				new Change(Attributes.ATTACK_SPEED, ATTACK_SPEED_BONUS,
						AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
				new Change(Attributes.ATTACK_DAMAGE, ATTACK_DAMAGE_PENALTY,
						AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)));
	}

	/** The real multiplier a {@code ADD_MULTIPLIED_TOTAL} amount produces. */
	public static double factor(double amount) {
		return 1.0 + amount;
	}

	/** Net damage-per-second multiplier: the product of the two factors. */
	public static double dpsFactor() {
		return factor(ATTACK_SPEED_BONUS) * factor(ATTACK_DAMAGE_PENALTY);
	}
}
