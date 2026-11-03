package com.entropymod.entropy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Central list of all effect definitions. This is the ONLY place new effects
 * need to be added -- the timer, GUI, and networking code never change when you
 * add effect #47.
 *
 * <p><b>Current content: 72 effects — 34 GOOD and 38 BAD, all permanent.</b>
 * Tier 1 is 34 (17 GOOD / 17 BAD) at entropy 0-25; the rest are Tier 2 at
 * entropy 25-50, plus Glass Cannon Pact, Unstable and Earthquake at 40-60. It is
 * a baseline, not the finished game: Tiers 3-4 and the odd/signature effects
 * still slot in the same way.
 *
 * <p>Every effect is permanent — see {@link EffectDefinition} for why there is
 * no duration field.
 */
public final class EffectRegistry {
	private static final List<EffectDefinition> ALL = new ArrayList<>();

	static {
		// =================================================================
		// TIER 1 GOOD (Entropy 0-25) -- 17 effects
		// =================================================================

		// --- attribute-driven ---
		register("sure_footing", "Sure Footing", "+15% movement speed",
				EffectCategory.MOVEMENT, EffectPhase.GOOD, 0, 25, true);
		register("thick_hide", "Thick Hide", "+2 hearts of maximum health",
				EffectCategory.SURVIVAL, EffectPhase.GOOD, 0, 25, true);
		register("steady_hands", "Steady Hands", "+2 attack damage",
				EffectCategory.COMBAT, EffectPhase.GOOD, 0, 25, true);
		register("lucky_find", "Lucky Find", "+2 Luck",
				EffectCategory.UTILITY, EffectPhase.GOOD, 0, 25, true);
		register("swift_strikes", "Swift Strikes", "+20% attack speed",
				EffectCategory.COMBAT, EffectPhase.GOOD, 0, 25, true);
		register("featherlight", "Featherlight", "Fall damage reduced by 40%",
				EffectCategory.MOVEMENT, EffectPhase.GOOD, 0, 25, true);
		register("efficient_miner", "Efficient Miner", "+15% mining speed",
				EffectCategory.TOOL, EffectPhase.GOOD, 0, 25, true);
		register("moon_walker", "Moon Walker", "Gravity reduced by 30% -- jump higher, fall slower",
				EffectCategory.MOVEMENT, EffectPhase.GOOD, 0, 25, true);
		register("magnetic_boots", "Magnetic Boots", "Items and XP are pulled in from twice as far",
				EffectCategory.UTILITY, EffectPhase.GOOD, 0, 25, true);

		// --- hook-driven ---
		register("iron_stomach", "Iron Stomach", "Hunger drains 25% slower",
				EffectCategory.SURVIVAL, EffectPhase.GOOD, 0, 25, true);
		register("iron_skin", "Iron Skin", "All damage taken reduced by 20%",
				EffectCategory.GEAR, EffectPhase.GOOD, 0, 25, true);
		register("fast_learner", "Fast Learner", "+50% experience gained",
				EffectCategory.META, EffectPhase.GOOD, 0, 25, true);
		register("frost_walker_innate", "Frost Walker Innate", "Water freezes under your feet -- no boots needed",
				EffectCategory.MOVEMENT, EffectPhase.GOOD, 0, 25, true);
		register("iron_will", "Iron Will", "Projectiles no longer knock you back",
				EffectCategory.COMBAT, EffectPhase.GOOD, 0, 25, true);
		register("beast_whisperer", "Beast Whisperer", "Animals never flee from you",
				EffectCategory.COMPANION, EffectPhase.GOOD, 0, 25, true);
		register("green_thumb", "Green Thumb", "Crops within 8 blocks of you fully mature in about 90 seconds",
				EffectCategory.UTILITY, EffectPhase.GOOD, 0, 25, true);
		register("second_guess", "Second Guess", "Once per run, reroll the options you are offered",
				EffectCategory.META, EffectPhase.GOOD, 0, 25, true);

		// =================================================================
		// TIER 1 BAD (Entropy 0-25) -- 17 effects
		//
		// Every one is counterplay = true: none can kill you on its own, and
		// each has an in-game answer (armour, food, care around ledges).
		// Required below entropy 40 -- see CLAUDE.md Part 2.
		// =================================================================

		// --- attribute-driven ---
		register("heavy_boots", "Heavy Boots", "-15% movement speed",
				EffectCategory.MOVEMENT, EffectPhase.BAD, 0, 25, true);
		register("brittle_bones", "Brittle Bones", "-2 hearts of maximum health",
				EffectCategory.SURVIVAL, EffectPhase.BAD, 0, 25, true);
		register("weak_grip", "Weak Grip", "-2 attack damage",
				EffectCategory.COMBAT, EffectPhase.BAD, 0, 25, true);
		register("unlucky", "Unlucky", "-2 Luck",
				EffectCategory.UTILITY, EffectPhase.BAD, 0, 25, true);
		register("sluggish_strikes", "Sluggish Strikes", "-20% attack speed",
				EffectCategory.COMBAT, EffectPhase.BAD, 0, 25, true);
		register("glass_jaw", "Glass Jaw", "Fall damage increased by 40%",
				EffectCategory.MOVEMENT, EffectPhase.BAD, 0, 25, true);
		register("dull_blade", "Dull Blade", "-20% mining speed",
				EffectCategory.TOOL, EffectPhase.BAD, 0, 25, true);
		register("leaden_legs", "Leaden Legs", "Gravity increased by 40% -- jump lower, fall harder",
				EffectCategory.MOVEMENT, EffectPhase.BAD, 0, 25, true);
		register("stone_feet", "Stone Feet", "-40% jump strength",
				EffectCategory.MOVEMENT, EffectPhase.BAD, 0, 25, true);

		// --- hook-driven ---
		register("growling_stomach", "Growling Stomach", "Hunger drains 25% faster",
				EffectCategory.SURVIVAL, EffectPhase.BAD, 0, 25, true);
		register("fragile", "Fragile", "All damage taken increased by 25%",
				EffectCategory.GEAR, EffectPhase.BAD, 0, 25, true);
		register("slow_learner", "Slow Learner", "-50% experience gained",
				EffectCategory.META, EffectPhase.BAD, 0, 25, true);
		register("exposed", "Exposed", "Loud footsteps -- sneaking no longer hides you from mobs",
				EffectCategory.DEBUFF, EffectPhase.BAD, 0, 25, true);
		register("blight_touched", "Blight Touched", "Every crop you walk through withers into a dead bush",
				EffectCategory.UTILITY, EffectPhase.BAD, 0, 25, true);
		register("leaky_pockets", "Leaky Pockets", "Jumping sometimes spills an item from your inventory",
				EffectCategory.GEAR, EffectPhase.BAD, 0, 25, true);
		register("clumsy_digger", "Clumsy Digger", "Your mining tools wear out far faster -- the better the tool, the worse",
				EffectCategory.TOOL, EffectPhase.BAD, 0, 25, true);
		register("bad_reputation", "Bad Reputation", "Villagers charge you more",
				EffectCategory.DEBUFF, EffectPhase.BAD, 0, 25, true);

		// --- TIER 2 GOOD (entropy 25-50) --- first Tier 2 GOOD content.
		register("embrace_the_moon", "Embrace the Moon",
				"Lunar gravity -- jump 4.5 blocks, drift down slowly, and land safely from twice as high",
				EffectCategory.MOVEMENT, EffectPhase.GOOD, 25, 50, true);
		register("creative_flight", "Creative Flight",
				"You can fly, permanently, in Survival",
				EffectCategory.MOVEMENT, EffectPhase.GOOD, 25, 50, true);
		register("behemoth_gauntlets", "Behemoth Gauntlets",
				"+20 damage bare-handed, but anything you hold hits for a quarter",
				EffectCategory.COMBAT, EffectPhase.GOOD, 25, 50, true);
		register("phoenix_chambered_heart", "Phoenix Chambered Heart",
				"Once per run, a killing blow is refused: you rise on Regeneration, "
						+ "Health Boost and Absorption X",
				EffectCategory.SURVIVAL, EffectPhase.GOOD, 25, 50, true);
		register("glass_cannon_pact", "Glass Cannon Pact",
				"+50% attack damage, but one heart less to survive on",
				EffectCategory.COMBAT, EffectPhase.GOOD, 40, 60, true);
		register("danger_sense", "Danger Sense",
				"Hostile mobs within 32 blocks glow -- visible through walls",
				EffectCategory.UTILITY, EffectPhase.GOOD, 25, 50, true);
		register("double_jump", "Double Jump",
				"One extra jump in mid-air, recharged when you land",
				EffectCategory.MOVEMENT, EffectPhase.GOOD, 25, 50, true);
		register("crouch_invincibility", "Crouch Invincibility",
				"Nothing can hurt you while you are sneaking",
				EffectCategory.SURVIVAL, EffectPhase.GOOD, 25, 50, true);

		// --- TIER 2 BAD (entropy 25-50) ---
		// The first client-side effects in the project: all three act on systems that
		// only exist on the client (KeyboardInput, Camera), which is why they need
		// ClientEffectsPayload. See CLAUDE.md.
		register("randomized_controls", "Randomized Movement Controls",
				"Your movement keys are permanently rewired to a random order",
				EffectCategory.MOVEMENT, EffectPhase.BAD, 25, 50, true);
		register("upside_down_camera", "Upside-Down Camera",
				"Your view is permanently upside-down",
				EffectCategory.DEBUFF, EffectPhase.BAD, 25, 50, true);
		register("random_jump", "Random Jump",
				"You jump on your own every 5-30 seconds",
				EffectCategory.UTILITY, EffectPhase.BAD, 25, 50, true);

		register("slippery_grip", "Slippery Grip",
				"Sprinting drops you to half your walking speed",
				EffectCategory.MOVEMENT, EffectPhase.BAD, 25, 50, true);
		register("giant_size", "Giant Size",
				"Five times your size: +10 hearts, double jump, 2-block steps, "
						+ "-25% damage taken -- but you fit almost nowhere",
				EffectCategory.DEBUFF, EffectPhase.BAD, 25, 50, true);
		register("slashed_pockets", "Slashed Pockets",
				"The top two rows of your inventory are torn open and unusable",
				EffectCategory.GEAR, EffectPhase.BAD, 25, 50, true);
		register("flamboyant", "Flamboyant",
				"Catching fire kills you outright",
				EffectCategory.SURVIVAL, EffectPhase.BAD, 25, 50, true);

		// The first two effects that spawn a threatening entity. Both share
		// SafeSpawn, and both are SURVIVAL on purpose -- "a lethal hazard appears
		// beside you on a timer" is one kind of thing, so anti-stacking should keep
		// them apart rather than hand a run both at once.
		//
		// counterplay = true for both, and neither is pushed to the top of the band:
		// Unstable's blast at its minimum distance is non-lethal from full health and
		// costs a fifth of its fuse to escape, and Creeper Magnet is an ordinary
		// creeper you can see coming. See the behavior classes for the derivations.
		// counterplay = FALSE -- the only false entry in this registry. The band is
		// 0-2 blocks and every point in it kills a stationary full-health player.
		//
		// 40-60, NOT 25-50, and that is what resolves it: CLAUDE.md Part 2 requires
		// bad effects below entropy 40 to be counterplay-survivable, and this one is
		// not. Raising the floor to 40 honours the invariant exactly, with no change
		// to the damage model or the distance band. Same shape as Glass Cannon Pact,
		// which already sits above the rest of Tier 2 for its own reason.
		register("unstable", "Unstable",
				"Primed TNT appears at your feet every 30 seconds -- move or die",
				EffectCategory.SURVIVAL, EffectPhase.BAD, 40, 60, false);
		register("creeper_magnet", "Creeper Magnet",
				"A creeper blinks into existence nearby every 30 seconds to 2 minutes",
				EffectCategory.SURVIVAL, EffectPhase.BAD, 25, 50, true);

		// --- TIER 2 GOOD companions (entropy 25-50) ---
		// All four are COMPANION, which anti-stacking then keeps apart: a run with
		// fifteen wolves AND four iron golems AND ten villagers is a different game,
		// and the category is the mechanism that stops the roll offering it.
		register("loyal_pack", "Loyal Pack",
				"Fifteen armoured wolves, tamed to you forever",
				EffectCategory.COMPANION, EffectPhase.GOOD, 25, 50, true);
		register("the_entourage", "The Entourage",
				"Four iron golems escort you and attack anything that attacks you",
				EffectCategory.COMPANION, EffectPhase.GOOD, 25, 50, true);
		register("the_audience", "The Audience",
				"Ten silent, invincible villagers follow you at a distance, watching",
				EffectCategory.COMPANION, EffectPhase.GOOD, 25, 50, true);
		register("emotional_support_llama", "Emotional Support Llama",
				"An invincible llama follows you, carrying 15 slots of storage",
				EffectCategory.COMPANION, EffectPhase.GOOD, 25, 50, true);

		// Tier 2 combat/survival batch. Butterfingers Extreme and Earthquake are BAD,
		// Vampire Rules GOOD, so the phase balance is unchanged.
		register("butterfingers_extreme", "Butterfingers Extreme",
				"Attacking can fling whatever you are holding out of your hand",
				EffectCategory.COMBAT, EffectPhase.BAD, 25, 50, true);
		register("vampire_rules", "Vampire Rules",
				"Damage you deal feeds you -- 8 damage restores a hunger shank, overflowing into saturation",
				EffectCategory.SURVIVAL, EffectPhase.GOOD, 25, 50, true);
		// Earthquake moved 25-50 -> 40-60 and counterplay true -> FALSE, and it is the
		// same resolution Unstable reached above rather than a coincidence. Slowness IV
		// became a real movement lock and the blindness went from half the window to all
		// of it, so during a tremor the player cannot walk, jump, sprint or see, and
		// both half-heart hits land regardless of anything they do. That is the
		// definition of no counterplay, and CLAUDE.md Part 2 forbids a
		// counterplay = false effect below entropy 40. 40-60 also matches Glass Cannon
		// Pact's shape. Attacking and mining still work -- it is a movement lock, not an
		// input lock -- but neither changes the tremor's outcome.
		register("earthquake", "Earthquake",
				"Every 45s-3min the ground pins you: no movement, blind, and two "
						+ "half-hearts of unblockable damage you cannot avoid",
				EffectCategory.DEBUFF, EffectPhase.BAD, 40, 60, false);

		// Counterplay is true and means it: the animals are slower than a sprinting
		// player (0.24 against a sprint's 5.6 b/s), need line of sight to target at
		// all, and a door or a two-block ledge ends the pursuit outright.
		register("beast_wrath", "Beast Wrath",
				"Passive animals and fish turn on you -- even wild wolves and cats. "
						+ "Your own tamed animals stay loyal",
				EffectCategory.COMBAT, EffectPhase.BAD, 25, 50, true);

		// Counterplay is true: you can simply stop placing blocks, and the gold
		// block is minable. See ReverseMidasBehavior for the two severe knock-on
		// consequences (a gold generator, and no crafting table/furnace/bed ever).
		register("reverse_midas", "Reverse Midas",
				"Every block you place turns to gold as it leaves your hand",
				EffectCategory.UTILITY, EffectPhase.BAD, 25, 50, true);

		register("grave_robber", "Grave Robber",
				"Mobs dying near you sometimes give up one extra piece of their loot",
				EffectCategory.COMBAT, EffectPhase.GOOD, 25, 50, true);

		// Ice friction (0.98) forced onto every block underfoot. Counterplay is
		// true: top speed is unchanged, so walking short bursts and letting the
		// slide settle works -- it is acceleration and braking that are gone.
		register("slick", "Slick",
				"Every block you walk on is as slippery as ice",
				EffectCategory.MOVEMENT, EffectPhase.BAD, 25, 50, true);

		// Cancels Player.maybeBackOffFromEdge. Counterplay is true and total:
		// stop at the edge yourself, or place a block before stepping out.
		register("loose_footing", "Loose Footing",
				"Sneaking no longer stops you walking off a ledge",
				EffectCategory.DEBUFF, EffectPhase.BAD, 25, 50, true);

		register("lethal_hook", "Lethal Hook",
				"Longer reach, and everything you hit in melee is dragged toward you "
						+ "instead of knocked away",
				EffectCategory.COMBAT, EffectPhase.GOOD, 25, 50, true);

		// BAD despite the attack-speed half reading as a buff: the two
		// ADD_MULTIPLIED_TOTAL factors multiply out to x0.76 DPS for every weapon.
		// Retuned from -0.75 (x0.25 damage, x0.475 DPS); the description moved with
		// the number, because "a quarter" was true at -0.75 and is not at -0.60.
		// See HumanMachinegunBehavior for the full derivation.
		register("human_machinegun", "Human Machinegun",
				"You swing 90% faster and hit for two fifths as much",
				EffectCategory.COMBAT, EffectPhase.BAD, 25, 50, true);

		// Reuses vanilla's own BedRule.explodes() branch. Counterplay is true for
		// the same reason Flamboyant's is despite killing outright: the trigger is
		// one deliberate, unhurried click the player never has to make.
		register("nightmares", "Nightmares",
				"Beds explode when you try to sleep, wherever you are",
				EffectCategory.UTILITY, EffectPhase.BAD, 25, 50, true);

		register("glutton", "Glutton",
				"Every food source restores 75% less hunger and saturation",
				EffectCategory.SURVIVAL, EffectPhase.BAD, 25, 50, true);

		// =================================================================
		// TIER 2 META -- effects that change the PICK LOOP, not the player
		//
		// All five are META, which is semantically right and has a consequence
		// worth knowing rather than discovering: anti-stacking excludes a whole
		// category once occupied, so holding ANY of these (or Second Guess,
		// Fast Learner, Slow Learner) keeps the rest out of the roll for the
		// phase they share. Anti-stacking is a SOFT rule dropped first when the
		// pool empties, so collisions are reachable -- but in ordinary play
		// these are close to mutually exclusive. Testing them together is what
		// /entropygrant is for; it bypasses the roll entirely.
		// =================================================================

		// Halves entropy gain for the REST OF THE RUN via a carry flag: +0 on one
		// pick, +1 on the next, forever. Exactly half, using only integer entropy.
		// 45-60 because that is a run-defining blessing early and a modest one
		// late, and the window is the only lever the roll offers -- see
		// BankedEntropyBehavior.
		register("banked_entropy", "Banked Entropy",
				"Entropy now rises half as fast -- every other pick is free",
				EffectCategory.META, EffectPhase.GOOD, 45, 60, true);

		// Grants BOTH the other two cards shown alongside it. 40-50: above the
		// whole Tier 2 mass so it is never an early-run option, and capped at 50
		// because above that the GOOD pool collapses to Glass Cannon Pact alone
		// and a Twin Fate that is one card of two is the default, not a highlight.
		register("twin_fate", "Twin Fate",
				"Take both of the other two blessings offered with this one",
				EffectCategory.META, EffectPhase.GOOD, 40, 50, true);

		// Banks 2 reroll charges now; the next curse you take is drawn from the
		// scalable subset and applied at DOUBLE strength. counterplay = true: every
		// member of that subset is a Tier 1 attribute curse whose doubled form is
		// still survivable -- see SCALABLE_SEVERITY_IDS for the per-effect clamp.
		register("all_in", "All In",
				"Two rerolls, banked -- and the next curse you take hits twice as hard",
				EffectCategory.META, EffectPhase.BAD, 25, 50, true);

		// Forces the next bad-phase roll to draw only from the most extreme tier
		// that exists, ignoring the entropy window for that one roll.
		//
		// 40-60 and counterplay = FALSE, and neither is a choice. The extreme BAD
		// tier is Unstable and Earthquake, both counterplay = false, so this
		// effect DELIVERS one -- CLAUDE.md Part 2 forbids that below entropy 40,
		// and labelling it true would be labelling the thing it hands you.
		// 40-60 is Unstable's and Earthquake's own window.
		register("debt_collector", "Debt Collector",
				"Your next curse is drawn from the worst the run has to offer",
				EffectCategory.META, EffectPhase.BAD, 40, 60, false);

		// counterplay = true, and it means it: it takes away a safety net rather
		// than doing anything to the player. A run that never had Second Guess and
		// never met All In loses nothing at all.
		register("no_do_overs", "No Do-Overs",
				"Rerolling is forbidden for the rest of the run -- banked charges included",
				EffectCategory.META, EffectPhase.BAD, 25, 55, true);

		// TODO: Tiers 3-4 + odd/signature effects go here, same pattern.
		// See entropy-modpack-effects.md for the full list to port over.
	}

