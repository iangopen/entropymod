package com.entropymod.mixin;

import com.entropymod.entropy.EffectHooks;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Vampire Rules: reads the damage a player actually landed on something else.
 *
 * <h2>This is the opposite DIRECTION from every other damage mixin here</h2>
 *
 * <p>{@code LivingEntityDamageMixin} and {@code ServerPlayerDamageMixin} both hook
 * {@code hurtServer} with the <em>player as victim</em>. This hooks the same class
 * with the player as <em>attacker</em>, and the target is chosen for one reason: it is
 * the only place the final number exists.
 *
 * <table border="1">
 *   <caption>candidate hooks and what their float actually is</caption>
 *   <tr><th>site</th><th>the value</th></tr>
 *   <tr><td>{@code Player.attack} -> {@code hurtOrSimulate} arg 1</td>
 *       <td>damage <b>sent</b> -- before the victim's armour, enchantments,
 *           resistance and absorption</td></tr>
 *   <tr><td>{@code LivingEntity.actuallyHurt} parameter</td>
 *       <td><b>still raw</b> -- javap shows {@code actuallyHurt} calls
 *           {@code getDamageAfterArmorAbsorb} and {@code getDamageAfterMagicAbsorb}
 *           itself, so mitigation happens after entry</td></tr>
 *   <tr><td><b>{@code CombatTracker.recordDamage} arg 1</b></td>
 *       <td><b>the health about to be lost</b> -- every reduction applied, the
 *           {@code f <= 0} early return already taken</td></tr>
 * </table>
 *
 * <p>Inside {@code actuallyHurt} the sequence is {@code recordDamage(source, f)} then
 * {@code setHealth(getHealth() - f)}, so arg 1 is exactly what
 * {@code setHealth} is about to subtract.
 *
 * <p><b>This is a read-only {@code @ModifyArg}</b> -- it returns its argument
 * untouched and exists only because an {@code @Inject} at the same instruction cannot
 * see the float without local capture, which is the more brittle of the two. Nothing
 * else in the project targets {@code recordDamage}, so there is no chaining question.
 *
 * <p>{@code (LivingEntity) (Object) this} is the <b>victim</b>; the attacker comes
 * from the damage source. The attacker/victim identity check and the acquired-set
 * lookup both live in {@link EffectHooks}, keeping this to the one-line shape.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityDamageDealtMixin {

	@ModifyArg(
			method = "actuallyHurt",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/damagesource/CombatTracker;recordDamage"
							+ "(Lnet/minecraft/world/damagesource/DamageSource;F)V"),
			index = 1)
	private float entropymod$vampireRules(DamageSource source, float damage) {
		EffectHooks.vampireDrain(source.getEntity(), (LivingEntity) (Object) this, damage);
		return damage;
	}
}
