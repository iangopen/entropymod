# FRAMEWORK-IDEAS.md — candidate standalone Fabric libraries

## What this file is, and what it is not

This is a **living developer document**, for a human audience, tracking places
Entropy Mod hit a genuine **framework or tooling gap** that a standalone,
publishable Fabric library mod could solve for the wider modding community.

It is deliberately **separate from `CLAUDE.md` and the three skills**
(`entropy-effects`, `entropy-design`, `entropy-mappings`). Those exist to give
Claude Code session context about *this* mod — what is built, what is verified,
what not to re-derive. This file exists to answer a different question:
**what did we have to build ourselves that nobody should have had to build?**

**This is NOT for entropymod-specific content.** An entry belongs here only if
it would still be useful to someone building a completely different mod. A
clever tuning number, a per-effect derivation or a project design decision goes
in the skills, not here.

**Nothing here is scheduled.** This is a running list to revisit *after* the
entropymod prototype itself is further along — the prototype is the priority,
and extracting a library before the pattern has been proven twice is premature.

### This file is meant to ACCUMULATE

**Whenever a future session hits a similar "no clean framework exists for this"
wall, add it here as a new entry**, with the same level of technical specificity
as the existing ones: the real class and method names, the real reason it is
hard, and the real current status. An entry that says "rendering is difficult"
is worthless; an entry that says "`RenderType.create(String, RenderSetup)` is
package-private and Fabric ships no pipeline-registration hook" can be resumed
from a year later.

Three rules for adding one:

1. **Cite real, verified specifics.** Every technical claim in this file traces
   to something javap-verified, run, or read out of a shipped jar, and recorded
   in `CLAUDE.md` or one of the skills. Where a claim does *not* have that
   backing, it is marked **⚠ PROVENANCE** rather than dressed up.
2. **Say what the blocker actually is.** "Hard" is not a blocker. "The
   constructor is private and there is no API to register the result" is.
3. **Record refutations too.** A hypothesis that was investigated and found
   false (the stale-player-reference story in §1.2) is worth as much as one that
   held — it stops the next person spending a session on it.

---

# Section 1: Proven patterns, ready to extract

Things this project has already built, tested and confirmed working, which are
generically useful well beyond entropymod.

---

## 1.1 Brain-driven mob AI override

**Package:** `com.entropymod.entropy.companion` —
`CompanionService` (the world-facing half), `CompanionMotion` (the decisions,
Minecraft-import-free).

### The gap

**A `Goal` added to a brain-driven mob is silently overridden and does nothing.**
This is not documented anywhere obvious, and it fails in the worst possible way:
the code compiles, the goal is registered, and the mob ignores it.

The discovery, from the companion cluster investigation:

- **`Villager`'s bytecode contains ZERO references to `goalSelector`.** Villagers
  are entirely brain-driven. A goal added to its selector — which would itself
  need an accessor mixin, since `Mob.goalSelector` is `protected final` — is
  overridden by `MoveToTargetSink` on the same tick.
- The same trap catches the *look* direction by a second route.
  `villager.getLookControl().setLookAt(player)` from a tick service does not
  persist. Inside `Mob.serverAiStep()`, javap-verified by offset:

  | offset | call |
  |---|---|
  | 166 | `PathNavigation.tick` |
  | **192** | `customServerAiStep` → `Brain.tick` → **`LookAtTargetSink`**, whose entire tick is `getLookControl().setLookAt(lookTarget.currentPosition())` |
  | 223 | `MoveControl.tick` |
  | **239** | **`LookControl.tick`** — the only consumer of what was set |

  A service on `END_SERVER_TICK` runs after 239, so its `setLookAt` is
  **overwritten at offset 192 of the next tick, before offset 239 ever reads
  it**, for as long as the brain holds a `LOOK_TARGET`. And it essentially always
  does: `LookAtTargetSink` is registered in the villager's **core** package, so
  it runs in every activity.

### The pattern that works

**Drive the schedule yourself; perform the change through vanilla's own
transition.** (The same rule Green Thumb established for crop growth.)

- **Movement:** a tick service re-issues `getNavigation().moveTo(...)` on a
  5-tick cadence. It works regardless of which system owns the mob's movement,
  which is why one service drives villagers, iron golems and a llama alike.
- **Look direction:** write `MemoryModuleType.LOOK_TARGET` directly rather than
  calling `LookControl`. `LookAtTargetSink` then does the rotation on the
  brain's own tick, at vanilla's gradual turn rate, with nothing left to fight.

Two non-obvious rules the implementation had to discover, both of which a
library should carry:

- **Refresh the look target EVERY tick, not on the movement cadence.** The write
  lands after the brain tick, so it owns the memory for exactly the *next* tick
  before a competitor may reclaim it. A 1-in-5 refresh hands the look to another
  villager four ticks in five, which at `LookControl`'s turn rate is
  indistinguishable from the feature not working.
- **Use `BlockPosTracker`, not `EntityTracker`.** This is the sharpest finding
  in the whole area. `LookAtTargetSink.canStillUse` is
  `getMemory(LOOK_TARGET).filter(t -> t.isVisibleBy(mob)).isPresent()`, and its
  `stop()` is a bare `brain.eraseMemory(LOOK_TARGET)` — **a tracker that answers
  "not visible" does not merely fail to turn the head, it erases what you just
  wrote.** And `EntityTracker.isVisibleBy(mob)` is **not a ray cast**: it is
  `mob.getBrain().getMemory(NEAREST_VISIBLE_LIVING_ENTITIES).map(n -> n.contains(target))`,
  fed by `NearestLivingEntitySensor` over a box inflated by `FOLLOW_RANGE` and
  refreshed only every 20 ticks. `BlockPosTracker.isVisibleBy` is literally
  `iconst_1; ireturn`, so the sink can never stop and can never erase. Rebuild it
  from `player.getEyePosition()` every tick and a fixed-position tracker is
  equivalent to a following one.