	private static void register(String id, String displayName, String description,
								  EffectCategory category, EffectPhase phase,
								  int minEntropy, int maxEntropy, boolean counterplay) {
		ALL.add(new EffectDefinition(id, displayName, description, category, phase,
				minEntropy, maxEntropy, counterplay));
	}

	/**
	 * The BAD effects whose severity can be <b>generically doubled</b>, which is the
	 * pool All In draws from. Eight of thirty-five, and the shortness is the finding
	 * rather than a gap in the investigation.
	 *
	 * <p>The rule is mechanical: the effect must be an
	 * {@link AttributeEffectBehavior} -- so "twice as severe" is literally
	 * {@code amount * 2} with no per-effect code -- <b>and</b> every one of its
	 * {@code Change}s must be unambiguously a penalty, <b>and</b> the doubled value
	 * must not land on a clamp that makes it degenerate.
	 *
	 * <table border="1">
	 *   <caption>the subset, with the doubled value each one really produces</caption>
	 *   <tr><th>effect</th><th>attribute</th><th>1x -&gt; 2x</th><th>result</th></tr>
	 *   <tr><td>heavy_boots</td><td>MOVEMENT_SPEED</td><td>-0.15 -&gt; -0.30</td>
	 *       <td>0.1 -&gt; 0.07 (4.32 -&gt; 3.02 b/s)</td></tr>
	 *   <tr><td>brittle_bones</td><td>MAX_HEALTH</td><td>-4.0 -&gt; -8.0</td>
	 *       <td>20 -&gt; 12, six hearts; floor is 1.0</td></tr>
	 *   <tr><td>weak_grip</td><td>ATTACK_DAMAGE</td><td>-2.0 -&gt; -4.0</td>
	 *       <td>iron sword 6.0 -&gt; 2.0; bare-handed was already clamped to 0.0</td></tr>
	 *   <tr><td>sluggish_strikes</td><td>ATTACK_SPEED</td><td>-0.20 -&gt; -0.40</td>
	 *       <td>4.0 -&gt; 2.4; floor 0.0 unreached</td></tr>
	 *   <tr><td>glass_jaw</td><td>FALL_DAMAGE_MULTIPLIER</td><td>+0.40 -&gt; +0.80</td>
	 *       <td>1.0 -&gt; 1.8</td></tr>
	 *   <tr><td>dull_blade</td><td>BLOCK_BREAK_SPEED</td><td>-0.20 -&gt; -0.40</td>
	 *       <td>1.0 -&gt; 0.6</td></tr>
	 *   <tr><td>leaden_legs</td><td>GRAVITY</td><td>+0.40 -&gt; +0.80</td>
	 *       <td>0.08 -&gt; 0.144; max is 1.0</td></tr>
	 *   <tr><td>stone_feet</td><td>JUMP_STRENGTH</td><td>-0.40 -&gt; -0.80</td>
	 *       <td>0.42 -&gt; 0.084; floor 0.0 unreached</td></tr>
	 * </table>
	 *
	 * <p><b>The three harshest members, flagged rather than left to be met in play.</b>
	 * Doubled {@code stone_feet} leaves a jump apex near 0.06 blocks and doubled
	 * {@code leaden_legs} near 0.70 -- neither clears anything, and the world stays
	 * traversable only because {@code STEP_HEIGHT} is untouched at 0.6, so slabs and
	 * stairs still auto-step. That is the same argument the skill records for their
	 * 1x values, at a larger magnitude. Doubled {@code weak_grip} is invisible
	 * bare-handed, because the 1x value already clamps that case to 0.0.
	 *
	 * <p><b>Excluded, and each for a stated reason rather than by omission:</b>
	 * <ul>
	 *   <li><b>unlucky</b> -- mechanically fine ({@code LUCK}'s floor is -1024), but
	 *       -4 luck against -2 is a shift in loot-table weighting no player can
	 *       observe. That is the perceptual-threshold failure this project has hit
	 *       four times; a doubled severity nobody can feel reads as broken.</li>
	 *   <li><b>giant_size, human_machinegun</b> -- attribute-driven but <b>mixed
	 *       valence</b>. Doubling also doubles Giant Size's +10 hearts, -25% damage
	 *       taken and +6.5 reach, and Human Machinegun's +90% attack speed. "Twice
	 *       as severe" would make both of them <em>better</em>.</li>
	 *   <li><b>every hook-driven curse</b> (24 of them) -- their magnitude is a
	 *       {@code static final} read by a mixin or a tick service, not a modifier
	 *       amount, so there is no generic doubling operation at all: only
	 *       twenty-four separate decisions. And several have no meaningful 2x even
	 *       then. {@code exposed}'s 1.25 detection multiplier is already at its
	 *       saturation point (any value at or above it behaves identically);
	 *       {@code clumsy_digger}'s chance saturates at 2x wear; {@code blight_touched}
	 *       is already <em>every</em> crop; and {@code loose_footing},
	 *       {@code nightmares}, {@code flamboyant}, {@code upside_down_camera} and
	 *       {@code randomized_controls} are booleans that are already total.</li>
	 * </ul>
	 *
	 * <p>Kept here rather than in {@code AllInBehavior} on purpose: this is metadata
	 * <em>about registry entries</em>, and this class is deliberately free of
	 * Minecraft imports. Reaching into the behavior package for it would pull
	 * {@code EffectContext} -- and therefore {@code MinecraftServer} -- into the one
	 * file the headless harness drives everything else from.
	 *
	 * <p><b>Adding an entry is a claim about a clamp.</b> Check the doubled value
	 * against the attribute table in the {@code entropy-effects} skill first; the
	 * harness asserts every member is attribute-driven and all-penalty, but it
	 * cannot tell you whether 2x is playable.
	 */
	public static final Set<String> SCALABLE_SEVERITY_IDS = Set.of(
			"heavy_boots",
			"brittle_bones",
			"weak_grip",
			"sluggish_strikes",
			"glass_jaw",
			"dull_blade",
			"leaden_legs",
			"stone_feet");

