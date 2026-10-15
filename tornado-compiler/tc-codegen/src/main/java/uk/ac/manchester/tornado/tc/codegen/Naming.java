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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import uk.ac.manchester.tornado.tc.ir.target.TargetCaps.Dialect;

/**
 * Turns Java names into legal, non-clashing identifiers of a target dialect, in one place for
 * every emitter. Java identifiers can be anything the target gives meaning to: a keyword or an
 * alternative operator token ({@code and}), a function the emitter calls ({@code max},
 * {@code sqrtf}, {@code printf}), a built-in variable or type ({@code threadIdx}, {@code half2}),
 * a macro ({@code INFINITY}) or a name in TC's own namespaces. The rules, in order:
 * <ol>
 * <li>characters outside {@code [A-Za-z0-9_]} (Java allows {@code $}) become {@code _}; a leading
 * digit gets a {@code v} prefix;</li>
 * <li>a name starting with {@code _} gets a {@code v} prefix: the underscore namespace belongs to
 * the C implementation and to TC's ABI names and temporaries ({@code _kernel_context},
 * {@code _dp_block});</li>
 * <li>a reserved word of the dialect, or a name starting with a reserved prefix ({@code tc_},
 * {@code java_}, dialect families such as OpenCL's {@code convert_}), gets a {@code _} suffix:
 * {@code and} becomes {@code and_};</li>
 * <li>a name already used in the function gets {@code _1}, {@code _2}, ...</li>
 * </ol>
 */
public final class Naming {

    private static final List<String> C_KEYWORDS = List.of("auto", "break", "case", "char", "const", "continue", "default", "do", "double", "else", "enum", "extern", "float", "for",
            "goto", "if", "inline", "int", "long", "register", "restrict", "return", "short", "signed", "sizeof", "static", "struct", "switch", "typedef", "union", "unsigned", "void",
            "volatile", "while", "bool", "true", "false", "NULL");

    private static final List<String> CPP_KEYWORDS = List.of("alignas", "alignof", "and", "and_eq", "asm", "bitand", "bitor", "catch", "char8_t", "char16_t", "char32_t", "class",
            "co_await", "co_return", "co_yield", "compl", "concept", "const_cast", "consteval", "constexpr", "constinit", "decltype", "delete", "dynamic_cast", "explicit", "export",
            "friend", "mutable", "namespace", "new", "noexcept", "not", "not_eq", "nullptr", "operator", "or", "or_eq", "private", "protected", "public", "reinterpret_cast", "requires",
            "static_assert", "static_cast", "template", "this", "thread_local", "throw", "try", "typeid", "typename", "using", "virtual", "wchar_t", "xor", "xor_eq", "std");

    /** Math and integer functions the emitters call, in every dialect (plus the f-suffixed C forms). */
    private static final List<String> MATH = List.of("acos", "acosh", "asin", "asinh", "atan", "atan2", "atanh", "cbrt", "ceil", "copysign", "cos", "cosh", "erf", "erfc", "exp",
            "exp2", "exp10", "expm1", "fabs", "fdim", "floor", "fma", "fmax", "fmin", "fmod", "frexp", "hypot", "ilogb", "isfinite", "isinf", "isnan", "ldexp", "lgamma", "log",
            "log10", "log1p", "log2", "logb", "lrint", "lround", "modf", "nan", "nearbyint", "nextafter", "pow", "remainder", "remquo", "rint", "round", "rsqrt", "scalbn", "signbit",
            "sin", "sincos", "sinh", "sqrt", "tan", "tanh", "tgamma", "trunc", "min", "max", "abs", "clamp", "mad", "mix", "sign", "step", "smoothstep", "degrees", "radians", "dot",
            "cross", "length", "normalize", "distance", "select", "popcount", "clz", "ctz", "mul24", "mad24", "mul_hi", "rotate", "hadd", "rhadd", "printf", "assert");

    private static final List<String> SCALARS = List.of("char", "uchar", "short", "ushort", "int", "uint", "long", "ulong", "longlong", "ulonglong", "float", "double", "half", "bool");

    private static final List<String> MACROS = List.of("INFINITY", "NAN", "HUGE_VAL", "HUGE_VALF", "EOF", "M_PI", "M_E", "FLT_MAX", "FLT_MIN", "FLT_EPSILON", "DBL_MAX", "DBL_MIN",
            "DBL_EPSILON", "INT_MAX", "INT_MIN", "LONG_MAX", "LONG_MIN", "MAXFLOAT", "CHAR_BIT");

    private static final List<String> CUDA = List.of("threadIdx", "blockIdx", "blockDim", "gridDim", "warpSize", "dim3", "malloc", "free", "memcpy", "memset", "clock", "clock64",
            "half2", "nv_bfloat16", "nv_bfloat162", "int8_t", "int16_t", "int32_t", "int64_t", "uint8_t", "uint16_t", "uint32_t", "uint64_t", "size_t", "ptrdiff_t", "cuda", "ct",
            "cooperative_groups", "popc", "brev", "ffs", "byte_perm", "funnelshift_l", "funnelshift_r", "atomicAdd", "atomicSub", "atomicExch", "atomicMin", "atomicMax", "atomicInc",
            "atomicDec", "atomicCAS", "atomicAnd", "atomicOr", "atomicXor", "dp4a", "cudaStreamTailLaunch", "cudaStreamFireAndForget", "cudaGetLastError", "cudaGetErrorString",
            "cudaSuccess", "cudaError_t");

