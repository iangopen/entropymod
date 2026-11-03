# Entropy

A Fabric mod for Minecraft 26.1.2 that turns a survival run into a race.

Every few minutes (3 by default) the game stops and makes you choose **one of three random Blessings**. At the next
interval you choose **one of three random Curses**, and the two keep alternating. Picking is mandatory: the choice
screen can't be closed. Every pick adds 1 to **Entropy**, and as Entropy climbs the offers get stronger and the
curses get nastier. Beat the Ender Dragon before Entropy reaches the cap (100 by default).

Everything you pick is permanent for the rest of the run. It survives death, relogging, dimension changes and a full
server restart.

<!--
  GAMEPLAY CLIP SLOT
  Put the clip in docs/media/ (for example docs/media/entropy-run.mp4 or .gif), then replace this comment with:
    https://github.com/user-attachments/... (drag the .mp4 into the GitHub editor), or
    ![Entropy gameplay](docs/media/entropy-run.gif)
  Don't add the link until the file exists.
-->

## What's in it

| | Count | Where it's counted from |
|---|---|---|
| Effects (Blessings + Curses) | **72**: 34 Blessings, 38 Curses | `register(...)` calls in `EffectRegistry.java`, each matched to one behavior class in `EffectBehaviors.java` |
| Tier 1 (Entropy 0–25) | 34: 17 Blessings, 17 Curses | `minEntropy`/`maxEntropy` in `EffectRegistry.java` |
| Tier 2 (Entropy 25–60) | 38: 17 Blessings, 21 Curses | same |
| Mixins | **47** classes, 38 common + 9 client-only, each listed once in the two mixin configs | `@Mixin` classes vs `entropymod.mixins.json` + `entropymod.client.mixins.json` |
| Network payloads | **10**: 6 server→client, 4 client→server | `PayloadTypeRegistry` calls in `EntropyMod.java` |

Effects span nine categories: movement, survival, combat, utility, tool, gear, meta, companion and debuff. A few
examples:

- **Blessings:** Double Jump, Creative Flight, Phoenix Chambered Heart (one death save), Green Thumb (crops grow
  around you), Danger Sense (hostiles within 32 blocks glow through walls), The Entourage (four iron golems escort
  you), Second Guess (reroll one offer per run).
- **Curses:** Earthquake (tremors pin you in place and blind you), Unstable (primed TNT at your feet every 30
  seconds), Creeper Magnet, Upside-Down Camera, Randomized Movement Controls (your movement keys are rewired),
  Reverse Midas (every block you place turns to gold), Nightmares (beds explode).
- **Meta effects** change the pick loop itself: Twin Fate gives you all three Blessings offered, Debt Collector
  draws your next Curse from the worst tier, and No Do-Overs forbids rerolls.

## How it's built

- **Effects are data plus one class each.** An effect is one `EffectDefinition` row in `EffectRegistry` and one
  `EffectBehavior` class with `apply`/`remove`. Adding an effect never touches the timer, the GUI or the networking.
- **The run's state lives in one place.** `AcquiredEffects`, a single set of effect ids, answers "what do I have",
  "what was already picked" and "which categories are taken". Attribute changes are rebuilt from it on every join,
  respawn and dimension change, so `apply` is written to be safe to run any number of times.
- **The run state is a `SavedData`** on the overworld (`entropymod:entropy_run`). It stores entropy, pick count,
  cap, interval, the acquired effects, the full pick history, the run state and outcome, the death count, per-player
  keybind snapshots, the companion roster and the meta-effect flags. Every codec field is optional with a default,
  so saves from older builds still load.
- **Rules are enforced in the roll.** No-repeat (an effect is never offered twice in a run) comes first.
  Anti-stacking (at most one effect per category) is dropped first if the two rules together would empty the pool.
  Phases strictly alternate Blessing, Curse, Blessing.