	/**
	 * A one-roll replacement for the normal entropy-window eligibility test.
	 *
	 * <p>Both non-{@code NONE} values <b>ignore {@link EffectDefinition#eligibleAt}
	 * entirely</b>, and for All In that is load-bearing rather than incidental: every
	 * member of {@link #SCALABLE_SEVERITY_IDS} is a Tier 1 curse capped at entropy
	 * 25, while All In itself cannot be offered below 25 -- so honouring the window
	 * would leave the forced pool permanently empty and the effect would silently do
	 * nothing.
	 *
	 * <p>No-repeat and anti-stacking still apply on top, in the usual priority
	 * order. An override narrows the pool; it does not suspend the rules.
	 */
	public enum PoolOverride {
		/** Ordinary eligibility -- the entropy window decides. */
		NONE,
		/** Debt Collector: only the most extreme tier this build defines for the phase. */
		EXTREME_TIER,
		/** All In: only curses whose severity can be generically doubled. */
		SCALABLE_SEVERITY
	}

	/**
	 * The effects of this phase with the <b>highest {@code minEntropy} any effect of
	 * this phase has</b> -- i.e. the most extreme tier that currently exists.
	 *
	 * <p>Derived rather than listed, so adding Tier 3 moves Debt Collector's target
	 * with it and no constant goes stale. For BAD today that is entropy 40:
	 * Unstable, Earthquake and Debt Collector itself -- the last of which no-repeat
	 * removes, since by the time the flag is read it has been acquired.
	 */
	public static List<EffectDefinition> extremeTier(EffectPhase phase) {
		int floor = ALL.stream()
				.filter(e -> e.phase() == phase)
				.mapToInt(EffectDefinition::minEntropy)
				.max()
				.orElse(Integer.MIN_VALUE);
		return ALL.stream()
				.filter(e -> e.phase() == phase && e.minEntropy() == floor)
				.collect(Collectors.toList());
	}

