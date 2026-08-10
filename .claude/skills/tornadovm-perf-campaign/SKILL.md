---
name: tornadovm-perf-campaign
description: Run a measured performance/correctness campaign on the TornadoVM runtime — profile first (JFR for host, Nsight Systems for driver), falsify the hypothesis with a standalone probe, A/B against a same-build baseline, then ship one PR per measured claim. Use when asked to make TornadoVM faster, to find where time goes in the runtime/dispatch path, to profile an application on top of TornadoVM (LLM decode, kfusion, benchmarks), or to follow up on issue #1028. For plain build/test/PR mechanics see the `tornadovm` skill; for nsys/hybrid-API specifics see `tornadovm-nvidia`.
---

# Running a TornadoVM optimisation campaign

A campaign is a loop, not a patch: **measure → attribute → hypothesise → falsify → change → re-measure → ship with numbers**. The hard part is not writing the optimisation; it is knowing which microsecond you are removing and proving you removed it.

Files here:
- `references/measurement.md` — exact profiling and A/B recipes, baselines, tool gotchas
- `references/probes.md` — standalone probe harness and ready-made probe shapes
- `references/catalogue.md` — the optimisation-pattern catalogue and what a previous campaign found (with numbers)

## The loop

1. **Profile before touching anything.** Host time = JFR; driver/device time = nsys. Attribute per phase (start-up vs steady state; per-execution vs per-token). Never profile with `-Dtornado.profiler=True` — TornadoVM's own profiler perturbs a short task graph by ~3x and misreports transfer timers by up to 20x (that was itself a finding, PR #1024).
2. **State the hypothesis as a number.** "The 24-byte stack-frame upload costs 2.00 µs per launch, the same as the 2 KB user copy" is a hypothesis. "Dispatch feels slow" is not.
3. **Falsify with a standalone probe before editing runtime code.** A probe is 40 lines in the scratchpad, compiles against the built SDK, and runs in seconds (`references/probes.md`). Several campaign hypotheses died here — cheaply.
4. **Change one mechanism.** If two changes have separate numbers, they are two PRs.
5. **A/B in the same build family**: stash the change, rebuild, measure baseline, restore, rebuild, measure. Medians of 3+ runs. Always include a **control** that must not move (a GPU-bound workload) — if it moves, the measurement is wrong.
6. **Separate displaced work from removed work.** Vary the iteration count: a fixed cost shrinks as a percentage, a per-iteration cost does not. A "speed-up" that vanishes at 512 iterations was a cost you moved, not one you deleted.
7. **Ship with the numbers, the controls, and the negatives.** See "PR shape" below.

## Rules that keep the numbers honest

- **Bailouts silently run sequential Java.** Every probe and benchmark gets `-Dtornado.recover.bailout=False`, or you are timing the CPU.
- **Aggregate test counts are only meaningful per machine.** On the reference box CUDA `--quickPass` fails 85 and OpenCL fails 271 on unmodified `develop` — so OpenCL claims must be **per test class, same device, before/after**, never aggregate.
- **Known flaky:** `compute.MMwithBytes`, `compute.TransformerKernelsTest`. Re-run in isolation before blaming a change.
- **A profiler-on measurement is a different workload.** Report profiler-on and profiler-off separately.
- **One machine is one data point.** Say which GPU, driver, JDK and backend in the PR.

## Prioritise by failure mode, not by diff size

When a campaign turns up bugs (it will), rank them:

