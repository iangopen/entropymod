package com.entropymod.entropy.behavior;

import com.entropymod.entropy.HookEffectBehavior;

/**
 * Slick (BAD / MOVEMENT, entropy 25-50) -- every block you stand on behaves like
 * ice.
 *
 * <h2>Friction is a real per-block float, and there is exactly one read of it</h2>
 *
 * <p>This is <b>not</b> an imitation of ice. {@code BlockBehaviour} carries a
 * {@code protected final float friction} (default <b>0.6</b>, set to <b>0.98</b>
 * by ice, packed ice and frosted ice), and
 * {@code LivingEntity.travelInAir(Vec3)} reads it at offset 23:
 *
 * <pre>
 *   float f = onGround()
 *           ? level().getBlockState(getBlockPosBelowThatAffectsMyMovement())
 *                    .getBlock().getFriction()
 *           : 1.0f;                                    // fstore_3
 * </pre>
 *
 * <p>So the effect is one value forced onto one local, and everything ice does
 * follows from vanilla's own unchanged code.
 *
 * <h2>The local has TWO consumers, and changing one is failure mode 2</h2>
 *
 * <table border="1">
 *   <caption>what slot 3 feeds</caption>
 *   <tr><th>offset</th><th>use</th><th>meaning</th></tr>
 *   <tr><td>31-36</td><td>{@code f * 0.91f} -&gt; slot 4</td>
 *       <td>the horizontal velocity <b>decay</b> applied every tick</td></tr>
 *   <tr><td>38-41</td><td>{@code handleRelativeFrictionAndCalculateMovement(vec, f)}</td>
 *       <td>-&gt; {@code getFrictionInfluencedSpeed(f) = getSpeed() * (0.21600002f / f*f*f)},
 *           the <b>acceleration</b></td></tr>
 * </table>
 *
 * <p>Ice is slippery because of <em>both</em>: you accelerate far more slowly
 * <em>and</em> you stop far more slowly. Modifying only the argument at the second
 * call site -- the tidier-looking {@code @ModifyArg} -- would have changed
 * acceleration and left the decay at vanilla's 0.546, which is a player who is
 * merely sluggish rather than one who slides. That is exactly the "modify a value,
 * miss a consumer" trap, so the <b>local itself</b> is overwritten before either
 * read.
 *
 * <h2>What 0.98 actually produces</h2>
 *
 * <table border="1">
 *   <caption>per-tick horizontal model, walking (input acceleration 0.1)</caption>
 *   <tr><th></th><th>friction</th><th>accel factor {@code 0.216/f^3}</th>
 *       <th>decay {@code f*0.91}</th><th>steady speed</th>
 *       <th>coast distance</th><th>ticks to settle</th></tr>
 *   <tr><td>ordinary ground</td><td>0.60</td><td>1.0000</td><td>0.5460</td>
 *       <td>4.4053 b/s</td><td>0.485 blocks</td><td>6</td></tr>
 *   <tr><td><b>Slick / ice</b></td><td><b>0.98</b></td><td><b>0.2295</b></td>
 *       <td><b>0.8918</b></td><td>4.2421 b/s</td><td><b>1.960 blocks</b></td>
 *       <td><b>27</b></td></tr>
 * </table>
 *
 * <p><b>Top speed is almost unchanged, and that is the point</b> -- the two changes
 * very nearly cancel at steady state (within 4%), which is why ice does not make
 * you fast or slow, it makes you unable to start or stop.
 *
 * <p><b>The felt magnitude is the overshoot: 1.96 blocks against 0.49, a 4.0x
 * slide, taking 27 ticks to settle instead of 6.</b> Sliding two full blocks past
 * where you stopped steering is a discrete, visible, self-evidently-caused event,
 * which is what clears the perceptual-threshold bar that Magnetic Boots and the
 * first Blight Touched failed. Note the overshoot is produced by the <em>decay</em>
 * term specifically -- so a version of this effect that changed only the
 * acceleration would have kept the whole speed story and delivered none of the
 * slide.
 *
 * <p><b>Ice, not blue ice.</b> Blue ice is 0.989, which would roughly double the
 * coast again; the spec is "behaves like ice", so this is ice's own constant.
 *
 * <h2>Only while on the ground</h2>
 *
 * <p>Vanilla substitutes a literal {@code 1.0f} for the airborne case, and that
 * value is still fed to the {@code f * 0.91f} decay. Overwriting it with 0.98
 * would add air drag the effect does not claim, so the override is gated on
 * {@code onGround()} -- the same condition vanilla's own branch uses.
 *
 * <p>apply()/remove() are empty by design -- see {@link HookEffectBehavior}.
 */
public final class SlickBehavior extends HookEffectBehavior {

	public static final String ID = "slick";

	/**
	 * Vanilla ice's own friction, read out of {@code Blocks}' class initialiser
	 * ({@code ldc_w 0.98f} immediately before
	 * {@code BlockBehaviour$Properties.friction(F)}) rather than typed from memory.
	 */
	public static final float ICE_FRICTION = 0.98f;

	/** {@code BlockBehaviour.Properties}' default, for the comparison above. */
	public static final float DEFAULT_FRICTION = 0.6f;

	/**
	 * The friction this player's ground should report, given what vanilla was
	 * about to use.
	 *
	 * <p>Minecraft-free and static so the harness can drive the rule directly: the
	 * two mixins that call it need a live {@code LivingEntity}, and the spawn
	 * cluster's post-mortem is the record of what happens when the only testable
	 * component is the one that already worked.
	 *
	 * @param vanillaFriction what {@code travelInAir} computed
	 * @param onGround        vanilla's own branch condition
	 * @param hasEffect       whether this player holds Slick
	 */
	public static float frictionFor(float vanillaFriction, boolean onGround, boolean hasEffect) {
		return hasEffect && onGround ? ICE_FRICTION : vanillaFriction;
	}
}