	/** Every registered effect, in registration order. Unmodifiable. */
	public static List<EffectDefinition> all() {
		return Collections.unmodifiableList(ALL);
	}

	/** All effects matching the given phase and eligible at the given entropy value. */
	public static List<EffectDefinition> eligible(EffectPhase phase, int entropy) {
		return ALL.stream()
				.filter(e -> e.phase() == phase && e.eligibleAt(entropy))
				.collect(Collectors.toList());
	}

	/**
	 * The outcome of a roll.
	 *
	 * @param choices          up to 3 effects; fewer if the pool is small, empty only
	 *                         if no effect of this phase is eligible at this entropy at all
	 * @param repeatFallback   true if the no-repeat rule had to be abandoned to produce
	 *                         any choices -- the player is about to be re-offered
	 *                         something already taken
	 * @param categoryFallback true if anti-stacking had to be abandoned as well
	 */
	public record RollResult(List<EffectDefinition> choices,
							 boolean repeatFallback,
							 boolean categoryFallback) {}

	/**
	 * Rolls up to 3 choices, applying both exclusion rules in priority order.
	 *
	 * <p><b>1. No-repeat (hard rule).</b> Anything in {@code alreadyPicked} is
	 * removed from the pool. Because effects are permanent, being offered a effect
	 * you already have is meaningless — there is nothing to refresh. This rule is
	 * absolute and is applied first.
	 *
	 * <p><b>2. Anti-stacking (soft rule, lower priority).</b> Effects whose
	 * category is already occupied are removed as well. If that leaves nothing,
	 * anti-stacking is dropped and the no-repeat-filtered pool is used, flagged via
	 * {@link RollResult#categoryFallback()}. Dropping this one first is deliberate:
	 * a second MOVEMENT effect is a real, playable outcome; a duplicate of an effect
	 * you already own is not.
	 *
	 * <p><b>3. Repeat fallback (last resort).</b> Only if no-repeat itself empties
	 * the pool -- every eligible effect of this phase already taken -- is the full
	 * eligible pool used, flagged via {@link RollResult#repeatFallback()}. With only
	 * 10 effects per phase this is genuinely reachable today, after 10 picks of one
	 * phase, so it is a tested path rather than a theoretical one. It exists so the
	 * loop cannot strand; it is not a considered design answer.
	 *
	 * <p>PLACEHOLDER POLICY, same status as the anti-stacking fallback: CLAUDE.md
	 * Open Question 6 is still open. More content is the real fix.
	 *
	 * <p>A partial result (1 or 2 choices) is not a fallback and is not flagged --
	 * it is the honest answer when the pool is genuinely that small.
	 *
	 * @param alreadyPicked      effect ids taken at any point this run (no-repeat)
	 * @param excludedCategories categories that already have an effect (anti-stacking)
	 */
	public static RollResult roll(EffectPhase phase, int entropy, Random random,
								  Set<String> alreadyPicked,
								  Set<EffectCategory> excludedCategories) {
		return roll(phase, entropy, random, alreadyPicked, excludedCategories, PoolOverride.NONE);
	}

