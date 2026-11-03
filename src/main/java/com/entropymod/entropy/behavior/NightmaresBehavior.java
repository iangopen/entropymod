package com.entropymod.entropy.behavior;

import com.entropymod.entropy.HookEffectBehavior;

/**
 * Nightmares (BAD / UTILITY, entropy 25-50) -- your beds explode, everywhere.
 *
 * <h2>Vanilla already owns this mechanic; the effect flips one boolean</h2>
 *
 * <p>"Beds explode outside the Overworld" is not hardcoded to a dimension in this
 * version -- it is a <b>data-driven environment attribute</b>.
 * {@code BedBlock.useWithoutItem} reads it at offset 57:
 *
 * <pre>
 *   BedRule rule = level.environmentAttributes()
 *                       .getValue(EnvironmentAttributes.BED_RULE, pos);
 *   if (rule.explodes()) {                            // offsets 73-78
 *       rule.errorMessage().ifPresent(player::displayClientMessage);
 *       level.removeBlock(pos, false);                             // the head
 *       ... removeBlock(neighbour) if it is also a bed ...         // the foot
 *       level.explode(null, level.damageSources().badRespawnPointExplosion(centre),
 *                     null, centre, 5.0F, true, ExplosionInteraction.BLOCK);
 *       return InteractionResult.SUCCESS_SERVER;
 *   }
 * </pre>
 *
 * <p>So redirecting the single {@code BedRule.explodes()} call inside that method
 * reuses <b>the entire branch verbatim</b>: both bed halves removed, vanilla's own
 * {@code badRespawnPointExplosion} damage source (and therefore its death message),
 * power <b>5.0</b>, fire on, and {@code ExplosionInteraction.BLOCK} so the block
 * rules are vanilla's. <b>Nothing about the explosion is built here</b> -- this mod
 * makes no {@code Level.explode} call for this effect at all, which is why it is
 * the cheapest build in its batch and why it cannot drift from vanilla's behaviour.
 *
 * <h2>Scope: the holder's bed use, not the world's beds</h2>
 *
 * <p>The redirect returns {@code original || holderHasEffect}. Three consequences,
 * all deliberate:
 *
 * <ul>
 *   <li><b>Per player, not global.</b> The {@code Player} is parameter 4 of the
 *       enclosing method, and a redirect handler may capture the enclosing
 *       parameters after its own, so the acquired-set lookup is on the player who
 *       actually clicked the bed. A second player on the same world sleeps
 *       normally in the same bed.</li>
 *   <li><b>Server-only, with no side scoping needed.</b> {@code useWithoutItem}'s
 *       first act is {@code if (level.isClientSide) return SUCCESS_SERVER}
 *       (offsets 0-10), so the redirected call is never reached on the client.</li>
 *   <li><b>{@code original} is preserved rather than replaced.</b> The Nether and
 *       the End keep exploding for everyone, including their error message, and a
 *       datapack that gives a custom dimension an exploding {@code BedRule} still
 *       works. The effect only ever <em>adds</em> the outcome.</li>
 * </ul>
 *
 * <p>Since every non-Overworld dimension already explodes, "explodes in the
 * Overworld" and "always explodes for the holder" are the same rule -- and the
 * second is the one that cannot go stale when a dimension is added.
 *
 * <p><b>Setting spawn is not separately blocked, and does not need to be.</b>
 * Vanilla's exploding branch returns before {@code startSleepInBed} is reached, so
 * the bed is destroyed instead of becoming a respawn point -- the same coupling the
 * Nether has.
 *
 * <p><b>{@code BedBlock} has no subclasses</b>, so the subclass-override trap does
 * not apply; the sixteen bed colours are sixteen instances of this one class,
 * distinguished by a {@code DyeColor} field.
 *
 * <h2>Why counterplay is true despite the explosion being lethal</h2>
 *
 * <p>A 5.0-power blast at point-blank is fatal from full health. The flag has always
 * meant "an in-game answer exists", not "cannot kill you" -- and here the answer is
 * total and entirely under the player's control: <b>do not click a bed.</b> The
 * trigger is a deliberate, single, non-time-pressured action that vanilla has
 * already trained every player to associate with an explosion, and the effect's
 * registry description says so outright. Flamboyant is the precedent: registered
 * {@code counterplay = true} while killing outright, because fire is avoidable.
 * Nothing here fires on a timer or without the player's own input, which is what
 * separates it from Unstable and Earthquake -- the two effects that had to move to
 * 40-60 with {@code counterplay = false}.
 *
 * <p>apply()/remove() are empty by design -- see {@link HookEffectBehavior}.
 */
public final class NightmaresBehavior extends HookEffectBehavior {

	public static final String ID = "nightmares";
}
