package com.entropymod.entropy.block;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Reverse Midas's two block tables: which placements have a real golden vanilla
 * counterpart, and which placements write a <b>second</b> world position.
 *
 * <p>Both are keyed on the {@code Block}, never on the item, and both were
 * enumerated from the shipped jar rather than from memory -- see the
 * {@code entropy-effects} skill for the javap evidence behind every row.
 *
 * <h2>Precedence</h2>
 *
 * <p>{@code EffectHooks.reverseMidasPlacement} consults these in a fixed order,
 * and the order is load-bearing rather than incidental:
 *
 * <ol>
 *   <li>the stored-contents carve-out (a non-empty shulker box places normally);</li>
 *   <li>{@link #goldenEquivalent} -- a real golden vanilla block, placed with its
 *       own {@code getStateForPlacement}, so it still <i>works</i>;</li>
 *   <li>{@link #placesTwoPositions} -- vanilla places the real block, both halves,
 *       and {@code reverseMidasAfterPlacement} turns both cells to gold;</li>
 *   <li>otherwise, a plain gold block, before {@code setBlock} as always.</li>
 * </ol>
 *
 * <p>Golden-equivalent is checked first because it is the narrowest and the only
 * one that keeps the placed block functional; nothing in that table is a
 * multi-position placer, so the two never actually contend -- the order is fixed
 * so that a future addition to either table cannot make the outcome depend on
 * which check happened to be written first.
 */
public final class ReverseMidasBlocks {

	/**
	 * The genuine golden vanilla counterpart of this block, or null.
	 *
	 * <h2>The inclusion rule</h2>
	 *
	 * <p><b>A pair is in this table only when a gold block would DESTROY the
	 * item's function.</b> That is what separates a functional substitution from a
	 * palette swap, and it is why the table is three rows rather than eight.
	 *
	 * <p>The full set of gold-material placeable blocks in this version, read out
	 * of {@code Blocks} rather than recalled: {@code GOLD_BLOCK},
	 * {@code RAW_GOLD_BLOCK}, {@code GOLD_ORE}, {@code DEEPSLATE_GOLD_ORE},
	 * {@code NETHER_GOLD_ORE}, {@code GILDED_BLACKSTONE},
	 * {@code LIGHT_WEIGHTED_PRESSURE_PLATE}, {@code POWERED_RAIL},
	 * {@code GOLDEN_DANDELION} (and {@code POTTED_GOLDEN_DANDELION}, which is not
	 * placeable from an item). The five solid ones are <b>deliberately excluded</b>:
	 * a gold ore or a gilded blackstone is a full solid block, and so is the gold
	 * block the effect already places, so substituting one changes the texture and
	 * nothing else. Only the last three name a shape a gold block cannot be.
	 *
	 * <table border="1">
	 *   <caption>The three real pairs</caption>
	 *   <tr><th>placed</th><th>becomes</th><th>what a gold block would have cost</th></tr>
	 *   <tr><td>any {@code BasePressurePlateBlock} -- stone, every wood, polished
	 *       blackstone, and the heavy (iron) weighted plate</td>
	 *       <td>{@code LIGHT_WEIGHTED_PRESSURE_PLATE}</td>
	 *       <td>a full block cannot be stood on to trigger anything</td></tr>
	 *   <tr><td>any {@code BaseRailBlock} -- {@code RailBlock},
	 *       {@code DetectorRailBlock}, {@code PoweredRailBlock} (which backs both the
	 *       powered and the activator rail)</td>
	 *       <td>{@code POWERED_RAIL}</td>
	 *       <td>a full block is not track; the minecart stops</td></tr>
	 *   <tr><td>{@code DANDELION}</td><td>{@code GOLDEN_DANDELION}</td>
	 *       <td>a full block is not a flower</td></tr>
	 * </table>
	 *
	 * <p><b>The dandelion row is the one that had to be found rather than
	 * assumed.</b> {@code GOLDEN_DANDELION} is new in this version and is
	 * registered in {@code Blocks}'s class initialiser immediately before
	 * {@code torchflower} with the identical property chain
	 * ({@code mapColor(PLANT).noCollision().instabreak().sound(GRASS)
	 * .offsetType(XZ).pushReaction(DESTROY)}) and no feature flag -- an ordinary
	 * small flower. It is mapped from the <b>dandelion only</b>, because that is the
	 * one exact species pair: a poppy's golden counterpart does not exist, and
	 * mapping every flower onto this one would be inventing an equivalence rather
	 * than finding one.
	 *
	 * <p>Rails are mapped as a whole family even though only the plain rail is
	 * literally the gold recipe. The alternative for a detector rail is a solid gold
	 * block sitting in the middle of the track, which preserves strictly less of
	 * what the player was doing than "it is still a rail".
	 *
	 * <p>The three gold blocks map to themselves, which is a harmless no-op and is
	 * why this is written as a predicate on the class rather than an exclusion list.
	 */
	public static Block goldenEquivalent(Block block) {
		if (block instanceof BasePressurePlateBlock) {
			return Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE;
		}
		if (block instanceof BaseRailBlock) {
			return Blocks.POWERED_RAIL;
		}
		if (block == Blocks.DANDELION) {
			return Blocks.GOLDEN_DANDELION;
		}
		return null;
	}

	/**
	 * True if vanilla's own placement of this block writes a <b>second</b> world
	 * position, so replacing the state before {@code setBlock} would produce one
	 * gold block where two cells were expected.
	 *
	 * <h2>The real list, and the real mechanism</h2>
	 *
	 * <p>Enumerated by dumping every {@code setPlacedBy} override in
	 * {@code net.minecraft.world.level.block} and reading each body for a
	 * {@code Level.setBlock} at a <i>different</i> {@code BlockPos}. Exactly three
	 * families do it, and they all do it the same way:
	 *
	 * <table border="1">
	 *   <caption>Second-position writers</caption>
	 *   <tr><th>block</th><th>second position</th><th>items</th></tr>
	 *   <tr><td>{@code DoorBlock} (+ {@code WeatheringCopperDoorBlock})</td>
	 *       <td>{@code pos.above()}, {@code HALF=UPPER}</td>
	 *       <td>every wooden, iron and copper door</td></tr>
	 *   <tr><td>{@code BedBlock}</td>
	 *       <td>{@code pos.relative(FACING)}, {@code PART=HEAD}</td>
	 *       <td>all 16 beds</td></tr>
	 *   <tr><td>{@code DoublePlantBlock} (+ {@code TallFlowerBlock},
	 *       {@code TallSeagrassBlock}, {@code SmallDripleafBlock})</td>
	 *       <td>{@code pos.above()}, {@code HALF=UPPER}</td>
	 *       <td>tall grass, large fern, sunflower, lilac, rose bush, peony,
	 *           tall seagrass, small dripleaf</td></tr>
	 * </table>
	 *
	 * <p><b>{@code PitcherCropBlock} extends {@code DoublePlantBlock} and is
	 * excluded, because it overrides {@code setPlacedBy} with an empty body</b>
	 * (javap: a bare {@code return} at offset 0). A pitcher pod plants one cell and
	 * grows its upper half later, so it is an ordinary single-block placement and
	 * takes the ordinary BEFORE path. Inheritance said "multi-block" here and the
	 * bytecode said otherwise.
	 *
	 * <p>{@code MossyCarpetBlock} was the other near miss and is also excluded: its
	 * {@code setPlacedBy} may write {@code pos.above()}, but only a decorative
	 * topper, only when {@code createTopperWithSideChance} returns a non-air state,
	 * and with a per-side <i>random</i> chance. That is optional growth rather than
	 * the other half of one block -- the carpet is complete without it -- so
	 * including it would make a single carpet place one or two gold blocks
	 * depending on a die roll.
	 *
	 * <p><b>The order of operations is the whole reason this table exists.</b>
	 * {@code BlockItem.place} calls {@code placeBlock} at offset 63 -- that is the
	 * one call the {@code @ModifyArg} rewrites -- and reaches {@code setPlacedBy}
	 * only at offset 171, <i>after</i> re-reading the world and branching on
	 * {@code worldState.is(placementState.getBlock())}. Gold matches gold, so the
	 * branch is taken and {@code setPlacedBy} is dispatched on
	 * <b>{@code Blocks.GOLD_BLOCK}</b>, which does not override it. The second
	 * position was therefore never written at all: a door became one gold block and
	 * a bed became one gold block, with nothing in the log to say so.
	 *
	 * <p>Two of these families also do work in the <i>item</i>:
	 * {@code BedItem.placeBlock} sets the foot itself with flag 26 and never calls
	 * super, and {@code DoubleHighBlockItem.placeBlock} clears {@code pos.above()}
	 * to air or water before calling super. Both still receive the
	 * {@code @ModifyArg}'d state, since the modification is at the call site in
	 * {@code place} and dispatch is virtual -- which is another reason to hand these
	 * three families back to vanilla whole rather than try to reproduce them.
	 *
	 * <p><b>None of the three needs a block entity carve-out, and one has a block
	 * entity.</b> {@code DoorBlock} and {@code DoublePlantBlock} are not
	 * {@code EntityBlock}s; {@code BedBlock} <i>is</i>, but a {@code BedBlockEntity}
	 * carries nothing from the item -- a bed's colour is the block, not its data --
	 * so the one thing the AFTER path creates and then destroys is empty. The
	 * shulker-box carve-out still runs first and is untouched by any of this.
	 */
	public static boolean placesTwoPositions(Block block) {
		if (block instanceof PitcherCropBlock) {
			return false;
		}
		return block instanceof DoorBlock
				|| block instanceof BedBlock
				|| block instanceof DoublePlantBlock;
	}

	/**
	 * The other cell of a placement covered by {@link #placesTwoPositions}, or null
	 * if this state does not name one.
	 *
	 * <p>Read from the state vanilla actually wrote, never recomputed: a bed uses
	 * {@code BedBlock.getConnectedDirection}, vanilla's own helper, and a door or
	 * double plant uses the shared {@code BlockStateProperties.DOUBLE_BLOCK_HALF}
	 * ({@code DoorBlock.HALF} and {@code DoublePlantBlock.HALF} are the same
	 * property object, verified in both class initialisers). That is the point of
	 * the AFTER exception -- orientation, hinge side, facing and the second position
	 * are all vanilla's answers, so nothing here can disagree with them.
	 */
	public static BlockPos partnerPos(BlockPos pos, BlockState state) {
		if (state.getBlock() instanceof BedBlock) {
			return pos.relative(BedBlock.getConnectedDirection(state));
		}
		if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
			return state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER
					? pos.above()
					: pos.below();
		}
		return null;
	}

	/**
	 * Flags used for both halves of the conversion.
	 *
	 * <p>{@code UPDATE_ALL} so redstone and observers see a block change.
	 * {@code UPDATE_KNOWN_SHAPE} because without it the first {@code setBlock}
	 * tells the surviving half its partner is gone, and a door half or plant half
	 * that hears that turns itself into air -- a race that would resolve differently
	 * depending on which cell we wrote first. {@code UPDATE_SUPPRESS_DROPS} so that
	 * if anything ever does destroy a half, it cannot hand the item back: the player
	 * would keep the door <i>and</i> get the gold.
	 */
	public static final int CONVERT_FLAGS =
			Block.UPDATE_ALL | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

	/**
	 * Every world cell this placement should turn to gold: the clicked position,
	 * and the partner when {@link #partnerPos} names one.
	 *
	 * <p>Split out from {@link #convertBothHalves} and free of {@code Level} on
	 * purpose (CLAUDE.md 1f): "a door produces gold in <b>both</b> cells" is a claim
	 * about this list, and this list can be driven headlessly against real
	 * {@code BlockState}s, where anything taking a {@code Level} cannot be.
	 */
	public static List<BlockPos> cellsToConvert(BlockPos pos, BlockState placed) {
		BlockPos partner = partnerPos(pos, placed);
		return partner == null ? List.of(pos) : List.of(pos, partner);
	}

	/**
	 * Turns both cells of a just-placed two-position block into gold.
	 *
	 * <p>The partner is only converted if it really holds the same block, which is
	 * what makes this safe for a family member that turns out not to have written a
	 * second cell (and is a second, cheaper guard than the {@code PitcherCropBlock}
	 * exclusion). The clicked position is always converted.
	 *
	 * @return the number of cells converted -- 2 for a real door, bed or double
	 *     plant, 1 if the partner cell held something else
	 */
	public static int convertBothHalves(Level level, BlockPos pos, BlockState placed) {
		BlockState gold = Blocks.GOLD_BLOCK.defaultBlockState();
		int converted = 0;
		for (BlockPos cell : cellsToConvert(pos, placed)) {
			if (!cell.equals(pos) && level.getBlockState(cell).getBlock() != placed.getBlock()) {
				continue;
			}
			level.setBlock(cell, gold, CONVERT_FLAGS);
			converted++;
		}
		return converted;
	}

	private ReverseMidasBlocks() {}
}