- **Run lifecycle:** `NOT_STARTED` (a start panel waits for you), then `IN_PROGRESS`, then `ENDED`. A run ends on the
  first Ender Dragon kill (a win; re-fights don't count) or when Entropy hits the cap (a loss). The end screen
  shows the outcome, final Entropy, every pick and the death count.
- **A headless test harness** (`src/harness`, run with `gradlew harness`) drives the real shipped classes against
  the Minecraft runtime classpath with no game client. It covers codecs, rolls, tuning tables and bytecode-level
  structural checks.

## Requirements

From `gradle.properties` and `build.gradle`:

| | Version |
|---|---|
| Minecraft | 26.1.2 |
| Fabric Loader | 0.19.3 or newer |
| Fabric API | 0.155.2+26.1.2 |
| Java | 25 (to build and to run) |
| Fabric Loom (build only) | 1.17-SNAPSHOT |

## Build

The Gradle wrapper is included. The first build downloads Minecraft and the Fabric toolchain, so it needs internet
access.

```sh
./gradlew build          # macOS / Linux
gradlew.bat build        # Windows (cmd or PowerShell: .\gradlew.bat build)
```

The mod jar is written to `build/libs/entropymod-<version>.jar` (currently `entropymod-0.1.0.jar`). The
`-sources.jar` next to it isn't the mod.

Other tasks:

```sh
./gradlew harness        # headless checks, kept separate from build on purpose
./gradlew runClient      # dev client with the mod loaded
```

## Install

1. Install **Fabric Loader** for Minecraft 26.1.2 with the official Fabric installer, and pick that profile in the
   launcher.
2. Put **Fabric API** (0.155.2+26.1.2) and **`entropymod-0.1.0.jar`** in the profile's `mods` folder.
3. Start a new world with **Allow Cheats: ON** if you want the debug commands below. A start panel opens when you
   join. The run and its timer begin when you press Start.

Debug commands (operator level 2): `/entropystatus`, `/entropyforcepick` (open the next pick now), `/entropygrant
<effect_id>` (give one specific effect through the real acquisition path), `/entropyforcewin`, `/entropyforceloss`.
Client-side: `/entropyhistory` prints the pick history, and `/entropypreview` opens the choice screen with **fake**
data, for checking layout only.

## Status

**Built and working in code:** the interval loop and phase alternation; 72 permanent effects across Tiers 1 and 2;
persistence across restart; re-application on death, relog and dimension change; no-repeat and anti-stacking; the
start gate, both end conditions, the death counter and the end screen; the responsive choice screen; the Entropy HUD;
the debug commands; the headless harness.

**Not built yet:**

- **Tiers 3 and 4.** No effect is eligible above Entropy 60. Past 60 the loop offers nothing and Entropy stops
  rising, so with the default cap of 100 the cap-loss can't be reached in normal play yet (`/entropyforceloss`
  tests that path). Above Entropy 50 the Blessing pool is down to one effect.
- **The "signature" effects** from the design doc (Mirror World, Fisheye, Doppelganger and others). Ore Sense
  has a detection layer but no renderer, and isn't registered.
- **A config surface.** Interval and cap can be changed in code but not from the game. There's no world-creation
  screen, game rule or config file.
- **Become Hardcore**, the third way to end a run, is designed but not built.
- **A pick-history screen.** History prints to chat for now.
- **Multiplayer isn't a decided target.** The code handles more than one player in places, but the mod is built
  and tested as single-player.
- **Pool exhaustion policy.** When no-repeat empties a phase's pool, the mod re-offers an effect you already have.
  That's placeholder behaviour.

**How it's verified:** `gradlew build` and the headless harness check the code. Many effects have been tuned from
in-game reports. There's no per-effect record of which effects have been played, though, so treat in-game coverage
as partial. [`CLAUDE.md`](CLAUDE.md) holds the current verified state and open questions, and
[`docs/history.md`](docs/history.md) has the branch lineage and session log.

## License

CC0 1.0. See [`LICENSE`](LICENSE).