### Also worth carrying: `moveTo(Entity, speed)` has no stop distance

`getNavigation().moveTo(entity, speed)` is `createPath(entity, 1)` →
`createPath(Set.of(entity.blockPosition()), 1, …)`, so **every path targets the
target's own block with accuracy 1.** Gating *when a path is issued* on a
distance threshold does nothing about where it ends, and nothing stops a path
already running. Any standoff behaviour must navigate to a computed point on a
ring (`CompanionMotion.idealHoldDistance`, `CompanionService.moveToRing`) and
call `navigation.stop()` on arrival — which also makes approach and retreat the
same instruction, so the two cases cannot drift apart.

### Why it would make a good library

Every mod that wants a pet, escort, follower or crowd of any brain-driven
species (villager, piglin, axolotl, allay, camel, goat, warden, frog, hoglin,
sniffer, breeze) hits all of this. The information is not in any tutorial, and
each of the four traps above fails silently rather than loudly.

### Status

**Built, shipped and in-game verified**, driving four effects (Loyal Pack, The
Entourage, The Audience, Emotional Support Llama). Extractable, but the
decision layer is currently written around this mod's own state machine — see
`CompanionMotion.AudienceState` — so extraction means separating "how to drive a
brain-driven mob" from "what entropymod wants the mob to do."

---

## 1.2 Permanent-companion persistence and recovery

