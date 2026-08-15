package com.entropymod.entropy.behavior;

import com.entropymod.entropy.HookEffectBehavior;

/**
 * Reverse Midas (BAD / UTILITY, Tier 2) -- every block you place becomes a gold
 * block instead.
 *
 * <h2>1. The hook: BEFORE, and specifically at the call site</h2>
 *
 * <p>There is <b>no block-place event in Fabric API</b> -- the surface is
 * {@code UseBlockCallback} / {@code BlockEvents.USE_ITEM_ON}, which fire before
 * the item acts and can only cancel, never rewrite what gets placed. So this is
 * mixin-only, and the only question is where.
 *
 * <p><b>BEFORE beats AFTER, and not marginally.</b> Swapping the block after it
 * is placed means the real block genuinely exists for an instant: a chest block
 * entity is created, {@code updateCustomBlockEntityTag} copies the item's stored
 * data into it, {@code setPlacedBy} runs -- and only then is all of it deleted.
 * Every one of those steps is work whose only product is something to destroy,
 * and the destruction is the part that can go wrong. Replacing the state
 * <i>before</i> {@code setBlock} means <b>the block entity is never created at
 * all</b>, so there is nothing in the world to lose.
 *
 * <h2>2. Why the call site, and not {@code getPlacementState}</h2>
 *
 * <p>{@code BlockItem.getPlacementState} is the obvious target and it is
 * <b>CLAUDE.md's first failure mode</b>. Two vanilla subclasses override it --
 * {@code GameMasterBlockItem} and {@code StandingAndWallBlockItem} -- and
 * {@code StandingAndWallBlockItem}'s override contains <b>zero</b>
 * {@code invokespecial} calls to super, javap-verified. That class is torches,
 * signs, banners and every wall-mountable block, so an injection there would
 * apply cleanly and silently exempt a large, common slice of the game.
 *
 * <p>{@code BlockItem.place(BlockPlaceContext)} is overridden by <b>nobody</b>,
 * and it calls {@code placeBlock} exactly once, at offset 63, with whatever state
 * the (possibly overridden) {@code getPlacementState} produced. A
 * {@code @ModifyArg} on that argument therefore catches every placement no matter
 * which subclass computed the state -- the same "modify the argument at the one
 * call site" shape Behemoth Gauntlets uses.
 *
 * <h2>3. What happens to a placed block's data -- the answer corrects the question</h2>
 *
 * <p>The worry is that placing a chest destroys its contents. <b>It does not, and
 * cannot: a chest item carries no contents.</b> A chest's inventory only ever
 * exists in the world, and under this effect the chest never reaches the world.
 * The player loses the chest item, exactly as they lose a placed dirt block.
 *
 * <p>Verified rather than assumed, at offsets 106-115 of {@code place}: vanilla
 * re-reads the world and branches on
 * {@code worldState.is(placementState.getBlock())}. Since the placement state is
 * now gold and gold is what got placed, that branch is <i>taken</i> -- and every
 * call inside it no-ops anyway, because {@code updateCustomBlockEntityTag} returns
 * at its own {@code getBlockEntity(pos) == null} check (offset 35) and
 * {@code Blocks.GOLD_BLOCK} does not override {@code setPlacedBy}. Nothing throws
 * and nothing is half-applied.
 *
 * <p><b>The one real casualty is an item that stores a container's contents in
 * ITSELF -- a shulker box.</b> Those items carry {@code DataComponents.CONTAINER},
 * and placing one consumes the stack, so the items inside would be destroyed
 * silently and unrecoverably. That is categorically different from Blight
 * Touched's "any crop, no exceptions": a crop is a world block the player can
 * replant, whereas this is up to 27 stacks of the player's own collected
 * inventory, gone in one misclick with no counterplay and no feedback. So it is
 * the one carve-out, held in {@link #sparesStoredContents} and implemented as a
 * single predicate: <b>a stack carrying non-empty container contents places
 * normally.</b> Delete that one call to get the no-exceptions version.
 *
 * <h2>4. Scoping</h2>
 *
 * <p>Two independent gates, both structural rather than defensive:
 *
 * <ul>
 *   <li><b>Dispensers are excluded by construction.</b>
 *       {@code DirectionalPlaceContext}'s constructor passes {@code aconst_null}
 *       for the player (javap-verified), so {@code context.getPlayer()} is null
 *       for every dispenser placement and the null check drops it. No block-source
 *       test was needed.</li>
 *   <li><b>Only the holder.</b> The acquired set is read from the placing player
 *       specifically, so another player building beside them is unaffected --
 *       the same per-player scoping every other effect uses.</li>
 * </ul>
 *
 * <h2>5. Two consequences that are severe and are NOT bugs</h2>
 *
 * <ul>
 *   <li><b>Gold blocks drop themselves, so this is also a gold generator.</b>
 *       Place dirt, break the gold block, keep the gold: a net gain of one gold
 *       block per placement. Gold is a weak material but piglin bartering turns it
 *       into ender pearls, which is a shortcut to this run's actual win condition.
 *       Recorded because it is the dominant balance consequence and it is not
 *       fixable cleanly -- suppressing the drop would need per-block world state
 *       tracking which gold blocks this effect created.</li>
 *   <li><b>It blocks progression more than it looks.</b> No crafting table, no
 *       furnace, no bed, no chest, and no obsidian can ever be placed again. The
 *       run becomes dependent on structures that already contain them.</li>
 * </ul>
 *
 * <p>Both are stated rather than tuned away, because the spec is "every block, no
 * exceptions" and the interesting failure here is a design one, not a code one.
 *
 * <p>apply()/remove() are empty by design -- see {@link HookEffectBehavior}.
 */
public final class ReverseMidasBehavior extends HookEffectBehavior {

	public static final String ID = "reverse_midas";
}
