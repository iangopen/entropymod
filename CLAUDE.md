# CLAUDE.md — Entropy Mod

Living source of truth for this project. Read this before touching code. If you
resolve one of the open questions below, update this file in the same session —
don't let decisions live only in chat history.

## One-liner

Fabric mod for MC 26.1.2. Every interval (default 3 min), pick 1 of 3 random
Blessings, then next interval 1 of 3 random Curses, alternating forever.
Every pick = +1 Entropy. Goal: beat the Ender Dragon before Entropy hits the
cap (default 100).

---

## Part 1: Core architecture — the rules that hold everywhere

*(Per-component status and known gaps moved to the `entropy-design` skill.
Per-effect implementation findings moved to the `entropy-effects` skill.)*

### Effect execution — BUILT, and now with real content
Picking an effect routes through a per-effect class in
`com.entropymod.entropy.behavior`. **As of the permanent-effects session these
are no longer stubs** — all 20 Tier 1 effects change real game state.

**Adding a new effect is exactly three things, and nothing else:**

1. one `register(...)` line in `EffectRegistry`
2. one new class in `com.entropymod.entropy.behavior`
3. one `register(...)` line in `EffectBehaviors`

Most new effects should extend `AttributeEffectBehavior` and be ~10 lines. If a
new effect makes you edit a fourth file, that's a signal the abstraction is
missing something; fix the abstraction rather than the caller.

- `EffectBehavior` — `apply(EffectContext)` / `remove(EffectContext)`. Per
  Open Question 9 this is option (b), one class per effect, not a big switch.
- `EffectContext` — wraps the server, the `EffectDefinition`, **a specific
  target `ServerPlayer`**, and a `Reason` (`PICKED` vs `REAPPLIED`). The target
  is not optional: re-application happens one player at a time, and the old
  "first player in the list" shortcut would have buffed the wrong player the
  moment a second one existed.
- `EffectBehaviors` — id → behavior map. Ids are matched to `EffectRegistry` by
  **string**, which the compiler cannot check, so each behavior exposes an `ID`
  constant that both files use, and `validate()` reports both directions of
  mismatch at init.

**`apply` MUST be idempotent.** This is the most important rule in the codebase
now and the easiest to break. `apply` runs once on the pick and then *again on
every respawn, rejoin and dimension change* — an unbounded number of times.
`AttributeEffectBehavior` gets this right via `addOrUpdateTransientModifier`;
see the `entropy-mappings` skill for why `addTransientModifier` is the trap.

### Permanent effects — the architecture shift, and why
**Every effect is permanent. `EffectDefinition` has no duration field at all.**

It used to have `durationTicks`, an int whose value encoded three different
lifetimes: `0` = fire once and never track, `-1` = until the next interval,
`>0` = a tick countdown. That was replaced rather than extended, because an
overloaded magic number cannot tell you which of its meanings is in play at the
point you read it, and every consumer had to branch on all three.

**If temporary effects ever return, they must not return as an int.** Add a
`sealed interface EffectDuration` with `Permanent` and `Ticks(int)` cases so the
compiler forces every call site to handle both. This is written into
`EffectDefinition`'s javadoc as well.

What this deleted, rather than left unreachable:
- `ActiveEffect` — the entire class. Its only job was holding `remainingTicks`
  and the interval-scoped flag.
- `ActiveEffectTracker.tickAndCollectExpired()` and `expireIntervalScoped()`.
- The expiry pass in `EntropyManager.tick()`, which is back to just counting.
- `EffectContext.announceApply()/announceRemove()` — the stub-era scaffolding.
  The pick announcement moved to `EntropyManager`, because it is a statement
  about the *run* ("you chose this"), and behaviors now also run on every
  respawn where announcing would be pure spam.

`ActiveEffectTracker` became **`AcquiredEffects`** — renamed because "tracker"
described countdown machinery that no longer exists. It is an insertion-ordered
`LinkedHashSet<String>` of effect ids and nothing more.

**The important consequence: three concerns collapsed into one set.** With
permanence, "currently active", "already picked", and "which categories are
occupied" are the same fact. They were three separate things under the old
model and are now one, which means they cannot drift apart. Do not reintroduce a
second set for any of them.

### Persistence — BUILT (was: "not built")
`EntropyManager` **is** a `SavedData`, stored on the overworld's
`SavedDataStorage`. This replaced the in-memory
`WeakHashMap<MinecraftServer, EntropyManager>`.

This became mandatory, not nice-to-have, the moment effects became permanent: in
singleplayer, quitting to the title screen stops the integrated server, so
without it every acquired effect and the entire run vanished on a trip through
the main menu. "Effects survive logout/login" cannot be delivered by
re-application alone if the list of what to re-apply is gone.

