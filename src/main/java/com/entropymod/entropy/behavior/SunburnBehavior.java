package com.entropymod.entropy.behavior;

import com.entropymod.entropy.HookEffectBehavior;

/**
 * Sunburn (BAD / SURVIVAL, entropy 61-100) -- daylight sets you on fire, like the
 * undead, unless you wear a helmet.
 *
 * <p>The rule is vanilla's own, not an imitation of it. {@code Mob.isSunBurnTick()}
 * (javap-verified) is: server side, the level's {@code MONSTERS_BURN} environment
 * attribute is true at the mob's position, the mob is not in water, rain or powder
 * snow, and {@code level.canSeeSky} holds at its eye position; {@code Mob.burnUndead}
 * then spares a mob with anything in its {@code sunProtectionSlot()} and otherwise
 * calls {@code igniteForSeconds(8.0f)}. {@link #shouldIgnite} is those same
 * conditions as one Minecraft-free function, so the harness drives every row of the
 * table, and {@code SunburnService} is the thin shell that reads them off the world.
 *
 * <p>Two deliberate differences from a zombie, both in the player's favour:
 * <ul>
 *   <li><b>No brightness roll.</b> Vanilla also requires
 *       {@code random * 30 < (brightness - 0.4) * 2}, which makes a zombie catch fire
 *       a few seconds after sunrise rather than on the first tick. Dropping it makes
 *       the effect deterministic -- "in the sun without a hat" always burns -- which
 *       is what makes it legible, and the service's own one-second cadence stands in
 *       for the delay.</li>
 *   <li><b>The helmet is not damaged.</b> Vanilla wears a zombie's helmet down until
 *       it breaks. Here the helmet is a complete and permanent answer: any item in
 *       the head slot, a carved pumpkin included.</li>
 * </ul>
 *
 * <p><b>Counterplay is true and is four separate things:</b> a helmet, a roof, water
 * or rain, and night. It is still an escalation over the 41-60 tier because it turns
 * the open sky -- the safest place in the game by day -- into a hazard, and because
 * it composes with what a long run already holds: with Flamboyant, stepping into
 * sunlight bare-headed is death; with Tinderbox each ignition lasts four times as
 * long. Salamander Blood's Fire Resistance cancels the damage outright.
 *
 * <p>A {@link HookEffectBehavior}: nothing is applied to the player, membership in
 * the acquired set is the effect, and the service does the rest.
 */
public final class SunburnBehavior extends HookEffectBehavior {

	public static final String ID = "sunburn";

	/** How often the sky is checked. One second -- the stand-in for vanilla's brightness roll. */
	public static final int CHECK_INTERVAL_TICKS = 20;

	/** Vanilla's own {@code burnUndead} value, passed to {@code igniteForSeconds}. */
	public static final float IGNITE_SECONDS = 8.0f;

	/**
	 * Whether this check should set the player alight.
	 *
	 * @param monstersBurn  the level's {@code MONSTERS_BURN} environment attribute at
	 *                      the player's position AND {@code Level.isBrightOutside()} --
	 *                      i.e. the sun is up in a dimension where the undead burn
	 * @param canSeeSky     {@code level.canSeeSky} at the eye position
	 * @param wet           in water, rain or powder snow
	 * @param headCovered   anything at all in the head slot
	 * @param exempt        creative or spectator
	 */
	public static boolean shouldIgnite(boolean monstersBurn, boolean canSeeSky, boolean wet,
									   boolean headCovered, boolean exempt) {
		return monstersBurn && canSeeSky && !wet && !headCovered && !exempt;
	}
}