	/**
	 * As {@link #roll(EffectPhase, int, Random, Set, Set)}, but with the starting
	 * pool replaced by a {@link PoolOverride}.
	 *
	 * <p>The override replaces <b>only</b> the entropy-window eligibility step. Both
	 * exclusion rules still run on the result, in the same priority order and with
	 * the same fallbacks, so a forced roll cannot re-offer something already taken
	 * and cannot quietly stop being subject to anti-stacking.
	 */
	public static RollResult roll(EffectPhase phase, int entropy, Random random,
								  Set<String> alreadyPicked,
								  Set<EffectCategory> excludedCategories,
								  PoolOverride override) {
		List<EffectDefinition> eligible = switch (override) {
			case NONE -> eligible(phase, entropy);
			case EXTREME_TIER -> extremeTier(phase);
			case SCALABLE_SEVERITY -> ALL.stream()
					.filter(e -> e.phase() == phase && SCALABLE_SEVERITY_IDS.contains(e.id()))
					.collect(Collectors.toList());
		};

		List<EffectDefinition> unpicked = eligible.stream()
				.filter(e -> !alreadyPicked.contains(e.id()))
				.collect(Collectors.toList());

		boolean repeatFallback = unpicked.isEmpty() && !eligible.isEmpty();
		List<EffectDefinition> pool = repeatFallback ? eligible : unpicked;

		List<EffectDefinition> categoryFiltered = pool.stream()
				.filter(e -> !excludedCategories.contains(e.category()))
				.collect(Collectors.toList());

		boolean categoryFallback = categoryFiltered.isEmpty() && !pool.isEmpty();
		List<EffectDefinition> source = new ArrayList<>(categoryFallback ? pool : categoryFiltered);

		Collections.shuffle(source, random);
		return new RollResult(
				source.stream().limit(3).collect(Collectors.toList()),
				repeatFallback,
				categoryFallback);
	}

	public static EffectDefinition byId(String id) {
		return ALL.stream().filter(e -> e.id().equals(id)).findFirst().orElse(null);
	}

	private EffectRegistry() {}
}
