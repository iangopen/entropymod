package com.entropymod.client.mixin;

import com.entropymod.client.ClientRunState;
import com.entropymod.entropy.behavior.SlickBehavior;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * The client half of Slick. See {@code LivingEntityFrictionMixin} for the mechanism.
 *
 * <p><b>This half is load-bearing, not a prediction fix.</b> Unlike Slippery Grip --
 * whose compensator is a {@code MOVEMENT_SPEED} modifier and therefore reaches the
 * client through {@code ClientboundUpdateAttributesPacket} anyway -- <b>friction is
 * not an attribute and there is nothing for the server to sync.</b> The client
 * simulates the player's own movement and sends the resulting position, so without
 * this the player would slide on the server's copy and walk normally on their own
 * screen, and the disagreement would surface as rubber-banding rather than as ice.
 * The same reasoning as Slippery Grip's two sprint-jump halves.
 *
 * <p>Reads {@link ClientRunState} rather than {@code EffectHooks}, which answers
 * "no effect" client-side by design, and computes the identical value through the
 * same {@link SlickBehavior#frictionFor} so the two sides cannot drift.
 *
 * <p>Scoped to {@code LocalPlayer} specifically: other players' entities have their
 * positions sent by the server and are never simulated locally, so applying ice
 * friction to them would fight the interpolation rather than match it.
 *
 * <p>{@code @ModifyVariable} handlers chain, so this and the common half coexist on
 * one target without the {@code @Redirect} conflict -- and since the side scoping
 * means exactly one ever acts, the chaining order is irrelevant.
 */
@Mixin(LivingEntity.class)
public abstract class ClientFrictionMixin {

	@ModifyVariable(method = "travelInAir", at = @At("STORE"), index = 3)
	private float entropymod$slickClient(float friction) {
		if (!((Object) this instanceof LocalPlayer player)) {
			return friction;
		}
		return SlickBehavior.frictionFor(friction, player.onGround(),
				ClientRunState.hasSlick());
	}
}
