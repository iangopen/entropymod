package com.entropymod.entropy;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * Base class for effects that are a permanent vanilla status effect -- Night
 * Vision, Resistance, Poison, Bad Omen and the rest of the Tier 3 status family.
 *
 * <p>A fourth shape alongside attribute, hook and tick service, and the reasoning is
 * finding 1 in CLAUDE.md: <b>grant real vanilla {@code MobEffectInstance}s rather
 * than imitating them.</b> Everything the effect does -- the icon, the client
 * rendering, the per-tick healing or damage, the interaction with milk and with
 * other sources of the same effect -- is vanilla's, and none of it is reimplemented.
 *
 * <h2>Refreshed, never infinite</h2>
 *
 * <p>The grant is {@value #DURATION_TICKS} ticks, re-granted every
 * {@value #REFRESH_INTERVAL_TICKS} by {@code StatusEffectService}. It is
 * deliberately <b>not</b> {@code MobEffectInstance.INFINITE_DURATION}, for the same
 * reason attribute modifiers are transient rather than permanent: active effects are
 * written into the player's own NBT, so an infinite one would make the save file a
 * second source of truth beside {@link AcquiredEffects} and would outlive the mod
 * being removed. A finite, refreshed grant is derived state -- stop refreshing and
 * it is gone within {@value #DURATION_TICKS} ticks.
 *
 * <p>That is the Danger Sense pattern ("refresh-and-expire beats add/remove when you
 * don't own the state"), and it composes the same way: {@code MobEffectInstance
 * .update} keeps the stronger or longer of two instances, so a potion the player
 * drank is never shortened or weakened by a refresh.
 *
 * <h2>Why 600 and 100</h2>
 *
 * <ul>
 *   <li><b>The remaining duration never drops below 500.</b> Night Vision's client
 *       rendering starts to blink once fewer than 200 ticks remain, and Darkness
 *       and Blindness fade over their final 20 (CLAUDE.md 1aa) -- both read
 *       <em>remaining</em> duration, so the floor has to clear them with margin or
 *       a "permanent" effect visibly pulses every refresh.</li>
 *   <li><b>Periodic effects keep an even cadence across a refresh.</b> Vanilla fires
 *       them on {@code duration % interval == 0} (Poison I every 25 ticks,
 *       Regeneration I every 50). The duration cycles 600 -> 501 and both 600 and
 *       100 are multiples of 25 and 50, so resetting the counter never skips or
 *       doubles a tick: Poison lands exactly four times per refresh, Regeneration
 *       exactly twice. A refresh interval that was not a multiple of the effect's
 *       own period would make the damage rate drift.</li>
 *   <li><b>Milk is five seconds of relief at most</b>, which is what
 *       {@value #REFRESH_INTERVAL_TICKS} ticks means for a curse.</li>
 * </ul>
 *
 * <h2>Idempotency</h2>
 *
 * <p>{@code apply} is "set to X": {@code addEffect} on an instance already present
 * updates it rather than adding a second one. It runs on the pick and on every
 * respawn -- which is what restores the effect after a death clears it, without
 * waiting up to a full refresh interval.
 *
 * <p>{@code remove} does nothing, on purpose. A blanket {@code removeEffect} would
 * also strip the same effect granted by a potion or a beacon, and this class has no
 * way to tell whose it is; expiry handles it within {@value #DURATION_TICKS} ticks.
 */
public abstract class StatusEffectBehavior implements EffectBehavior {

	/** Length of each grant. 30 seconds. */
	public static final int DURATION_TICKS = 600;

	/** How often the grant is renewed. 5 seconds. */
	public static final int REFRESH_INTERVAL_TICKS = 100;

	private final Holder<MobEffect> effect;
	private final int amplifier;

	/**
	 * @param effect    the vanilla effect
	 * @param amplifier zero-based, as vanilla stores it: 0 displays as level I
	 */
	protected StatusEffectBehavior(Holder<MobEffect> effect, int amplifier) {
		this.effect = effect;
		this.amplifier = amplifier;
	}

	public Holder<MobEffect> effect() {
		return effect;
	}

	public int amplifier() {
		return amplifier;
	}

	@Override
	public void apply(EffectContext ctx) {
		grant(ctx.target());
	}

	@Override
	public void remove(EffectContext ctx) {
		// Intentionally empty -- see the class javadoc. Expiry is the removal.
	}

	/**
	 * Grants or renews the effect. ambient = true and visible = false: this is a
	 * standing property of the run rather than a potion, so it should not trail
	 * particles off the player for the rest of the game. The icon stays, because a
	 * status the player cannot see they have is the "is it even on" failure.
	 */
	public void grant(ServerPlayer player) {
		player.addEffect(new MobEffectInstance(effect, DURATION_TICKS, amplifier, true, false, true));
	}
}
