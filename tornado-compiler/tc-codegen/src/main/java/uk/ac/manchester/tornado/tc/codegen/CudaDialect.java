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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import uk.ac.manchester.tornado.tc.ir.AddressSpace;
import uk.ac.manchester.tornado.tc.ir.Type;
import uk.ac.manchester.tornado.tc.ir.target.TargetCaps.Dialect;

/** CUDA C++ for NVRTC (and CUDA Tile C++ for nvcc). Address spaces are implicit in pointers. */
final class CudaDialect implements CDialect {

    private static final String ABI_PARAMETERS = "long long *_kernel_context, unsigned char *_constant_region, unsigned char *_local_region, int *_atomics";

    @Override
    public Dialect id() {
        return Dialect.CUDA;
    }

    @Override
    public String kernelPrefix(boolean tiles) {
        // A TileContext kernel is CUDA Tile C++, compiled by nvcc (the runtime looks for __tile_global__).
        return tiles ? "extern \"C\" __tile_global__ void " : "extern \"C\" __global__ void ";
    }

    @Override
    public String abiParameters() {
        return ABI_PARAMETERS;
    }

    @Override
    public String abiArguments() {
        return "_kernel_context, _constant_region, _local_region, _atomics";
    }

    @Override
    public String functionPrefix() {
        return "__device__ static ";
    }

    @Override
    public String arrayQualifier(AddressSpace space) {
        return space == AddressSpace.SHARED ? "__shared__ " : "";
    }

    @Override
    public String pointee(AddressSpace space, String element) {
        return element; // generic addressing: CUDA pointers carry no address space
    }

    @Override
    public Optional<String> typeName(Type type, Set<Feature> features) {
        return switch (type) {
            case Type.FloatType f when f.kind() == Type.FloatType.Kind.F16 -> {
                features.add(Feature.HALF);
                yield Optional.of("half");
            }
            case Type.FloatType f when f.kind() == Type.FloatType.Kind.BF16 -> {
                features.add(Feature.BFLOAT16);
                yield Optional.of("__nv_bfloat16");
            }
            case Type.VectorType(var element, var length) when element.equals(Type.F16) && length == 2 -> {
                features.add(Feature.HALF);
                yield Optional.of("half2");
            }
            // Register vectors (tensor-core fragments): the CUDA built-in vector types; charN lanes are signed char.
            case Type.VectorType(var element, var length) when length >= 2 && length <= 4 && element instanceof Type.IntType(var bits) && bits > 1 ->
                Optional.of((bits == 8 ? "char" : bits == 16 ? "short" : bits == 32 ? "int" : "longlong") + length);
            case Type.VectorType(var element, var length) when length >= 2 && length <= 4 && (element.equals(Type.F32) || element.equals(Type.F64)) ->
                Optional.of((element.equals(Type.F32) ? "float" : "double") + length);
            default -> Optional.empty();
        };
    }

    @Override
    public String vector(String typeName, List<String> lanes) {
        return "make_" + typeName + "(" + String.join(", ", lanes) + ")";
    }

    @Override
    public String bitcast(Type from, Type to, Set<Feature> features) {
        if (from.equals(Type.F16) || to.equals(Type.F16)) {
            features.add(Feature.HALF);
        }
        return switch (from.text() + "->" + to.text()) {
            case "f32->i32" -> "__float_as_int";
            case "i32->f32" -> "__int_as_float";
            case "f64->i64" -> "__double_as_longlong";
            case "i64->f64" -> "__longlong_as_double";
            case "f16->i16" -> "__half_as_short";
            case "i16->f16" -> "__short_as_half";
            default -> throw new CEmitter.EmitException("TC-E4001", "bitcast " + from.text() + " to " + to.text() + " is not supported on CUDA yet");
        };
    }

    @Override
    public String saturatingToInt(Type from, Type to, Set<Feature> features) {
        // The CUDA round-toward-zero conversions saturate and map NaN to 0, as Java does.
        if (from.equals(Type.F16)) {
            features.add(Feature.HALF);
            return to.equals(Type.I64) ? "__half2ll_rz" : "__half2int_rz";
        }
        return (from.equals(Type.F64) ? "__double2" : "__float2") + (to.equals(Type.I64) ? "ll_rz" : "int_rz");
    }

    @Override
    public Optional<String> convertTo(Type to, String value, Set<Feature> features) {
        if (to.equals(Type.F16)) {
            features.add(Feature.HALF);
            return Optional.of("__float2half_rn(" + value + ")");
        }
        if (to.equals(Type.BF16)) {
            features.add(Feature.BFLOAT16);
            return Optional.of("__float2bfloat16_rn(" + value + ")");
        }
        return Optional.empty();
    }

    /** CUDA Tile C++ ({@link TileSpellings}), compiled by nvcc. */
    @Override
    public String tileExpression(String name, uk.ac.manchester.tornado.tc.ir.Inst inst, List<String> operands) {
        return TileSpellings.expression(name, inst, operands);
    }

    @Override
    public String tileType(Type.TileType type) {
        return TileSpellings.tileType(type);
    }

    @Override
    public String unreachable() {
        return "__builtin_unreachable();";
    }

    /**
     * Grid = ceil(global / local) per dimension (a global size of 1 is one block); the child gets its
     * parent's ABI regions; TAIL and FIRE_AND_FORGET use the CUDA named streams; a failed launch is
     * reported and, with --threadInfo, the launching thread prints the host's "Task info" block.
     */
    @Override
    public List<String> deviceLaunch(Launch l, Set<String> helpers) {
        var stream = switch (l.mode()) {
            case "tail" -> ", 0, cudaStreamTailLaunch";
            case "fire_and_forget" -> ", 0, cudaStreamFireAndForget";
            default -> "";
        };
        var grid = new ArrayList<String>();
        for (int d = 0; d < 3; d++) {
            var c = "xyz".charAt(d);
            grid.add(l.global().get(d).equals("1") ? "1" : "(" + l.globalAsUnsigned().get(d) + " + _dp_block." + c + " - 1) / _dp_block." + c);
        }
        var args = new StringBuilder(abiArguments());
        l.arguments().forEach(a -> args.append(", ").append(a));
        helpers.add("tc_device_launch_check");
        return List.of("dim3 _dp_block(" + String.join(", ", l.local()) + ");",
                "dim3 _dp_grid(" + String.join(", ", grid) + ");",
                CEmitter.deviceKernelEntry(l.child()) + "<<<_dp_grid, _dp_block" + stream + ">>>(" + args + ");",
                "tc_device_launch_check(_kernel_context, \"" + l.shortName() + "\", \"" + l.reportLabel() + "\", " + String.join(", ", l.global()) + ", _dp_grid, _dp_block);");
    }

    @Override
    public List<String> preamble(Set<Feature> features) {
        var lines = new ArrayList<String>();
        if (features.contains(Feature.TILES)) {
            lines.add(TileSpellings.PRELUDE.strip());
        }
        // cuda_fp8.h builds on cuda_fp16.h.
        if (features.contains(Feature.HALF) || features.contains(Feature.FP8)) {
            lines.add("#include <cuda_fp16.h>");
        }
        if (features.contains(Feature.BFLOAT16)) {
            lines.add("#include <cuda_bf16.h>");
        }
        if (features.contains(Feature.FP8)) {
            lines.add("#include <cuda_fp8.h>");
        }
        return lines;
    }
}
