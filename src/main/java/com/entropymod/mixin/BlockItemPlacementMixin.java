package com.entropymod.mixin;

import com.entropymod.entropy.EffectHooks;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Reverse Midas: replaces the state a placement is about to write with a gold
 * block.
 *
 * <p><b>The target is the call site inside {@code place}, not
 * {@code getPlacementState}</b>, and that is the whole of the mixin's design.
 * Two vanilla subclasses override {@code getPlacementState}, and
 * {@code StandingAndWallBlockItem}'s override never calls super (javap: zero
 * {@code invokespecial} to it) -- that class covers torches, signs, banners and
 * every wall-mountable block, so injecting there would build green and silently
 * exempt a large slice of the game. CLAUDE.md failure mode 1, and it would have
 * looked like a working effect.
 *
 * <p>{@code BlockItem.place(BlockPlaceContext)} is overridden by <b>nobody</b> and
 * calls {@code placeBlock} exactly once (offset 63), with whatever state the
 * possibly-overridden {@code getPlacementState} produced. Modifying that argument
 * therefore catches every placement regardless of which subclass computed it --
 * and because the modification lands <i>before</i> {@code setBlock}, the original
 * block's block entity is never created, so there is nothing in the world to
 * destroy afterwards.
 *
 * <p>Everything downstream stays coherent for free. Vanilla re-reads the world at
 * offset 101 and branches on {@code worldState.is(placementState.getBlock())};
 * that now compares gold against gold, so the branch is taken and its calls
 * simply find no block entity to write to. The placement <b>sound and the
 * {@code BLOCK_PLACE} game event both read the re-read world state</b> (local 8,
 * offsets 197 and 248), so a gold block sounds like a gold block with no code of
 * ours.
 *
 * <p><b>Nothing here decides anything.</b> The one-line shape every mixin in this
 * project keeps: all of the gating -- server side, non-null player, the acquired
 * set, and the stored-contents carve-out -- lives in
 * {@link EffectHooks#reverseMidasPlacement}.
 */
@Mixin(BlockItem.class)
public abstract class BlockItemPlacementMixin {

	@ModifyArg(
			method = "place",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/item/BlockItem;placeBlock("
							+ "Lnet/minecraft/world/item/context/BlockPlaceContext;"
							+ "Lnet/minecraft/world/level/block/state/BlockState;)Z"),
			index = 1)
	private BlockState entropymod$placeGoldInstead(BlockPlaceContext context, BlockState state) {
		return EffectHooks.reverseMidasPlacement(context, state);
	}
}
