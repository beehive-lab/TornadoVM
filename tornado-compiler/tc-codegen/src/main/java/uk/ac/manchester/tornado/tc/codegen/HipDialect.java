/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package uk.ac.manchester.tornado.tc.codegen;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import uk.ac.manchester.tornado.tc.ir.AddressSpace;
import uk.ac.manchester.tornado.tc.ir.Inst;
import uk.ac.manchester.tornado.tc.ir.Type;
import uk.ac.manchester.tornado.tc.ir.target.TargetCaps.Dialect;

/**
 * HIP C++ for hipRTC. HIP spells kernels, address spaces, vector types and most built-ins as CUDA C++
 * does, so this dialect takes the CUDA spellings and overrides what differs: the bfloat16 type and its
 * conversions, the double-to-half conversion, Java's float-to-integer conversions (helpers, as on Metal),
 * the strict-floating-point pragma, and the CUDA-only features (CUDA Tile, device-side launches).
 */
final class HipDialect implements CDialect {

    private final CudaDialect cuda = new CudaDialect();

    @Override
    public Dialect id() {
        return Dialect.HIP;
    }

    @Override
    public String integerName(int bits) {
        return cuda.integerName(bits);
    }

    @Override
    public String unsignedName(int bits) {
        return cuda.unsignedName(bits);
    }

    @Override
    public String longSuffix() {
        return cuda.longSuffix();
    }

    @Override
    public String kernelPrefix(boolean tiles) {
        if (tiles) {
            throw new CEmitter.EmitException("TC-E4001", "TileContext kernels are CUDA Tile C++: they run only on the CUDA backend");
        }
        return cuda.kernelPrefix(false);
    }

    @Override
    public String abiParameters() {
        return cuda.abiParameters();
    }

    @Override
    public String abiArguments() {
        return cuda.abiArguments();
    }

    @Override
    public String functionPrefix() {
        return cuda.functionPrefix();
    }

    @Override
    public String arrayQualifier(AddressSpace space) {
        return cuda.arrayQualifier(space);
    }

    @Override
    public String pointee(AddressSpace space, String element) {
        return cuda.pointee(space, element);
    }

    @Override
    public Optional<String> typeName(Type type, Set<Feature> features) {
        if (type instanceof Type.FloatType f && f.kind() == Type.FloatType.Kind.BF16) {
            features.add(Feature.BFLOAT16);
            return Optional.of("__hip_bfloat16");
        }
        return cuda.typeName(type, features);
    }

    @Override
    public String vector(String typeName, List<String> lanes) {
        return cuda.vector(typeName, lanes);
    }

    @Override
    public String bitcast(Type from, Type to, Set<Feature> features) {
        return cuda.bitcast(from, to, features);
    }

    @Override
    public String saturatingToInt(Type from, Type to, Set<Feature> features) {
        // The HIP round-toward-zero conversions are plain C casts, undefined for NaN and out-of-range
        // values: helpers with Java's rules (preamble), as on Metal.
        if (from.equals(Type.F16)) {
            features.add(Feature.HALF);
        }
        return to.equals(Type.I64) ? "tc_f2l" : "tc_f2i";
    }

    @Override
    public Optional<String> convertTo(Type from, Type to, String value, Set<Feature> features) {
        boolean fromDouble = from.equals(Type.F64);
        if (to.equals(Type.F16)) {
            features.add(Feature.HALF);
            return Optional.of((fromDouble ? "tc_double2half(" : "__float2half_rn(") + value + ")");
        }
        if (to.equals(Type.BF16)) {
            features.add(Feature.BFLOAT16);
            return Optional.of((fromDouble ? "tc_double2bfloat16(" : "__float2bfloat16(") + value + ")");
        }
        return Optional.empty();
    }

    @Override
    public String tileExpression(String name, Inst inst, List<String> operands) {
        throw new CEmitter.EmitException("TC-E4001", "TileContext kernels are CUDA Tile C++: they run only on the CUDA backend");
    }

    @Override
    public String tileType(Type.TileType type) {
        throw new CEmitter.EmitException("TC-E4001", "TileContext kernels are CUDA Tile C++: they run only on the CUDA backend");
    }

    @Override
    public String unreachable() {
        return cuda.unreachable();
    }

    @Override
    public List<String> deviceLaunch(Launch launch, Set<String> helpers) {
        throw new CEmitter.EmitException("TC-E4016", "HIP kernels cannot launch kernels");
    }

