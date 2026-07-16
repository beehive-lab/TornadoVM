#!/usr/bin/env python3
# Copyright (c) 2024, APT Group, Department of Computer Science, The University of Manchester.
# Licensed under the Apache License, Version 2.0.
"""
Crash-isolating driver for the TornadoVM CUDA fuzzer.

Each batch of cases runs in a fresh JVM under the `tornado` launcher. If the JVM
dies (native segfault / hs_err) mid-batch, the driver reads the last
`CASE seed=<s>` line the harness printed, records that seed as a CRASH finding,
copies any hs_err_pid*.log into the finding bundle, then resumes at seed+1. The
CUDA backend is forced default via -Dtornado.cuda.priority=100.

Usage:
    scripts/tornado-fuzz.py --seed 1 --total 2000 --batch 200 --out /tmp/fuzz
"""
import argparse
import glob
import os
import re
import shutil
import subprocess
import sys

MAIN = "tornado.fuzz/uk.ac.manchester.tornado.fuzz.FuzzMain"
CASE_RE = re.compile(r"CASE seed=(\d+)")


def run_batch(seed, count, phase, out_dir, dump_kernel):
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
    last_seed = None
    for line in proc.stdout:
        sys.stdout.write(line)
        sys.stdout.flush()
        m = CASE_RE.search(line)
        if m:
            last_seed = int(m.group(1))
    proc.wait()
    return proc.returncode, last_seed


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


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--seed", type=int, default=1, help="first seed")
    ap.add_argument("--total", type=int, default=1000, help="total cases to run")
    ap.add_argument("--batch", type=int, default=200, help="cases per JVM")
    ap.add_argument("--phase", type=int, default=1)
    ap.add_argument("--out", default="/tmp/fuzz", help="output directory")
    ap.add_argument("--dump-kernel", action="store_true", help="dump generated CUDA-C for every batch")
    args = ap.parse_args()

    os.makedirs(args.out, exist_ok=True)
    seed = args.seed
    remaining = args.total
    while remaining > 0:
        count = min(args.batch, remaining)
        rc, last_seed = run_batch(seed, count, args.phase, args.out, args.dump_kernel)
        if rc != 0:
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
