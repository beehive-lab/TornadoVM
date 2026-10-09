#!/usr/bin/env python3
"""Summarise tornado-test --verbose logs into per-test results.

Usage: summarize.py LOG [LOG...]          -> prints totals per log
       summarize.py --json OUT LOG [...]   -> also writes {label: {Class#test: STATUS}}
       summarize.py --diff A B             -> tests whose status differs between two logs
"""
import json, re, sys, pathlib

ANSI = re.compile(r"\x1b\[[0-9;]*m")
CLASS = re.compile(r'--params "([\w.$]+)"')
TEST = re.compile(r"Running test:\s+(\S+)\s+\.+\s+\[([A-Z ]+?)\]")

def parse(path):
    cls, res = "?", {}
    for line in pathlib.Path(path).read_text(errors="replace").splitlines():
        line = ANSI.sub("", line)
        if m := CLASS.search(line):
            cls = m.group(1).removeprefix("uk.ac.manchester.tornado.unittests.")
        elif m := TEST.search(line):
            res[f"{cls}#{m.group(1)}"] = m.group(2).strip()
    return res

def totals(res):
    out = {}
    for s in res.values():
        out[s] = out.get(s, 0) + 1
    return out

args = sys.argv[1:]
if args and args[0] == "--diff":
    a, b = parse(args[1]), parse(args[2])
    for k in sorted(set(a) | set(b)):
        if a.get(k) != b.get(k):
            print(f"{k}: {a.get(k, '-')} -> {b.get(k, '-')}")
    sys.exit(0)
out = None
if args and args[0] == "--json":
    out, args = args[1], args[2:]
all_res = {}
for p in args:
    r = parse(p)
    all_res[pathlib.Path(p).stem] = r
    print(f"{pathlib.Path(p).stem}: {len(r)} tests {totals(r)}")
if out:
    pathlib.Path(out).write_text(json.dumps(all_res, indent=1, sort_keys=True))
