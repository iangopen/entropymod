package com.entropymod.mixin;

import com.entropymod.entropy.EffectHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Earthquake's movement paralysis -- the SERVER half.
 *
 * <h2>{@code isImmobile()} is vanilla's own movement lock, and it is MOVEMENT-only</h2>
 *
 * <p>Inside {@code LivingEntity.aiStep}, javap-verified by offset:
 *
 * <pre>
 * 218:  applyInput()              // writes xxa / zza / jumping from the input source
 * 222:  if (isImmobile()) {
 * 230:      this.jumping = false;
 * 235:      this.xxa = 0.0f;      // strafe input
 * 240:      this.zza = 0.0f;      // forward input
 *       }
 * 460:  jumpFromGround()          // gated on this.jumping
 * 516:  new Vec3(xxa, yya, zza) -> travel()
 * </pre>
 *
 * <p><b>Three fields, and nothing else.</b> The ordering is what makes it usable: the
 * write lands after {@code applyInput} has read the input source and before either
 * consumer. Attacking, mining and item use never read {@code xxa}, {@code zza} or
 * {@code jumping} -- they run off {@code Minecraft.handleKeybinds} and
 * {@code MultiPlayerGameMode}, on a path {@code aiStep}'s movement block never touches.
 * That is why sleeping, this method's only vanilla player use, leaves them working.
 *
 * <h2>Why an attribute could not do this</h2>
 *
 * <p>Slowness cannot express paralysis at ANY level, and the reason is not the clamp --
 * {@code MOVEMENT_SPEED}'s minimum is a literal {@code 0.0} and amplifier 6 reaches it.
 * A player at zero {@code MOVEMENT_SPEED} is still mobile by three routes that read no
 * attribute at all: {@code getFrictionInfluencedSpeed}'s airborne branch returns
 * {@code getFlyingSpeed()} (a hardcoded {@code 0.02}/{@code 0.026}),
 * {@code jumpFromGround} adds a flat {@code +0.2} forward on a sprint jump, and
 * {@code JUMP_STRENGTH} is a separate attribute entirely. Zeroing the input closes all
 * three at once: {@code moveRelative} multiplies a zero vector, so the airborne branch
 * has nothing to accelerate, and {@code jumping = false} means the impulse is never
 * reached. See {@code EarthquakeBehavior.PARALYSIS_TICKS}.
 *
 * <h2>Target chain: checked, no subclass trap</h2>
 *
 * <p>{@code Player.isImmobile()} is the OUTERMOST override --
 * {@code LivingEntity} -> {@code Avatar} -> {@code Player}, and neither
 * {@code ServerPlayer} nor {@code LocalPlayer} overrides it (javap: no match in either).
 * So this one target covers every player on both sides.
 *
 * <h2>Side scoping, and why this half alone is not enough</h2>
 *
 * <p>Scoped to {@code ServerLevel} per the project's common/client rule. <b>This half
 * does not actually stop anybody</b>: {@code ServerPlayer} never writes
 * {@code jumping} / {@code xxa} / {@code zza} (zero {@code putfield}s), because a
 * player's position arrives as movement packets rather than being integrated
 * server-side. It is here so the server's own copy of the player agrees with the
 * client's, and so anything server-side that reads those fields sees the lock.
 * {@code ClientImmobileMixin} is the half that does the work.
 *
 * <p>Splitting them is safe in a way Slippery Grip's pair were not: both only ever
 * answer <em>true</em> and neither removes anything, so no ordering question exists even
 * if both ran on one entity. The scoping is discipline, not a fix.
 *
 * <p>{@code @Inject} at RETURN rather than {@code @Overwrite}: it can only ever turn a
 * {@code false} into {@code true}, so sleeping and dying still immobilise on their own
 * terms and this composes with them instead of replacing them.
 */
@Mixin(Player.class)
public abstract class PlayerImmobileMixin {

	@Inject(method = "isImmobile", at = @At("RETURN"), cancellable = true)
	private void entropymod$tremorParalysis(CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) {
			return;
		}
		Player self = (Player) (Object) this;
		if (!(self.level() instanceof ServerLevel)) {
			return;
		}
		if (EffectHooks.isMovementLocked(self)) {
			cir.setReturnValue(true);
		}
	}
}
