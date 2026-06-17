# TornadoVM — Publicity Wins Execution Plan (Claude handoff)

> **Purpose:** This file is a self-contained work order. Drop it anywhere in a fresh
> Claude Code session on the TornadoVM checkout and say *"Execute the tasks in
> TORNADOVM_PUBLICITY_PLAN.md, one PR per task."* Each task is isolated, needs no
> input from other tasks, and ends in a Pull Request. Claude should run these to
> completion **autonomously** within the rules below.

---

## 0. Mission & context

TornadoVM lets you write plain Java and run it on GPUs/FPGAs (OpenCL, PTX, SPIR-V,
Metal backends). Goal of these tasks: ship **quick, high-shareability publicity
artifacts** ("Show HN" / r/java catnip) that lower first-touch friction and showcase
the project's real moat (a mature multi-backend Java→GPU compiler) and its AI wedge
(GPU LLM inference in pure Java).

**Competitive clock:** Project Babylon / HAT (OpenJDK) is building "write Java, run on
GPU" into the JDK. These artifacts must emphasize TornadoVM's *maturity, multi-backend
reach, and working AI stack* — the things Babylon does not have yet.

---

## 1. Operating rules (READ FIRST — applies to every task)

- **Repo:** `beehive-lab/TornadoVM` unless a task says otherwise (PR-6 is a different repo).
- **One PR per task.** Never bundle tasks. Never touch files outside a task's stated scope.
- **Branching:** branch from the up-to-date default branch.
  ```bash
  git fetch origin
  git switch -c claude/publicity-<task-id> origin/master   # confirm default branch name first
  ```
- **Commits:** small, descriptive, conventional prefixes used in this repo
  (`[docs]`, `[examples]`, `[chore]`, `[ci]`). End each commit body with:
  ```
  Co-Authored-By: Claude <noreply@anthropic.com>
  ```
- **Push with upstream:** `git push -u origin <branch>`; retry on network error with backoff (2s/4s/8s/16s).
- **Open the PR** with `gh pr create` (preferred) or the GitHub MCP tools. Use the
  title/body provided in each task. **Mark hardware-dependent tasks as `--draft`.**
- **Do NOT push to `master`/`develop`. Do NOT merge. Do NOT create issues** unless asked.
- **No GPU required.** Most tasks use checked-in artifacts or virtual-device mode (below).
  If a task genuinely needs a GPU to verify, open it as a **draft PR** with a checklist of
  what a human must confirm on hardware, and say so in the PR body.

### Build / verify cheatsheet
- Generated reference kernels (no GPU needed): `tornado-assembly/src/examples/generated/`
  (`add.cl`, `add.ptx`, `add.spv`, `virtualDevice/*.cl`).
- Virtual-device mode emits kernel code without hardware — search docs for
  `virtual` / `VirtualOCLTornadoDevice` and `docs/source/*` for how to enable it.
- Docs build: see `docs/` (`.readthedocs.yaml`, Sphinx `.rst`). Validate Markdown/RST renders.
- Java examples live in `tornado-examples/src/main/java/uk/ac/manchester/tornado/examples/`.
- Build (only if a code task needs it): `make BACKEND=opencl` (see `Makefile`, `BUILD_AND_RUN.md`).

### Definition of done (every task)
1. Artifact created in the right path. 2. Builds/renders cleanly (or virtual-device validated).
3. Branch pushed. 4. PR opened with the given title/body. 5. PR body lists any human/GPU
verification still required.

---

## 2. Tasks (ordered: most autonomous first)

### PR-1 — Compiler deep-dive: "Watch Java bytecode become a GPU kernel" ⭐ fully autonomous
**Why it lands:** HN loves compiler internals; this showcases the moat over Babylon.
**Scope:** docs only — NO source changes.
**Build:**
- New article `docs/source/under-the-hood.rst` (link it from `docs/source/index.rst`).
- Take a tiny `@Parallel` loop (e.g. vector add, mirror `docs/source/codesamples/Compute.java`)
  and show, side by side, the generated **OpenCL C / PTX / SPIR-V** using the checked-in
  kernels in `tornado-assembly/src/examples/generated/` (`add.cl`, `add.ptx`, `add.spv`).
- Annotate: how the loop index maps to `get_global_id`, how the `IntArray` becomes a buffer
  param, where the bounds guard comes from. Keep it ~800–1200 words, copy-pasteable.
- Add a "reproduce this yourself" box using `--printKernel` / virtual-device mode.
**Acceptance:** RST renders; all three kernel languages shown from real repo artifacts; no claims that require running a GPU.
**PR title:** `[docs] Add "Under the Hood: Java to GPU kernel" deep-dive article`
**PR body:** Summary + screenshot/snippet of the side-by-side + "intended as a blog/HN piece, sourced from checked-in generated kernels."

---

