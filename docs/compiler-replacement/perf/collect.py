#!/usr/bin/env python3
"""Collect Graal vs TC results from ~/tc-perf/run into JSON (profiler, compile times, nsys)."""
import csv, glob, json, os, re, statistics
RUN = os.path.expanduser("~/tc-perf/run")
out = {}
for f in sorted(glob.glob(f"{RUN}/*.prof1.log")):
    b, c = os.path.basename(f).split(".")[:2]
    rec = out.setdefault(b, {}).setdefault(c, {})
    mins, avgs, comp, drv, tcn, fb = [], [], [], [], None, None
    for r in (1, 2, 3):
        p = f"{RUN}/{b}.{c}.prof{r}.log"
        if not os.path.exists(p):
            continue
        t = open(p, errors="replace").read()
        for m in re.finditer(r"bm=(\S+), id=1:0, kernelMin=([\d.Ee+]+), kernelAvg=([\d.Ee+]+)", t):
            rec["config"] = m.group(1); mins.append(float(m.group(2))); avgs.append(float(m.group(3)))
        for m in re.finditer(r"\[compile-times\] compiler=(\w+) method=(\S+) task=\S+ compilerNs=(\d+) driverNs=(\d+)", t):
            rec.setdefault("methods", []).append(m.group(2)) if m.group(2) not in rec.get("methods", []) else None
            comp.append(int(m.group(3))); drv.append(int(m.group(4)))
            rec.setdefault("compiledBy", set()).add(m.group(1))
        m = re.search(r"kernels compiled by TC: (\d+), fell back to Graal: (\d+)", t)
        if m: tcn, fb = int(m.group(1)), int(m.group(2))
    if mins:
        rec["kernelMinNs"] = statistics.median(mins); rec["kernelAvgNs"] = statistics.median(avgs)
    if comp:
        rec["compileMs"] = statistics.median(comp) / 1e6; rec["driverMs"] = statistics.median(drv) / 1e6
        rec["compileRuns"] = len(comp)
    rec["compiledBy"] = sorted(rec.get("compiledBy", []))
    if tcn is not None: rec["tcKernels"], rec["fallbacks"] = tcn, fb
    k = f"{RUN}/{b}.{c}_cuda_gpu_kern_sum.csv"
    if os.path.exists(k):
        rows = list(csv.DictReader(open(k)))
        rec["nsys"] = [{"name": r["Name"], "instances": int(r["Instances"]), "medNs": float(r["Med (ns)"]), "avgNs": float(r["Avg (ns)"]),
                        "minNs": float(r["Min (ns)"]), "totalNs": float(r["Total Time (ns)"])} for r in rows]
print(json.dumps(out, indent=1, default=list))
