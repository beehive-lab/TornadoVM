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
 * OpenCL C 1.2 (with the {@code cl_khr_fp64}, {@code cl_khr_fp16} and {@code cl_khr_int64_base_atomics}
 * extensions) for {@code clBuildProgram}. Pointers carry their address space ({@code __global},
 * {@code __local}); the kernel's four leading parameters are the TornadoVM regions with the qualifiers the
 * OpenCL runtime binds them with. Spellings follow the OpenCL C specification.
 */
final class OpenClDialect implements CDialect {

    @Override
    public boolean unrollPrivateStoreLoops() {
        return true;
    }

    @Override
    public Dialect id() {
        return Dialect.OPENCL;
    }

    @Override
    public String integerName(int bits) {
        return switch (bits) {
            case 1 -> "bool";
            case 8 -> "char"; // OpenCL C's char is signed
            case 16 -> "short";
            case 32 -> "int";
            default -> "long"; // 64-bit in OpenCL C
        };
    }

    @Override
    public String unsignedName(int bits) {
        return switch (bits) {
            case 8 -> "uchar";
            case 16 -> "ushort";
            case 64 -> "ulong";
            default -> "uint";
        };
    }

    @Override
    public String longSuffix() {
        return "L";
    }

    @Override
    public String kernelPrefix(boolean tiles) {
        if (tiles) {
            throw new CEmitter.EmitException("TC-E4001", "TileContext kernels are CUDA Tile C++: they run only on the CUDA backend");
        }
        return "__kernel void ";
    }

    @Override
    public String abiParameters() {
        return "__global long *_kernel_context, __constant uchar *_constant_region, __local uchar *_local_region, __global int *_atomics";
    }

    @Override
    public String abiArguments() {
        return "_kernel_context, _constant_region, _local_region, _atomics";
    }

    @Override
    public String functionPrefix() {
        return "static ";
    }

    @Override
    public String arrayQualifier(AddressSpace space) {
        return space == AddressSpace.SHARED ? "__local " : "";
    }

    @Override
    public String pointee(AddressSpace space, String element) {
        return switch (space) {
            case GLOBAL -> "__global " + element;
            case SHARED -> "__local " + element;
            case CONSTANT -> "__constant " + element;
            default -> element; // private: the default address space
        };
    }

    @Override
    public Optional<String> typeName(Type type, Set<Feature> features) {
        return switch (type) {
            case Type.FloatType f when f.kind() == Type.FloatType.Kind.F16 -> {
                features.add(Feature.HALF);
                yield Optional.of("half");
            }
            // OpenCL C has built-in vectors of 2, 3, 4, 8 and 16 lanes of every scalar type.
            case Type.VectorType(var element, var length) when element instanceof Type.IntType(var bits) && bits > 1 -> Optional.of(integerName(bits) + length);
            case Type.VectorType(var element, var length) when element.equals(Type.F32) || element.equals(Type.F64) || element.equals(Type.F16) -> {
                if (element.equals(Type.F16)) {
                    features.add(Feature.HALF);
                }
                yield Optional.of((element.equals(Type.F32) ? "float" : element.equals(Type.F64) ? "double" : "half") + length);
            }
            default -> Optional.empty(); // bfloat16 has no OpenCL C type
        };
    }

    @Override
    public String vector(String typeName, List<String> lanes) {
        return "(" + typeName + ") (" + String.join(", ", lanes) + ")";
    }

    @Override
    public String bitcast(Type from, Type to, Set<Feature> features) {
        if (from.equals(Type.F16) || to.equals(Type.F16)) {
            // as_half / as_short(half) need the full cl_khr_fp16; the bits go through private memory instead.
            features.add(Feature.HALF);
            features.add(Feature.HALF_BITS);
            return from.equals(Type.F16) ? "tc_half_as_short" : "tc_short_as_half";
        }
        // as_<type> reinterprets the bits of a value of the same size.
        return "as_" + (to instanceof Type.IntType(var bits) ? integerName(bits) : to.equals(Type.F32) ? "float" : to.equals(Type.F64) ? "double" : "half");
    }

    @Override
    public void halfRounding(Set<Feature> features) {
        // OpenCL C contracts by default (FP_CONTRACT ON), and the NVIDIA and Intel compilers fuse a
        // half-rounded product into the following add, dropping the rounding: off for such kernels.
        features.add(Feature.NO_CONTRACT);
    }

    @Override
    public String saturatingToInt(Type from, Type to, Set<Feature> features) {
        if (from.equals(Type.F16)) {
            features.add(Feature.HALF);
        }
        // Saturating, round toward zero; NaN converts to 0 (OpenCL C, conversions): Java's rules.
        return "convert_" + (to.equals(Type.I64) ? "long" : "int") + "_sat_rtz";
    }

    @Override
    public Optional<String> convertTo(Type from, Type to, String value, Set<Feature> features) {
        if (to.equals(Type.F16)) {
            // A (half) cast, rounding to nearest even: convert_half_rte needs the full cl_khr_fp16, which
            // the RTX 4090's OpenCL compiler lacks (it accepts half storage and casts, as Graal emits).
            features.add(Feature.HALF);
            return Optional.empty();
        }
        if (to.equals(Type.BF16)) {
            throw new CEmitter.EmitException("TC-E4001", "bfloat16 has no OpenCL C type");
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
        return "return;"; // never reached: kernels are void, and device functions are inlined
    }

    @Override
    public List<String> deviceLaunch(Launch launch, Set<String> helpers) {
        throw new CEmitter.EmitException("TC-E4016", "OpenCL kernels cannot launch kernels (no device-side enqueue in OpenCL 1.2)");
    }

    @Override
    public List<String> preamble(Set<Feature> features) {
        // As the OpenCL driver's own kernels: doubles, halves and 64-bit atomics enabled up front.
        var lines = new ArrayList<String>();
        lines.add("#pragma OPENCL EXTENSION cl_khr_fp64 : enable");
        lines.add("#pragma OPENCL EXTENSION cl_khr_fp16 : enable");
        lines.add("#pragma OPENCL EXTENSION cl_khr_int64_base_atomics : enable");
        if (features.contains(Feature.NO_CONTRACT)) {
            // OpenCL C contracts a * b + c by default; a strict plan, or a kernel rounding to half, keeps
            // Java's separate roundings.
            lines.add("#pragma OPENCL FP_CONTRACT OFF");
        }
        if (features.contains(Feature.HALF_BITS)) {
            lines.add("static inline short tc_half_as_short(half h) { return *((__private short *) &h); }");
            lines.add("static inline half tc_short_as_half(short s) { return *((__private half *) &s); }");
        }
        return lines;
    }
}
