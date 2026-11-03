package com.entropymod.entropy.behavior;

import com.entropymod.entropy.HookEffectBehavior;

/**
 * Loose Footing (BAD / DEBUFF, entropy 25-50) -- sneaking no longer stops you
 * walking off a ledge.
 *
 * <h2>The vanilla mechanism is one overridden method, and nothing else</h2>
 *
 * <p>Crouch-edge safety is <b>not</b> spread through the movement code. It is
 * {@code Player.maybeBackOffFromEdge(Vec3, MoverType)}, called from
 * {@code Entity.move} before the vector is applied, and its guard is:
 *
 * <pre>
 *   if (!abilities.flying &amp;&amp; vec.y &lt;= 0.0
 *       &amp;&amp; (type == MoverType.SELF || type == MoverType.PLAYER)
 *       &amp;&amp; isStayingOnGroundSurface()          // == isShiftKeyDown(), offset 39
 *       &amp;&amp; isAboveGround(maxUpStep())) { ...clamp x and z... }
 *   return vec;                                   // offsets 53-54, the pass-through
 * </pre>
 *
 * <p>The body then walks {@code x} and {@code z} toward zero in 0.05 steps while
 * {@code canFallAtLeast(...)} reports a drop. <b>It reads and writes nothing but
 * the returned vector</b> -- no field, no flag, no side effect -- so returning the
 * vector unchanged is byte-for-byte the state the guard's own {@code return vec}
 * produces for a player who is not sneaking. There is no partial state to leave
 * behind and nothing to restore.
 *
 * <h2>Everything else sneaking does is untouched, by construction</h2>
 *
 * <p>The other behaviours live on completely different paths and none of them
 * consults this method:
 *
 * <table border="1">
 *   <caption>what sneaking still does</caption>
 *   <tr><th>behaviour</th><th>where it lives</th></tr>
 *   <tr><td>reduced hitbox and eye height, the crouch pose</td>
 *       <td>{@code Entity.refreshDimensions} + {@code Pose.CROUCHING}</td></tr>
 *   <tr><td>slower movement</td>
 *       <td>{@code Player.getFrictionInfluencedSpeed}'s crouch factor</td></tr>
 *   <tr><td>hidden from mobs (0.8x detection)</td>
 *       <td>{@code LivingEntity.getVisibilityPercent}</td></tr>
 *   <tr><td>place a block against a chest instead of opening it</td>
 *       <td>{@code Player.isSecondaryUseActive} -&gt; {@code UseOnContext}</td></tr>
 *   <tr><td>holding on to a ladder, scaffolding, honey or powder snow</td>
 *       <td>each block's own {@code entityInside} / climb handling in {@code travel}</td></tr>
 *   <tr><td>not being pushed off by other entities</td>
 *       <td>{@code Entity.push} / {@code isPushable}</td></tr>
 * </table>
 *
 * <p><b>Including "not falling off while building".</b> That phrase covers two
 * different things and only one of them is this method: standing on the lip of a
 * block placing outward is edge-stop and <em>is</em> removed, which is the effect;
 * sneaking to place a block <em>against</em> a chest or a furnace rather than
 * opening it is {@code isSecondaryUseActive} and is untouched.
 *
 * <h2>Why it is the outermost override</h2>
 *
 * <p>Checked rather than assumed, per the subclass-override trap: <b>{@code Player}
 * declares it and {@code Avatar}, {@code ServerPlayer}, {@code AbstractClientPlayer}
 * and {@code LocalPlayer} declare none.</b> So a {@code Player} mixin is reached for
 * every player on both sides.
 *
 * <p><b>And both sides are needed.</b> The guard accepts {@code MoverType.PLAYER} as
 * well as {@code SELF}, and {@code PLAYER} is the type
 * {@code ServerGamePacketListenerImpl.handleMovePlayer} moves a {@code ServerPlayer}
 * with -- so the server runs this too, and a client-only cancel would be clamped
 * straight back by the server's own edge-stop on the next position update.
 *
 * <p>Splitting the gate across two mixins is safe here in the way Earthquake's
 * immobility pair is and Slippery Grip's original pair was not: <b>both halves only
 * ever cancel, neither adds or removes anything</b>, so no ordering question exists.
 * The side scoping is discipline, not a fix.
 *
 * <p>apply()/remove() are empty by design -- see {@link HookEffectBehavior}.
 */
public final class LooseFootingBehavior extends HookEffectBehavior {

	public static final String ID = "loose_footing";
}
