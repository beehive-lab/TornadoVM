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
"""
Builds tornado-mlx/coverage.json, the MLX operation coverage manifest.

Inputs:
  * mlx-c-api.json            the MLX operation catalog, as named in mlx-c's headers
  * coverage-overrides.json   hand-kept decisions: Tier 1 list, CPU-only ops, exclusions
  * sources, scanned:
      - bound:        Mlx factory methods annotated @MlxOp("mlx_...") in tornado-mlx
      - tested:       references to those factories (Mlx::name or Mlx.name(...)) in the MLX unit tests
      - benchmarked:  references to those factories in the MLX benchmarks

    python3 tornado-mlx/scripts/update_coverage.py            # rewrite coverage.json
    python3 tornado-mlx/scripts/update_coverage.py --check    # verify only (run by the Maven build)

--check fails if an operation is bound but untested, if an @MlxOp names an unknown operation,
or if coverage.json is out of date.
"""

import argparse
import glob
import json
import os
import re
import sys

MODULE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REPO_DIR = os.path.dirname(MODULE_DIR)
API_FILE = os.path.join(MODULE_DIR, "mlx-c-api.json")
OVERRIDES_FILE = os.path.join(MODULE_DIR, "coverage-overrides.json")
COVERAGE_FILE = os.path.join(MODULE_DIR, "coverage.json")

FACTORY_SOURCES = os.path.join(MODULE_DIR, "src/main/java/uk/ac/manchester/tornado/mlx")
JIT_SOURCES = os.path.join(MODULE_DIR, "src/main/java/uk/ac/manchester/tornado/mlx/jit")
TEST_SOURCES = os.path.join(REPO_DIR, "tornado-unittests/src/main/java/uk/ac/manchester/tornado/unittests/mlx")
BENCHMARK_SOURCES = [
    os.path.join(REPO_DIR, "tornado-benchmarks/src/main/java"),
]

# TornadoVM array type -> MLX dtype name
DTYPES = {
    "FloatArray": "float32",
    "HalfFloatArray": "float16",
    "BFloat16Array": "bfloat16",
    "DoubleArray": "float64",
    "IntArray": "int32",
    "LongArray": "int64",
    "ShortArray": "int16",
    "Int8Array": "int8",
    "ByteArray": "uint8",
    "CharArray": "uint16",
}

# Tier 2 per the plan: reductions, math, indexing/gather/scatter, sort, fft, conv, linalg.
TIER2_PATTERNS = [
    r"^mlx_(sum|mean|max|min|prod|all|any|var|std|logsumexp|cumsum|cumprod|cummax|cummin|logcumsumexp|median)(_|$)",
    r"^mlx_(abs|sign|ceil|floor|round|log|log2|log10|log1p|expm1|power|remainder|floor_divide|divmod|reciprocal|clip|erfinv|logaddexp)$",
    r"^mlx_(sin|cos|tan|arcsin|arccos|arctan|arctan2|sinh|cosh|arcsinh|arccosh|arctanh|degrees|radians)$",
    r"^mlx_(gather|scatter|take|put_along_axis|slice|masked_scatter|where)",
    r"^mlx_(sort|argsort|partition|argpartition|argmin)",
    r"^mlx_fft_",
    r"^mlx_conv",
    r"^mlx_linalg_",
]

JIT_RE = re.compile(r"@JitBaseline\(\s*(?:value\s*=\s*)?(\{[^}]*\}|\"[^\"]*\")(?:\s*,\s*source\s*=\s*\"([^\"]*)\")?\s*\)\s*public\s+static\s+\S+\s+(\w+)\s*\(", re.S)
FACTORY_RE = re.compile(r"@MlxOp\(\s*(\{[^}]*\}|\"[^\"]*\")\s*\)\s*(?:@\w+(?:\([^)]*\))?\s*)*public\s+static\s+\S+\s+(\w+)\s*\(([^)]*)\)", re.S)


