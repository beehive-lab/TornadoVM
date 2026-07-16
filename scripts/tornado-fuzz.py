#!/usr/bin/env python3
# Copyright (c) 2024, APT Group, Department of Computer Science, The University of Manchester.
# Licensed under the Apache License, Version 2.0.
"""
Crash- and hang-isolating driver for the TornadoVM CUDA fuzzer.

Each batch of cases runs in a fresh JVM under the `tornado` launcher. The driver
reads the `CASE seed=<s>` markers the harness prints and handles two failure
modes the in-process oracle cannot:

  * CRASH — the JVM dies (native segfault / hs_err); recorded for the last seed,
    hs_err_pid*.log copied into the bundle, resume at seed+1.
  * HANG  — no output for --hang-timeout seconds while the process is alive (a
    wedged CUDA kernel blocks the JVM in a native call and cannot be interrupted
    from Java, so the process is killed); recorded as a HANG finding, resume.

The CUDA backend is forced default via -Dtornado.cuda.priority=100.

Usage:
    scripts/tornado-fuzz.py --seed 1 --total 2000 --batch 200 --out /tmp/fuzz
    scripts/tornado-fuzz.py --seed 1 --total 500 --batch 1 --hang-timeout 30 --out /tmp/fuzz
"""
import argparse
import glob
import os
import queue
import re
import shutil
import subprocess
import sys
import threading

MAIN = "tornado.fuzz/uk.ac.manchester.tornado.fuzz.FuzzMain"
CASE_RE = re.compile(r"CASE seed=(\d+)")


def _pump(stream, q):
    for line in stream:
        q.put(line)
    q.put(None)  # EOF sentinel


def run_batch(seed, count, phase, out_dir, dump_kernel, hang_timeout):
    jvm = "-Dtornado.cuda.priority=100"
    if dump_kernel:
        jvm += f" -Dtornado.printKernel=true -Dtornado.print.kernel.dir={out_dir} -Dtornado.print.bytecodes=true"
    cmd = [
        "tornado",
        "-m", MAIN,
        f"--jvm={jvm}",
        "--params", f"seed={seed} count={count} phase={phase} outDir={out_dir}",
    ]
    print("+ " + " ".join(cmd), flush=True)
    proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1)

    # A hung CUDA kernel blocks the JVM thread inside a native (JNI) call, so it
    # cannot be interrupted from Java — the whole process must be killed. We detect
    # a hang as an inactivity stall: no output line for `hang_timeout` seconds while
    # the process is still alive (a healthy case emits its CASE line in well under a
    # second). A reader thread decouples us from the blocking pipe read.
    q = queue.Queue()
    reader = threading.Thread(target=_pump, args=(proc.stdout, q), daemon=True)
    reader.start()

    last_seed = None
    hung = False
    while True:
        try:
            line = q.get(timeout=hang_timeout if hang_timeout > 0 else None)
        except queue.Empty:
            if proc.poll() is None:
                hung = True
                proc.kill()
            break
        if line is None:
            break
        sys.stdout.write(line)
        sys.stdout.flush()
        m = CASE_RE.search(line)
        if m:
            last_seed = int(m.group(1))
    proc.wait()
    return (None if hung else proc.returncode), last_seed, hung


def record_crash(out_dir, seed, returncode):
    bundle = os.path.join(out_dir, "findings", f"crash-{seed}")
    os.makedirs(bundle, exist_ok=True)
    for hs in glob.glob("hs_err_pid*.log") + glob.glob(os.path.join(out_dir, "hs_err_pid*.log")):
        try:
            shutil.move(hs, os.path.join(bundle, os.path.basename(hs)))
        except OSError:
            pass
    with open(os.path.join(out_dir, "findings.jsonl"), "a") as f:
        f.write('{"seed":%d,"kind":"CRASH","exitCode":%d}\n' % (seed, returncode))
    with open(os.path.join(bundle, "FIX_ME.md"), "w") as f:
        f.write("# CUDA backend fuzz finding — CRASH\n\n")
        f.write("The JVM died (native crash / non-zero exit) while running this seed.\n\n")
        f.write("## Reproduce\n\n```bash\ntornado -m %s \\\n" % MAIN)
        f.write('    --jvm="-Dtornado.cuda.priority=100 -Dtornado.printKernel=true -Dtornado.print.kernel.dir=$(pwd)" \\\n')
        f.write('    --params "seed=%d count=1 phase=1 outDir=/tmp/fuzz"\n```\n\n' % seed)
        f.write("Inspect the copied `hs_err_pid*.log` for the native frame, and the dumped\n")
        f.write("`*.cu` for the CUDA source that triggered it.\n")
    print(f"CRASH recorded: seed={seed} exit={returncode} -> {bundle}", flush=True)