### PR-2 — JBang visual one-liner: GPU Mandelbrot in one command ⭐ mostly autonomous
**Why it lands:** zero-install "wow", produces an image, bulletproof for a live thread.
**Scope:** new JBang script + catalog + README snippet; wraps existing
`examples/compute/Mandelbrot.java` (do not rewrite the algorithm).
**Build:**
- `jbang/Mandelbrot.java` (or `examples/jbang/`): a JBang-headered entry point
  (`//JAVA 21+`, `//DEPS io.github.beehive-lab:tornado-api:<ver>` + runtime,
  `//JAVA_OPTIONS` for the JVMCI/module exports — derive from `example-argfile`)
  that runs the Mandelbrot TaskGraph and writes `mandelbrot.png`.
- `jbang-catalog.json` at repo root registering alias `mandelbrot`.
- README "Try it in one line" block:
  ```bash
  jbang mandelbrot@beehive-lab     # -> writes mandelbrot.png, computed on your GPU
  ```
- **Graceful fallback:** if no accelerator, fall back to CPU and print a clear note (don't crash).
**Acceptance:** script is syntactically valid; `//JAVA_OPTIONS` match the working argfile;
fallback path present. **GPU run = human verify (draft PR).**
**PR title:** `[examples] Add JBang one-liner for GPU Mandelbrot demo`
**PR body:** the one-liner, a note it needs `jbang`, and a "verify on GPU" checklist. Open as **draft**.

---

### PR-3 — One-click cloud env: devcontainer + Colab notebook ⭐ fully autonomous
**Why it lands:** kills the install wall that tanks first impressions.
**Scope:** new infra files only.
**Build:**
- `.devcontainer/devcontainer.json` + `Dockerfile`: JDK 21 + OpenCL CPU runtime
  (e.g. POCL) so TornadoVM runs CPU-side in Codespaces with no GPU; postCreate builds
  the OpenCL backend. Add an "Open in GitHub Codespaces" badge to README.
- `colab/TornadoVM_Quickstart.ipynb`: a notebook that installs a backend and runs the
  ArrayAdd/Mandelbrot example, for readers with a Colab GPU.
**Acceptance:** devcontainer.json is valid JSON and references a buildable Dockerfile;
notebook cells are coherent and ordered. **Full container build = human verify (draft PR).**
**PR title:** `[chore] Add devcontainer + Colab quickstart for zero-install trials`
**PR body:** what each file does + verification checklist. Open as **draft**.

---

### PR-4 — Positioning post: "TornadoVM vs Project Babylon/HAT" ⭐ fully autonomous
**Why it lands:** the JVM community is actively curious about this right now.
**Scope:** one Markdown article (e.g. `docs/source/resources/` or a `blog/` dir).
**Build:** balanced, technical comparison — what each is, code-reflection vs bytecode JIT,
backend maturity, where each shines, how they could coexist (TornadoVM as the optimizing
backend). ~1000 words. **No disparagement; cite public sources.**
**Acceptance:** renders; factually careful; flagged in PR as "needs maintainer review of positioning before publishing."
**PR title:** `[docs] Add TornadoVM vs Project Babylon/HAT comparison article`

---

### PR-5 — Apple Silicon angle: "Java on the M-series GPU" (Metal) ⭐ author-only, human verifies
**Why it lands:** huge surprise factor for the large Mac dev audience.
**Scope:** a short post + reuse the PR-2 JBang demo pointed at the Metal backend.
**Build:** `docs/source/metal-demo.rst` (or blog md): how to run a TornadoVM example on the
Apple GPU via the Metal backend, with the one-liner and an expectation of output.
**Acceptance:** instructions consistent with the Metal backend docs/build. **Must be verified
on a real Mac — open as draft with a clear "tested on M-series?" checklist.**
**PR title:** `[docs] Add Apple Silicon (Metal backend) GPU demo & writeup`

---

### PR-6 — (stretch, DIFFERENT REPO) JBang LLM one-liner for GPULlama3.java
**Repo:** `beehive-lab/GPULlama3.java` (NOT TornadoVM). Only do this if that repo is in scope.
**Why it lands:** the marquee win — "Run Llama3 on your GPU in one line of pure Java."
**Build:** `jbang-catalog.json` + entry script wrapping the existing inference main, defaulting
to a small GGUF (e.g. Qwen2.5-0.5B) for a sub-minute first run, with `--tok-s` flag and CPU
fallback. README "one-liner" block.
**Acceptance:** valid catalog + script; small default model; fallback present.
**GPU run = human verify (draft PR).**
**PR title:** `Add JBang one-liner: run an LLM on your GPU in one line of pure Java`

---

## 3. Suggested execution order
1. **PR-1** (deep-dive) and **PR-4** (Babylon post) — 100% autonomous, no hardware, highest
   ratio of impact to risk. Do these first; they can merge without a GPU.
2. **PR-3** (devcontainer/Colab) — autonomous authoring, draft for container verify.
3. **PR-2** (Mandelbrot JBang) — autonomous authoring, draft for GPU verify.
4. **PR-5** (Apple Silicon) — draft, needs a Mac.
5. **PR-6** (LLM one-liner) — only if GPULlama3.java repo is available.

## 4. After PRs are open
- Post a one-line summary per PR (link + what a human still needs to verify).
- If asked to babysit, subscribe to PR activity and fix CI; otherwise stop.
- Do not announce on HN/Reddit — that's the maintainer's call.