- Persisted: entropy, pick count, game-over, cap, interval, the acquired-effect
  ids, and the full pick history.
- **Not** persisted, deliberately: `tickCounter` (a saved partial countdown
  would let a player reload repeatedly to sit just below the threshold forever;
  losing at most one interval on load is the better failure) and
  `waitingOnChoice` (a pending choice cannot outlive the client answering it).
- **Every mutation must call `setDirty()`** or it is not written. That is the
  one easy-to-miss rule in the file.
- **Every codec field is `optionalFieldOf` with a default.** That is what lets a
  save from an older build load in a newer one. Adding a *required* field would
  make every existing save unloadable — don't.
- An effect id in a save that this build no longer defines is skipped with a
  warning, not a load failure.

### Re-application across death, relog and dimensions — BUILT
**Verified, not assumed:** vanilla's `ServerPlayer.restoreFrom` calls
`AttributeMap.assignPermanentModifiers` only inside an `ifeq` on its boolean
parameter, and `PlayerList.respawn` passes that parameter straight through —
`false` for a death. A death respawn also constructs a brand-new `ServerPlayer`.
So **attribute modifiers are dropped on death respawn.**

The fix is three hooks in `EntropyMod`, all calling `reapplyAll`:
`ServerPlayerEvents.JOIN`, `ServerPlayerEvents.AFTER_RESPAWN`, and
`ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL` (an ordinary portal
moves the same entity and does *not* go through respawn, so without the third
hook a trip to the Nether would quietly drop everything).

**These hooks are not a safety net — they are the only thing that puts effects
back.** Modifiers are applied *transiently*, deliberately:
`addOrReplacePermanentModifier` would write them into the player's own NBT,
making the save file a second source of truth that could disagree with
`AcquiredEffects` — including leaving orphaned buffs behind if the mod were
removed. Transient means the acquired set is the **only** truth and attributes
are derived state, rebuilt from it every time. The cost is that re-application
is mandatory; that is a good trade for an invariant that cannot drift.

### No-repeat — BUILT, and distinct from anti-stacking
These are two different rules and are easy to confuse. They are applied in
priority order in `EffectRegistry.roll`:

| | No-repeat | Anti-stacking |
|---|---|---|
| Keyed on | effect **id** | effect **category** |
| Scope | the whole run, permanently | while a category is occupied |
| Status | **hard rule**, applied first | **soft rule**, dropped first |

**No-repeat:** once an effect is picked it never appears again for the rest of
the run. Under permanence, being re-offered something you already have is
meaningless — there is nothing to refresh.

**Priority matters and is deliberate.** If both filters together empty the pool,
anti-stacking is abandoned *first* and no-repeat is kept: a second MOVEMENT
effect is a real playable outcome, a duplicate of an effect you already own is
not. Only if no-repeat *alone* empties the pool is it dropped, flagged via
`RollResult.repeatFallback()`.

**The repeat fallback is genuinely reachable today**, not theoretical: with 17
GOOD and 17 BAD effects it fires on the 18th pick of either phase — asserted
exactly, at that pick number, by the headless harness. It is a
tested path, and it is still PLACEHOLDER policy — Open Question 6 remains open,
and more content is the real fix.

A partial result (1 or 2 cards) is not a fallback and is not flagged. It is the
honest answer when the pool is that small, and it is the normal experience for
the last two picks of a phase.


### When a mixin applies cleanly and STILL changes nothing — read this first
The mixin cluster shipped with javap-verified targets and 116 green headless
checks, and two of its five effects did not work in game. Neither was a broken
injection. **"The mixin applied" and "the behaviour changed" are separate
claims, and the project has now been burned by the gap between them.** Three
distinct failure modes, all real, none visible to the build:

1. **A subclass overrides the method you targeted.** Targeting
   `LivingEntity.foo` does nothing for players if `Player` overrides `foo` and
   never calls `super`. Both of these exist here and were missed on the first
   pass: **`Player` overrides `playStepSound`** and **`ServerPlayer` overrides
   `onChangedBlock`**. Both happen to call `super` on the common path, so those
   two mixins survived — that was luck, not design. Before targeting anything on
   `Entity`/`LivingEntity`, check the whole chain and whether the override
   delegates:

   ```bash
   for c in ...player.Player ...entity.Avatar ...level.ServerPlayer; do
     javap -p -cp <jar> $c | grep -i '<methodName>'; done
   ```

2. **Something downstream re-clamps the value you changed.** The Heavy Footsteps
   case above: `getVisibilityPercent` is genuinely multiplied, and the result is
   genuinely thrown away by a second distance check that does not consult it.
   Modifying a value is not the same as changing the outcome — **trace every
   consumer of the value, not just the one you found.**