def record_hang(out_dir, seed, hang_timeout):
    bundle = os.path.join(out_dir, "findings", f"hang-{seed}")
    os.makedirs(bundle, exist_ok=True)
    with open(os.path.join(out_dir, "findings.jsonl"), "a") as f:
        f.write('{"seed":%d,"kind":"HANG","timeoutSec":%d}\n' % (seed, hang_timeout))
    with open(os.path.join(bundle, "FIX_ME.md"), "w") as f:
        f.write("# CUDA backend fuzz finding — HANG\n\n")
        f.write("The kernel for this seed produced no output for %d s and the JVM was\n" % hang_timeout)
        f.write("blocked in a native call (a healthy case completes in well under a second).\n")
        f.write("Likely an infinite loop or a deadlocked barrier in the generated CUDA code.\n\n")
        f.write("## Reproduce\n\n```bash\ntimeout %ds tornado -m %s \\\n" % (max(2 * hang_timeout, 30), MAIN))
        f.write('    --jvm="-Dtornado.cuda.priority=100 -Dtornado.printKernel=true -Dtornado.print.kernel.dir=$(pwd)" \\\n')
        f.write('    --params "seed=%d count=1 phase=1 outDir=/tmp/fuzz"\n```\n\n' % seed)
        f.write("Inspect the dumped `*.cu`: check loop bounds and any divergent `__syncthreads`.\n")
    print(f"HANG recorded: seed={seed} (no output for {hang_timeout}s) -> {bundle}", flush=True)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--seed", type=int, default=1, help="first seed")
    ap.add_argument("--total", type=int, default=1000, help="total cases to run")
    ap.add_argument("--batch", type=int, default=200, help="cases per JVM")
    ap.add_argument("--phase", type=int, default=1)
    ap.add_argument("--out", default="/tmp/fuzz", help="output directory")
    ap.add_argument("--dump-kernel", action="store_true", help="dump generated CUDA-C for every batch")
    ap.add_argument("--hang-timeout", type=int, default=60,
                    help="seconds of no output before treating a case as hung (0 = disabled). "
                         "Use --batch 1 for exact hang attribution.")
    args = ap.parse_args()

    os.makedirs(args.out, exist_ok=True)
    seed = args.seed
    remaining = args.total
    while remaining > 0:
        count = min(args.batch, remaining)
        rc, last_seed, hung = run_batch(seed, count, args.phase, args.out, args.dump_kernel, args.hang_timeout)
        if hung:
            # The hung case is the one after the last CASE line that completed, or the
            # in-flight one if it printed its CASE line before wedging.
            hang_seed = last_seed if last_seed is not None else seed
            record_hang(args.out, hang_seed, args.hang_timeout)
            advanced = (hang_seed - seed) + 1
            remaining -= max(advanced, 1)
            seed = hang_seed + 1
        elif rc != 0:
            crash_seed = last_seed if last_seed is not None else seed
            record_crash(args.out, crash_seed, rc)
            # Resume just past the crashing case.
            advanced = (crash_seed - seed) + 1
            remaining -= max(advanced, 1)
            seed = crash_seed + 1
        else:
            remaining -= count
            seed += count

    print(f"Fuzzing complete. Findings in {os.path.join(args.out, 'findings')}", flush=True)


if __name__ == "__main__":
    main()