1. **Silent wrong answer** — worst. Driver errors swallowed (#1025), a copy-out that silently no-ops on a graph-selected plan, a dropped wait list on OpenCL/Metal, an inverted `useDeps` flag (the last three in #1032).
2. **Crash** — visible, so second (#1035: batched graph with >1 output).
3. **Slow** — everything else.

A 2-character fix in category 1 outranks a 2x speed-up.

## PR shape for a performance claim

One PR = one mechanism = one number. Body:

1. **The bug / the cost** — with the profile line that shows it.
2. **Cause** — quote the 3–5 lines of code that do the wrong thing.
3. **Change** — what now happens, and what is deliberately *not* changed (guards, fallbacks).
4. **Numbers** — a table, medians, the configuration, and a **control row** that does not move.
5. **Testing** — new tests; `tornado-test --quickPass` versus the same-machine `develop` baseline (state both numbers); `./mvnw checkstyle:check` clean.
6. **Backends/OS tested** — including the ones you *could not* test and why.
7. **Negatives** — where it does not help, what it costs (memory, contract), what remains unproven. A PR that only lists wins reads as unmeasured.

Commit messages: one line, no body, no AI attribution. Base branch `develop`.

## Cadence and cost (reference box: RTX 4090, JDK 21)

| Step | Cost | Note |
|---|---|---|
| `make BACKEND=cuda` | ~3 min | Run in background while writing the next probe |
| `tornado-test --quickPass` | ~3.5 min | Only before a PR, not per edit |
| single test class | 5–30 s | The inner loop |
| standalone probe | ~10 s | The real inner loop — prefer this over tests while exploring |
| nsys capture + `nsys stats` | ~1 min | Counts are robust; totals include start-up |
| JFR capture + aggregate | ~1 min | Needs a run of a few seconds to get samples |

Rebuilding for an A/B costs two builds. Budget it; do not eyeball a 5% claim without it.

## Claude-side workflow that makes this efficient

- **Caveman mode** (`/caveman full`) for the whole campaign. Long sessions with dozens of build/test cycles produce a lot of narration; compressing it keeps context for the numbers. Code, commits, PR bodies and safety warnings stay in normal prose.
- **Background the builds.** `Bash(run_in_background: true)` for `make`, then write the probe or the test while it compiles. Never sit idle through a 3-minute build.
- **Batch independent calls.** Greps, file reads and status checks in one message; they run in parallel.
- **The scratchpad is the lab.** Probes, `agg.py`, A/B shell loops, nsys reports all live in the session scratchpad — never in the repo. Only tests and the fix land in git.
- **Forks/subagents for mechanical GitHub work only** — labelling, triage tables, changelog sweeps. They start cold: they cannot re-derive a measurement. Never delegate "find why this is slow".
- **`/loop` is for polling external state** (a CI run, a long benchmark sweep), not for driving edits. Prefer a long interval and a single check.
- **Write a resume pointer to memory** when a campaign spans sessions: branch, baseline failure counts, the last measured table, and the next hypothesis. Re-deriving a baseline costs two builds.
- **Task list for a multi-PR campaign.** A campaign fans out into 5–10 PRs plus an umbrella issue; track them, and keep the umbrella issue updated with each result — it is the campaign's shared memory.

### Git discipline (these bit a previous campaign)

- **Never commit onto a branch that already has an open PR** unless the commit belongs to that PR. Check `git log --oneline origin/<branch>..HEAD` before committing.
- **`git stash drop` drops `stash@{0}`, whoever pushed it.** List first (`git stash list`), drop by name/index, or use `git stash apply` + explicit drop. A dropped stash is recoverable via `git fsck --unreachable`, but only if you notice.
- **Stack PRs deliberately**: branch B off A's branch, and say so in B's description. Rebase the whole stack when A changes.
- `gh pr edit` can fail on repos with classic Projects (`GraphQL: Projects (classic) is being deprecated`). Fall back to `gh api -X PATCH repos/<owner>/<repo>/pulls/<n> -F body=@file.md`.

## Where the campaign usually goes next

Read `references/catalogue.md` first — it lists the patterns that have already paid off (dirty-bit memoization, request coalescing, deferred/lazy completion, high-water marks, capability negotiation) and the open leads with their measured ceilings. Optimising a path that is already 0.1% of wall is the most common way to waste a campaign; the catalogue records the shares.
