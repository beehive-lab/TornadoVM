#!/usr/bin/env python3
#
# Copyright (c) 2026, APT Group, Department of Computer Science,
# The University of Manchester.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
"""Runs HostMemoryBenchmark (TornadoVM) and host_memory_bandwidth.cu (nvcc) for every host-memory
mode, and prints one markdown table comparing them, plus the pass/fail of the checks below.

    source setvars.sh                     # a TornadoVM SDK built with the CUDA backend
    python3 tornado-benchmarks/src/main/cuda/compare_host_memory.py [--csv out.csv]

Modes (TornadoVM label = nvcc label):
    nopin    = nopin        pageable memory, never page-locked
    pageable = registered   TornadoVM's default: pageable, page-locked on first use (cuMemHostRegister)
    pinned   = pinned       HostMemoryType.PINNED / cudaHostAlloc
    mapped   = mapped       HostMemoryType.MAPPED / cudaHostAlloc(Mapped), zero-copy
    staged   = -            pageable with withStagedTransfers() (TornadoVM only)
"""
import argparse
import csv
import io
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
PAIRS = [("nopin", "nopin"), ("pageable", "registered"), ("pinned", "pinned"), ("mapped", "mapped"), ("staged", None)]
BENCHMARK = "tornado.benchmarks/uk.ac.manchester.tornado.benchmarks.hostmemory.HostMemoryBenchmark"


def rows(output):
    lines = [l[4:] for l in output.splitlines() if l.startswith("CSV,") and not l.startswith("CSV,impl")]
    return list(csv.DictReader(io.StringIO("impl,label,case,bytes,median_ms,GBps\n" + "\n".join(lines))))


def run(cmd, env=None):
    print("$ " + " ".join(cmd), file=sys.stderr)
    return subprocess.run(cmd, check=True, capture_output=True, text=True, env=env).stdout


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--csv", help="also write all rows to this file")
    parser.add_argument("--nvcc", default="nvcc")
    args = parser.parse_args()

    binary = os.path.join(HERE, "host_memory_bandwidth")
    run([args.nvcc, "-O3", "-arch=native", "-o", binary, os.path.join(HERE, "host_memory_bandwidth.cu")])

    results = []
    for tornado_label, nvcc_label in PAIRS:
        mode = "pageable" if tornado_label == "nopin" else tornado_label
        jvm = "-Dtornado.cuda.priority=100 -Xmx16g" + (" -Dtornado.cuda.host.pinning=false" if tornado_label == "nopin" else "")
        results += rows(run(["tornado", "--jvm=" + jvm, "-m", BENCHMARK, mode, tornado_label]))
        if nvcc_label:
            results += rows(run([binary, nvcc_label]))

    if args.csv:
        with open(args.csv, "w", newline="") as f:
            writer = csv.DictWriter(f, fieldnames=list(results[0].keys()))
            writer.writeheader()
            writer.writerows(results)

    table = {(r["impl"], r["label"], r["case"], int(r["bytes"])): r for r in results}
    keys = sorted({(r["case"], int(r["bytes"])) for r in results}, key=lambda k: (["h2d", "d2h", "zc_read", "saxpy", "reduce_once", "reread16", "sparse_read", "alloc"].index(k[0]), k[1]))

    def cell(impl, label, case, size, field):
        r = table.get((impl, label, case, size))
        return float(r[field]) if r else None

    header = "| case | size |" + "".join(f" {t} TVM | {n or '-'} nvcc |" for t, n in PAIRS)
    print(header)
    print("|" + "---|" * (2 + 2 * len(PAIRS)))
    for case, size in keys:
        field = "median_ms" if case in ("saxpy", "reduce_once", "reread16", "sparse_read", "alloc") else "GBps"
        unit = " ms" if field == "median_ms" else ""
        line = f"| {case} | {size >> 20} MB |"
        for t, n in PAIRS:
            tv = cell("tornadovm", t, case, size, field)
            nv = cell("nvcc", n, case, size, field) if n else None
            line += f" {tv:.2f}{unit} |" if tv is not None else " - |"
            line += f" {nv:.2f}{unit} |" if nv is not None else " - |"
        print(line)
    print("\nGB/s for h2d/d2h/zc_read (higher is better); ms per execution for the workloads and alloc (lower is better).")

    checks = []
    for case in ("h2d", "d2h"):
        for size in (s for c, s in keys if c == case and s >= 16 << 20):
            tv, nv = cell("tornadovm", "pinned", case, size, "GBps"), cell("nvcc", "pinned", case, size, "GBps")
            checks.append((f"PINNED {case} {size >> 20} MB within 90% of nvcc", tv >= 0.9 * nv, f"{tv:.2f} vs {nv:.2f} GB/s"))
            tp = cell("tornadovm", "nopin", case, size, "GBps")
            checks.append((f"PINNED {case} {size >> 20} MB faster than pageable", tv > tp, f"{tv:.2f} vs {tp:.2f} GB/s"))
    for size in (s for c, s in keys if c == "zc_read" and s >= 16 << 20):
        tv, nv = cell("tornadovm", "mapped", "zc_read", size, "GBps"), cell("nvcc", "mapped", "zc_read", size, "GBps")
        checks.append((f"MAPPED zero-copy read {size >> 20} MB within 90% of nvcc", tv >= 0.9 * nv, f"{tv:.2f} vs {nv:.2f} GB/s"))
    size = next(s for c, s in keys if c == "sparse_read")
    tm, tp = cell("tornadovm", "mapped", "sparse_read", size, "median_ms"), cell("tornadovm", "pinned", "sparse_read", size, "median_ms")
    checks.append(("MAPPED beats PINNED copies on sparse access", tm < tp, f"{tm:.2f} vs {tp:.2f} ms"))
    for case in ("saxpy", "reduce_once", "reread16", "sparse_read"):
        size = next(s for c, s in keys if c == case)
        tv, nv = cell("tornadovm", "pinned", case, size, "median_ms"), cell("nvcc", "pinned", case, size, "median_ms")
        checks.append((f"PINNED {case} within 10% of nvcc", tv <= 1.1 * nv, f"{tv:.2f} vs {nv:.2f} ms"))
    print("\n| check | result | values |\n|---|---|---|")
    for name, ok, values in checks:
        print(f"| {name} | {'PASS' if ok else 'FAIL'} | {values} |")
    return 0 if all(ok for _, ok, _ in checks) else 1


if __name__ == "__main__":
    sys.exit(main())