3. **The change is real but below the perceptual threshold.** Magnetic Boots at
   1.5x moved pickup reach from 1.3 to 1.8 blocks and read as "may work". Not a
   bug; the wrong magnitude, and indistinguishable from a bug from the player's
   seat.

**The diagnostic that actually resolved all of this was `run/logs/latest.log`,
which is in the repo.** It is the single highest-value evidence source in the
project and it beats reasoning from the source every time — it proved the
attribute registered, the effects applied with no error, all 28 behaviors wired,
and the entire history round-trip working. **Read it before forming a
hypothesis.** Useful greps:

```bash
grep -iE "entropymod|Entropy" run/logs/latest.log | tail -60
grep -iE "mixin|inject|redirect|does not have|Registered custom attribute" run/logs/latest.log
```

Corollary for future effects: **an effect is not done when the mixin applies.**
It is done when a value has been observed to change in game. Prefer effects whose
result is directly measurable, and when a magnitude is chosen, sanity-check what
it means in blocks/hearts/seconds before shipping it.

### Two findings from the survival-batch revision that generalise
*(Derivations, tables and javap evidence are in the `entropy-effects` skill, under
Slippery Grip and Phoenix Chambered Heart. These are here because they are not
really about those effects.)*

**1. Grant real vanilla `MobEffectInstance`s rather than imitating them.** Phoenix
Chambered Heart's death save grants Regeneration/Health Boost/Absorption X instead
of setting a health number: **+40.0 max health, 40.0 absorption granted filled,
1.0 HP healed every tick**. The part that generalises is **expiry** —
`LivingEntity.refreshDirtyAttributes()` → `onAttributeUpdated` clamps health and
absorption down when their maxima fall, so **there is no mod-side timer and
nothing to clean up**, and the grants compose with the permanent `MAX_HEALTH`
effects because each modifier carries its own `Identifier`. A hand-rolled version
would have had to get all of that right by hand.

**1b. Changing an attribute is not the same as changing a mechanic — sweep for
every branch that grants the same thing.** Slippery Grip halved sprint speed
through `MOVEMENT_SPEED` and shipped; sprint-jumping ignored it almost entirely.
No injection was broken. Vanilla rewards sprinting in **three** independent
places, and the attribute is only one: `jumpFromGround` adds a **flat 0.2
blocks/tick** forward impulse that reads no attribute, and
`Player.getFlyingSpeed` returns `0.02 -> 0.025999999`. The second is the one that
matters most, because `getFrictionInfluencedSpeed` is
`onGround() ? getSpeed() * ... : getFlyingSpeed()` — **while airborne
`MOVEMENT_SPEED` is not consulted at all**, so for the whole of every jump the
compensator was inert rather than merely outvoted. Measured, the curse's sprint
was 2.16 b/s on the ground and **6.33 b/s while jumping — faster than walking**,
i.e. inverted into a reason to sprint.

This is a **fourth** failure mode alongside the three above, and the diagnostic
that finds it is not a log: it is `javap | grep isSprinting` (or whatever the
condition is) across the *whole* movement path, before writing anything. The fix
generalises too — scale **every** branch by one shared factor read from the live
value, never a second constant. Vanilla's horizontal movement is linear and
homogeneous in (ground accel, air accel, impulse), so one factor scales every
sprinting motion by exactly that factor and preserves vanilla's own internal
ratios (sprint-jumping stays worth the same 27% over flat sprinting).

**1c. An entity query is O(entities); a block sweep is O(volume).** Danger Sense's
32-block radius is a 274,625-position box — 56x Green Thumb's — and is nearly free,
because `getEntitiesOfClass` is served by entity section storage. Never reason
about a proximity effect's cost from its radius without first asking which of the
two it is. Corollary: the query is a *box* and effects are *spheres*, so a missing
`distanceToSqr` filter silently leaks the range by 73% (`r × √3`).

**1d. A tick-driven effect must model its SAMPLING POINT, not the idealised event
order.** Double Jump shipped with a green harness that fed a state
`END_CLIENT_TICK` can never observe: `aiStep` calls `jumpFromGround` at offset 460
but `travel`/`move` — which writes `onGround` — at 615, so on the jump tick
`onGround()` already reads false. The logic was right and wrong in place. Establish
what your hook observes relative to the vanilla code you care about **by offset**,
then test that order. (Fifth failure mode; unlike the others it is not about mixins.)

