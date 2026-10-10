#!/usr/bin/env python3
"""Differential harness, Graal vs TC (Phase 6).

Runs each tornado-test class twice on one device - Graal, then TC with fallback off - with the
class's own suite parameters (testParameters / testMethods from tornado-test), and compares per test:
pass/fail status, and per task the launch configuration (dims, global and local work size, from
-Dtornado.threadInfo). Writes <out>/diff.json and <out>/diff.md (a parity-matrix section).

  tc-diff.py run  [--device 1:0] [--out DIR] [--classes FILE | --families a,b,..] [--compilers graal,tc]
  tc-diff.py diff --out DIR          # compare existing logs only
Generated text is not compared: the two compilers print different code by design.
"""
import argparse, collections, json, os, re, subprocess, sys, time

ANSI = re.compile(r"\x1b\[[0-9;]*m")
HOME = os.path.expanduser("~")
REPO = os.path.join(HOME, "tornadovm-compiler")
NO_THREAD_INFO = {"uk.ac.manchester.tornado.unittests.memory.leak.TestMemoryLeak"}  # launch loops: output would block the pipe


def sdk_bin():
    out = subprocess.run(["bash", "-c", f"cd {REPO} && source setvars.sh >/dev/null && dirname $(which tornado-test)"], capture_output=True, text=True).stdout.strip()
    return out


def suite_entries(bindir):
    """Class -> (jvm parameters, methods) from tornado-test's TestEntry list, in suite order."""
    text = open(os.path.join(bindir, "tornado-test")).read()
    body = text[text.index("__TEST_THE_WORLD__"):]
    entries, i = [], 0
    while (i := body.find("TestEntry(", i)) >= 0:
        depth, j = 0, i + len("TestEntry")
        while True:  # the matching close parenthesis
            depth += {"(": 1, ")": -1}.get(body[j], 0)
            j += 1
            if depth == 0:
                break
        e = body[i:j]
        i = j
        name = re.search(r'testName="([^"]+)"', e) or re.search(r'TestEntry\(\s*"([^"]+)"', e)
        if not name:
            continue
        lists = {k: re.findall(r'"([^"]+)"', m.group(1)) for k in ("testParameters", "testMethods") if (m := re.search(k + r"=\[(.*?)\]", e, re.S))}
        if "TORNADOVM_HOME" in e:
            continue  # virtual-device tests (OpenCL only): parameters built from the environment
        entries.append((name.group(1), lists.get("testParameters", []), lists.get("testMethods", [])))
        if "__TORNADO_TESTS_WHITE_LIST__" in body[:i]:
            break
    return entries


def run_class(bindir, cls, params, methods, compiler, device, out):
    jvm = [f"-Dtornado.unittests.device={device}", f"-Dtornado.compiler={compiler}", "-Dtornado.compiler.fallback=false", "-Dtornado.tc.stats=true"] + params
    if cls not in NO_THREAD_INFO:
        jvm.append("-Dtornado.threadInfo=True")
    targets = [f"{cls}#{m}" for m in methods] or [cls]
    log = os.path.join(out, compiler, cls + ".log")
    with open(log, "w") as f:
        for t in targets:
            f.write(f'tornado-test --params "{t}"\n'); f.flush()
            cmd = ["bash", "-c", f'cd {REPO} && source setvars.sh >/dev/null && timeout 1800 tornado-test --verbose -J"{" ".join(jvm)}" {t}']
            subprocess.run(cmd, stdout=f, stderr=subprocess.STDOUT)
    return log


def parse(log):
    # Launches in order per task: the runner prints results only after a whole class, so a launch
    # cannot be tied to its test; both compilers run the same tests in the same order instead.
    tests, launches, stats = {}, collections.defaultdict(list), collections.Counter()
    cls = "?"
    lines = [ANSI.sub("", l) for l in open(log, errors="replace").read().splitlines()]
    for i, line in enumerate(lines):
        if m := re.search(r'--params "uk\.ac\.manchester\.tornado\.unittests\.([\w.$]+?)(?:#\w+)?"', line):
            cls = m.group(1)
        elif m := re.search(r"Running test:\s+(\S+)\s+\.+\s+\[([A-Z :]+?)\]", line):
            tests[f"{cls}#{m.group(1)}"] = m.group(2).split(":")[0].strip()
        elif m := re.match(r"Task info: (\S+)", line):
            info = {}
            for j in range(i + 1, min(i + 9, len(lines))):
                if mm := re.match(r"\s+(Dims|Global work size|Local\s+work size)\s*:\s*(.*)", lines[j]):
                    info[mm.group(1).replace("  ", " ")] = mm.group(2).strip()
            launches[m.group(1)].append((info.get("Dims"), info.get("Global work size"), info.get("Local work size")))
        elif m := re.search(r"kernels compiled by TC: (\d+), fell back to Graal: (\d+) \{(.*)\}", line):
            stats["tc"] += int(m.group(1))
            for code, n in re.findall(r"(TC-E\d+)=(\d+)", m.group(3)):
                stats[code] += int(n)
    return tests, dict(launches), stats


