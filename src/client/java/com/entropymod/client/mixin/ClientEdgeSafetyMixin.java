package com.entropymod.client.mixin;

import com.entropymod.client.ClientRunState;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The client half of Loose Footing. See {@code PlayerEdgeSafetyMixin} for the
 * mechanism and for why {@code Player} is the correct target class.
 *
 * <p><b>This is the half the player actually feels.</b> Edge-stop clamps the
 * movement vector during {@code Entity.move}, and for a player that simulation runs
 * on the client -- so a server-only cancel would apply cleanly and change nothing
 * the player could see. The server half exists because vanilla's guard also accepts
 * {@code MoverType.PLAYER}, which is how {@code handleMovePlayer} moves a
 * {@code ServerPlayer}: without it the server would clamp the position straight back
 * and the player would rubber-band on every ledge instead of stepping off it.
 *
 * <p>Reads {@link ClientRunState} rather than {@code EffectHooks}, which answers
 * "no effect" client-side by design.
 *
 * <p>Scoped to {@code LocalPlayer}: other players' movement arrives from the server
 * already resolved and is never run through this path locally.
 *
 * <p>Both halves only ever <b>cancel</b> -- neither adds nor removes any state --
 * so unlike Slippery Grip's original pair there is no ordering question between the
 * two configs at all.
 */
@Mixin(Player.class)
public abstract class ClientEdgeSafetyMixin {

	@Inject(method = "maybeBackOffFromEdge", at = @At("HEAD"), cancellable = true)
	private void entropymod$looseFootingClient(Vec3 movement, MoverType type,
											   CallbackInfoReturnable<Vec3> cir) {
		if ((Object) this instanceof LocalPlayer && ClientRunState.hasLooseFooting()) {
			cir.setReturnValue(movement);
		}
	}
}
