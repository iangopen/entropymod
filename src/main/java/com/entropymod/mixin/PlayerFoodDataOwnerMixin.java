package com.entropymod.mixin;

import com.entropymod.entropy.FoodDataOwner;
import com.mojang.authlib.GameProfile;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stamps the owner onto a player's {@code FoodData}, for Glutton.
 *
 * <p><b>The constructor, not {@code getFoodData()}.</b> {@code Player} has exactly
 * one constructor -- {@code (Level, GameProfile)} -- and it builds its
 * {@code FoodData} at offset 20, so a TAIL inject is unambiguous and guarantees the
 * reference is present before any {@code add} can possibly run. Injecting into
 * {@code getFoodData()} would also have worked (all three restoration sources reach
 * {@code FoodData.add} through it), but it would pay a field write on every call to
 * a getter for a guarantee the constructor gives once.
 *
 * <p>It re-stamps on every construction, which matters: {@code PlayerList.respawn}
 * builds a <b>brand-new {@code ServerPlayer}</b> after a death, with a new
 * {@code FoodData}. Nothing here has to notice that, because the new object's
 * constructor does the work -- the same reason the attribute effects rebuild rather
 * than persist.
 */
@Mixin(Player.class)
public abstract class PlayerFoodDataOwnerMixin {

	@Inject(method = "<init>", at = @At("TAIL"))
	private void entropymod$stampFoodDataOwner(Level level, GameProfile profile, CallbackInfo ci) {
		Player player = (Player) (Object) this;
		FoodData food = player.getFoodData();
		if (food instanceof FoodDataOwner owner) {
			owner.entropymod$setOwner(player);
		}
	}
}
