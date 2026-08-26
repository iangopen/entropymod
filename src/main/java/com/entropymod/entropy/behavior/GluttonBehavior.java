package com.entropymod.entropy.behavior;

import com.entropymod.entropy.HookEffectBehavior;

/**
 * Glutton (BAD / SURVIVAL, entropy 25-50) -- every food source restores 75% less
 * hunger and saturation.
 *
 * <h2>This is the EATING side of the hunger system, and it has one choke point</h2>
 *
 * <p>Iron Stomach and Growling Stomach scale the <em>drain</em>, through
 * {@code Player.causeFoodExhaustion}. This is the other direction, and the same
 * structural problem applies: <b>{@code FoodData} holds no reference to the player
 * it belongs to</b>, which is exactly why {@code PlayerExhaustionMixin} targets
 * {@code Player} rather than {@code FoodData.addExhaustion}.
 *
 * <p>But unlike exhaustion, restoration has a single private funnel that every
 * source passes through:
 *
 * <pre>
 *   private void add(int nutrition, float saturation) {
 *       this.foodLevel       = Mth.clamp(nutrition + this.foodLevel, 0, 20);
 *       this.saturationLevel = Mth.clamp(saturation + this.saturationLevel, 0.0f, (float) this.foodLevel);
 *   }
 *   public void eat(int n, float mod)     { add(n, FoodConstants.saturationByModifier(n, mod)); }
 *   public void eat(FoodProperties p)     { add(p.nutrition(), p.saturation()); }
 * </pre>
 *
 * <p>Every {@code FoodData.eat} call site in the shipped jar was enumerated, and
 * there are exactly three -- all of them reaching {@code add}, and all of them
 * reaching it via {@code Player.getFoodData()}:
 *
 * <table border="1">
 *   <caption>every restoration source in the game</caption>
 *   <tr><th>caller</th><th>covers</th></tr>
 *   <tr><td>{@code FoodProperties.onConsume(Level, LivingEntity, ItemStack, Consumable)}</td>
 *       <td><b>every food item</b> -- food is a data component, so this is one hook
 *           for all of them, vanilla and modded alike</td></tr>
 *   <tr><td>{@code CakeBlock.eat(...)} -&gt; {@code eat(2, 0.1f)}</td>
 *       <td>cake, which is a block and never an item on the way in</td></tr>
 *   <tr><td>{@code SaturationMobEffect.applyEffectTick} -&gt; {@code eat(amplifier + 1, 1.0f)}</td>
 *       <td>the Saturation potion effect and the golden-apple / suspicious-stew
 *           grants that route through it</td></tr>
 * </table>
 *
 * <p><b>Hooking {@code add} rather than the three call sites is what makes
 * "uniformly, all sources" structural instead of a list that can go stale.</b> A
 * mod or a future version that adds a fourth source is covered without an edit,
 * which is the same reason Clumsy Digger is gated on a tag rather than an item list.
 *
 * <h2>Getting the player: an owner stamped once, in the one constructor</h2>
 *
 * <p>{@code Player} has exactly <b>one</b> constructor, {@code (Level, GameProfile)},
 * and it builds its {@code FoodData} at offset 20. {@code PlayerFoodDataOwnerMixin}
 * stamps the owner at its TAIL, so by the time any {@code add} can run the reference
 * is present. That is a strictly stronger guarantee than injecting at
 * {@code getFoodData()} would give, and it costs one field write per player rather
 * than one per call.
 *
 * <p>An unstamped {@code FoodData} -- one built by something other than a player, or
 * read before the constructor finishes -- reports no owner and the hook returns
 * {@code 1.0f}, so the failure direction is "vanilla behaviour", never a crash.
 *
 * <h2>Rounding: {@code Math.round}, and no floor</h2>
 *
 * <p>{@code foodLevel} is an {@code int}, so the nutrition side has to round.
 * Truncation was rejected for the reason {@code EffectHooks.scaleAmount} exists on
 * the XP side -- {@code (int)(2 * 0.25f)} is 0, and quietly zeroing every small food
 * is a different effect from reducing it. Saturation is a {@code float} and is scaled
 * exactly.
 *
 * <table border="1">
 *   <caption>the reduction as it actually lands</caption>
 *   <tr><th>food</th><th>nutrition</th><th>cursed</th><th>saturation mod</th><th>cursed</th></tr>
 *   <tr><td>golden carrot</td><td>6</td><td><b>2</b></td><td>1.2</td><td><b>0.3</b></td></tr>
 *   <tr><td>cooked beef</td><td>8</td><td><b>2</b></td><td>0.8</td><td><b>0.2</b></td></tr>
 *   <tr><td>bread</td><td>5</td><td><b>1</b></td><td>0.6</td><td><b>0.15</b></td></tr>
 *   <tr><td>cake slice</td><td>2</td><td><b>1</b></td><td>0.1</td><td><b>0.025</b></td></tr>
 *   <tr><td>dried kelp</td><td>1</td><td><b>0</b></td><td>0.6</td><td><b>0.15</b></td></tr>
 * </table>
 *
 * <p><b>Flagged rather than papered over: a nutrition-1 food restores no hunger at
 * all.</b> Dried kelp is the only vanilla item in that position. A floor of 1 was
 * considered and rejected -- it would make a 2-point food a 50% reduction and a
 * 20-point one a 75% reduction, i.e. exactly the non-uniformity the spec rules out.
 * The saturation half still lands, so the item is not completely inert.
 *
 * <p>Note the reduction happens <b>before</b> vanilla's own clamps, so
 * {@code saturationLevel &lt;= foodLevel} still holds and nothing here can push
 * either field out of its normal range.
 *
 * <p><b>The client predicts the full restoration for one tick.</b>
 * {@code EffectHooks} answers "no effect" client-side by design, so a client-side
 * {@code add} is unscaled and is then corrected by
 * {@code ClientboundSetHealthPacket}. Cosmetic only, and the same known gap Slashed
 * Pockets has.
 *
 * <p>apply()/remove() are empty by design -- see {@link HookEffectBehavior}.
 */
public final class GluttonBehavior extends HookEffectBehavior {

	public static final String ID = "glutton";

	/** Food and saturation restore at 25% of normal, i.e. 75% less. */
	public static final float MULTIPLIER = 0.25f;

	/**
	 * Nutrition after the reduction, rounded rather than truncated.
	 *
	 * <p>Minecraft-free and static so the rounding table above is driven by the
	 * harness against the shipped constant rather than restated in prose.
	 */
	public static int reduceNutrition(int nutrition) {
		return Math.round(nutrition * MULTIPLIER);
	}

	/** Saturation after the reduction. A float, so this is exact. */
	public static float reduceSaturation(float saturation) {
		return saturation * MULTIPLIER;
	}
}
