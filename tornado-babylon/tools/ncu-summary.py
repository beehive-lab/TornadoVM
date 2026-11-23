#!/usr/bin/env python3
"""Summarises a profile.sh ncu CSV: per kernel, launches and mean duration, SM and DRAM
throughput, achieved occupancy and registers per thread."""
import collections
import csv
import sys


def main(path):
    rows = [r for r in csv.reader(open(path)) if len(r) > 10]
    if not rows:
        print(f"no kernel launches profiled ({path})")
        return
    header = rows[0]
    metrics = collections.defaultdict(lambda: collections.defaultdict(list))
    for r in rows[1:]:
        d = dict(zip(header, r))
        try:
            metrics[d["Kernel Name"]][d["Metric Name"]].append(float(d["Metric Value"].replace(",", "")))
        except (KeyError, ValueError):
            pass
    print(f"{'kernel':48s} {'launches':>8s} {'mean us':>9s} {'SM %':>6s} {'DRAM %':>7s} {'occup %':>8s} {'regs':>5s}")
    for kernel, m in sorted(metrics.items(), key=lambda kv: -sum(kv[1].get("gpu__time_duration.sum", [0]))):
        def mean(name):
            v = m.get(name, [])
            return sum(v) / len(v) if v else float("nan")
        n = len(m.get("gpu__time_duration.sum", []))
        print(f"{kernel[:48]:48s} {n:8d} {mean('gpu__time_duration.sum') / 1000:9.2f} {mean('sm__throughput.avg.pct_of_peak_sustained_elapsed'):6.1f} "
              f"{mean('dram__throughput.avg.pct_of_peak_sustained_elapsed'):7.1f} {mean('sm__warps_active.avg.pct_of_peak_sustained_active'):8.1f} {mean('launch__registers_per_thread'):5.0f}")
    print(f"\nraw metrics: {path}")


if __name__ == "__main__":
    main(sys.argv[1])
