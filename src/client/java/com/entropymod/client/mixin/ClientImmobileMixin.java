package com.entropymod.client.mixin;

import com.entropymod.client.ClientRunState;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Earthquake's movement paralysis -- the CLIENT half, and <b>the half that actually
 * stops the player.</b>
 *
 * <p>See {@code PlayerImmobileMixin} for the mechanism, the javap-verified
 * {@code aiStep} offsets, and why no Slowness value can do this. The short version:
 * {@code isImmobile()} makes vanilla zero {@code jumping} / {@code xxa} / {@code zza}
 * immediately after the input is read and before movement consumes it, and those three
 * fields only exist meaningfully on the side that simulates the movement.
 *
 * <p><b>For a player that side is the client.</b> {@code ServerPlayer} never writes any
 * of them; positions arrive from the client as movement packets. So the server half is
 * bookkeeping and this one is the lock.
 *
 * <p>Scoped to {@code LocalPlayer} -- same shape as {@code ClientSprintMixin} beside
 * {@code LivingEntitySprintMixin} -- so exactly one of the pair ever acts on a given
 * entity. Other players' {@code RemotePlayer} entities are not locked, and should not
 * be: their movement is replicated from their own client, which is applying its own
 * lock if it has one.
 *
 * <p>The source is {@code ClientRunState}'s tremor countdown rather than
 * {@code EffectHooks}, which answers "no effect" client-side by design and cannot see
 * the acquired set. The countdown is fed by {@code TremorLockPayload} and expires by
 * itself, so a dropped packet costs at most one tremor rather than locking the player
 * permanently.
 *
 * <p>Not cancellable in the harmful direction: it only ever turns {@code false} into
 * {@code true}, so sleeping and dying still immobilise on their own terms.
 */
@Mixin(Player.class)
public abstract class ClientImmobileMixin {

	@Inject(method = "isImmobile", at = @At("RETURN"), cancellable = true)
	private void entropymod$clientTremorParalysis(CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) {
			return;
		}
		if (!((Object) this instanceof LocalPlayer)) {
			return;
		}
		if (ClientRunState.isTremorLocked()) {
			cir.setReturnValue(true);
		}
	}
}