def load(path):
    with open(path) as fh:
        return json.load(fh)


def java_files(roots):
    for root in roots:
        if os.path.isdir(root):
            yield from sorted(glob.glob(os.path.join(root, "**", "*.java"), recursive=True))


def scan_factories():
    """{factory method name: set(op)} and {op: set(dtype)} from @MlxOp-annotated methods."""
    methods, dtypes, where = {}, {}, {}
    for path in java_files([FACTORY_SOURCES]):
        text = open(path).read()
        for m in FACTORY_RE.finditer(text):
            ops = re.findall(r"\"([^\"]+)\"", m.group(1))
            name, params = m.group(2), m.group(3)
            methods.setdefault(name, set()).update(ops)
            types = {DTYPES[t] for t in re.findall(r"\b(\w+Array)\b", params) if t in DTYPES}
            for op in ops:
                dtypes.setdefault(op, set()).update(types)
                where.setdefault(op, set()).add(os.path.relpath(path, REPO_DIR))
    return methods, dtypes, where


def scan_jit_baselines():
    """{kernel name: set(op)} and {op: [(class.kernel, source)]} from @JitBaseline-annotated kernels."""
    kernels, baselines = {}, {}
    for path in java_files([JIT_SOURCES]):
        cls = os.path.splitext(os.path.basename(path))[0]
        for m in JIT_RE.finditer(open(path).read()):
            ops = re.findall(r"\"([^\"]+)\"", m.group(1))
            source, name = m.group(2) or "written", m.group(3)
            kernels.setdefault(cls + "::" + name, set()).update(ops)
            for op in ops:
                baselines.setdefault(op, []).append((cls + "." + name, source))
    return kernels, baselines


def scan_jit_references(roots, kernels):
    """Ops whose JIT kernels are referenced (JitX::name or JitX.name) in the given sources."""
    used = set()
    for path in java_files(roots):
        for cls, name in re.findall(r"\b(Jit\w+)\s*(?:::|\.)\s*(\w+)", open(path).read()):
            used.update(kernels.get(cls + "::" + name, ()))
    return used


def scan_references(roots, methods):
    """Ops whose factories are referenced (MlxX::name or MlxX.name(), for any factory class MlxX) in the given sources."""
    used = set()
    for path in java_files(roots):
        text = open(path).read()
        for name in re.findall(r"\bMlx\w*\s*(?:::|\.)\s*(\w+)", text):
            used.update(methods.get(name, ()))
    return used


def tier_of(op, tier1):
    if op in tier1:
        return 1
    if any(re.search(p, op) for p in TIER2_PATTERNS):
        return 2
    return 3


def exclusion_of(op, overrides):
    for rule in overrides.get("excluded", []):
        if op in rule.get("ops", []) or ("prefix" in rule and op.startswith(rule["prefix"])):
            return rule["reason"]
    return None