**1e. A mob's ground acceleration is its `MOVEMENT_SPEED` SQUARED, and its real
`FOLLOW_RANGE` is not the attribute's default.** `Mob.setSpeed(f)` also calls
`setZza(f)`, so a mob's forward input is its speed rather than a player's
normalised 1.0 — read a creeper's 0.25 the player way and you overstate it 4x.
Separately, `Attributes.FOLLOW_RANGE` defaults to 32.0 but
`Mob.createMobAttributes()` overrides it to 16.0. **Always read a mob's attributes
from `DefaultAttributes.getSupplier(EntityType.X)`, never from the attribute.**
Spawning anything also means reusing `SafeSpawn` — see `entropy-effects`.

**1f. The component you can test is not the component that breaks.** Unstable and
Creeper Magnet shipped with the cadence asserted over 200 consecutive cycles and
failed in play as "fired once, then silence" — the schedules were perfect and
`SafeSpawn` was returning null on three triggers in four, because every line of it
needed a `ServerLevel` and none of it was harness-reachable. **Split a scheduled
effect's pure decisions out as Minecraft-free statics so they can be driven, and
make every failure path name its own cause in the log** — the diagnosis here
needed the world save's heightmap only because the log said *that* it failed and
not *why*. See `entropy-effects`.

**1g. Model a mechanic in the quantity the GAME uses, and log what shipped.**
Unstable's spawn band was measured horizontally while vanilla's explosion damage
reads the 3D distance and discards anything past `2 * radius` — so a "5-7 block"
TNT could sit 10.63 blocks away, and 27.6% of spawns did literally nothing. Its
damage table also assumed full exposure, when `ServerExplosion.getSeenPercent`
multiplies `impact` by an occlusion fraction that reaches 0 in ordinary terrain.
**Both drive `impact` to 0, and the formula's trailing `+1` then IS the result —
1.0 damage, which is exactly what got reported.** Only failures were logged, so
there was no record of the distance actually achieved; a tuned number never
compared against what shipped is a number nobody is checking. See
`entropy-effects`.

**1h. Idempotency for an ENTITY-SPAWNING effect is a different property.** Every
effect before the companion cluster rebuilt *derived* state, so "apply ten times,
assert the value didn't move" was the test. Companions are *world state that
already exists*: re-running `apply` is fifteen more wolves. The careful-looking fix
— record UUIDs, resolve them, top up the shortfall — is a bug, because
`ServerLevel.getEntity(UUID)` only finds **loaded** entities, so a player logging in
far from their pack gets a fresh set every relog. **Spawn only on
`EffectContext.isFreshPick()`**, which makes "no duplicates" structural rather than
dependent on a lookup succeeding. See `entropy-effects`.

**1t-1y. Three findings from the Tier 2 combat batch, all in `entropy-effects`.** The
damage-DEALT hook is not the damage-taken hook reversed — only
`CombatTracker.recordDamage`'s argument, inside `actuallyHurt`, is the post-mitigation
number. Vanilla's only true-damage precedent is **sonic boom, not the void**:
`#bypasses_enchantments` has exactly one entry, so void damage is still reduced by
Protection. And `LivingEntity.INVULNERABLE_DURATION` puts a **10-tick floor** under any
effect that promises two hits — closer than that and the second is discarded silently.

**1af/1ag. NO attribute value can immobilise a player, and the lock belongs on the
CLIENT.** `MOVEMENT_SPEED`'s floor really is 0.0 and Slowness 6 reaches it — that is not
the obstacle. A player at zero still jumps and bunny-hops, because `getFlyingSpeed`
(hardcoded `0.02`/`0.026`), `jumpFromGround`'s flat `+0.2` sprint impulse and
`JUMP_STRENGTH` read no `MOVEMENT_SPEED` at all. Use **`Player.isImmobile()`**, vanilla's
own lock: it zeroes `jumping`/`xxa`/`zza` in `aiStep` between `applyInput` (218) and both
consumers (460, 516), which is a **movement-only** lock — attack/mine/interact run off a
different path entirely. It must be **client-side**: `ServerPlayer` never writes those
fields, and vanilla's own `handleMovePlayer` sleeping branch is only a generous
snap-back backstop behind the client lock. See `entropy-effects` 1af-1ah, including why
"blind for the whole 80-tick window" has to be granted as **101**.

