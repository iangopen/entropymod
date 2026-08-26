package com.entropymod.mixin;

import com.entropymod.entropy.EffectHooks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Reverses the sprint-hit and Knockback-enchantment bonus, for Lethal Hook.
 *
 * <h2>Why this exists ALONGSIDE the redirect in {@code LivingEntityKnockbackMixin}</h2>
 *
 * <p>A landed player melee applies knockback at <b>two</b> independent call sites:
 * the base shove inside {@code LivingEntity.hurtServer}, and this one --
 * {@code Player.causeExtraKnockback}, which vanilla calls from {@code Player.attack}
 * at offset 276 with {@code getKnockback(target, source) + (sprinting ? 0.5f : 0f)}.
 *
 * <p><b>Reversing only the base one would have been worse than reversing neither.</b>
 * A sprinting attack would pull with one call and shove with the other, and which
 * won would depend on their relative magnitudes -- so the effect would look correct
 * while walking and invert the moment the player sprinted. That is the shape of
 * Slippery Grip's sprint-jump bypass, and the fix is the same one: scale (here,
 * flip) <em>every</em> branch that grants the same thing.
 *
 * <p>These are two different instructions in two different methods, so unlike the
 * base site there is no redirect-conflict question -- {@code @Redirect} is available
 * and is the right shape, because the handler has to substitute a different
 * <em>argument</em>, not merely observe the call.
 *
 * <h2>The flip is on the direction, not the strength</h2>
 *
 * <p>{@code knockback(strength, x, z)} normalises {@code (x, 0, z)}, scales it by
 * the resistance-adjusted strength and <b>subtracts</b> it. Negating x and z
 * inverts the horizontal component into a pull while leaving
 * {@code KNOCKBACK_RESISTANCE} and the sign-independent vertical pop intact.
 * Negating {@code strength} instead would trip vanilla's {@code strength <= 0.0}
 * early return and cancel the knockback rather than reverse it.
 *
 * <p><b>No damage source is in scope here and none is needed.</b>
 * {@code causeExtraKnockback} is reached only from {@code Player.attack}, which is
 * melee by definition -- a bow or trident never touches it -- so the melee-only
 * rule the other site enforces through {@code #is_projectile} holds here by
 * construction.
 *
 * <p>{@code Player} overrides {@code LivingEntity.causeExtraKnockback} and is the
 * outermost override ({@code Avatar}, {@code ServerPlayer} and {@code LocalPlayer}
 * declare none), and the enclosing method contains exactly one
 * {@code knockback(DDD)} call.
 */
@Mixin(Player.class)
public abstract class PlayerExtraKnockbackMixin {

	@Redirect(
			method = "causeExtraKnockback",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;knockback(DDD)V"))
	private void entropymod$lethalHookExtraKnockback(LivingEntity victim, double strength,
													double x, double z,
													Entity target, float knockback, Vec3 preAttackDelta) {
		if (EffectHooks.reversesMeleeKnockback((Player) (Object) this)) {
			victim.knockback(strength, -x, -z);
			return;
		}
		victim.knockback(strength, x, z);
	}
}
