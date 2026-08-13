package com.entropymod.mixin;

import com.entropymod.entropy.EffectHooks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Butterfingers Extreme: flings the held item out of the player's hand on a landed
 * attack.
 *
 * <h2>The anchor is the landed hit, not the method</h2>
 *
 * <p>Injecting at {@code TAIL} of {@code Player.attack} would have been wrong twice
 * over. {@code attack} has <b>three</b> {@code RETURN} instructions -- offsets 8
 * ({@code cannotAttack}), 87 (the cooldown/miss path) and 350 -- and Mixin's
 * {@code TAIL} binds only the last of them, so a miss would either fire nothing or,
 * worse, fire depending on which early return happened to be last after a vanilla
 * refactor. The rule the effect actually wants is "you hit something".
 *
 * <p>So this anchors on {@code Entity.hurtOrSimulate}, which javap confirms occurs
 * <b>exactly once</b> inside {@code attack} (offset 242) -- the same single call site
 * {@code PlayerAttackDamageMixin} modifies the damage argument of. {@code SHIFT.AFTER}
 * puts this after the hit has been dealt, so the item leaves the hand as a
 * consequence of the swing connecting.
 *
 * <p><b>Two mixins on one call site is deliberate here and is safe.</b> The shape rule
 * in CLAUDE.md is about two handlers that both <em>write</em> the same value -- two
 * {@code @Redirect}s on one instruction are an apply-time conflict, and a common and a
 * client half both mutating one argument race. This is an {@code @ModifyArg} on the
 * argument and an {@code @Inject} after the call: they touch different things, neither
 * cancels, and the order between them is fixed by the instruction, not by injection
 * priority.
 *
 * <h2>Why the stack has to be cleared before it is dropped</h2>
 *
 * <p>{@code Player.drop} builds and adds an {@code ItemEntity}; it does <b>not</b>
 * remove anything from the inventory. Vanilla's own Q-key path calls
 * {@code Inventory.removeFromSelected} first, so this does the same -- taking a copy,
 * emptying the slot, then dropping the copy. Reversing the order would duplicate the
 * item.
 *
 * <p>{@code drop(stack, true)} is {@code dropAround = false, includeThrowerName =
 * true}: thrown along the look vector at 0.3 blocks/tick, with the thrower recorded.
 * See {@code ButterfingersExtremeBehavior} for the two velocity profiles and why this
 * is the one.
 *
 * <p>No {@code instanceof} guard -- {@code Player.attack} is only called for players,
 * and {@code EffectHooks} answers "no effect" client-side by design, so the roll never
 * happens twice.
 */
@Mixin(Player.class)
public abstract class PlayerButterfingersMixin {

	@Inject(
			method = "attack",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/Entity;hurtOrSimulate"
							+ "(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
					shift = At.Shift.AFTER))
	private void entropymod$butterfingers(Entity target, CallbackInfo ci) {
		Player player = (Player) (Object) this;
		if (!EffectHooks.rollButterfingers(player, player.getRandom())) {
			return;
		}
		// Copy, clear, then drop -- see the class javadoc. The copy is what gets
		// thrown; the live slot is emptied first so nothing is duplicated.
		ItemStack flung = player.getMainHandItem().copy();
		player.getInventory().removeFromSelected(true);
		player.drop(flung, true);
	}
}