**1ac/1ad. TWO ways to ship a cue the TARGETED player never sees, with a green build.**
Both were hit on Earthquake's particles; both are in `entropy-effects`, flagged. **A
`levelEvent` `data` int is an untyped per-event encoding** — for `PARTICLES_SMASH_ATTACK`
it is very nearly a literal particle count, and shipping `2` where vanilla's own caller
passes **750** rendered three particles, two of them 3.5 blocks away. Read the client's
`switch` case for the units *and* vanilla's caller for the scale; the second is the one
that catches it. Separately, **`levelEvent` and `playSound` both take a player to
EXCLUDE** as their first argument (`PlayerList.broadcast`: `if (player == except)
continue`), because vanilla's usual caller is a client-predicted action. Nothing a mod
schedules server-side is predicted, so **it must be `null`** — passing the affected
player delivers the cue to every bystander and never to them. `levelEvent`'s 3-arg
overload is safe by construction; **`playSound` has no safe overload.** Regression-tested
by `Checks.methodPushesExplicitNull`, which decodes bytecode rather than scanning it.

**1aj-1am. Adding a goal is not the inverse of vetoing one, and a MISSING attribute is not
a zero.** Beast Whisperer's shape does not transfer to Beast Wrath: vetoing a goal that has
already resolved a target needs only a predicate, while *adding* aggression needs the goals
to exist, an attribute they can read, and an install point. `ATTACK_DAMAGE` is absent from
`LivingEntity`/`Mob`/`Animal`'s suppliers alike, so `getAttribute` returns **null** (no
instance for `addOrUpdateTransientModifier` to touch) and `getAttributeValue` **throws** —
and `Mob.doHurtTarget` reads it first thing, so an armed cow without it crashes the tick.
See `entropy-effects` 1aj-1am, including why the install point is `Mob`'s constructor
rather than `registerGoals`.

**1ar-1aw. To change what a block PLACEMENT writes, modify the argument at `place`'s one
`placeBlock` call — never `getPlacementState`** (`StandingAndWallBlockItem` overrides it and
never calls super, so torches, signs and banners would silently escape). Replacing the state
*before* `setBlock` means the original block entity is never created, so there is nothing to
destroy; a chest item carries no contents, and the only real casualty is a **shulker box**,
whose contents live in the item. Separately: **item components are datapack-bound, so a
harness cannot construct an `ItemStack` at all** — design item predicates to take the
component value, not the stack. See `entropy-effects` 1ar-1aw.

**1an-1aq. FISH ARE NOT `Animal`, and an owner you have to RESOLVE is an owner you can
lose.** `Cod -> AbstractFish -> WaterAnimal -> PathfinderMob` never passes through `Animal`,
so a second attribute injection was needed; `Squid.travel(Vec3)` discards its argument
entirely, so no navigation-driven goal can move a squid and it is excluded rather than
faked; and the aquatic melee precedent is **Dolphin** (stock `MeleeAttackGoal`) not Guardian
(a custom laser goal). For "is this mob mine", use `EntityReference.matches` — a bare UUID
compare — never `OwnableEntity.getOwner()`, which resolves through the level and would read
an owner in an unloaded chunk as *no* owner. See `entropy-effects` 1an-1aq.

**1z. The squared speed rule (1e/1l) is a MOB rule — it does NOT apply to players.**
A mob's forward input is its own speed value (`Mob.setSpeed` also calls `setZza`); a
player's comes from `ClientInput` as a normalised 1.0, so player ground speed is
**linear** in `MOVEMENT_SPEED`. Applying the square to a player effect is wrong by
nearly half. See `entropy-effects` for that, for Blindness's 20-tick fade floor, and
for `DUST_PILLAR` as vanilla's own ground-shatter particle.

**1i. `moveTo(Entity, speed)` has NO stop distance -- it paths to the target's own
block.** It is `createPath(entity, 1)`, so a "follow when further than 6" loop still
delivers the follower to within a block of you; the threshold gates only *when a
path is issued*, not where it ends, and nothing stops a path already running. Any
standoff behaviour must navigate to a computed point on a ring and call
`navigation.stop()` on arrival. See `entropy-effects` for the companion hold bands
and the Audience state machine.

**2. A mixin's *shape* is part of its correctness when two sides share a target.**
Slippery Grip's two mixins used to be `@ModifyVariable`s that both forced
`setSprinting`'s argument false, which chained safely **only because both halves
forced the same value**. Making each half *add or remove* an attribute modifier
destroyed that: the common mixin runs on the client too (where `EffectHooks`
answers "no effect" by design) and would delete what the client half just added,
with the winner decided by injection order. **Rule: when a common and a client
mixin target one method, scope each to the side it is the authority for** — here
`player.level() instanceof ServerLevel` and `instanceof LocalPlayer` — so exactly
one ever acts. This failure mode is invisible to the build.


### Pick history — BUILT
`PickRecord` (pick number, phase, effect id/name/description, entropy at the
time) appended in `onChoiceMade`, exposed via `EntropyManager.getHistory()`.

- `entropyAtPick` is the value **before** that pick's +1 — i.e. the number the
  player was looking at when they chose.
