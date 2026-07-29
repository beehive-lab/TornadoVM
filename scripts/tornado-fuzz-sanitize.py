#!/usr/bin/env python3
# Copyright (c) 2024, APT Group, Department of Computer Science, The University of Manchester.
# Licensed under the Apache License, Version 2.0.
"""
Decoupled compute-sanitizer pass for the TornadoVM CUDA fuzzer (inspired by
cuFuzz's SAND). Complements the fast value-oracle fuzzing loop with NVIDIA's
compute-sanitizer, catching bug classes the differential oracle cannot see:

  racecheck  - shared-memory data races (barrier/reduction kernels)
  memcheck   - out-of-bounds / misaligned device memory accesses
  initcheck  - reads of uninitialized device memory
  synccheck  - invalid / divergent __syncthreads usage

The sanitizer is slow (instruments every warp), so it runs as a SEPARATE pass
over a chosen seed range, batching several seeds per sanitized process to
amortize CUDA + sanitizer startup (the persistent-mode idea). Use --batch 1 for
exact per-seed attribution; larger batches trade attribution precision for speed
(cuFuzz's abstraction-level knob).

Because the generated phase-2 kernel class is named G<seed>, a sanitizer error
that names G<seed> is auto-attributed to its seed even in large batches. The
race-prone phase-1 templates (localmem-reduce, atomic-add) are targeted by
default via --only.

Example:
  scripts/tornado-fuzz-sanitize.py --tool racecheck --seed 1 --total 200 \
      --only localmem-reduce,atomic-add --out /tmp/fuzz-san
  scripts/tornado-fuzz-sanitize.py --tool memcheck --phase 2 --seed 1 --total 100 \
      --gen /tmp/gen --out /tmp/fuzz-san
"""
import argparse
import glob
import os
import re
import subprocess
import sys

MAIN = "tornado.fuzz/uk.ac.manchester.tornado.fuzz.FuzzMain"
CASE_RE = re.compile(r"CASE seed=(\d+)")
GSEED_RE = re.compile(r"\bG(\d+)\b")
ERR_SUMMARY_RE = re.compile(r"ERROR SUMMARY:\s*(\d+)\s*error")
RACE_SUMMARY_RE = re.compile(r"RACECHECK SUMMARY:\s*(\d+)\s*hazards displayed\s*\((\d+)\s*error")

DEFAULT_SANITIZERS = [
    "/usr/local/cuda-12.6/bin/compute-sanitizer",
    "/usr/local/cuda/bin/compute-sanitizer",
    "compute-sanitizer",
]


def find_sanitizer(override):
    if override:
        return override
    for c in DEFAULT_SANITIZERS:
        if c == "compute-sanitizer" or os.path.exists(c):
            return c
    return "compute-sanitizer"


def run_batch(sanitizer, tool, seed, count, phase, only, out_dir, gen_dir):
    cmd = [sanitizer, "--tool", tool, "--target-processes", "all", "tornado"]
    if phase == 2:
        cmd += ["-cp", gen_dir]
    cmd += ["-m", MAIN]
    if phase == 2:
        cmd += ["--jvm=-Dtornado.fuzz.genDir=" + gen_dir]
    params = f"seed={seed} count={count} phase={phase} outDir={out_dir}"
    if only:
        params += f" only={only}"
    cmd += ["--params", params]

    print("+ " + " ".join(cmd), flush=True)
    proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1)

    cur_seed = seed
    block = []           # accumulated sanitizer detail lines for the current error region
    captured = []        # full sanitizer transcript for this batch
    errors = 0
    for line in proc.stdout:
        captured.append(line.rstrip("\n"))
        m = CASE_RE.search(line)
        if m:
            cur_seed = int(m.group(1))
        if line.startswith("========="):
            block.append(line.rstrip("\n"))
        # Summaries close a batch's sanitizer report.
        rm = RACE_SUMMARY_RE.search(line)
        em = ERR_SUMMARY_RE.search(line)
        if rm:
            errors += int(rm.group(2))
        elif em:
            errors += int(em.group(1))
    proc.wait()
    return proc.returncode, errors, cur_seed, block, captured


def attribute_seed(block, fallback_seed, batch):
    """A phase-2 kernel name G<seed> pins the seed exactly even in large batches."""
    for line in block:
        g = GSEED_RE.search(line)
        if g:
            return int(g.group(1))
    return fallback_seed


def write_finding(out_dir, tool, seed, errors, block, captured):
    bundle = os.path.join(out_dir, "findings", f"san-{tool}-{seed}")
    os.makedirs(bundle, exist_ok=True)
    with open(os.path.join(bundle, "sanitizer.txt"), "w") as f:
        f.write("\n".join(captured) + "\n")
    with open(os.path.join(out_dir, "sanitizer.jsonl"), "a") as f:
        f.write('{"seed":%d,"tool":"%s","errors":%d}\n' % (seed, tool, errors))
    with open(os.path.join(bundle, "FIX_ME.md"), "w") as f:
        f.write(f"# CUDA backend fuzz finding — SANITIZER ({tool})\n\n")
        f.write(f"compute-sanitizer `{tool}` reported **{errors} error(s)** in the kernel generated for seed {seed}.\n\n")
        f.write("## Sanitizer report (excerpt)\n\n```\n")
        f.write("\n".join(block[:60]) + "\n```\n\n")
        f.write("## Reproduce\n\n```bash\n")
        f.write(f"{tool_repro(tool)} \\\n")
        f.write(f"    tornado -m {MAIN} --params \"seed={seed} count=1 phase=<P> outDir=/tmp/fuzz-san\"\n```\n\n")
        f.write("Then inspect the generated CUDA-C with `-Dtornado.printKernel=true`.\n")
    print(f"SANITIZER FINDING {tool} seed={seed} errors={errors} -> {bundle}", flush=True)


def tool_repro(tool):
    return f"compute-sanitizer --tool {tool} --target-processes all"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--tool", default="racecheck", choices=["racecheck", "memcheck", "initcheck", "synccheck"])
    ap.add_argument("--seed", type=int, default=1)
    ap.add_argument("--total", type=int, default=100)
    ap.add_argument("--batch", type=int, default=1, help="seeds per sanitized process (1 = exact attribution)")
    ap.add_argument("--phase", type=int, default=1)
    ap.add_argument("--only", default="localmem-reduce,atomic-add", help="phase-1 template filter (race-prone by default)")
    ap.add_argument("--out", default="/tmp/fuzz-san")
    ap.add_argument("--gen", default="/tmp/gen", help="phase-2 genDir (also placed on -cp)")
    ap.add_argument("--cuda-sanitizer", default=None, help="path to compute-sanitizer")
    args = ap.parse_args()

    sanitizer = find_sanitizer(args.cuda_sanitizer)
    os.makedirs(args.out, exist_ok=True)
    if args.phase == 2:
        os.makedirs(args.gen, exist_ok=True)
    only = None if args.phase == 2 else args.only

    seed = args.seed
    remaining = args.total
    total_findings = 0
    while remaining > 0:
        count = min(args.batch, remaining)
        rc, errors, last_seed, block, captured = run_batch(sanitizer, args.tool, seed, count, args.phase, only, args.out, args.gen)
        if errors > 0:
            attributed = attribute_seed(block, last_seed, count)
            write_finding(args.out, args.tool, attributed, errors, block, captured)
            total_findings += 1
        remaining -= count
        seed += count

    print(f"Sanitizer pass complete ({args.tool}). findings={total_findings} out={args.out}", flush=True)


if __name__ == "__main__":
    main()
