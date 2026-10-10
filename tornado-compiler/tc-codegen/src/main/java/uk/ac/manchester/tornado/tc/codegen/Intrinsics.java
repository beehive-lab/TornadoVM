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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import uk.ac.manchester.tornado.tc.ir.Type;
import uk.ac.manchester.tornado.tc.ir.target.TargetCaps.Dialect;

/**
 * The intrinsic registry: one row per TC intrinsic, with a source template per dialect and, where
 * the spelling depends on it, per result type. Templates use {@code $0}, {@code $1}, … for operands.
 * Spellings come from the vendor specifications (CUDA C++ Programming Guide, OpenCL C 3.0, Metal
 * Shading Language Specification), never from another compiler's tables. A template may name a
 * Java-semantics helper from {@link Helpers}; the emitter then adds that helper to the preamble.
 */
public final class Intrinsics {

    /** One spelling: the template and the helper it needs, if any. */
    public record Spelling(String template, Optional<String> helper) {
        static Spelling of(String template) {
            return new Spelling(template, Optional.empty());
        }

        static Spelling withHelper(String template, String helper) {
            return new Spelling(template, Optional.of(helper));
        }
    }

    private record Key(String name, Dialect dialect, String type) {
    }

    private static final Map<Key, Spelling> TABLE = new HashMap<>();

    static {
        // Thread indexing (KernelContext and @Parallel lowering).
        for (var d : List.of("x", "y", "z")) {
            cuda("tornado.thread_id.global." + d, "(int) (blockIdx." + d + " * blockDim." + d + " + threadIdx." + d + ")");
            cuda("tornado.thread_id.local." + d, "(int) threadIdx." + d);
            cuda("tornado.group_id." + d, "(int) blockIdx." + d);
            cuda("tornado.group_size.local." + d, "(int) blockDim." + d);
            cuda("tornado.group_count." + d, "(int) gridDim." + d);
            cuda("tornado.global_size." + d, "(int) (gridDim." + d + " * blockDim." + d + ")");
        }
        // Warp operations: every lane of the warp takes part (full mask). simdSum is a butterfly (XOR)
        // reduction, so every lane receives the sum.
        TABLE.put(new Key("tornado.simd.sum", Dialect.CUDA, ""), Spelling.withHelper("tc_warp_sum($0)", "tc_warp_sum"));
        cuda("tornado.simd.shuffle_down", "__shfl_down_sync(0xffffffffu, $0, $1)");
        cuda("tornado.simd.broadcast_first", "__shfl_sync(0xffffffffu, $0, 0)");
        cuda("tornado.simd.any", "(__any_sync(0xffffffffu, $0) != 0)");
        cuda("tornado.simd.all", "(__all_sync(0xffffffffu, $0) != 0)");
        cuda("tornado.simd.ballot", "(int) __ballot_sync(0xffffffffu, $0)");
        // Atomics: $&0 is the address of the element ($0 is its pointer); typed by the value.
        for (var t : List.of("i32", "i64", "f32", "f64")) {
            cudaTyped("tornado.atomic.add", t, t.equals("i64") ? "atomicAdd((unsigned long long *) $&0, (unsigned long long) $1)" : "atomicAdd($&0, $1)");
        }
        cudaTyped("tornado.atomic.cas", "i32", "atomicCAS($&0, $1, $2)");
        cudaTyped("tornado.atomic.exchange", "i32", "atomicExch($&0, $1)");
        cudaTyped("tornado.atomic.min", "i32", "atomicMin($&0, $1)");
        cudaTyped("tornado.atomic.max", "i32", "atomicMax($&0, $1)");
        // Synchronisation.
        cuda("tornado.barrier.local", "__syncthreads()");
        cuda("tornado.barrier.global", "__syncthreads()");
        // Math, f32 then f64. Java semantics where CUDA differs (min/max with NaN and signed zeros).
        unaryMath("math.sqrt", "sqrtf", "sqrt");
        unaryMath("math.rsqrt", "rsqrtf", "rsqrt");
        unaryMath("math.exp", "expf", "exp");
        unaryMath("math.log", "logf", "log");
        unaryMath("math.sin", "sinf", "sin");
        unaryMath("math.cos", "cosf", "cos");
        unaryMath("math.tan", "tanf", "tan");
        unaryMath("math.tanh", "tanhf", "tanh");
        unaryMath("math.abs", "fabsf", "fabs");
        unaryMath("math.floor", "floorf", "floor");
        unaryMath("math.ceil", "ceilf", "ceil");
        for (var fn : new String[] { "atan", "asin", "acos", "sinh", "cosh", "asinh", "acosh", "atanh", "cbrt", "log10" }) {
            unaryMath("math." + fn, fn + "f", fn);
        }
        cudaTyped("math.atan2", "f32", "atan2f($0, $1)");
        cudaTyped("math.atan2", "f64", "atan2($0, $1)");
        cudaTyped("math.pow", "f32", "powf($0, $1)");
        cudaTyped("math.pow", "f64", "pow($0, $1)");
        cudaTyped("math.fma", "f32", "fmaf($0, $1, $2)");
        cudaTyped("math.fma", "f64", "fma($0, $1, $2)");
        TABLE.put(new Key("math.min", Dialect.CUDA, "f32"), Spelling.withHelper("java_fminf($0, $1)", "java_fminf"));
        TABLE.put(new Key("math.max", Dialect.CUDA, "f32"), Spelling.withHelper("java_fmaxf($0, $1)", "java_fmaxf"));
        TABLE.put(new Key("math.min", Dialect.CUDA, "f64"), Spelling.withHelper("java_fmin($0, $1)", "java_fmin"));
        TABLE.put(new Key("math.max", Dialect.CUDA, "f64"), Spelling.withHelper("java_fmax($0, $1)", "java_fmax"));
        cudaTyped("math.min", "i32", "min($0, $1)");
        cudaTyped("math.max", "i32", "max($0, $1)");
        cudaTyped("math.min", "i64", "llmin($0, $1)");
        cudaTyped("math.max", "i64", "llmax($0, $1)");
        cudaTyped("math.abs", "i32", "abs($0)");
        cudaTyped("math.abs", "i64", "llabs($0)");
    }

    private Intrinsics() {
    }

    private static void cuda(String name, String template) {
        TABLE.put(new Key(name, Dialect.CUDA, ""), Spelling.of(template));
    }

    private static void cudaTyped(String name, String type, String template) {
        TABLE.put(new Key(name, Dialect.CUDA, type), Spelling.of(template));
    }

    private static void unaryMath(String name, String f32, String f64) {
        cudaTyped(name, "f32", f32 + "($0)");
        cudaTyped(name, "f64", f64 + "($0)");
    }

    /** The spelling of {@code name} for {@code dialect}, preferring a row for {@code type}. */
    public static Optional<Spelling> lookup(String name, Dialect dialect, Type type) {
        var typed = TABLE.get(new Key(name, dialect, type.text()));
        if (typed != null) {
            return Optional.of(typed);
        }
        return Optional.ofNullable(TABLE.get(new Key(name, dialect, "")));
    }

    /** Fills a template's {@code $i} placeholders; operands are already printed expressions. */
    static String expand(String template, List<String> operands) {
        var out = template;
        for (int i = operands.size() - 1; i >= 0; i--) {
            out = out.replace("$" + i, operands.get(i));
        }
        return out;
    }
}
