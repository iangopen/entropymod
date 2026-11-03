package com.entropymod.mixin;

import com.entropymod.entropy.EffectHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Removes sneak edge-stop, for Loose Footing. Server half;
 * {@code ClientEdgeSafetyMixin} is the other one.
 *
 * <p>{@code Player.maybeBackOffFromEdge(Vec3, MoverType)} is the whole of vanilla's
 * crouch-edge safety, and it is a pure function of its argument: it clamps the x
 * and z of the movement vector toward zero while {@code canFallAtLeast} reports a
 * drop, and touches no field and no flag. So returning the argument unchanged is
 * literally the {@code return vec} at offsets 53-54 that vanilla itself takes for a
 * player who is not sneaking -- there is nothing partially applied to unwind and
 * nothing else about sneaking to disturb.
 *
 * <p><b>{@code Player} is the outermost override</b>, checked rather than assumed:
 * {@code Avatar}, {@code ServerPlayer}, {@code AbstractClientPlayer} and
 * {@code LocalPlayer} declare none.
 *
 * <p><b>The server genuinely runs this.</b> Vanilla's guard accepts
 * {@code MoverType.PLAYER} as well as {@code SELF}, and {@code PLAYER} is what
 * {@code ServerGamePacketListenerImpl.handleMovePlayer} moves a {@code ServerPlayer}
 * with -- so a client-only cancel would be clamped back by the server on the next
 * position update, and the player would rubber-band on the lip of every ledge.
 *
 * <p>Injecting at HEAD and cancelling, which chains safely with the client half
 * because <b>both halves only ever cancel</b>: neither adds nor removes state, so
 * unlike Slippery Grip's original pair there is no ordering question at all. The
 * side scoping below is discipline rather than a fix.
 */
@Mixin(Player.class)
public abstract class PlayerEdgeSafetyMixin {

	@Inject(method = "maybeBackOffFromEdge", at = @At("HEAD"), cancellable = true)
	private void entropymod$looseFooting(Vec3 movement, MoverType type,
										 CallbackInfoReturnable<Vec3> cir) {
		Player player = (Player) (Object) this;
		if (player.level() instanceof ServerLevel && EffectHooks.ignoresEdgeSafety(player)) {
			cir.setReturnValue(movement);
		}
	}
}
