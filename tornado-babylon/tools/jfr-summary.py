#!/usr/bin/env python3
"""Summarises a profile.sh jfr recording: HAT dispatches per kernel (first, with the plan and
compilation, and later ones), code-model translations, and where the CPU samples went."""
import collections
import json
import subprocess
import sys


def events(path, name):
    out = subprocess.run(["jfr", "print", "--json", "--stack-depth", "64", "--events", name, path], capture_output=True, text=True).stdout
    return json.loads(out)["recording"]["events"] if out.strip() else []


def ms(duration):
    # ISO-8601, e.g. PT0.012345S
    return float(duration[2:-1]) * 1000 if duration.startswith("PT") and duration.endswith("S") else 0.0


def classify(frames):
    """The innermost frame that belongs to a known component (frames[0] is the top of the stack)."""
    for f in frames:
        if "babylon.tc" in f:
            return "Babylon frontend"
        if ".tornado.tc." in f:
            return "TC compiler"
        if "babylon.hat" in f:
            return "HAT backend"
        if "manchester.tornado" in f:
            return "TornadoVM runtime/driver"
        if f.startswith(("hat.", "optkl.")):
            return "HAT core"
        if f.startswith("jdk.incubator.code"):
            return "Babylon code reflection"
    return "application/JDK"


def main(path):
    dispatches = collections.defaultdict(list)
    for e in events(path, "tornado.hat.Dispatch"):
        v = e["values"]
        dispatches[(v["kernel"], v["tile"])].append((v["newPlan"], ms(v["duration"])))
    if dispatches:
        print(f"{'HAT dispatch':44s} {'n':>5s} {'first ms':>10s} {'later mean ms':>14s} {'later min ms':>13s}")
        for (kernel, tile), ds in sorted(dispatches.items(), key=lambda kv: -sum(d for _, d in kv[1])):
            first = [d for p, d in ds if p]
            later = [d for p, d in ds if not p]
            name = kernel + (" (tile)" if tile else "")
            print(f"{name:44s} {len(ds):5d} {(sum(first) if first else 0):10.2f} {(sum(later) / len(later) if later else 0):14.3f} {(min(later) if later else 0):13.3f}")
    translations = events(path, "tornado.babylon.Translate")
    if translations:
        total = sum(ms(e["values"]["duration"]) for e in translations)
        print(f"\ncode models translated: {len(translations)}, {total:.1f} ms in total")
        for e in sorted(translations, key=lambda e: -ms(e["values"]["duration"]))[:8]:
            v = e["values"]
            print(f"  {v['kernel']:42s} {ms(v['duration']):8.2f} ms  {v['operations']:5d} operations")
    compiles = events(path, "tornado.Compile")
    if compiles:
        by = collections.defaultdict(float)
        for e in compiles:
            by[e["values"]["phase"]] += ms(e["values"]["duration"])
        print("\ncompilations (tornado.Compile): " + ", ".join(f"{k} {v:.0f} ms" for k, v in sorted(by.items())))
    samples = events(path, "jdk.ExecutionSample") + events(path, "jdk.NativeMethodSample")
    if samples:
        kinds = collections.Counter()
        for s in samples:
            frames = [f["method"]["type"]["name"] for f in s["values"]["stackTrace"]["frames"]]
            kinds[classify(frames)] += 1
        total = sum(kinds.values())
        print("\nCPU samples: " + ", ".join(f"{k} {100 * n / total:.0f}%" for k, n in kinds.most_common()))
    print(f"\nrecording: {path}  (jfr print --events tornado.hat.Dispatch {path}; or open it in JDK Mission Control)")


if __name__ == "__main__":
    main(sys.argv[1])
