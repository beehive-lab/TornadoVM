/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.tc.codegen;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import uk.ac.manchester.tornado.tc.ir.Inst;
import uk.ac.manchester.tornado.tc.ir.Opcode;
import uk.ac.manchester.tornado.tc.ir.Type;
import uk.ac.manchester.tornado.tc.ir.Type.TileType;
import uk.ac.manchester.tornado.tc.ir.Value;
import uk.ac.manchester.tornado.tc.ir.analysis.Constants;

/**
 * CUDA Tile C++ ({@code cuda_tile.h}, namespace {@code ct}) for the {@code tile.*} intrinsics of a
 * TileContext kernel. Tiles have static types ({@code ct::tile<float, ct::shape<64, 64>>}); views
 * are {@code ct::partition_view}s over {@code ct::tensor_span}s and are only ever declared with
 * {@code auto}. FP8 has no arithmetic in CUDA Tile: operands widen to float and results round back
 * after every operation, so chained operations see the API's FP8 values.
 */
final class TileSpellings {

    static final String PRELUDE = """
            #include "cuda_tile.h"
            namespace ct = cuda::tiles;
            using namespace ct::literals;
            """;

    private static final Map<String, String> CPP = Map.of("F16", "__half", "BF16", "__nv_bfloat16", "F32", "float", "F64", "double", "TF32", "__nv_tf32", "FP8_E4M3", "__nv_fp8_e4m3",
            "FP8_E5M2", "__nv_fp8_e5m2", "S8", "signed char", "S32", "int", "PRED", "bool");

    private static final Map<String, String> OPERATORS = Map.ofEntries(Map.entry("add", "+"), Map.entry("sub", "-"), Map.entry("mul", "*"), Map.entry("div", "/"),
            Map.entry("bitwiseAnd", "&"), Map.entry("bitwiseOr", "|"), Map.entry("bitwiseXor", "^"), Map.entry("shiftLeft", "<<"), Map.entry("shiftRight", ">>"),
            Map.entry("logicalAnd", "&"), Map.entry("logicalOr", "|"), Map.entry("lessThan", "<"), Map.entry("lessOrEqual", "<="), Map.entry("greaterThan", ">"),
            Map.entry("greaterOrEqual", ">="), Map.entry("equalTo", "=="), Map.entry("notEqualTo", "!="));

    private static final Map<String, String> CALLS = Map.of("maximum", "max", "minimum", "min", "atan2", "atan2", "pow", "pow", "remainder", "remainder", "floorDiv", "floordiv",
            "ceilDiv", "ceildiv", "mulhi", "mulhi", "matmul", "matmul");

    private static final Map<String, String> REDUCTIONS = Map.ofEntries(Map.entry("sum", "sum"), Map.entry("max", "reduce_max"), Map.entry("min", "reduce_min"),
            Map.entry("prod", "prod"), Map.entry("reduceBitAnd", "reduce_bitand"), Map.entry("reduceBitOr", "reduce_bitor"), Map.entry("reduceBitXor", "reduce_bitxor"),
            Map.entry("allOf", "all_of"), Map.entry("anyOf", "any_of"), Map.entry("prefixSum", "partial_sum"), Map.entry("prefixProduct", "partial_prod"));

    private static final Map<String, String> ATOMICS = Map.of("add", "atomic_add", "sub", "atomic_sub", "min", "atomic_min", "max", "atomic_max", "and", "atomic_and", "or",
            "atomic_or", "xor", "atomic_xor", "exchange", "atomic_xchg");

    private static final String STRIDED_EXTENTS = "ct::extents<uint32_t, ct::dynamic_extent, ct::dynamic_extent>";

    private TileSpellings() {
    }

    static String cpp(String dtype) {
        return required(CPP, dtype, "element type");
    }

    /** A table entry that must exist: a missing one is an error, never the text "null" (CG-4). */
    private static String required(Map<String, String> table, String key, String what) {
        var value = table.get(key);
        if (value == null) {
            throw new CEmitter.EmitException("TC-E9001", "no CUDA Tile spelling for " + what + " " + key);
        }
        return value;
    }

    /** {@code ct::tile<float, ct::shape<64, 64>>}. */
    static String tileType(TileType t) {
        return "ct::tile<" + cpp(t.dtype()) + ", " + shapeType(t.shape()) + ">";
    }

    private static String shapeType(List<Integer> shape) {
        return "ct::shape<" + shape.stream().map(String::valueOf).collect(Collectors.joining(", ")) + ">";
    }

    private static String shapeValue(List<Integer> shape) {
        return "ct::shape{" + shape.stream().map(n -> n + "_ic").collect(Collectors.joining(", ")) + "}";
    }

    private static boolean fp8(String dtype) {
        return dtype.startsWith("FP8");
    }

    private static String operand(String dtype, String text) {
        return fp8(dtype) ? "ct::element_cast<float>(" + text + ")" : text;
    }

    private static String result(String dtype, String expression) {
        return fp8(dtype) ? "ct::element_cast<" + cpp(dtype) + ">(" + expression + ")" : expression;
    }

