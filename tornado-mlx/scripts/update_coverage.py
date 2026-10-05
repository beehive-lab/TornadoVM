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
  * coverage-overrides.json   hand-kept decisions: LLM operation list, CPU-only ops, exclusions
  * sources, scanned:
      - bound:        Mlx factory methods annotated @MlxOp("mlx_...") in tornado-mlx
      - tested:       references to those factories (Mlx::name or Mlx.name(...)) in the MLX unit tests

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
TEST_SOURCES = os.path.join(REPO_DIR, "tornado-unittests/src/main/java/uk/ac/manchester/tornado/unittests/mlx")

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

# Operation categories, matched in order on the mlx-c name; the first match wins.
CATEGORIES = [
    ("Transforms", r"^mlx_(async_eval|checkpoint|custom_function|custom_vjp|eval|jvp|value_and_grad|vjp|stop_gradient|depends)$"),
    ("CustomKernels", r"^mlx_fast_(cuda|metal)_kernel"),
    ("NeuralNetwork", r"^mlx_fast_"),
    ("Fft", r"^mlx_fft_"),
    ("Random", r"^mlx_random_"),
    ("Quantization", r"^mlx_(quantize|dequantize|quantized_matmul|gather_qmm|qqmm|to_fp8|from_fp8)$"),
    ("Convolution", r"^mlx_conv"),
    ("LinearAlgebra", r"^mlx_(linalg_\w+|matmul|addmm|einsum|inner|outer|kron|tensordot(_axis)?|block_masked_mm|segmented_mm|gather_mm|hadamard_transform)$"),
    ("Scans", r"^mlx_(cumsum|cumprod|cummax|cummin|logcumsumexp)$"),
    ("Sorting", r"^mlx_(arg)?(sort|partition)(_axis)?$|^mlx_topk"),
    ("Reductions", r"^mlx_(sum|prod|max|min|mean|var|std|logsumexp|all|any|argmax|argmin|softmax)(_axis|_axes)?$|^mlx_median$"),
    ("Indexing", r"^mlx_(take|gather|scatter|put_along_axis|masked_scatter|slice)"),
    ("Creation", r"^mlx_(arange|linspace|eye|identity|tri|tril|triu|diag|diagonal|trace|full|full_like|zeros|zeros_like|ones|ones_like|bartlett|blackman|hamming|hanning|meshgrid)$"),
    ("Logic", r"^mlx_(equal|not_equal|greater|greater_equal|less|less_equal|logical_\w+|bitwise_\w+|left_shift|right_shift|is\w+|allclose|array_equal)$"),
    ("Shape", r"^mlx_(reshape|flatten|unflatten|squeeze\w*|expand_dims\w*|atleast_\w+|transpose\w*|swapaxes|moveaxis|broadcast_\w+|as_strided|contiguous|copy|astype|view|number_of_elements|concatenate\w*|stack\w*|split\w*|repeat\w*|tile|roll\w*|pad\w*)$"),
    ("Arithmetic", r"^mlx_"),
]

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


def scan_references(roots, methods):
    """Ops whose factories are referenced (MlxX::name or MlxX.name(), for any factory class MlxX) in the given sources."""
    used = set()
    for path in java_files(roots):
        text = open(path).read()
        for name in re.findall(r"\bMlx\w*\s*(?:::|\.)\s*(\w+)", text):
            used.update(methods.get(name, ()))
    return used


def category_of(op):
    return next(name for name, pattern in CATEGORIES if re.search(pattern, op))


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

    known = {o["name"] for o in api["operations"]}
    bound_ops = set().union(*methods.values()) if methods else set()
    unknown = sorted(bound_ops - known)

    llm = set(overrides.get("llmOperations", []))
    cpu_only = set(overrides.get("cpuOnly", {}).get("ops", []))
    ops = []
    for o in api["operations"]:
        name = o["name"]
        entry = {
            "name": name,
            "header": o["header"],
            "category": category_of(name),
            "llm": name in llm,
            "device": "cpu" if name in cpu_only else "gpu",
            "bound": name in bound_ops,
            "tested": name in tested,
        }
        reason = exclusion_of(name, overrides)
        if reason:
            entry["excluded"] = reason
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
        "remaining": sum(not e["bound"] for e in in_scope),
        "llm": {
            "inScope": sum(1 for e in in_scope if e["llm"]),
            "bound": sum(1 for e in in_scope if e["llm"] and e["bound"]),
        },
        "byCategory": {
            name: {
                "inScope": sum(1 for e in in_scope if e["category"] == name),
                "bound": sum(1 for e in in_scope if e["category"] == name and e["bound"]),
            }
            for name, _ in CATEGORIES if any(e["category"] == name for e in in_scope)
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
    line = "[mlx coverage] %d/%d in-scope operations bound, %d tested; %d excluded (LLM operations: %d/%d bound)" % (
        s["bound"], s["inScope"], s["tested"], s["excluded"], s["llm"]["bound"], s["llm"]["inScope"])
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
