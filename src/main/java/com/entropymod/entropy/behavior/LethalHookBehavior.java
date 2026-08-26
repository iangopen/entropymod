package com.entropymod.entropy.behavior;

import com.entropymod.entropy.AttributeEffectBehavior;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.List;

/**
 * Lethal Hook (GOOD / COMBAT, entropy 25-50) -- you reach further, and everything
 * you hit in melee is dragged toward you instead of away.
 *
 * <h2>An outgoing melee hit applies knockback TWICE, and both had to be reversed</h2>
 *
 * <p>Iron Will established that {@code LivingEntity.knockback(double, double,
 * double)} takes no {@code DamageSource}, so the discrimination has to happen at a
 * call site. This effect needs the same treatment from the other direction, and
 * there turn out to be two sites, not one:
 *
 * <table border="1">
 *   <caption>knockback on a landed player melee</caption>
 *   <tr><th>site</th><th>what it is</th><th>hook</th></tr>
 *   <tr><td>{@code LivingEntity.hurtServer} -&gt; {@code knockback(DDD)}</td>
 *       <td>the base hit's shove</td>
 *       <td><b>{@code LivingEntityKnockbackMixin}</b> -- Iron Will's existing
 *           redirect, extended rather than duplicated</td></tr>
 *   <tr><td>{@code Player.causeExtraKnockback} -&gt; {@code knockback(DDD)}</td>
 *       <td>the sprint-hit bonus and the Knockback enchantment</td>
 *       <td>{@code PlayerExtraKnockbackMixin}</td></tr>
 * </table>
 *
 * <p><b>Reversing only the first would have been worse than reversing neither.</b>
 * A sprinting attack would then pull with one call and shove with the other, and
 * which one won would depend on the relative magnitudes -- so the effect would
 * appear to work while walking and break the moment the player sprinted, which is
 * exactly how Slippery Grip's sprint-jump bypass presented.
 *
 * <p><b>A second {@code @Redirect} on Iron Will's instruction would be an apply-time
 * conflict</b>, which is why the base-hit half lives inside that mixin's existing
 * handler instead of in a new one. Two effects sharing one call site is the
 * Slippery Grip shape rule; here the resolution is one handler, not two scoped ones,
 * because both decisions are made from the same {@code DamageSource}.
 *
 * <h2>The reversal is a sign flip on the direction, not on the strength</h2>
 *
 * <p>{@code knockback(strength, x, z)}, javap-verified:
 *
 * <pre>
 *   strength *= 1.0 - getAttributeValue(KNOCKBACK_RESISTANCE);
 *   if (strength &lt;= 0.0) return;
 *   Vec3 push = new Vec3(x, 0.0, z).normalize().scale(strength);
 *   setDeltaMovement(delta.x / 2.0 - push.x,
 *                    onGround() ? Math.min(0.4, delta.y / 2.0 + strength) : delta.y,
 *                    delta.z / 2.0 - push.z);
 * </pre>
 *
 * <p>The caller in {@code hurtServer} passes {@code x = attacker.getX() - victim.getX()}
 * and the matching {@code z}, and the body <b>subtracts</b> the normalised vector --
 * which is what pushes the victim away. <b>Negating {@code x} and {@code z} therefore
 * inverts the horizontal component into a pull, and nothing else changes.</b>
 *
 * <p>Two properties fall out of that and are the reason the sign flip is preferable
 * to negating {@code strength}:
 *
 * <ul>
 *   <li><b>{@code KNOCKBACK_RESISTANCE} still applies.</b> It is folded into
 *       {@code strength} before the direction is used, so an armoured target or an
 *       iron golem resists the pull exactly as it resists a shove. Negating
 *       {@code strength} would have hit the {@code strength &lt;= 0.0} early return
 *       and cancelled the knockback outright instead of reversing it.</li>
 *   <li><b>The small upward pop survives.</b> The vertical term reads
 *       {@code strength}, not the direction, so it is sign-independent -- the target
 *       is lifted slightly as it comes in, which is what makes the pull read as a
 *       yank rather than a teleport.</li>
 * </ul>
 *
 * <h2>Melee only</h2>
 *
 * <p>Gated on the damage source <b>not</b> being in {@code #minecraft:is_projectile},
 * so an arrow or a trident still knocks its target back normally. That is the spec
 * ("the player's own outgoing melee attacks") and it also keeps the effect from
 * silently becoming a grappling hook at range. Explosions never reach either site --
 * {@code #no_knockback} contains {@code explosion} and {@code player_explosion}, and
 * explosion shove is applied by the explosion itself.
 *
 * <h2>The buff: +1.5 entity interaction range</h2>
 *
 * <p>The brainstorm did not specify one, so the reasoning is recorded rather than
 * the number alone. <b>Reach is the stat that composes with the mechanic instead of
 * merely sitting alongside it</b>: an effect whose content is "close the distance for
 * you" wants the player able to <em>open</em> at a distance, and a hook that reaches
 * further is the same object as a hook that pulls. A flat damage or health bonus
 * would have been a second, unrelated effect stapled on.
 *
 * <p>{@code ENTITY_INTERACTION_RANGE} is a real registered {@code RangedAttribute}
 * (default <b>3.0</b>, min 0.0, max 64.0) and its only consumer is
 * {@code Player.entityInteractionRange()}, so this changes attack reach and nothing
 * else -- notably <b>not</b> block reach, which is the separate
 * {@code BLOCK_INTERACTION_RANGE}. It is registered {@code setSyncable(true)},
 * verified in {@code Attributes}' class initialiser at offset 283, so the client's
 * own target raycast extends too and there is no server-corrects-the-client gap.
 *
 * <p><b>3.0 -&gt; 4.5 is +50%, and it is modest in the sense that matters:</b> it is
 * half of Giant Size's +6.5, it leaves the value an order of magnitude below the
 * 64.0 ceiling, and one and a half blocks is roughly the gap a player already closes
 * with a single step. {@code ADD_VALUE} rather than a multiplier, so it reads as one
 * fixed extra reach regardless of what else is held.
 */
public final class LethalHookBehavior extends AttributeEffectBehavior {

	public static final String ID = "lethal_hook";

	/** +1.5 blocks of attack reach: 3.0 -&gt; 4.5, well inside the 64.0 cap. */
	public static final double REACH_BONUS = 1.5;

	public LethalHookBehavior() {
		super(List.of(
				new Change(Attributes.ENTITY_INTERACTION_RANGE, REACH_BONUS,
						AttributeModifier.Operation.ADD_VALUE)));
	}
}