    private static TileType tile(Value v) {
        return (TileType) v.type();
    }

    /** A constant operand (an axis, a fill value): TileTyping guarantees it; anything else is an error, never 0 (CG-4). */
    private static long intConstant(Value v) {
        return Constants.integer(v).orElseThrow(() -> new CEmitter.EmitException("TC-E3401", "a tile axis must be a compile-time constant"));
    }

    private static double floatConstant(Value v) {
        if (v instanceof Inst c && c.opcode() instanceof Opcode.ConstFloat(var d)) {
            return d;
        }
        if (v instanceof Inst c && c.opcode() instanceof Opcode.ConstInt(var n)) {
            return n;
        }
        throw new CEmitter.EmitException("TC-E3401", "a tile fill value must be a compile-time constant");
    }

    /** A {@code ct::full} fill value in the tile's element type (it is part of the source, so it must fold). */
    private static String literal(String dtype, double value) {
        return switch (dtype) {
            case "S8", "S32" -> Long.toString((long) value);
            case "F64" -> Double.toString(value);
            default -> value + "f";
        };
    }

    private static String join(List<String> texts) {
        return String.join(", ", texts);
    }

    /** The C++ expression of tile intrinsic {@code name}; {@code ops} are the operand texts. */
    static String expression(String name, Inst inst, List<String> ops) {
        var values = inst.operands();
        var type = inst.type() instanceof TileType t ? t : null;
        // A view's element type is an immediate, or else a DType operand after the array.
        int first = inst.immediates().has("elem") ? 1 : 2;
        if (name.equals("tile.view_strided")) {
            var pointer = "(reinterpret_cast<" + cpp(type.dtype()) + " *>(" + ops.get(0) + ") + " + ops.get(first) + ")";
            return "ct::tensor_span{" + pointer + ", ct::layout_strided_mapping<" + STRIDED_EXTENTS + ", " + STRIDED_EXTENTS + ">{" + STRIDED_EXTENTS + "{(uint32_t) "
                    + ops.get(first + 1) + ", (uint32_t) " + ops.get(first + 2) + "}, " + STRIDED_EXTENTS + "{(uint32_t) " + ops.get(first + 3) + ", 1u}}}";
        }
        if (name.equals("tile.view")) {
            // The payload is 32-byte aligned by the runtime: the hint keeps loads TMA eligible.
            return "ct::tensor_span{ct::assume_aligned(reinterpret_cast<" + cpp(type.dtype()) + " *>(" + ops.get(0) + "), 16_ic), ct::extents{" + join(ops.subList(first, ops.size()))
                    + "}}";
        }
        switch (name) {
            case "tile.bid" -> {
                return Intrinsics.fillImmediates("ct::bid().{dim|xyz}", inst.immediates());
            }
            case "tile.num_blocks" -> {
                return Intrinsics.fillImmediates("ct::num_blocks().{dim|xyz}", inst.immediates());
            }
            case "tile.partition" -> {
                return "ct::partition_view{" + ops.get(0) + ", " + shapeValue(type.shape()) + "}";
            }
            case "tile.load", "tile.load_masked" -> {
                return ops.get(0) + "." + (name.endsWith("masked") ? "load_masked(" : "load(") + join(ops.subList(1, ops.size())) + ")";
            }
            case "tile.atomic_load" -> {
                // atomic_load's memory-order parameter has no default.
                return ops.get(0) + ".atomic_load(ct::memory_order_relaxed_t{}, " + join(ops.subList(1, ops.size())) + ")";
            }
            case "tile.store", "tile.store_masked" -> {
                return ops.get(0) + "." + (name.endsWith("masked") ? "store_masked(" : "store(") + join(ops.subList(1, ops.size())) + ")";
            }
            case "tile.atomic_store" -> {
                return ops.get(0) + ".atomic_store(" + ops.get(1) + ", ct::memory_order_relaxed_t{}, " + join(ops.subList(2, ops.size())) + ")";
            }
            case "tile.zeros" -> {
                return "ct::full<" + tileType(type) + ">(" + literal(type.dtype(), 0) + ")";
            }
            case "tile.ones" -> {
                return "ct::full<" + tileType(type) + ">(" + literal(type.dtype(), 1) + ")";
            }
            case "tile.full" -> {
                return "ct::full<" + tileType(type) + ">(" + literal(type.dtype(), floatConstant(values.get(1))) + ")";
            }
            case "tile.iota" -> {
                return "ct::iota<" + tileType(type) + ">()";
            }
            case "tile.broadcast", "tile.reshape" -> {
                return "ct::" + name.substring(5) + "(" + ops.get(0) + ", " + shapeType(type.shape()) + "{})";
            }
            case "tile.extract" -> {
                int rank = type.shape().size();
                return "ct::extract(" + ops.get(0) + ", " + shapeType(type.shape()) + "{}, " + join(ops.subList(1 + rank, ops.size())) + ")";
            }
            case "tile.select" -> {
                return "ct::select(" + join(ops) + ")";
            }
            case "tile.fma" -> {
                var d = type.dtype();
                return result(d, "ct::fma(" + operand(d, ops.get(0)) + ", " + operand(d, ops.get(1)) + ", " + operand(d, ops.get(2)) + ")");
            }
            case "tile.mma" -> {
                return "ct::mma(" + join(ops) + ")";
            }
            case "tile.scale" -> {
                var d = type.dtype();
                var factor = "(" + cpp(d) + ") " + ops.get(1);
                return result(d, operand(d, ops.get(0)) + " * " + (fp8(d) ? "(float) (" + factor + ")" : factor));
            }
            case "tile.cast" -> {
                return "ct::element_cast<" + cpp(type.dtype()) + ">(" + ops.get(0) + ")";
            }
            case "tile.bitcast" -> {
                return "ct::element_bitcast<" + cpp(type.dtype()) + ">(" + ops.get(0) + ")";
            }
            case "tile.concat" -> {
                return "ct::cat(" + ops.get(0) + ", " + ops.get(1) + ", " + intConstant(values.get(2)) + "_ic)";
            }
            default -> {
            }
        }
        if (name.startsWith("tile.binary.")) {
            var op = name.substring("tile.binary.".length());
            var d = tile(values.get(0)).dtype();
            if (CALLS.containsKey(op)) {
                var call = "ct::" + required(CALLS, op, "call") + "(" + operand(d, ops.get(0)) + ", " + operand(d, ops.get(1)) + ")";
                return op.equals("matmul") ? "ct::matmul(" + ops.get(0) + ", " + ops.get(1) + ")" : result(d, call);
            }
            var expression = operand(d, ops.get(0)) + " " + required(OPERATORS, op, "operator") + " " + operand(d, ops.get(1));
            return op.startsWith("logical") ? expression : result(d, expression);
        }
        if (name.startsWith("tile.compare.")) {
            return ops.get(0) + " " + required(OPERATORS, name.substring("tile.compare.".length()), "comparison") + " " + ops.get(1);
        }
        if (name.startsWith("tile.compare_scalar.")) {
            return ops.get(0) + " " + required(OPERATORS, name.substring("tile.compare_scalar.".length()), "comparison") + " (" + cpp(tile(values.get(0)).dtype()) + ") " + ops.get(1);
        }
        if (name.startsWith("tile.unary.")) {
            var op = name.substring("tile.unary.".length());
            var d = tile(values.get(0)).dtype();
            return switch (op) {
                case "bitwiseNot" -> "~" + ops.get(0);
                case "logicalNot" -> "!" + ops.get(0);
                case "isNaN" -> "ct::isnan(" + ops.get(0) + ")";
                case "isInfinite" -> "ct::isinf(" + ops.get(0) + ")";
                case "transpose" -> "ct::transpose(" + ops.get(0) + ")";
                default -> result(d, "ct::" + op + "(" + operand(d, ops.get(0)) + ")");
            };
        }
        if (name.startsWith("tile.reduce.")) {
            var op = name.substring("tile.reduce.".length());
            var d = tile(values.get(0)).dtype();
            var call = "ct::" + required(REDUCTIONS, op, "reduction") + "(" + operand(d, ops.get(0)) + ", " + intConstant(values.get(1)) + "_ic)";
            return d.equals("PRED") ? call : result(d, call);
        }
        if (name.startsWith("tile.atomic.")) {
            // tile.atomic.<op>(view, tile, block indices..., buffer, row extent): CUDA Tile has no
            // read-modify-write on a partition view, so the element pointers are built from iotas.
            var view = tile(values.get(0));
            var shape = view.shape();
            int rank = shape.size();
            var base = "reinterpret_cast<" + cpp(view.dtype()) + " *>(" + ops.get(2 + rank) + ")";
            String pointers;
            if (rank == 1) {
                pointers = base + " + (ct::iota<" + tileType(new TileType("S32", shape, false)) + ">() + " + ops.get(2) + " * " + shape.get(0) + ")";
            } else {
                var full = shapeType(shape) + "{}";
                var rows = "ct::broadcast(ct::iota<" + tileType(new TileType("S32", List.of(shape.get(0), 1), false)) + ">(), " + full + ")";
                var columns = "ct::broadcast(ct::iota<" + tileType(new TileType("S32", List.of(1, shape.get(1)), false)) + ">(), " + full + ")";
                pointers = base + " + ((" + rows + " + " + ops.get(2) + " * " + shape.get(0) + ") * " + ops.get(3 + rank) + " + (" + columns + " + " + ops.get(3) + " * " + shape.get(1)
                        + "))";
            }
            return "ct::" + required(ATOMICS, name.substring("tile.atomic.".length()), "atomic") + "(" + pointers + ", " + ops.get(1) + ", ct::memory_order_relaxed_t{}, ct::thread_scope_device_t{})";
        }
        throw new CEmitter.EmitException("TC-E3403", "no CUDA Tile spelling for " + name);
    }
}