def build():
    api = load(API_FILE)
    overrides = load(OVERRIDES_FILE)
    methods, dtypes, where = scan_factories()
    tested = scan_references([TEST_SOURCES], methods)
    jit_kernels, jit_baselines = scan_jit_baselines()
    jit_tested = scan_jit_references([TEST_SOURCES], jit_kernels)
    benchmarked = scan_references(BENCHMARK_SOURCES, methods)

    known = {o["name"] for o in api["operations"]}
    bound_ops = set().union(*methods.values()) if methods else set()
    unknown = sorted(bound_ops - known)

    tier1 = set(overrides.get("tier1", []))
    cpu_only = set(overrides.get("cpuOnly", {}).get("ops", []))
    ops = []
    for o in api["operations"]:
        name = o["name"]
        entry = {
            "name": name,
            "header": o["header"],
            "tier": tier_of(name, tier1),
            "device": "cpu" if name in cpu_only else "gpu",
            "bound": name in bound_ops,
            "tested": name in tested,
            "benchmarked": name in benchmarked,
        }
        reason = exclusion_of(name, overrides)
        if reason:
            entry["excluded"] = reason
        if name in jit_baselines:
            entry["jitBaseline"] = {
                "kernels": sorted(k for k, _ in jit_baselines[name]),
                "sources": sorted({src for _, src in jit_baselines[name]}),
                "tested": name in jit_tested,
            }
        elif name in overrides.get("noJitBaseline", {}):
            entry["jitBaseline"] = {"none": overrides["noJitBaseline"][name]}
        if name in dtypes:
            entry["dtypes"] = sorted(dtypes[name])
            entry["factories"] = sorted(where[name])
        ops.append(entry)

    in_scope = [e for e in ops if "excluded" not in e]
    summary = {
        "operations": len(ops),
        "excluded": len(ops) - len(in_scope),
        "inScope": len(in_scope),
        "bound": sum(e["bound"] for e in in_scope),
        "tested": sum(e["tested"] for e in in_scope),
        "benchmarked": sum(e["benchmarked"] for e in in_scope),
        "jitBaselines": sum(1 for e in in_scope if e.get("jitBaseline", {}).get("tested")),
        "remaining": sum(not e["bound"] for e in in_scope),
        "byTier": {
            str(t): {
                "inScope": sum(1 for e in in_scope if e["tier"] == t),
                "bound": sum(1 for e in in_scope if e["tier"] == t and e["bound"]),
            }
            for t in (1, 2, 3)
        },
    }
    coverage = {
        "generatedBy": "tornado-mlx/scripts/update_coverage.py",
        "mlxCVersion": api["mlxCVersion"],
        "summary": summary,
        "operations": ops,
    }
    problems = []
    warnings = []
    for op in unknown:
        problems.append("@MlxOp names %s, which is not an mlx-c operation (see mlx-c-api.json)" % op)
    for e in ops:
        if e["bound"] and not e["tested"]:
            problems.append("%s is bound (%s) but no MLX unit test uses it" % (e["name"], ", ".join(e.get("factories", []))))
        if e["bound"]:
            jit = e.get("jitBaseline")
            if jit is None or ("none" not in jit and not jit["tested"]):
                missing = "has no JIT baseline" if jit is None else "has a JIT baseline no MLX test uses"
                message = "%s is bound but %s (a KernelContext kernel annotated @JitBaseline, or a noJitBaseline entry with a reason)" % (e["name"], missing)
                (problems if overrides.get("requireJitBaseline") else warnings).append(message)
        if e["bound"] and "excluded" in e:
            problems.append("%s is bound but also excluded: %s" % (e["name"], e["excluded"]))
    return coverage, problems, warnings


def render(coverage):
    return json.dumps(coverage, indent=2) + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true", help="verify only; do not rewrite coverage.json")
    args = parser.parse_args()

    coverage, problems, warnings = build()
    s = coverage["summary"]
    line = "[mlx coverage] %d/%d in-scope operations bound, %d tested, %d with a tested JIT baseline, %d benchmarked; %d excluded (Tier 1: %d/%d bound)" % (
        s["bound"], s["inScope"], s["tested"], s["jitBaselines"], s["benchmarked"], s["excluded"], s["byTier"]["1"]["bound"], s["byTier"]["1"]["inScope"])
    if args.check:
        current = open(COVERAGE_FILE).read() if os.path.exists(COVERAGE_FILE) else ""
        if current != render(coverage):
            problems.append("coverage.json is out of date: run python3 tornado-mlx/scripts/update_coverage.py")
    else:
        with open(COVERAGE_FILE, "w") as fh:
            fh.write(render(coverage))
    print(line)
    for w in warnings:
        print("[mlx coverage] WARNING: " + w, file=sys.stderr)
    for p in problems:
        print("[mlx coverage] ERROR: " + p, file=sys.stderr)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
