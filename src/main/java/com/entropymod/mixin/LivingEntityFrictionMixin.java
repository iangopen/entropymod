package com.entropymod.mixin;

import com.entropymod.entropy.EffectHooks;
import com.entropymod.entropy.behavior.SlickBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Forces ice's friction onto whatever this player is standing on, for Slick.
 * Server half; {@code ClientFrictionMixin} is the other one.
 *
 * <h2>Why the LOCAL and not either consumer</h2>
 *
 * <p>{@code LivingEntity.travelInAir(Vec3)} computes the ground friction once into
 * slot 3 and then reads it <b>twice</b> -- as {@code f * 0.91f}, the per-tick
 * horizontal decay, and as the argument to
 * {@code handleRelativeFrictionAndCalculateMovement}, which becomes the
 * acceleration via {@code getFrictionInfluencedSpeed(f) = getSpeed() * (0.216/f^3)}.
 * Ice is slippery because of both, so an {@code @ModifyArg} on the second call --
 * the obvious, tidier target -- would have changed how fast the player gets going
 * and left how fast they stop at vanilla's 0.546. Overwriting the local before
 * either read is what makes the two agree by construction.
 *
 * <p>{@code @ModifyVariable} was chosen over {@code @Redirect} on
 * {@code Block.getFriction()} for a second reason: <b>the two sides share this
 * target</b>, and two {@code @Redirect}s on one instruction is an apply-time
 * conflict (the trap Slippery Grip's sprint-jump pair hit). {@code @ModifyVariable}
 * handlers chain, and each side returns the value untouched when it is not the
 * authority, so ordering cannot matter.
 *
 * <p>The store is unambiguous: {@code travelInAir} is private, has one overload,
 * and contains exactly one {@code fstore_3} (offset 30).
 *
 * <h2>Scoped to the server, and to standing on the ground</h2>
 *
 * <p>{@code player.level() instanceof ServerLevel} keeps this half off the client,
 * where {@code EffectHooks} answers "no effect" by design and would hand back
 * vanilla friction. The {@code onGround()} gate mirrors vanilla's own branch:
 * airborne, vanilla substitutes a literal {@code 1.0f} which is still fed to the
 * {@code f * 0.91f} decay, so overwriting it with 0.98 would add air drag this
 * effect does not claim.
 *
 * <p>{@code hurtServer}-style breadth applies here too -- {@code travelInAir} runs
 * for every living entity in the world -- so the {@code instanceof Player} check is
 * load-bearing, not defensive.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityFrictionMixin {

	@ModifyVariable(method = "travelInAir", at = @At("STORE"), index = 3)
	private float entropymod$slick(float friction) {
		if (!((Object) this instanceof Player player) || !(player.level() instanceof ServerLevel)) {
			return friction;
		}
		return SlickBehavior.frictionFor(friction, player.onGround(),
				EffectHooks.slidesOnEveryBlock(player));
	}
}