**Classes:** `CompanionRoster` (Minecraft-import-free, persisted in
`EntropyManager`'s codec, keyed `playerUuid/effectId`), `CompanionSpawner`,
`CompanionService.gather`, `CompanionMotion.TeleportWatch`.

### The gap

"Spawn some entities that belong to the player and keep belonging to them" turns
out to be four separate problems, three of which have a plausible-looking wrong
answer.

**Problem 1 — spawn idempotency is a different property for entities.** Every
attribute or hook effect rebuilds *derived* state, so "apply ten times, assert
the value did not move" is the right test. **A companion effect is the opposite:
the wolves are world state that already exists.** Re-running the spawn is not a
no-op, it is fifteen more wolves — and in this project `apply` runs again on
every respawn, rejoin and dimension change.

The design that *looks* careful and is a bug: record each spawned entity's UUID,
resolve them on re-application, top up the shortfall. **`ServerLevel.getEntity(UUID)`
only finds entities in loaded chunks**, so a player who logs in far from their
pack, or steps through a Nether portal, resolves zero live companions and is
handed a fresh set — then another on the next relog. **The failure mode of a
top-up design is unbounded duplication, triggered by exactly the events
re-application fires on.**

The shipped guarantee is **structural instead**: spawn only on
`EffectContext.isFreshPick()`. Re-application spawns nothing at all, so "no
duplicates" is true by construction rather than true if a lookup happens to
succeed. The roster exists so the service can *find* the companions — **not** to
decide whether to spawn.

**Problem 2 — "gone" usually means unloaded, not dead.** Four iron golems were
reported missing after a respawn. Decoded out of `entities/*.mca` they were
**alive at 94-97/100 HP, 400.9-407.0 blocks away.** The catch-up teleport used
`Level.getEntity(UUID)` — loaded-only — so:

> **The further a companion is left behind, the less able the recovery is to do
> anything about it, and past simulation distance (~160 blocks) it cannot act at
> all. The mechanism was weakest in exactly the case it existed for.**

Fix: resolve with **`ServerLevel.getEntityInAnyDimension(UUID)`** (tries this
level, then iterates `getAllLevels()`, so a companion left in the Nether is
found) and move with **`teleportTo(ServerLevel, …)`, not `snapTo`** — `snapTo`
cannot cross dimensions. Call it from the JOIN / AFTER_RESPAWN /
AFTER_PLAYER_CHANGE_LEVEL hooks, because at the moment of the event the
companion's chunk is still loaded — the player was standing in it a fraction of
a second earlier.

**Problem 3 — the events you would bind that recovery to do not fire for a
teleport.** This one is a distinct failure mode: a component that is correct,
tested, wired, and **never invoked**. javap on
`ServerPlayer.teleport(TeleportTransition)`:

| offset | what |
|---|---|
| **62–67** | `if_acmpne 104` — *are the two dimensions different?* |
| 71–100 | **same dimension:** `connection.teleport(...)`, `resetPosition()`, `postTeleportTransition`, **return** |
| **289** | **`triggerDimensionChangeTriggers(oldLevel)`** |

and Fabric's `ServerPlayerMixin` fires `AFTER_PLAYER_CHANGE_LEVEL` from **exactly
one place**: `@Inject(method = "triggerDimensionChangeTriggers", at = @At("TAIL"))`.
So a `/tp`, an ender pearl and a chorus fruit all take the early branch and fire
**nothing**. Confirmed in the log: four `/tp`s with companions held, and not one
gather line — the recovery had never executed in play.

The fix stops depending on events. `CompanionMotion.TeleportWatch` samples each
player's position every tick and reports a discontinuity: a level change
(unconditional), or a single-tick move past `TELEPORT_DELTA = 16` blocks.
Sixteen is not tuned — the fastest sustained travel in the game is
elytra-with-rockets at roughly **2 blocks/tick**, so 16 is a gap between what
movement can produce and what only a discontinuity can.

**Problem 4 — the stale-player-reference hypothesis, REFUTED.** Worth recording
as refuted, because it is the obvious story and it is false in both directions.
`PlayerList.respawn` does build an **entirely new `ServerPlayer`** (same UUID),
so "something cached the player" would explain everything. It did not:

- **Mod side.** The service re-reads `server.getPlayerList().getPlayers()` every
  pass and every roster lookup is keyed `player.getUUID().toString()`.
- **Vanilla side, and this is the reusable half.** A tamed owner is stored as
  `EntityReference<LivingEntity>` — internally `Either<UUID, StoredEntityType>`.
  `getEntity` checks `isRemoved()` on the held entity and, when true, **writes
  the reference back down to the UUID branch** before resolving through
  `UUIDLookup.lookup`. **Vanilla self-heals a stale entity reference.** Tamed
  ownership is structurally safe across a respawn and needs no mod-side work.

### Also carried: despawn immunity is free, and `setInvulnerable` is a trap

- **`Animal.removeWhenFarAway(double)` is literally `iconst_0; ireturn`** — every
  animal in the game is despawn-immune, tamed or not, and `Villager` is the same.
  `setPersistenceRequired()` is never needed for these and adding it is cargo
  cult. Persistence across save/reload is ordinary entity NBT: nothing mod-side
  persists companions and nothing mod-side can lose them.
- **`setInvulnerable(true)` is not only a damage decision — it deletes
  targetability.** `LivingEntity.canBeSeenAsEnemy()` is
  `!isInvulnerable() && canBeSeenByAnyone()`, reached from
  `TargetingConditions.test` via `attacker.canAttack(target)`, and
  `NearestAttackableTargetGoal` *and* `HurtByTargetGoal` both use `forCombat()`.
  So an invulnerable escort **cannot be acquired as a target by any hostile mob,
  and a mob it punches cannot retaliate.** The Entourage's golems were walked
  past by zombies and could never pull aggro — the effect's own description
  described something that could not occur. **For a companion that should survive
  rather than be ignored, raise `MAX_HEALTH` instead.**
- **Attribute buffs on a companion invert this project's own transient rule.**
  `AttributeInstance.pack()` writes **`baseValue` and `permanentModifiers` and
  nothing else** (javap-verified), and that packed list is ordinary entity NBT
  saved with the chunk. A spawned companion's `apply` never runs again, so a
  transient modifier would vanish on the first chunk unload with no hook to
  restore it. Use `addOrReplacePermanentModifier`.

### Why it would make a good library

Pet/minion/summon mods are one of the most common categories on CurseForge and
Modrinth, and every one of them has to solve duplication-on-relog, recovery
after a teleport, and cross-dimension resolution. All three have a wrong answer
that works in testing and fails in play.

### Status

**Built and shipped.** Bugs 2 and 3 above were found *in play* after shipping and
are fixed. One stated limit remains: **a companion that had already unloaded
before the event** (walk 500 blocks, then relog) still cannot be resolved —
nothing records where it was, and there is no vanilla lookup for an unloaded
UUID. The complete fix is to persist last-known position in the roster; not
built, because the reported failure did not need it. **That limitation is
exactly what a library would need to close.**

---

## 1.3 `SafeSpawn` — "where near this player can something actually be spawned"

**Class:** `com.entropymod.entropy.spawn.SafeSpawn`.

### The gap

Every mod that puts an entity into the world near a player needs this, and a
wrong answer is severe and hard to see: an entity spawned in stone suffocates or
is shoved through the wall, and one spawned behind a wall gives no warning at
all. There is no vanilla or Fabric helper for it.

### What it does, and the four things that are easy to get wrong

**The position test is vanilla's own, and naming it explicitly is load-bearing.**
`SpawnPlacementTypes.ON_GROUND.isSpawnPositionOk(level, pos, type)` is what the
natural spawner uses. Read out of `SpawnPlacementTypes$1`'s bytecode it is four
things: inside the world border, `below.isValidSpawn(...)`, and
`NaturalSpawner.isValidEmptySpawnBlock` at both feet and head — which in turn
requires a non-full collision shape, no fluid, not a signal source, not in
`#prevent_mob_spawning_inside`, and not `type.isBlockDangerous`. So "not embedded"
and "on solid ground" come from the definition the rest of the game already uses,
**including for modded blocks and datapack tag edits.**

> **Do NOT use `SpawnPlacements.isSpawnPositionOk(type, level, pos)` instead.**
> That looks the placement type up per entity, and anything with no natural spawn
> — TNT included — gets `NO_RESTRICTIONS`, i.e. *anywhere*, which is the one
> answer this must never give.

Four more rules, each of which is invisible in play if wrong:

- **Line of sight is required**, and it must aim **up the spawned entity's own
  body**, not at the feet block. Aiming at the centre of the feet block means the
  ray grazes the terrain for its whole length and clips on any rise — in every
  model run this was the single dominant rejection.
  `AIM_FRACTIONS = {0.5, 1.0}`, passing if **any** is visible.
- **A distance-capped vanilla helper you reimplement must keep the cap.** This
  one shipped wrong. Vanilla's `hasLineOfSight(Entity, Block, Fluid, double)`
  compares `Vec3.distanceTo` against `128.0` and **returns false before clipping
  at all**. The reimplementation had neither that gate nor the different-level
  one, so it answered "visible" for pairs vanilla calls invisible and paid for a
  140-block ray to do it.
- **Measure the band in whatever quantity the effect's mechanic depends on.**
  `SafeSpawn.DistanceMode` now makes callers say which they mean: a blast wants
  `SPHERICAL` (vanilla's explosion damage reads the 3D distance and culls past
  `2 × radius`), a mob that walks to you wants `HORIZONTAL`. Getting this wrong
  is not merely imprecise — a horizontal band of 5–7 with a vertical search of 8
  permits a real 3D distance of **10.63 blocks**, and **27.6% of spawns did
  literally nothing.**
- **`Level.isLoaded(BlockPos)`, not `hasChunkAt`**, and **`Mth.floor`, not a
  cast** (a cast truncates toward zero and is off by one everywhere west or north
  of the origin).

**Returning `null` is a real outcome, not an error.** A player sealed into a 1×2
shaft has nowhere valid. Callers must not retry in a loop; `Attempt` carries
**per-gate rejection counts** so the log can say *which rule* declined.

### The companion component: `SpawnSchedule<K>`

`com.entropymod.entropy.spawn.SpawnSchedule<K>` is a per-key countdown that
re-rolls in `[min, max]` on every fire. **A fixed cadence is `min == max`**,
which makes the re-roll `nextInt(1)` and therefore always 0 — so a fixed 30s and
a random 30s–2min run through identical arithmetic with no second branch. Two
rules it exists to hold:

- **The first trigger is a full interval away.** A brand-new key schedules on its
  first tick rather than firing on it.
- **`retainAll` is unconditional.** The obvious guard ("only prune when
  `remaining.size() > live.size()`") is wrong: one player leaving while another
  joins leaves the sizes equal and the departed timer behind forever.

**These are two components, not one**, and the separation is the point — a timer
should not drag a heightmap search along behind it.

### Status

**Built, shipped, and revised after a real in-game failure** in which
`findNear` returned null on roughly three triggers in four. Three defects were
found and fixed: `VERTICAL_SEARCH` was 4 where real terrain needed 8 (**34.4% of
candidate columns** had their surface more than 4 blocks from the player's Y),
the band was drawn horizontally and tested in 3D so *finding valid ground could
disqualify a column for having been found*, and the line-of-sight aim point was
wrong as above. `SpawnSchedule.rearm(key, 40)` makes the symptom impossible
rather than rarer: a failed search retries in 2 seconds instead of after the
whole interval.

---

## 1.4 A headless test harness that can load mod classes — `HarnessBootstrap`

**Classes:** `src/harness/java/com/entropymod/harness/` — `HarnessBootstrap`,
`Checks`, plus Minecraft-free models (`SprintModel`, `CropGrowthModel`).

### The gap

**Testing a Fabric mod without booting Minecraft is harder than it should be, and
the reason is a single ordering detail nobody documents.**

Vanilla freezes `BuiltInRegistries` at the end of `Bootstrap.bootStrap()`. Mod
registration works in game *only* because Fabric's registry-sync `BootstrapMixin`
replaces that freeze — in a method literally named **`delayRegistryFreeze()`**.
In a plain `java -cp` harness there is no Fabric, so merely *touching* a class
that registers anything dies with `ExceptionInInitializerError`.

`HarnessBootstrap.init()` reproduces Fabric's ordering by hand: bootstrap,
reflectively unfreeze `ATTRIBUTE`, register, then **bind the holders** — reading
the value out of `MappedRegistry.byValue` directly, because every public accessor
that would bind a `Holder.Reference` routes through the unbound `value()` call
you are trying to fix. It is idempotent so each test section can call it without
coordinating.

That one prologue unlocked the check that matters most for this project:
**idempotency against the real `AttributeInstance`** — apply ten times, assert
the value has not moved and that exactly one modifier exists rather than ten
stacked copies.

### `Checks` — bytecode assertions without a bytecode library

Three tools, each answering a question reflection cannot:

- **`constant(Class, String)` reads constants by reflection, never by reference.**
  `static final` primitives are inlined at the *caller's* compile time, so a
  harness that named `SomeBehavior.MULTIPLIER` directly would compare its own
  compiled-in snapshot against itself and pass regardless of what ships.
- **`classReferences(Class, String)` scans a compiled class's constant pool for a
  name** — the tool for "*which* vanilla method does this hook call" when calling
  it for real would need a live entity. Two boolean-returning methods are
  indistinguishable by reflection; they are not indistinguishable in bytecode.
  It proves presence or absence, not location.
- **`hasConstant` / `hasMethod` assert a retired code path was DELETED** rather
  than left returning a neutral value. A neutral value can be quietly re-wired by
  a later session that does not know why it was emptied; code that does not exist
  cannot.
- **`methodPushesExplicitNull(Class, String)` decodes the `Code` attribute
  instruction by instruction** — a full constant-pool parser plus a JVM
  instruction-length table, about 150 lines with no ASM dependency. Written
  because `aconst_null` is opcode `0x01`, a byte that occurs constantly inside
  other instructions' operands, so a raw byte scan would pass on almost anything.

### The discipline that makes it work: Minecraft-free decision components

Several classes in the main source set are **deliberately free of Minecraft
imports** so a plain harness can drive the real shipped code rather than a copy:
`AcquiredEffects`, `PickRecord`, `EffectRegistry`, `EntropyPalette`,
`CropSchedule`, `TramplePath`, `MovementScramble`, `KeybindSnapshot`,
`DoubleJumpState`, `SpawnSchedule`, `CompanionRoster`, `CompanionMotion`,
`CompanionBuffs`, and parts of `SafeSpawn`.

**This is not tidiness — it is the difference between a tested component and an
untested one**, and the project learned it the hard way. Unstable and Creeper
Magnet shipped with their cadence asserted over 200 consecutive cycles and failed
in play as "fired once, then silence." The schedules were perfect; `SafeSpawn`
was returning null, and **every line of it needed a `ServerLevel`, so no part of
its geometry was harness-reachable. The tested component was the one that
worked.**

### Two hard-won lessons a library should carry as documentation

- **A tick-sampled state machine must be tested at its SAMPLING POINT, not
  against the idealised event order.** Double Jump shipped with a green harness
  that fed a state `END_CLIENT_TICK` can never observe: inside `LivingEntity.aiStep`,
  `jumpFromGround()` is at offset **460** but `travel()` → `move()` — which writes
  `onGround` — is at offset **615**. So on the jump tick `onGround()` already
  reads false. The logic was right in isolation and wrong in place.
- **A green harness is not evidence when the assertion was copied from the
  javadoc.** This has now happened **three times** in this project. The Audience
  state machine shipped with three assertions *requiring* the buggy behaviour,
  written from the same doc comment that described the wrong design — so they
  tested that the code matched the prose, not that the prose was right. The
  counter-practice: drive regressions from **observed failure data**. The ten real
  distances out of the world save (60.1 … 140.3) and the four real post-respawn
  distances (400.9 … 407.0) are hard-coded into the harness, so the reported
  symptom itself is the assertion.

### Status

**Built and in continuous use** — `./gradlew harness` currently runs **1343
checks**. It is a separate source set, deliberately **not** wired into `build`:
a check needing an update after a deliberate retune must not block a release
build. Extraction would mean generalising `HarnessBootstrap` past this project's
one custom attribute, and deciding whether `Checks` should keep its
no-dependency stance or take ASM.

---

## 1.5 Smaller, but genuinely reusable

- **`CompanionBuffs.speedModifierAmount(m) = sqrt(m) - 1`.** To buff a *mob's*
  real speed by `m`, multiply its `MOVEMENT_SPEED` attribute by `sqrt(m)`, not
  by `m`. `Mob.setSpeed(f)` also calls `setZza(f)`, so a mob's forward *input* is
  its speed value where a player's is a normalised 1.0, and `moveRelative`
  multiplies the two — **so real b/s goes as the square of the attribute.** The
  naive form overshoots by roughly the buff again (a "+25%" buff delivers +56.25%)
  and the error is invisible, because nobody times a mob with a stopwatch. **The
  rule does not apply to players**, whose ground speed is linear in the attribute.
- **Read a mob's attributes from `DefaultAttributes.getSupplier(EntityType.X)`,
  never from the attribute's own `getDefaultValue()`.** `Attributes.FOLLOW_RANGE`
  defaults to 32.0, but `Mob.createMobAttributes()` overrides it to **16.0**. A
  first draft sized a spawn distance on the 32.0 default and the harness caught it.
- **A vanilla `levelEvent`/`playSound` broadcast takes a player to EXCLUDE.**
  Both end in `PlayerList.broadcast(Player except, …)`, whose first statement is
  `if (player == except) continue`, because vanilla's usual caller is an action
  the acting client already predicted locally. **Nothing a mod schedules
  server-side is predicted**, so passing the affected player delivers the cue to
  every bystander and never to them. `levelEvent`'s 3-arg overload is safe by
  construction (a default method that supplies `null` itself); **`playSound` has
  no safe overload.**
- **When granting vanilla state you do not exclusively own, prefer a short
  refreshed grant over explicit removal.** Danger Sense grants a 20-tick Glowing
  and re-grants every 10 ticks, and never calls `removeEffect` — which would also
  strip a glow from a spectral arrow or a command. `MobEffectInstance.update`
  keeps the stronger/longer of the two, so another source survives intact. Expiry
  is free and needs no bookkeeping.

---

# Section 2: Hard barriers hit, unsolved

Real walls, verified by real investigation rather than assumed.

---

## 2.1 Rendering through terrain — the render-pipeline construction path is closed to mods

**Hit by:** Ore Sense (an ore-highlighting effect). **Status: investigated in
full, NOT built, and deliberately not registered** in either `EffectRegistry` or
`EffectBehaviors` — a registered effect that draws nothing would be a dead entry
in the roll pool, which is worse than an absent one. The harness asserts that
absence so it cannot be half-shipped by accident.

### The blocker

Drawing anything see-through-walls needs a `RenderPipeline` with depth testing
disabled, and **the construction path is closed**:

| what you need | visibility |
|---|---|
| `RenderPipeline.builder(Snippet...)` | **public** ✅ |
| `RenderPipeline.Builder.with*` (shaders, format, depth, cull, colour target) | **public** ✅ |
| `RenderSetup.builder(RenderPipeline)` / `RenderSetupBuilder` | **public** ✅ |
| **`RenderType.create(String, RenderSetup)`** | **package-private** ❌ |
| `RenderType`'s constructor | private ❌ |
| `RenderPipelines.MATRICES_PROJECTION_SNIPPET`, `DEBUG_FILLED_SNIPPET` | **private** ❌ |
| `RenderPipelines.register`, `PIPELINES_BY_LOCATION` | **private** ❌ |
| a Fabric API for registering a render pipeline | **does not exist** ❌ |

`FabricRenderPipeline` exists in `fabric-rendering-v1` and is **not** it — its
only member is `usePipelineDrawModeForGui()`. A scan of the Fabric API jars found
no pipeline-registration hook of any kind.

### The non-precedent, recorded because it wasted a session

**`RenderPipelines.DEBUG_FILLED_BOX` is NOT see-through**, contrary to what an
earlier pass of this project's own notes claimed. `DEBUG_FILLED_SNIPPET` is built
with `new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false)` — an ordinary
depth **test**, merely without depth **write**. Debug boxes are occluded by
terrain like anything else. The see-through value is `CompareOp.ALWAYS_PASS`,
which exists but is used by no render type that is publicly reachable.

### What a fix costs today

Two accessor mixins into vanilla rendering internals: an `@Invoker` for
`RenderType.create`, and an `@Accessor` for one of the private snippets (the
snippets carry the projection-matrix uniforms the `core/position_color` shaders
require — building from a bare `builder()` means declaring those by hand). **That
is a materially different risk class from anything this project has done**; its
only render-adjacent precedent is a single `@Redirect` on a public method.

**And one unknown remains even then.** `ShaderManager.apply` calls
`GpuDevice.precompilePipeline(pipeline, shaderSource)` on resource reload, for
**registered pipelines only**. `GpuDevice.precompilePipeline(RenderPipeline)` *is*
public and `clearPipelineCache()` exists, which strongly implies the device caches
compiled pipelines keyed on the object and would compile on first use. **Strongly
implies is not verified.**

### Adjacent finding worth keeping

**`WorldRenderEvents` does not exist.** Every world-render tutorial online uses
`net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents`. In this Fabric
API version (`fabric-rendering-v1` 23.3.1) that class is **gone**, replaced by
`...rendering.v1.level.LevelRenderEvents`, whose events take a
`LevelRenderContext` (`poseStack()`, `bufferSource()`, `submitNodeCollector()`) or
a `LevelExtractionContext` (`level()`, `camera()`, `deltaTracker()`). The usable
hooks are `AFTER_TRANSLUCENT_TERRAIN` and `BEFORE_GIZMOS`.

### The library shape

**"Register a custom render pipeline / render type" is the single highest-value
missing Fabric API found by this project.** X-ray-style highlighting, waypoint
markers, entity outlines, custom beams and most minimap overlays all need it, and
right now every one of them either ships accessor mixins into vanilla rendering
internals or does not ship. A thin library that owns those two accessors, exposes
a safe builder, and handles first-use compilation would be used immediately and
widely.

**What IS built and headlessly verified** (so extraction would not start from
zero): the block-set tag, the client-side cached scan on a 20-tick cadence
(**never scan per frame** — 4,913 positions × 60 fps ≈ 295,000 block reads a
second), and the sphere-vs-box correction (a bare 17-cube reaches `8 × √3 = 13.86`
blocks at its corners, a **73% over-range**).

---

## 2.2 Applying a player's skin to an arbitrary mob entity

**Wanted by:** Doppelganger — an effect in which a hostile copy of the player
hunts them. **Status: NEVER ATTEMPTED. Still blocked.**

### What is actually recorded

`entropy-design` Open Question 8 lists Doppelganger among the odd/signature
effects and says it needs "custom entity + skin injection." **That is the whole
of what this project knows about it.** No investigation has been done, no
mechanism has been identified, and no `javap` has been run against the skin path.

### Why it is genuinely hard (stated as the shape of the problem, not as findings)

A player's skin is not an entity property. It is resolved client-side from the
player's Mojang profile through the skin/texture cache and is consumed by
`PlayerRenderer` specifically — which is not the renderer any mob uses. So
"put a player's skin on a mob" is at minimum: a custom entity type, a custom
renderer built on the player model, a client-side route from a player profile to
a texture, and a way to tell the client *which* profile without leaking it. None
of that has a Fabric API today.

> **⚠ PROVENANCE.** Unlike every other entry in this file, the paragraph above is
> **reasoning about the problem, not a verified finding.** Nothing in
> `CLAUDE.md` or the skills records an investigation of the skin pipeline. Treat
> it as a starting hypothesis to check with `javap`, not as established fact —
> and note that this project has been wrong about exactly this kind of
> reasoned-not-verified claim before (see the `DEBUG_FILLED_BOX` non-precedent in
> §2.1, and the refuted golem-invisibility hypothesis).

### The library shape

"Render an arbitrary entity with a given player's skin" would serve
doppelganger/clone/mimic effects, statue and NPC mods, and mannequin/display
blocks. Worth an investigation session before it is worth a library.

---

## 2.3 No world-wide container registry

**Wanted by:** The Toll — an effect that would need to reach every container in
the world. **Status: blocked, not built.**

### The blocker

There is no index of "every chest in the world." Vanilla tracks block entities
**per loaded chunk** (`LevelChunk`'s block-entity map), and there is no
cross-chunk, cross-dimension registry to query. Anything world-wide therefore
means either walking every region file on disk — which is a save-format problem,
not a game-API one — or maintaining a mod-side index updated on every container
placement and break, which must then survive world edits made while the mod is
absent.

This project has an adjacent verified data point about what *is* reachable:
`ServerLevel.getEntity(UUID)` is **loaded-only**, and
`getEntityInAnyDimension(UUID)` iterates levels but still only finds loaded
entities. The same loaded-only ceiling applies to block entities, and §1.2's
Problem 2 is the same shape of bug reached from the entity side — a mechanism
that works in testing (everything nearby is loaded) and fails in play.

> **⚠ PROVENANCE.** The Toll as an effect, and the specific conclusion that it
> was blocked on a container registry, are **not recorded anywhere in
> `CLAUDE.md`, the skills, or the git history** — this entry exists because the
> gap was raised directly. The blocker above is stated from the general shape of
> vanilla's chunk storage plus this project's own verified loaded-only findings.
> **The specific investigation has not been done and should be, before anyone
> plans work on it.**

### The library shape

A persistent, incrementally-maintained container index — "give me every chest
within N chunks / in this dimension / in the world" — with honest handling of
the not-currently-loaded case. Useful to storage-network mods, search/sorting
mods, quest and economy mods, and anything with a "find your stuff" feature.
The hard part is not the query; it is staying correct across world edits the
index did not observe.

---

## 2.4 Arbitrary-direction / omnidirectional gravity

**Status: research-level only. Confirmed to need reworking player orientation,
movement and rendering together. Not attempted.**

### What this project verified about gravity, and why it settles the easy question

Entropy Mod has shipped three gravity effects, so the attribute path is
well-established here — and it is **exactly what cannot express direction**:

- **`GRAVITY` is a real, player-applicable attribute** granted by
  `LivingEntity.createLivingAttributes()`. `LivingEntity.getDefaultGravity()` is
  literally `getAttributeValue(Attributes.GRAVITY)`.
- **It is a scalar magnitude, and `Entity.applyGravity()` subtracts it from
  VERTICAL motion** every airborne tick. There is no direction anywhere in it.
- Its clamp is `[-1.0, 1.0]`, and **negative gravity is legal** — which launches
  the entity upward permanently. So the attribute can invert the axis and nothing
  else. It cannot tilt it by one degree.

**So the attribute is a dead end for this by construction, not by difficulty.**

### Why the real thing is a rework rather than a feature

Making gravity point in an arbitrary direction means every one of these moves
together, because they all assume a world-space Y axis:

- **Movement.** `getFrictionInfluencedSpeed`, `travel`/`travelInAir`, `move`,
  and the `onGround` write inside `move` are all axis-aligned. This project has
  already measured how tangled that path is: vanilla rewards sprinting in **three
  independent places** (`setSprinting`'s `MOVEMENT_SPEED` modifier,
  `jumpFromGround`'s flat +0.2 forward impulse, and `Player.getFlyingSpeed`'s
  `0.02 → 0.025999999`), and **while airborne `MOVEMENT_SPEED` is not consulted
  at all.** A single mechanic there needed three coordinated changes; reorienting
  the axis touches all of them and more.
- **Orientation and collision.** Bounding boxes are axis-aligned by definition
  (`AABB`), step-up (`STEP_HEIGHT` / `maxUpStep()`) is vertical, and fall
  distance / `calculateFallPower` accumulate along Y.
- **Rendering.** The camera's basis is built by
  `Camera.setRotation(yRot, xRot)` → `rotation.rotationYXZ(PI - yRot·DEG2RAD,
  -xRot·DEG2RAD, 0.0f)`, and **vanilla hardcodes the third argument (roll) to
  zero.** This project fills that slot for Upside-Down Camera with a single
  `@Redirect`, so the *camera* half of a reorientation is genuinely cheap — but
  the camera is the one part that is, and rolling the view while the physics stay
  Y-aligned is a visual effect, not gravity.

> **⚠ PROVENANCE.** The gravity-attribute facts, the three sprint bonuses and the
> camera roll slot are all javap-verified and recorded in `entropy-effects`. The
> **claim that real omnidirectional-gravity mods exist for older Minecraft
> versions and that none is confirmed for 26.1.2** was raised directly and is
> **not recorded or verified in this repo** — no survey of existing mods has been
> run. The synthesis above ("this is a rework of three systems together") follows
> from the verified pieces, but has not been tested by attempting it.

### The library shape

If it were solved once as a library — a reorientable gravity frame that movement,
collision, camera and fall damage all read — it would enable a category of mod
(gravity guns, planetoids, wall-walking, space stations) that currently does not
exist on modern versions. It is also, by a wide margin, the largest item in this
file.

---

## 2.5 Smaller confirmed gaps

- **No Fabric trade-price event.** `fabric-api` has **no trade-related classes at
  all** in this version, so anything touching villager pricing is mixin-only.
  Entropy Mod's Bad Reputation hooks `Villager.updateSpecialPrices(Player)` — a
  **private** method, which happens to make the subclass-override trap
  structurally impossible here, but a public API would be better than luck.
  *(Related mapping trap: `Villager` moved to
  `net.minecraft.world.entity.npc.villager.Villager`, and villager trades are
  data-driven now.)*
- **No durable per-world CLIENT-side storage, anywhere.** Investigated properly
  and it changed a design. `SavedData`/`SavedDataStorage` are **server-side only**;
  the client's own durable state is *global*, not per-world (`options.txt`); and
  the only per-world client-reachable directory is the save folder via
  `Minecraft.getLevelSource()`, **which exists in singleplayer only**. The nearest
  imitation is a mod-managed file under `Minecraft.gameDirectory` keyed by some
  world identity, and that identity is unreliable in exactly the ways that matter:
  a renamed or copied world, two servers behind one address, a world played from
  a second machine. **The workaround this project shipped is worth reusing:
  capture on the client, persist on the SERVER** (here, a keybind snapshot in
  `EntropyManager`'s codec, mirrored back to its owner). That is per-world by
  construction with no world-identity guessing, works identically in singleplayer
  and multiplayer, and is headlessly testable as a codec round-trip.
- **No per-viewer entity glow.** The Glowing effect drives shared entity flag 6
  inside `SHARED_FLAGS_ID` synced data, so **the glow is visible to every player
  tracking the mob**, not only the one who should see it. **There is no
  per-viewer glow anywhere in vanilla — no API, no packet, nothing to piggyback
  on.** Making it private would mean rewriting the tracked-data packet per
  connection. (See §3.2 — this shipped as a stated limitation.)
- **No general mob hearing.** `Sensing`, the mob perception class, has **exactly
  one query: `hasLineOfSight(Entity)`.** There is no noise radius and nothing in
  ordinary hostile AI that reacts to sound. The only sound-driven perception in
  the game is the vibration/sculk system, which covers *one mob*. (See §3.1.)

---

# Section 3: Ideas scoped down for lack of framework support

Real features that shipped smaller or different because building the full
version was not worth it as part of this mod.

---

## 3.1 "Mobs notice you from farther away" → "sneaking no longer hides you"

**Effect:** originally Heavy Footsteps, **renamed to Exposed** once its real
mechanic was understood — the id changed, which is why a save from before the
rename carries an effect id this build no longer defines.

**Wanted:** footsteps make noise, and mobs hear you from farther away.

**Shipped:** a **1.25×** multiplier on `LivingEntity.getVisibilityPercent`, which
cancels the sneaking discount rather than extending detection range. The
description was factually wrong and was rewritten.

### The two independent reasons

**1. There is no hearing to hook.** See §2.5 — `Sensing` has one query and it is
line of sight. Wiring footsteps to the sculk vibration system would have covered
the Warden, not "mobs" — a fake version of the effect.

**2. The general detectability lever exists but is one-directional, and that is
an asymmetry in vanilla:**

| step | method | uses visibility? |
|---|---|---|
| acquisition | `TargetingConditions.test` | **yes** — `max(followDistance × visibilityPercent, 2.0)` |
| retention | `TargetGoal.canContinueToUse` | **no** — `distanceToSqr > followDistance²`, raw |

A target acquired beyond the raw follow distance is dropped again immediately, so
**`getVisibilityPercent` can only ever REDUCE effective detection range, never
extend it.** That is exactly why vanilla only ever uses values ≤ 1.0 (sneaking's
0.8, invisibility's armour fraction). **Anything above 1.0 is silently
discarded** — which was confirmed in play, not deduced: a zombie with follow
range 35 still acquired the player at 35 blocks with the multiplier at 1.35, when
47.25 was expected.

1.25 is therefore the **saturation point**: the exact inverse of sneaking's 0.8,
so `0.8 × 1.25 = 1.0`. Every value above it does nothing at all.

**Also corrected here:** footstep volume does **not** widen the audible radius.
`SoundEvent.getRange(volume)` is `volume > 1.0f ? 16.0f × volume : 16.0f`, and
footsteps are ~0.15 — far below the threshold. Widening would need >6.7×.

**What a library would need to provide:** a general mob-hearing sense — a noise
event with a radius that ordinary hostile AI consults — which does not exist in
any form. That is a substantial AI addition, not a hook.

---

## 3.2 A glow only the effect's holder can see → a glow everyone can see

**Effect:** Danger Sense (nearby hostiles glow). **Shipped with the limitation
stated rather than silently accepted.**

The Glowing `MobEffectInstance` is applied to the target mob server-side and
drives shared entity flag 6 in `SHARED_FLAGS_ID` synced data. Two consequences:
no per-tick push of our own is needed (`updateGlowingStatus` is reached from
`updateDirtyEffects`, i.e. exactly when the effect set changes) — **and the glow
is visible to every player tracking the mob.**

**There is no per-viewer glow anywhere in vanilla.** Making it private means
rewriting the tracked-data packet per connection: real work, not a flag. Reported
rather than silently decided; this project is singleplayer-scoped, so in practice
there is no observable difference today.

**What a library would need to provide:** per-connection synced-data overrides —
useful far beyond glowing (per-player nameplates, per-player entity states,
spectator/team-visibility features).

---

## 3.3 A double chest on the llama → 15 slots

**Effect:** Emotional Support Llama. **Wanted: a real double chest, 54 slots.
Shipped: 15.**

The ceiling is arithmetic, not a limitation of effort:

```
Llama.getInventoryColumns()                          = hasChest() ? getStrength() : 0
AbstractMountInventoryMenu.getInventorySize(columns) = columns * 3
Llama MAX_STRENGTH                                   = 5
```

**The largest llama inventory that exists is 5 × 3 = 15 slots, against a double
chest's 54 — 27.8% of the request.**

Reaching 54 means **abandoning the llama container entirely** for a custom menu
with its own screen, its own persistence and its own client/server sync — at
which point it is no longer "a llama carrying a chest", it is a custom container
that happens to be attached to a llama. That is a self-contained piece of work
with no reusable payoff for this mod.

**What shipped instead is a real llama at maximum strength**, which needs
`LlamaStrengthAccessor`, an `@Invoker` onto the **private** `setStrength` —
`setRandomStrength` rolls `1 + nextInt(nextFloat() < 0.04 ? 5 : 3)`, so a natural
llama is strength 1–3 and hits 5 about 1.6% of the time. Worth noting the risk
distinction: **an `@Invoker` is a materially lower risk class than an injection**
— it adds no behaviour and changes no control flow, and its only shared failure
mode is a wrong target name.

**What a library would need to provide:** a "custom container attached to an
entity" helper — menu type, screen, NBT persistence and sync in one piece.
Common enough (backpacks, mounts, portable storage, pet inventories) that most
storage mods reimplement it.

---

## 3.4 Client-side prediction for inventory restrictions

**Effect:** Slashed Pockets (the upper half of the inventory is unusable).
**Shipped complete and server-authoritative; one cosmetic gap left open
deliberately.**

`EffectHooks` returns "no effect" client-side by design, so client-side
prediction still permits a placement into a locked slot and the server corrects
it — **the item snaps back**. Purely cosmetic: the server is authoritative and
the per-tick sweep is unconditional.

The clean fix is a client-side reader of `ClientRunState` for `Slot.mayPlace`,
and it was **deliberately not built, because it would mean two mixins racing on
one method for one frame of polish.** Recorded here rather than in the skills
because the general shape recurs: **any mod that restricts an inventory operation
server-side inherits this snap-back**, and Fabric offers nothing to declare a
slot restriction on both sides from one definition.

---

## 3.5 Ore Sense — the whole effect

Cross-referenced from §2.1 rather than repeated. **This is the only case in the
project where a feature was scoped down to zero**: the detection layer is built
and headlessly tested, the renderer is not, and the effect is deliberately
absent from the registry so it cannot be half-shipped. It is listed in this
section too because "shipped smaller" and "did not ship at all" are the same
decision made at different magnitudes, and a future reader looking for
scoped-down features should find it here.

---

## Provenance summary

Per the rule at the top of this file, everything above traces to a verified
finding in `CLAUDE.md`, `entropy-effects`, `entropy-design` or `entropy-mappings`
— **except** the three items marked **⚠ PROVENANCE**, which are recorded honestly
as unverified rather than dressed up with invented specifics:

| entry | what is NOT recorded anywhere in this repo |
|---|---|
| §2.2 Doppelganger | Only the words "custom entity + skin injection" in Open Question 8. The skin-pipeline reasoning is a hypothesis, not a finding — no `javap` has been run against it. |
| §2.3 The Toll | The effect, and the conclusion that a container registry blocked it, appear in **no** file and **no** commit. The blocker is stated from vanilla's chunk storage plus this project's verified loaded-only entity findings. |
| §2.4 Omnidirectional gravity | The gravity, sprint and camera facts *are* verified. The claim that such mods exist for older versions and none for 26.1.2 is unverified — **no mod survey has been run.** |

Everything in Section 1 and Section 3 is drawn from shipped, in-game-verified
work with the real class names checked against the source tree.
