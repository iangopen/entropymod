# Session history

## Branch lineage map (verified 2026-11-03)

The repo has 31 branches (`main` plus 30 feature branches), but there is only one line of work. Every feature
branch is a point on a single linear chain that leaves `main` at `6269e3a`. The newest point, `run-lifecycle-ended`
(`ba1319c`), contains every other branch tip. Nothing on any branch is missing from it.

How this was checked: for every `origin/*` branch, `git merge-base --is-ancestor <branch> origin/run-lifecycle-ended`
succeeds, and `git rev-list --count <branch> ^origin/run-lifecycle-ended` is 0. The only commits in the whole repo
that `run-lifecycle-ended` doesn't contain are `main`'s three commits from after the rewrite (`292aed1`
`.gitignore`, `9358a94` the entropy-mappings skill, `a4b5826` CLAUDE.md's repository-state section).
`git log --oneline --all ^origin/run-lifecycle-ended` lists exactly those three. There are no tags and no stashes.

**The one tip to merge is `run-lifecycle-ended`.** Merging any other branch tip would leave out everything after it.

Shared base, already on `main`: `4dffd90` skeleton → `6269e3a` EntropyColors + HUD (7 commits).

The chain from oldest to newest. "New" is the number of commits since the previous row.

| Branch(es) at this point | Tip | Date | New | Tip commit |
|---|---|---|---|---|
| `effect-behavior-architecture` | `2a81cad` | 2026-08-01 | 4 | adding entropy proper history registering |
| `permanent-effects-and-tier1-content` | `31eae59` | 2026-08-01 | 2 | adding concrete effect boosters, making sure effects work |
| `movement-physics-effects` | `1f636f0` | 2026-08-02 | 2 | added gravity changing effects |
| `mixin-cluster-effects` | `1753448` | 2026-08-02 | 2 | first rendition of mixin rework stuff |
| `diagnose-history-and-mixin-efficacy` | `707fc3d` | 2026-08-02 | 1 | Fix /entropyhistory visibility, Heavy Footsteps efficacy, Magnetic Boots scale |
| `crops-events-and-second-guess` | `4981834` | 2026-08-02 | 2 | more mixin entropy mod features |
| `entropygrant-and-growth-tuning` | `446b025` | 2026-08-02 | 1 | Add /entropygrant; retune Leaky Pockets and Green Thumb; check in the harness |
| `green-thumb-active-growth` | `f751728` | 2026-08-02 | 2 | changes to green thumb work state |
| `blight-touched-trample` | `8eab929` | 2026-08-02 | 1 | Blight Touched: replace the growth-slowdown hook with crop trampling |
| `effect-tuning-and-reroll-state` | `e796125` | 2026-08-03 | 1 | Clumsy Digger diagnosis, Bad Reputation severity, Second Guess button state |
| `clumsy-digger-proportional-wear` | `8b36dee` | 2026-08-03 | 1 | Clumsy Digger: extra wear proportional to the tool's own durability |
| `clumsy-digger-tools-only` | `b36c0c0` | 2026-08-03 | 1 | Clumsy Digger: scope to mining tools, exempt armour and elytra |
| `investigate-become-hardcore` | `6bc465a` | 2026-08-03 | 1 | Investigate Become Hardcore: verdict buildable as specified (no code) |
| `run-lifecycle-design-notes` | `afe9db6` | 2026-08-03 | 2 | Mark the Become Hardcore investigation as superseded |
| `tier2-input-camera-effects` | `00f4d28` | 2026-08-03 | 2 | Fix crash on world launch: @Shadow cannot resolve inherited fields |
| `run-lifecycle-start-gate` | `5f3eb24` | 2026-08-03 | 1 | Run lifecycle: start gate, start panel, and keybind-anchored controls |
| `tier2-content-batch` | `5bbda10` | 2026-08-03 | 1 | Tier 2 content batch: 7 effects |
| `tier2-moon-and-giant` | `04d134b` | 2026-08-03 | 2 | clearing test logs |
| `tier2-survival-batch` | `c41a86d` | 2026-08-05 | 4 | entropy-effects: sprint-jump findings, and the Ctrl-release verdict |
| `tier2-sense-batch` | `48ea2de` | 2026-08-05 | 1 | Tier 2 sense batch: Danger Sense and Double Jump (Ore Sense investigated only) |
| `tier2-oresense-and-doublejump-fix` | `2c14099` | 2026-08-06 | 1 | Fix Double Jump's same-tick bug; Ore Sense detection layer (renderer blocked) |
| `tier2-spawn-hazards` | `1184946` | 2026-08-07 | 3 | Unstable: fix the band's units, correct the damage model, retune |
| `tier2-companions` | `365c601` | 2026-08-13 | 6 | Entourage: drop setInvulnerable -- it was deleting the golems' targetability |
| `tier2-combat-batch` | `ae60b43` | 2026-08-14 | 5 | Earthquake: real movement paralysis, blindness for the whole window, 40-60 |
| `beast-wrath` | `08a7078` | 2026-08-15 | 2 | Beast Wrath: fish and wild tameables, with the player's own pack spared |
| `reverse-midas-grave-robber` | `1934cbc` | 2026-08-25 | 2 | Reverse Midas: both cells and golden equivalents; Grave Robber 30% |
| `tier2-meta-effects`, `tier2-slick-hook-glutton` | `9aa12ed` | 2026-08-26 | 1 | Six Tier 2 effects: Slick, Loose Footing, Lethal Hook, Human Machinegun, Nightmares, Glutton |
| `tier2-meta-loop-effects` | `5063bde` | 2026-09-22 | 1 | Five Tier 2 meta effects, and the loop infrastructure they needed |
| **`run-lifecycle-ended`** | **`ba1319c`** | 2026-09-22 | 1 | Run lifecycle: RunState.ENDED, both end conditions, death counter, end screen |

That's 56 commits on the chain. Two branch names don't match what they hold, so go by the table, not the name:
`tier2-meta-effects` points at the Slick/Hook/Glutton commit, the same commit as `tier2-slick-hook-glutton`.
The meta effects themselves are on `tier2-meta-loop-effects`. `tier2-moon-and-giant`'s tip only clears logs; the
Embrace the Moon and Giant Size work is the commit before it, `3939b76`.

Since every branch is an ancestor of `run-lifecycle-ended`, deleting the 30 feature branches after the merge
reaches `main` would lose nothing. That hasn't been done; it's the owner's call.

## 2026-11-03: lineage merged onto a branch, not yet onto main
- Mapped the lineage (above). Created `merge/lineage-into-main` from `main` (`a4b5826`) and merged
  `origin/run-lifecycle-ended` with `--no-ff` (merge commit `460a615`). The only conflict was CLAUDE.md: kept the
  lineage's file and inserted `main`'s Repository-state section unchanged. `main`'s old "Part 0" mapping notes had
  already moved into the `entropy-mappings` skill on the lineage, and the merge brings that skill in from `main`.
- Extended `.gitignore` (`*.class`, `*.log`, `crash-reports/`, `saves/`, `world/`, `.claude/settings.local.json`).
  `git ls-files` holds no generated output. The only binary under `gradle/` is the wrapper jar, which is source.
- Counted from source: 72 effects, 47 mixin classes = 47 mixin-config entries, 10 payloads. Details and commands
  are in CLAUDE.md's Repository-state section.
- Rewrote README.md for the merged state.
- `main` and every other branch were left untouched. Build/harness: see CLAUDE.md's Repository-state section for
  what ran this session.

## 2026-11-02: history rewrite published
- Re-ran the push plan's checks (`portfolio-audit/ENTROPY-PUSH-PLAN.md` §4). The backup bundle hash and verify
  passed, there were no new local commits, GitHub's 9 heads matched the leases, the rewritten repo passed fsck
  (31 branches, 64 commits, 1.10 MiB), and `run-lifecycle-ended` passed `gradlew build` and `gradlew harness`
  (2470 passed, 0 failed). PR #1's 3 commits were saved as patches.
- Pushed with `--atomic` and explicit `--force-with-lease` per branch: 9 forced updates and 22 new branches. All 31
  GitHub heads equal the rewritten SHAs. A fresh clone passes fsck, has 64 commits and is 1.19 MiB.
- PR #1 was auto-closed by GitHub on the base force-push, and a closing comment was added. Dependabot alerts were
  turned on. Secret scanning and push protection were already on.
- Committed `.claude/skills/entropy-mappings/SKILL.md` (scanned clean), restored from the backup.
