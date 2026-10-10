#!/usr/bin/env python3
"""Build the TC vs Graal report page from ~/tc-perf data (run/, ncu/, cold/, warm/)."""
import csv, glob, json, os, re, statistics, subprocess, sys
H = os.path.expanduser("~/tc-perf")
OUT = sys.argv[1]
data = json.loads(subprocess.run([sys.executable, f"{H}/collect.py"], capture_output=True, text=True).stdout)

def main_kernel(x):
    n = x.get("nsys") or []
    return max(n, key=lambda r: r["totalNs"]) if n else None

rows = []
for b, v in sorted(data.items()):
    g, t = v.get("graal", {}), v.get("tc", {})
    gk, tk = main_kernel(g), main_kernel(t)
    rows.append(dict(bench=b, config=g.get("config") or t.get("config"), gK=gk["medNs"] if gk else None, tK=tk["medNs"] if tk else None,
                     gName=gk["name"] if gk else "", tName=tk["name"] if tk else "", by=t.get("compiledBy") or [], fallbacks=t.get("fallbacks")))

# ncu
ncu = {}
for f in sorted(glob.glob(f"{H}/ncu/*.csv")):
    b, c = os.path.basename(f).split(".")[:2]
    lines = [l for l in open(f, errors="replace") if l.startswith('"')]
    if not lines:
        continue
    r = list(csv.reader(lines)); h = r[0]
    ki, mi, vi = h.index("Kernel Name"), h.index("Metric Name"), h.index("Metric Value")
    d = {}
    for x in r[1:]:
        d["kernel"] = x[ki]; d[x[mi]] = x[vi].replace(",", "")
    ncu.setdefault(b, {})[c] = d

# cold compile times (code cache off)
cold = {}
for f in sorted(glob.glob(f"{H}/cold/*.log")):
    b, c = os.path.basename(f).split(".")[:2]
    for m in re.finditer(r"compiler=(\w+) method=(\S+) task=\S+ compilerNs=(\d+) driverNs=(\d+)", open(f).read()):
        cold.setdefault(b, {}).setdefault(c, {"by": set(), "comp": [], "drv": []})
        e = cold[b][c]; e["by"].add(m.group(1)); e["comp"].append(int(m.group(3)) / 1e6); e["drv"].append(int(m.group(4)) / 1e6)
coldRows = []
for b, v in sorted(cold.items()):
    g, t = v.get("graal"), v.get("tc")
    if not g or not t:
        continue
    coldRows.append(dict(bench=b, gC=statistics.median(g["comp"]), tC=statistics.median(t["comp"]), gD=statistics.median(g["drv"]), tD=statistics.median(t["drv"]),
                         by=sorted(t["by"]), runs=len(t["comp"])))

# warm compile times
warm = None
if all(os.path.exists(f"{H}/warm/{c}.log") for c in ("graal", "tc")) and os.path.exists(f"{H}/warm.clean"):
    warm = {}
    for c in ("graal", "tc"):
        comp, drv, per = [], [], {}
        for line in open(f"{H}/warm/{c}.log"):
            m = re.search(r"compiler=(\w+) method=(\S+) task=\S+ compilerNs=(\d+) driverNs=(\d+)", line)
            if m and m.group(1) == c:
                comp.append(int(m.group(3)) / 1e6); drv.append(int(m.group(4)) / 1e6); per.setdefault(m.group(2), []).append(int(m.group(3)) / 1e6)
        q = statistics.quantiles(comp, n=10)
        warm[c] = dict(n=len(comp), median=statistics.median(comp), p10=q[0], p90=q[-1], driver=statistics.median(drv), per={k: statistics.median(v) for k, v in per.items()})
    common = set(warm["graal"]["per"]) & set(warm["tc"]["per"])
    ratios = [warm["tc"]["per"][m] / warm["graal"]["per"][m] for m in common]
    warm["common"] = len(common); warm["ratio"] = statistics.median(ratios); warm["tcFaster"] = sum(r < 1 for r in ratios)
    for c in ("graal", "tc"):
        warm[c].pop("per")

payload = json.dumps(dict(rows=rows, ncu=ncu, cold=coldRows, warm=warm))
tpl = open(f"{H}/report-template.html").read()
open(OUT, "w").write(tpl.replace("__DATA__", payload))
print(f"rows={len(rows)} ncu={len(ncu)} cold={len(coldRows)} warm={'yes' if warm else 'pending'}")
