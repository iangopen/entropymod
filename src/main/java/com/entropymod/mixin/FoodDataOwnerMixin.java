package com.entropymod.mixin;

import com.entropymod.entropy.FoodDataOwner;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Adds an owner reference to {@code FoodData}, so Glutton can tell whose hunger bar
 * it is looking at. See {@link FoodDataOwner} for why this is necessary.
 *
 * <p>This mixin adds state and no behaviour: it injects nothing, redirects nothing
 * and changes no control flow, which puts it in the same low risk class as
 * {@code LlamaStrengthAccessor}. The one thing it can get wrong is the target class,
 * and {@code FoodData} is final-shaped in practice -- nothing in the jar extends it.
 *
 * <p>The field is deliberately <b>not</b> serialised. {@code FoodData} has its own
 * {@code readAdditionalSaveData} / {@code addAdditionalSaveData} pair and this rides
 * neither: the owner is re-stamped by the constructor every time a {@code Player} is
 * built, including the brand-new {@code ServerPlayer} a death respawn produces. That
 * is the same "derived state, rebuilt rather than stored" discipline the attribute
 * effects follow, and it means a stale reference to a removed player cannot outlive
 * the object that holds it.
 */
@Mixin(FoodData.class)
public abstract class FoodDataOwnerMixin implements FoodDataOwner {

	@Unique
	private Player entropymod$owner;

	@Override
	public Player entropymod$owner() {
		return this.entropymod$owner;
	}

	@Override
	public void entropymod$setOwner(Player player) {
		this.entropymod$owner = player;
	}
}