    private static final List<String> OPENCL = List.of("kernel", "global", "local", "constant", "private", "read_only", "write_only", "read_write", "uniform", "pipe", "sampler_t",
            "event_t", "image1d_t", "image2d_t", "image3d_t", "image1d_array_t", "image2d_array_t", "image1d_buffer_t", "size_t", "ptrdiff_t", "intptr_t", "uintptr_t", "barrier",
            "mem_fence", "read_mem_fence", "write_mem_fence", "work_group_barrier", "CLK_LOCAL_MEM_FENCE", "CLK_GLOBAL_MEM_FENCE", "native", "sub_group_barrier");

    private static final List<String> OPENCL_PREFIXES = List.of("get_", "convert_", "as_", "vload", "vstore", "atomic_", "atom_", "async_work_group", "native_", "half_", "sub_group_",
            "work_group_", "read_image", "write_image", "intel_");

    private static final List<String> METAL = List.of("kernel", "device", "threadgroup", "thread", "constant", "vertex", "fragment", "metal", "bfloat", "texture1d", "texture2d",
            "texture3d", "sampler", "threadgroup_barrier", "simdgroup_barrier", "mem_flags", "mem_none", "mem_device", "mem_threadgroup", "access", "uint8_t", "int8_t", "uint16_t",
            "int16_t", "uint32_t", "int32_t", "uint64_t", "int64_t", "size_t", "ptrdiff_t");

    private static final List<String> METAL_PREFIXES = List.of("simd_", "quad_", "packed_", "atomic_");

    /** TC's own helper families, in every dialect. */
    private static final List<String> TC_PREFIXES = List.of("tc_", "java_");

    private final Set<String> reserved = new HashSet<>();
    private final List<String> prefixes;

    private Naming(Dialect dialect) {
        reserved.addAll(C_KEYWORDS);
        reserved.addAll(MATH);
        MATH.forEach(f -> reserved.add(f + "f"));
        reserved.addAll(MACROS);
        reserved.addAll(SCALARS);
        for (var s : SCALARS) {
            for (int n : new int[] { 1, 2, 3, 4, 8, 16 }) {
                reserved.add(s + n);
                reserved.add("make_" + s + n);
            }
        }
        switch (dialect) {
            case CUDA -> {
                reserved.addAll(CPP_KEYWORDS);
                reserved.addAll(CUDA);
                prefixes = TC_PREFIXES;
            }
            case OPENCL -> {
                reserved.addAll(OPENCL);
                prefixes = concat(TC_PREFIXES, OPENCL_PREFIXES);
            }
            case METAL -> {
                reserved.addAll(CPP_KEYWORDS);
                reserved.addAll(METAL);
                prefixes = concat(TC_PREFIXES, METAL_PREFIXES);
            }
            default -> throw new IllegalArgumentException(dialect.name());
        }
        // Every function the emitter can print for this dialect: intrinsic templates, Java-semantics
        // helpers and the preamble with all its features. A local of that name would hide it (a Java
        // local `sinpi` declared at the top of the kernel makes `sinpi(x)` a call of a float).
        var printed = new java.util.ArrayList<String>(Intrinsics.templates(dialect));
        printed.addAll(Helpers.sources(dialect));
        printed.addAll(CDialect.of(dialect).preamble(java.util.EnumSet.allOf(CDialect.Feature.class)));
        printed.forEach(text -> reserved.addAll(calledNames(text)));
    }

    private static final java.util.regex.Pattern CALLED = java.util.regex.Pattern.compile("(?<![.\\w>$#])([A-Za-z][A-Za-z0-9_]*)(?:\\s*\\(|<(?=[A-Za-z_]))");

    /**
     * The names {@code text} calls or instantiates ({@code f(...)}, {@code as_type<float>}), member
     * calls ({@code .x(}, {@code ->f(}) excluded. Names with a leading underscore are already reserved.
     */
    static Set<String> calledNames(String text) {
        var names = new HashSet<String>();
        var m = CALLED.matcher(text);
        while (m.find()) {
            names.add(m.group(1));
        }
        return names;
    }

    private static List<String> concat(List<String> a, List<String> b) {
        var all = new java.util.ArrayList<>(a);
        all.addAll(b);
        return List.copyOf(all);
    }

    private static final java.util.Map<Dialect, Naming> CACHE = new java.util.EnumMap<>(Dialect.class);

    public static synchronized Naming of(Dialect dialect) {
        return CACHE.computeIfAbsent(dialect, Naming::new);
    }

    /** Whether {@code name} needs renaming in this dialect (before de-duplication). */
    public boolean isReserved(String name) {
        return reserved.contains(name) || prefixes.stream().anyMatch(name::startsWith);
    }

    /** The legal, unused identifier for {@code javaName}; records it in {@code used}. */
    public String identifier(String javaName, Set<String> used) {
        var sb = new StringBuilder();
        for (char c : javaName.toCharArray()) {
            sb.append(c < 128 && (Character.isLetterOrDigit(c) || c == '_') ? c : '_');
        }
        var base = sb.isEmpty() ? "v" : sb.toString();
        if (Character.isDigit(base.charAt(0)) || base.charAt(0) == '_') {
            base = "v" + base;
        }
        if (isReserved(base)) {
            base = base + "_";
        }
        var candidate = base;
        for (int k = 1; !used.add(candidate); k++) {
            candidate = base + "_" + k;
        }
        return candidate;
    }
}
