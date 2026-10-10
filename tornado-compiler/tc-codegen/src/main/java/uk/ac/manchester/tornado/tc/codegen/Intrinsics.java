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
        // {dim|xyz}: the dimension immediate as x, y or z.
        cuda("tornado.thread_id.global", "(int) (blockIdx.{dim|xyz} * blockDim.{dim|xyz} + threadIdx.{dim|xyz})");
        cuda("tornado.thread_id.local", "(int) threadIdx.{dim|xyz}");
        cuda("tornado.group_id", "(int) blockIdx.{dim|xyz}");
        cuda("tornado.group_size.local", "(int) blockDim.{dim|xyz}");
        cuda("tornado.group_count", "(int) gridDim.{dim|xyz}");
        cuda("tornado.global_size", "(int) (gridDim.{dim|xyz} * blockDim.{dim|xyz})");
        // Warp operations: every lane of the warp takes part (full mask). simdSum is a butterfly (XOR)
        // reduction, so every lane receives the sum.
        TABLE.put(new Key("tornado.simd.sum", Dialect.CUDA, ""), Spelling.withHelper("tc_warp_sum($0)", "tc_warp_sum"));
        cuda("tornado.simd.shuffle_down", "__shfl_down_sync(0xffffffffu, $0, $1)");
        cuda("tornado.simd.broadcast_first", "__shfl_sync(0xffffffffu, $0, 0)");
        cuda("tornado.simd.any", "(__any_sync(0xffffffffu, $0) != 0)");
        cuda("tornado.simd.all", "(__all_sync(0xffffffffu, $0) != 0)");
        cuda("tornado.simd.ballot", "(int) __ballot_sync(0xffffffffu, $0)");
        // Lanes of a register vector (vector types, fragment element reads at a constant index), and
        // a vector with one lane replaced (setX on a vector value).
        cuda("tornado.vector.lane", "$0.{lane|xyzw}");
        TABLE.put(new Key("tornado.vector.insert", Dialect.CUDA, ""), Spelling.withHelper("tc_with_{lane|xyzw}($0, $1)", "tc_with"));
        // @Reduce: work-group reduction of each thread's value (Helpers.blockReduce).
        for (var op : List.of("add", "mul", "max", "min")) {
            for (var t : List.of("f32", "f64", "i32", "i64")) {
                TABLE.put(new Key("tornado.reduce." + op, Dialect.CUDA, t), Spelling.withHelper("tc_reduce_" + op + "_" + t + "($0)", "tc_reduce_" + op + "_" + t));
            }
        }
        // AtomicInteger counters: slot $0 of the kernel's atomics region (AtomicSlots), operand $1.
        cuda("tornado.atomic_int.add_get", "atomicAdd(&_atomics[$0], $1) + $1");
        cuda("tornado.atomic_int.get_add", "atomicAdd(&_atomics[$0], $1)");
        cuda("tornado.atomic_int.sub_get", "atomicSub(&_atomics[$0], $1) - $1");
        cuda("tornado.atomic_int.get_sub", "atomicSub(&_atomics[$0], $1)");
        cuda("tornado.atomic_int.get", "((volatile int *) _atomics)[$0]");
        cuda("tornado.atomic_int.set", "atomicExch(&_atomics[$0], $1)");
        // Tensor cores, m16n8k16 (PTX ISA fragment layouts); helpers written from the PTX ISA.
        TABLE.put(new Key("tornado.mma.fragment", Dialect.CUDA, ""), Spelling.withHelper("tc_mma_fragment($0)", "tc_mma_fragment"));
        TABLE.put(new Key("tornado.mma.load_a", Dialect.CUDA, ""), Spelling.withHelper("tc_mma_load_a((const int *) $0, $1, 2)", "tc_mma_load_a"));
        TABLE.put(new Key("tornado.mma.load_b", Dialect.CUDA, ""), Spelling.withHelper("tc_mma_load_b((const int *) $0, $1)", "tc_mma_load_b"));
        // With a byte offset: the tile base moves by that many bytes (a warp's sub-tile).
        TABLE.put(new Key("tornado.mma.load_a.global", Dialect.CUDA, ""), Spelling.withHelper("tc_mma_load_a_global((const unsigned short *) ($0), $1, $2, $3)", "tc_mma_load_a_global"));
        TABLE.put(new Key("tornado.mma.load_b.global", Dialect.CUDA, ""), Spelling.withHelper("tc_mma_load_b_global((const unsigned short *) ($0), $1, $2, $3)", "tc_mma_load_b_global"));
        TABLE.put(new Key("tornado.mma.load_a.offset", Dialect.CUDA, ""), Spelling.withHelper("tc_mma_load_a((const int *) ((const char *) $0 + $2), $1, 2)", "tc_mma_load_a"));
        TABLE.put(new Key("tornado.mma.load_b.offset", Dialect.CUDA, ""), Spelling.withHelper("tc_mma_load_b((const int *) ((const char *) $0 + $2), $1)", "tc_mma_load_b"));
        // Swizzled B tiles (fp16, stride-32 permutation: byte b -> b ^ (((b >> 7) & 7) << 4)).
        TABLE.put(new Key("tornado.mma.load_b.swizzled", Dialect.CUDA, ""), Spelling.withHelper("tc_mma_load_b_swizzled($0, $1, 0)", "tc_mma_load_b_swizzled"));
        TABLE.put(new Key("tornado.mma.load_b.swizzled.offset", Dialect.CUDA, ""), Spelling.withHelper("tc_mma_load_b_swizzled($0, $1, $2)", "tc_mma_load_b_swizzled"));
        TABLE.put(new Key("tornado.mma.store_b.swizzled", Dialect.CUDA, ""), Spelling.withHelper("tc_mma_store_b_swizzled($0, $1, $2, $3, $4, $5)", "tc_mma_store_b_swizzled"));
        // int8: m16n8k32, s8 inputs, s32 accumulators; tiles hold k one-byte elements per row.
        TABLE.put(new Key("tornado.mma.fragment_int", Dialect.CUDA, ""), Spelling.withHelper("tc_mma_fragment_int($0)", "tc_mma_fragment_int"));
        TABLE.put(new Key("tornado.mma.load_a_s8", Dialect.CUDA, ""), Spelling.withHelper("tc_mma_load_a((const int *) $0, $1, 1)", "tc_mma_load_a"));
        TABLE.put(new Key("tornado.mma.load_a_s8.offset", Dialect.CUDA, ""), Spelling.withHelper("tc_mma_load_a((const int *) ((const char *) $0 + $2), $1, 1)", "tc_mma_load_a"));
        TABLE.put(new Key("tornado.mma.load_b_s8", Dialect.CUDA, ""), Spelling.withHelper("tc_mma_load_b((const int *) $0, $1)", "tc_mma_load_b"));
        TABLE.put(new Key("tornado.mma.load_b_s8.offset", Dialect.CUDA, ""), Spelling.withHelper("tc_mma_load_b((const int *) ((const char *) $0 + $2), $1)", "tc_mma_load_b"));
        TABLE.put(new Key("tornado.mma.m16n8k32_s8", Dialect.CUDA, ""), Spelling.withHelper("tc_mma_m16n8k32_s8($0, $1, $2)", "tc_mma_m16n8k32_s8"));
        TABLE.put(new Key("tornado.mma.store_int", Dialect.CUDA, ""), Spelling.withHelper("tc_mma_store_int($0, (int *) ($1), $2, $3, $4)", "tc_mma_store_int"));
        TABLE.put(new Key("tornado.mma.m16n8k16_bf16", Dialect.CUDA, ""), Spelling.withHelper("tc_mma_m16n8k16_bf16($0, $1, $2)", "tc_mma_m16n8k16_bf16"));
        TABLE.put(new Key("tornado.mma.m16n8k32_e4m3", Dialect.CUDA, ""), Spelling.withHelper("tc_mma_m16n8k32_e4m3($0, $1, $2)", "tc_mma_m16n8k32_e4m3"));
        TABLE.put(new Key("tornado.mma.m16n8k32_e5m2", Dialect.CUDA, ""), Spelling.withHelper("tc_mma_m16n8k32_e5m2($0, $1, $2)", "tc_mma_m16n8k32_e5m2"));
        TABLE.put(new Key("tornado.mma.m16n8k16", Dialect.CUDA, ""), Spelling.withHelper("tc_mma_m16n8k16($0, $1, $2)", "tc_mma_m16n8k16"));
        TABLE.put(new Key("tornado.mma.store", Dialect.CUDA, ""), Spelling.withHelper("tc_mma_store($0, (float *) ($1), $2, $3, $4)", "tc_mma_store"));
        // Atomics: $&0 is the address of the element ($0 is its pointer); typed by the value.
        for (var t : List.of("i32", "i64", "f32", "f64")) {
            cudaTyped("tornado.atomic.add", t, t.equals("i64") ? "atomicAdd((unsigned long long *) $&0, (unsigned long long) $1)" : "atomicAdd($&0, $1)");
        }
        cudaTyped("tornado.atomic.cas", "i32", "atomicCAS($&0, $1, $2)");
        cudaTyped("tornado.atomic.exchange", "i32", "atomicExch($&0, $1)");
        cudaTyped("tornado.atomic.min", "i32", "atomicMin($&0, $1)");
        cudaTyped("tornado.atomic.max", "i32", "atomicMax($&0, $1)");
        // cp.async (sm_80+) of a 4- or 16-byte slot into a shared int[]; the source index counts elements of elem_bytes each.
        TABLE.put(new Key("tornado.async_copy", Dialect.CUDA, ""), Spelling.withHelper("tc_cp_async_{bytes}((int *) $0 + $1, (const char *) $2 + (long long) $3 * {elem_bytes})", "tc_cp_async_{bytes}"));
        TABLE.put(new Key("tornado.async_copy.commit", Dialect.CUDA, ""), Spelling.withHelper("tc_cp_async_commit()", "tc_cp_async_commit"));
        TABLE.put(new Key("tornado.async_copy.wait", Dialect.CUDA, ""), Spelling.withHelper("tc_cp_async_wait($0)", "tc_cp_async_wait"));
        // DP4A (sm_61+): operands are the int8 data pointers (+ byte offsets) or packed words.
        cuda("tornado.dp4a", "__dp4a(*((const int *) ((const char *) $0 + $1)), *((const int *) ((const char *) $2 + $3)), $4)");
        cuda("tornado.dp4a.packed", "__dp4a($0, $1, $2)");
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

    /**
     * Fills a template's immediate placeholders: {@code {key}} is the immediate's value and
     * {@code {key|xyz}} the character at that index (a dimension or lane as x, y, z, w).
     */
    static String fillImmediates(String template, uk.ac.manchester.tornado.tc.ir.Immediates immediates) {
        var m = java.util.regex.Pattern.compile("\\{([a-z_]+)(?:\\|([a-z]+))?}").matcher(template);
        var out = new StringBuilder();
        while (m.find()) {
            var key = m.group(1);
            String value;
            if (m.group(2) != null) {
                int index = immediates.requireInt(key);
                if (index < 0 || index >= m.group(2).length()) {
                    throw new CEmitter.EmitException("TC-E9001", "immediate " + key + " = " + index + " out of range for " + m.group(2));
                }
                value = String.valueOf(m.group(2).charAt(index));
            } else {
                value = immediates.values().containsKey(key) ? immediates.values().get(key).toString() : null;
                if (value == null) {
                    throw new CEmitter.EmitException("TC-E9001", "missing immediate " + key);
                }
            }
            m.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(value));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** Fills a template's {@code $i} placeholders; operands are already printed expressions. */
    /**
     * Substitutes operands into a spelling template. An operand that fills a whole call argument
     * ({@code f($0, $1)}) is inserted as it is; anywhere else ({@code (long long) $3 * 2},
     * {@code x - $1}) it is parenthesised unless {@code atomic} says it is a single token, so a
     * folded {@code i + j} keeps its meaning.
     */
    static String expand(String template, List<String> operands, java.util.function.IntPredicate atomic) {
        var out = new StringBuilder();
        for (int p = 0; p < template.length(); p++) {
            char c = template.charAt(p);
            if (c == '$' && p + 1 < template.length() && Character.isDigit(template.charAt(p + 1))) {
                int q = p + 1;
                while (q < template.length() && Character.isDigit(template.charAt(q))) {
                    q++;
                }
                int i = Integer.parseInt(template.substring(p + 1, q));
                var text = operands.get(i);
                boolean wholeArgument = previousNonSpace(template, p) == '(' || previousNonSpace(template, p) == ',';
                wholeArgument = wholeArgument && (nextNonSpace(template, q) == ',' || nextNonSpace(template, q) == ')');
                out.append(wholeArgument || atomic.test(i) ? text : "(" + text + ")");
                p = q - 1;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    static String expand(String template, List<String> operands) {
        return expand(template, operands, i -> true);
    }

    private static char previousNonSpace(String s, int at) {
        for (int k = at - 1; k >= 0; k--) {
            if (s.charAt(k) != ' ') {
                return s.charAt(k);
            }
        }
        return 0;
    }

    private static char nextNonSpace(String s, int from) {
        for (int k = from; k < s.length(); k++) {
            if (s.charAt(k) != ' ') {
                return s.charAt(k);
            }
        }
        return 0;
    }

    /**
     * Whether an expanded template is one call, {@code name(...)} with the closing parenthesis last:
     * then it binds like an atom; any other template (an operator, a cast) binds loosest.
     */
    static boolean isSingleCall(String text) {
        int open = text.indexOf('(');
        if (open <= 0 || !text.substring(0, open).matches("[A-Za-z_][A-Za-z0-9_:<>, ]*") || !text.endsWith(")")) {
            return false;
        }
        int depth = 0;
        for (int k = open; k < text.length(); k++) {
            depth += text.charAt(k) == '(' ? 1 : text.charAt(k) == ')' ? -1 : 0;
            if (depth == 0 && k != text.length() - 1) {
                return false;
            }
        }
        return true;
    }
}