def compare(out):
    result = {"classes": {}, "summary": collections.Counter()}
    gdir, tdir = os.path.join(out, "graal"), os.path.join(out, "tc")
    for name in sorted(os.listdir(tdir)):
        if not name.endswith(".log") or not os.path.exists(os.path.join(gdir, name)):
            continue
        gt, gl, _ = parse(os.path.join(gdir, name))
        tt, tl, ts = parse(os.path.join(tdir, name))
        diffs = [{"test": k, "graal": gt.get(k), "tc": tt.get(k)} for k in sorted(set(gt) | set(tt)) if gt.get(k) != tt.get(k)]
        launch, counts = [], []
        norm = lambda conf: (conf[0], conf[1], "[1, 1, 1]" if conf[0] == "0" and conf[2] == "null" else conf[2])  # 1-thread launch, local size unset
        for k in sorted(set(gl) & set(tl)):
            if len(gl[k]) != len(tl[k]):
                counts.append({"task": k, "graal": len(gl[k]), "tc": len(tl[k])})  # a refused kernel launches fewer times
                continue
            for n, (g, c) in enumerate(zip(gl[k], tl[k])):
                if norm(g) != norm(c):
                    launch.append({"task": k, "launch": n, "graal": g, "tc": c})
                    break
        result["classes"][name[:-4]] = {"tests": len(tt), "pass": sum(v == "PASS" for v in tt.values()), "graalPass": sum(v == "PASS" for v in gt.values()),
                                       "diffs": diffs, "launchDiffs": launch, "launchCountDiffs": counts, "tcStats": dict(ts)}
        s = result["summary"]
        s["tests"] += len(tt); s["tcPass"] += sum(v == "PASS" for v in tt.values()); s["graalPass"] += sum(v == "PASS" for v in gt.values())
        s["diffs"] += len(diffs); s["launchDiffs"] += len(launch); s["tcKernels"] += ts.get("tc", 0)
        s["tcBetter"] += sum(d["tc"] == "PASS" and d["graal"] != "PASS" for d in diffs)
        s["tcWorse"] += sum(d["graal"] == "PASS" and d["tc"] != "PASS" for d in diffs)
        for code, n in ts.items():
            if code.startswith("TC-E"):
                result.setdefault("refusals", collections.Counter())[code] += n
    json.dump(result, open(os.path.join(out, "diff.json"), "w"), indent=1, default=dict)
    write_md(result, out)
    return result


def write_md(r, out):
    s = r["summary"]
    lines = ["<!-- tc-diff: generated by baseline/tc-diff.py; do not edit by hand -->",
             f"Run `{os.path.basename(out)}`: {s['tests']} tests in {len(r['classes'])} classes, TC with fallback **off**. "
             f"PASS: Graal {s['graalPass']}, TC {s['tcPass']}. Status differences: {s['diffs']} (TC worse {s['tcWorse']}, TC better {s['tcBetter']}). "
             f"Launch-configuration differences: {s['launchDiffs']}. Kernels compiled by TC: {s['tcKernels']}.", ""]
    if r.get("refusals"):
        lines.append("TC refusals (each fails its test with fallback off): " + ", ".join(f"{k} {v}" for k, v in sorted(r["refusals"].items(), key=lambda x: -x[1])) + ".")
        lines.append("")
    lines += ["| Class | Tests | Graal PASS | TC PASS | Differences |", "|---|---:|---:|---:|---|"]
    for c, v in r["classes"].items():
        if v["diffs"] or v["launchDiffs"]:
            d = "; ".join(f"`{x['test'].split('#')[1]}` {x['graal']}→{x['tc']}" for x in v["diffs"][:6]) + (" …" if len(v["diffs"]) > 6 else "")
            if v["launchDiffs"]:
                d += ("; " if d else "") + f"{len(v['launchDiffs'])} launch config(s) differ"
            lines.append(f"| {c.replace('uk.ac.manchester.tornado.unittests.', '')} | {v['tests']} | {v['graalPass']} | {v['pass']} | {d} |")
    lines.append("<!-- /tc-diff -->")
    open(os.path.join(out, "diff.md"), "w").write("\n".join(lines) + "\n")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("mode", choices=["run", "diff"])
    ap.add_argument("--device", default="1:0")
    ap.add_argument("--out", default=os.path.join(HOME, "tc-diff", time.strftime("%Y%m%d-%H%M")))
    ap.add_argument("--classes")
    ap.add_argument("--families")
    ap.add_argument("--compilers", default="graal,tc")
    ap.add_argument("--exclude", default="", help="regex of class names to skip")
    ap.add_argument("--with-memory-leak", action="store_true",
                    help="also run memory.leak.TestMemoryLeak (300 s loops): only for the final validation of the CUDA backend work")
    a = ap.parse_args()
    if a.mode == "run":
        bindir = sdk_bin()
        entries = suite_entries(bindir)
        if a.classes:
            wanted = set(open(a.classes).read().split())
            entries = [e for e in entries if e[0] in wanted]
        if a.families:
            fams = tuple(f"uk.ac.manchester.tornado.unittests.{f}." for f in a.families.split(","))
            entries = [e for e in entries if e[0].startswith(fams)]
        if a.exclude:
            entries = [e for e in entries if not re.search(a.exclude, e[0])]
        if not a.with_memory_leak:
            entries = [e for e in entries if e[0] not in NO_THREAD_INFO]
        for c in a.compilers.split(","):
            os.makedirs(os.path.join(a.out, c), exist_ok=True)
        print(f"{len(entries)} classes -> {a.out}", flush=True)
        for name, params, methods in entries:
            for c in a.compilers.split(","):
                run_class(bindir, name, params, methods, c, a.device, a.out)
            print(f"{time.strftime('%T')} {name}", flush=True)
    r = compare(a.out)
    print(json.dumps(r["summary"]))


if __name__ == "__main__":
    main()
