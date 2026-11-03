package com.entropymod.mixin;

import com.entropymod.entropy.EffectHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Makes the holder's beds explode, for Nightmares.
 *
 * <p>Vanilla's "beds explode outside the Overworld" is a data-driven environment
 * attribute in this version, and {@code BedBlock.useWithoutItem} consults it
 * through exactly one call -- {@code BedRule.explodes()} at offset 73, whose
 * {@code ifeq} guards the entire explosion branch. <b>Redirecting that one boolean
 * reuses the whole branch verbatim</b>: both bed halves removed, vanilla's
 * {@code badRespawnPointExplosion} damage source and its death message, power 5.0,
 * fire on, {@code ExplosionInteraction.BLOCK}. This mod makes no {@code explode}
 * call of its own for this effect.
 *
 * <p><b>ORed with the original, never replacing it.</b> The Nether and the End keep
 * exploding for everyone including their error message, and a datapack that gives a
 * custom dimension an exploding {@code BedRule} is honoured -- the effect only adds
 * the outcome. Since every non-Overworld dimension already explodes, "explodes in
 * the Overworld for the holder" and "always explodes for the holder" are the same
 * rule, and the second cannot go stale when a dimension is added.
 *
 * <p><b>Scoped to the player who clicked the bed, not to the world.</b> The
 * {@code Player} is parameter 4 of the enclosing method, and a redirect handler may
 * capture the enclosing parameters after its own -- so a second player on the same
 * world sleeps in the same bed normally.
 *
 * <p><b>No side scoping is needed.</b> {@code useWithoutItem} opens with
 * {@code if (level.isClientSide) return SUCCESS_SERVER} at offsets 0-10, so the
 * redirected call is unreachable on the client.
 *
 * <p><b>{@code BedBlock} has no subclasses</b> -- the sixteen colours are sixteen
 * instances carrying a {@code DyeColor} field -- so the subclass-override trap does
 * not apply, and {@code useWithoutItem} contains exactly one call to the target.
 */
@Mixin(BedBlock.class)
public abstract class BedExplosionMixin {

	@Redirect(
			method = "useWithoutItem",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/attribute/BedRule;explodes()Z"))
	private boolean entropymod$nightmares(BedRule rule, BlockState state, Level level,
										  BlockPos pos, Player player, BlockHitResult hit) {
		return rule.explodes() || EffectHooks.bedsExplode(player);
	}
}