- Name and description are stored as strings rather than an id reference on
  purpose: history records what the player actually saw, so it must survive an
  effect being retuned or deleted from the registry in a later version.
- Fetched **on demand** via `HistoryRequestPayload` (C2S, zero bytes) →
  `HistoryResponsePayload` (S2C, list), answered only to the player who asked.
  Deliberately not ridden along on `OpenChoicePayload`, which every pick pays
  for.
- No screen or keybind yet. `/entropyhistory` requests it and the client prints
  it to chat — interim path, replace with a real screen. It logged *only* until
  the diagnostic session, which is why it looked broken in game; see "Debug
  output has to reach the player".
- `EffectPhase`'s wire codec now lives in `network/EntropyCodecs.java` since
  two payloads need it. Sent by name, not ordinal, so reordering the enum
  can't flip GOOD and BAD on the wire.

### Debug commands — know which ones are REAL and which are FAKE
This distinction has already cost one session. Read it before adding another
test command.

| Command | Side | Real pipeline? |
|---|---|---|
| `/entropyforcepick` | server | **YES** — the real path |
| `/entropygrant <effect_id>` | server | **YES** — the real acquisition path |
| `/entropystatus` | server | **YES** — reads real manager state |
| `/entropyhistory` | client → server | **YES** — real request/response, prints to chat |
| `/entropypreview` | client only | **NO** — hardcoded fake data |

#### `/entropygrant` is the standard way to test a specific effect
**Use this before building anything that depends on an effect working.** Rolling
until the one effect you want to test happens to be offered is the friction that
made the last two sessions ship effects that were never observed in game; this
removes it. `/entropygrant green_thumb` and it is on, immediately.

It is a **real** path, held to the same standard as `/entropyforcepick`:
`EntropyManager.grantEffect` writes to the same `AcquiredEffects`, calls the same
`setDirty()`, and dispatches through the same private `applyToAll` that
`onChoiceMade` uses. A granted effect therefore persists across a save, is
re-applied on death/rejoin/dimension change, and occupies its category for
anti-stacking — indistinguishable from having picked it. If a future edit makes
it construct its own `EffectContext` or call a behavior directly, that is a bug.

Three properties that are deliberate, not incidental:

- **It does not advance the run.** Entropy, pick count, the interval timer and
  the history list are untouched. This is checkable rather than asserted:
  `grantEffect`'s bytecode contains **one** call to `applyToAll`, one to
  `acquired.add`, one to `setDirty`, and **zero** references to the `entropy`,
  `pickCount`, `history` or `tickCounter` fields.

  ```bash
  javap -p -c -cp build/classes/java/main com.entropymod.entropy.EntropyManager \
    | awk '/GrantResult grantEffect/{f=1} f&&/^$/{exit} f'
  ```

- **Re-granting is rejected, not re-applied.** Silently applying an
  already-acquired effect a second time would exercise — and therefore could
  hide — exactly the idempotency guarantee the whole respawn/rejoin design rests
  on. The debug tool must not mask the class of bug it exists to find.
- **A bad id is rejected out loud**, with the full list of valid ids sent to
  chat, and the argument tab-completes from `EffectRegistry` live.

**`/entropyforcepick` is the one that actually tests things.** It calls
`EntropyManager.forcePick`, which delegates straight to the private
`triggerPick` the tick loop uses — same entropy-cap check, same interval-scoped
expiry, same phase alternation, same anti-stacking roll, same
`OpenChoicePayload`. It bypasses the clock and *nothing else*. Its guards
(`gameOver`, `waitingOnChoice`) are exactly the ones `tick()` applies before it
would reach `triggerPick`, so a forced pick can't reach a state a real interval
firing couldn't. It returns a `PickTrigger` so the command can say *why* nothing
opened instead of failing silently.

**If a future session makes this command roll its own choices or build its own
payload, that is a bug**, not an optimisation — it would stop testing the thing
it exists to test.

Both server commands need permission level 2 (`LEVEL_GAMEMASTERS`), so **in
singleplayer the world must have Allow Cheats: ON** or they won't appear.