    /**
     * hipRTC declares the half and bfloat16 types itself; the headers are included only where they
     * exist on the include path (hipcc, or hipRTC given the ROCm include directory), so the same source
     * compiles both ways. Definitions are guarded: a module with several functions prints the preamble
     * once per function.
     */
    @Override
    public List<String> preamble(Set<Feature> features) {
        var lines = new ArrayList<String>();
        if (features.contains(Feature.NO_CONTRACT)) {
            // A strict plan: a * b + c is not fused into fma (clang contracts by default).
            lines.add("#pragma clang fp contract(off)");
        }
        boolean half = features.contains(Feature.HALF) || features.contains(Feature.FP8);
        if (half) {
            lines.add("#if __has_include(<hip/hip_fp16.h>)");
            lines.add("#include <hip/hip_fp16.h>");
            lines.add("#endif");
        }
        if (features.contains(Feature.BFLOAT16)) {
            lines.add("#if __has_include(<hip/hip_bf16.h>)");
            lines.add("#include <hip/hip_bf16.h>");
            lines.add("#endif");
        }
        if (features.contains(Feature.HALF4)) {
            guarded(lines, "TC_HALF4", "struct __align__(8) half4 { half x, y, z, w; };",
                    "static __device__ __forceinline__ half4 make_half4(half x, half y, half z, half w) { half4 v; v.x = x; v.y = y; v.z = z; v.w = w; return v; }");
        }
        // Java's float-to-integer conversions: NaN is 0, out-of-range values saturate. NaN is tested on
        // the bits, so a kernel compiled with -ffast-math keeps it.
        guarded(lines, "TC_F2I", "static __device__ __forceinline__ bool tc_isnan(float x) { return (__float_as_uint(x) & 0x7fffffffu) > 0x7f800000u; }",
                "static __device__ __forceinline__ bool tc_isnan(double x) { return (__double_as_longlong(x) & 0x7fffffffffffffffLL) > 0x7ff0000000000000LL; }",
                "static __device__ __forceinline__ int tc_f2i(float x) { return tc_isnan(x) ? 0 : x >= 2147483647.0f ? 2147483647 : x <= -2147483648.0f ? (-2147483647 - 1) : (int) x; }",
                "static __device__ __forceinline__ int tc_f2i(double x) { return tc_isnan(x) ? 0 : x >= 2147483647.0 ? 2147483647 : x <= -2147483648.0 ? (-2147483647 - 1) : (int) x; }",
                "static __device__ __forceinline__ long long tc_f2l(float x) { return tc_isnan(x) ? 0 : x >= 9223372036854775807.0f ? 9223372036854775807LL : x <= -9223372036854775808.0f ? (-9223372036854775807LL - 1) : (long long) x; }",
                "static __device__ __forceinline__ long long tc_f2l(double x) { return tc_isnan(x) ? 0 : x >= 9223372036854775807.0 ? 9223372036854775807LL : x <= -9223372036854775808.0 ? (-9223372036854775807LL - 1) : (long long) x; }");
        if (half) {
            guarded(lines, "TC_F2I_HALF", "static __device__ __forceinline__ int tc_f2i(half x) { return tc_f2i(__half2float(x)); }",
                    "static __device__ __forceinline__ long long tc_f2l(half x) { return tc_f2l(__half2float(x)); }");
        }
        // A double rounded once to half or bfloat16: through a float rounded to odd (truncated, with the
        // last bit set when inexact), which has enough extra bits that the second rounding is exact.
        if (features.contains(Feature.HALF) || features.contains(Feature.BFLOAT16)) {
            guarded(lines, "TC_DOUBLE2FLOAT_ODD", "static __device__ __forceinline__ float tc_double2float_odd(double x) { float f = __double2float_rz(x); "
                    + "return (double) f == x || tc_isnan(x) ? f : __uint_as_float(__float_as_uint(f) | 1u); }");
        }
        if (features.contains(Feature.HALF)) {
            guarded(lines, "TC_DOUBLE2HALF", "static __device__ __forceinline__ half tc_double2half(double x) { return __float2half_rn(tc_double2float_odd(x)); }");
        }
        if (features.contains(Feature.BFLOAT16)) {
            guarded(lines, "TC_DOUBLE2BFLOAT16", "static __device__ __forceinline__ __hip_bfloat16 tc_double2bfloat16(double x) { return __float2bfloat16(tc_double2float_odd(x)); }");
        }
        return lines;
    }

    private static void guarded(List<String> lines, String guard, String... definitions) {
        lines.add("#ifndef " + guard);
        lines.add("#define " + guard);
        lines.addAll(List.of(definitions));
        lines.add("#endif");
    }

    @Override
    public Optional<String> halfArithmetic(String op, Set<Feature> features) {
        return cuda.halfArithmetic(op, features);
    }
}