**Why `/entropypreview` got renamed (the trap, recorded so it isn't reset).**
It used to be called `/entropytest`, and the name was actively misleading: it
opens `ChoiceScreen` client-side with three hardcoded effect strings and never
contacts the server. Clicking a card in it sends no `ChoiceMadePayload`, so it
never reaches `onChoiceMade` — no history entry, nothing added to
`AcquiredEffects`, no anti-stacking, no `EffectBehavior` firing. On screen it is **indistinguishable
from a real pick**, which is exactly what made it dangerous: the effect-behavior
architecture session shipped with the reasonable-sounding belief that
`/entropytest` could verify it, and it could not.

It still exists, deliberately — instant colour/layout checks across the whole
entropy range with no world state are genuinely useful, and that's worth a
command. The fix was the name, not the feature.

**The general rule: a debug shortcut that fabricates data must say so in its
name.** If you add another, name it `preview`/`mock`/`fake`, and if it's meant
to test real behavior, make it call the real entry point rather than
reconstructing what the real entry point does.

### How to check this without booting Minecraft
**`./gradlew harness` — the harness is in the repo now** (`src/harness/java`,
its own source set, not wired into `build`). Every previous session rebuilt an
equivalent by hand in a scratch directory and threw it away, which is why the
same numbers kept being re-derived. For what it currently covers, read
`HarnessMain` — the check list lives there and goes stale the moment it is
restated anywhere else.

**The start gate is tested by ticking with a `null` server, and that is the
assertion rather than a shortcut.** `triggerPick` dereferences the server almost
immediately, so a gate that let the loop run would blow up rather than quietly
passing — and the loop is ticked 500 past the interval length to guarantee it
would be reached. The counter staying at 0 is checked separately, because that is
what distinguishes "paused" from "counting invisibly".

**Clumsy Digger's table reads real durabilities out of `ToolMaterial` rather
than hardcoding them** (`ToolMaterial.NETHERITE.durability()`, etc.), which is
what makes it a check rather than a restatement — and it is how the COPPER tier
was noticed. No bootstrap is needed: `ToolMaterial`'s constants are plain records
over `TagKey`s. Copy that approach for anything else keyed on vanilla item data.

**The payload round-trip is the model to copy for any future payload change.** It
runs the real `StreamCodec` against `RegistryAccess.EMPTY` — no bootstrap needed,
because every component codec is a string, varint or enum-by-name — and it checks
**field by field with distinct values**, not with a whole-record `equals`. That
matters concretely here: `entropy` and `entropyCap` are both ints and adjacent, so
a swap would cancel out under equality and pass.

It has already earned its keep once: an earlier session's first run failed on a
hand-derived expected value for blighted wheat (the roll bound at speed 2.5 is
11, not 12). The model was right and the number written next to it was not.

**`Checks.hasMethod` is the counterpart to `Checks.hasConstant`** — it asserts a
retired *code path* was deleted rather than left returning a neutral value. Both
crop-hook retirements are pinned that way. Reach for it whenever a session removes
a mechanic rather than changing one.

Two things about it worth keeping:

- **It reads constants by reflection, never by reference.** `static final`
  primitives are inlined at the *caller's* compile time, so a harness that named
  `GreenThumbBehavior.MULTIPLIER` directly would compare its own compiled-in
  snapshot against itself and pass regardless of what ships.
- **It is not in `build`.** A check that needs updating after a deliberate retune
  must not block a release build; `./gradlew build` and `./gradlew harness` are
  separate answers to separate questions.

`AcquiredEffects`, `PickRecord`, `EffectRegistry` and the whole data model are
**free of Minecraft imports on purpose** — same discipline as `EntropyPalette`
on the client side. A plain `javac`/`java -cp build/classes/java/main` harness
drives the no-repeat rule, both fallbacks, and pool exhaustion against the real
shipped classes. Do that rather than testing a copy.

For anything that *does* need Minecraft classes (codecs, attributes), a harness
can still run headlessly against the full runtime classpath — call
`SharedConstants.tryDetectVersion()` then `Bootstrap.bootStrap()` first or any
registry access throws "Not bootstrapped". That is how the attribute clamps in
the `entropy-effects` skill were established, and how the idempotency claim was
proven against the real `AttributeInstance` rather than a mock.

Three things are worth re-checking with a harness whenever they're touched:

1. **The no-repeat rule and its fallbacks**, including deliberately draining a
   phase's pool — that path is reachable in normal play, not an edge case.
2. **The persistence codec.** A broken one doesn't misbehave subtly, it makes
   the world fail to load. Round-trip it field by field with distinct values,
   and check that a save missing newer fields still loads.
3. **Idempotency.** Apply a modifier ten times and assert the value didn't move.
   This is the guarantee the whole respawn/relog design rests on.

What none of this can prove, and what still needs a real game session: that the
effects are *felt* (speed, hearts, mining rate), that the mixins actually inject
against the live game, that a real death/relog restores everything exactly once,
that the chat messages actually appear, that expiry lands on the right wall-clock
moment in a live server tick loop, and that the history request survives a real
client↔server round trip. `/entropyforcepick` is what makes that session
practical — without it you'd be waiting out a 3-minute interval per pick.

Payload changes still need the field-by-field round-trip check described above;
`HistoryResponsePayload` has been verified that way (a whole-record `equals`
would still pass if two same-typed fields were swapped in a way that cancelled
out).

One structural claim *is* checkable without the game, and worth re-checking if
`forcePick` is ever edited: that it delegates rather than duplicates. Its
bytecode should contain exactly one call, to `triggerPick`, and
`EntropyCommands` should reference nothing but `EntropyManager` accessors:

```bash
javap -p -c -cp build/classes/java/main com.entropymod.entropy.EntropyManager
javap -p -c -cp build/classes/java/main com.entropymod.command.EntropyCommands
```

Note `runServer` needs `run/eula.txt` with `eula=true`, which isn't in the repo
— accepting Mojang's EULA is the repo owner's call, not a tool's.

### Config (interval length, entropy cap) — fields exist, no player-facing surface
`EntropyManager.setIntervalTicks()` / `setEntropyCap()` exist and work, but
there's no world-creation config screen, no GameRule, no command, and no
config file wiring any of them up. Right now the only way to change these is
to edit code and rebuild. See Open Question 5.

---

## Part 2: Design invariants already locked in (don't relitigate these casually)

- Categories for anti-stacking: `MOVEMENT, SURVIVAL, TOOL, COMBAT, GEAR,
  COMPANION, UTILITY, DEBUFF, META` — max 1 active effect per category,
  **enforced by exclusion from the roll pool, not by replacement** (a
  conflicting option never appears in the three cards; picking never silently
  cancels something you already have). *Now enforced in code.*
- Effect behavior is one class per effect (`EffectBehavior` implementations in
  `com.entropymod.entropy.behavior`), never a central switch. New effect =
  new file + two registration lines, zero edits to existing code.
- **All effects are permanent.** No durations, no expiry. `EffectDefinition` has
  no duration field and must not regrow one as a magic int.
- **`EffectBehavior.apply` must be idempotent** — it runs again on every
  respawn, rejoin, and dimension change.
- **No-repeat: an effect picked once never appears again that run.** Distinct
  from anti-stacking (id vs category) and outranks it.
- **`AcquiredEffects` is the single source of truth** for what the player has —
  active set, already-picked set, and category occupancy all read from it, and
  attribute state is derived from it rather than stored alongside it.
- Phase alternates strictly GOOD → BAD → GOOD → BAD, never two of the same
  phase in a row
- Bad effects below entropy 40 must be counterplay-survivable (no
  unavoidable-death effects until later tiers)
- Picking is mandatory — no way to dismiss/skip a pick without choosing
- Fabric is the confirmed loader choice (not Forge/NeoForge/CurseForge —
  CurseForge is a distribution platform, not a loader)
- Effects are data-driven (`EffectDefinition` records), never hardcoded into
  the timer/GUI/networking layer

---


---

## Where the rest of this document lives

This file used to carry every verified finding inline and had grown past the size
where Claude Code warns about a single memory file. The material was **moved, not
deleted** — it now loads on demand instead of in every session.

| Skill | Load it when | What's in it |
|---|---|---|
| `entropy-effects` | Implementing, tuning, renaming or debugging a specific effect; **writing any new mixin**; **spawning any entity** | Attribute clamps and floors, the javap-verified mixin target table, the `@Shadow` crash trap, Frost Walker's data-driven finding, `SafeSpawn`/`SpawnSchedule` (the shared groundwork every spawn-based effect must reuse), and the full derivation behind every shipped effect's constants |
| `entropy-design` | Planning what to build next, deciding scope, resolving an open question | Per-component status and gaps, the run lifecycle, the three paths to `ENDED`, and all open design questions |
| `entropy-mappings` | **Writing any Minecraft API code**; anything that won't compile; a method that "does not override" what it should | The Yarn→Mojang name table, the changed screen/HUD render pipeline, verified GUI/command/attribute signatures, and the `javap` recipe for checking a symbol against the shipped jar |

**Two things to read before you start, because they will not be loaded for you:**

- **Load `entropy-mappings` before you write Minecraft API code**, and reach for
  it rather than a tutorial the moment something won't compile. Almost every
  Minecraft snippet you'll find is written in Yarn names; this project uses
  Mojang mappings and targets a version Yarn never covered, so the tutorial is
  wrong before you start.
- **Before writing a mixin → load `entropy-effects` first.** Three traps live
  there that build green and fail at runtime: `@Shadow` does not resolve
  inherited fields (hard crash on every world launch), a subclass may override
  the method you targeted, and a value you successfully modify may be re-clamped
  downstream. Compiling proves nothing about a mixin.
