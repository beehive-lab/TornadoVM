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
import uk.ac.manchester.tornado.tc.ir.ApiTypes;
import uk.ac.manchester.tornado.tc.ir.Inst;
import uk.ac.manchester.tornado.tc.ir.Type;
import uk.ac.manchester.tornado.tc.ir.target.TargetCaps.Dialect;

/**
 * Metal Shading Language (C++14-based, Metal 2.4+) for {@code newLibraryWithSource}. Every pointer
 * names its address space ({@code device}, {@code threadgroup}, {@code constant}, {@code thread}).
 * The kernel ABI is the one the TornadoVM Metal runtime binds: the four regions at buffer indices
 * 0-3 (the local region as {@code [[threadgroup(2)]]}), each parameter at the next buffer index
 * (arrays as {@code device uchar *}, scalars by reference in {@code constant}), then the thread
 * positions as system values and the grid size in a buffer after the parameters
 * ({@code _global_sizes}). There is no double precision, no printf and no vector wider than four
 * lanes. Spellings follow the Metal Shading Language specification.
 */
final class MetalDialect implements CDialect {

    @Override
    public Dialect id() {
        return Dialect.METAL;
    }

    @Override
    public String integerName(int bits) {
        return switch (bits) {
            case 1 -> "bool";
            case 8 -> "char";
            case 16 -> "short";
            case 32 -> "int";
            default -> "long"; // 64-bit in MSL
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
        return "kernel void ";
    }

    @Override
    public String abiParameters() {
        return "device long *_kernel_context [[buffer(0)]], constant uchar *_constant_region [[buffer(1)]], threadgroup uchar *_local_region [[threadgroup(2)]], "
                + "device int *_atomics [[buffer(3)]]";
    }

    @Override
    public String abiArguments() {
        return "_kernel_context, _constant_region, _local_region, _atomics";
    }

    @Override
    public String kernelParameter(Type type, String declaration, String name, int index) {
        if (type instanceof Type.PointerType) {
            return declaration + " [[buffer(" + index + ")]]";
        }
        // A scalar arrives as bytes (setBytes): a reference into the constant address space.
        var base = declaration.substring(0, declaration.length() - name.length()).trim();
        return "constant " + base + " &" + name + " [[buffer(" + index + ")]]";
    }

    @Override
    public String trailingKernelParameters(int nextIndex) {
        // System values (they take no argument index), then the grid size the runtime binds last.
        return ", uint3 _thread_position_in_grid [[thread_position_in_grid]], uint3 _thread_position_in_threadgroup [[thread_position_in_threadgroup]], "
                + "uint3 _threadgroup_position_in_grid [[threadgroup_position_in_grid]], uint3 _local_size [[threads_per_threadgroup]], "
                + "uint3 _threadgroups_per_grid [[threadgroups_per_grid]], device uint *_global_sizes [[buffer(" + nextIndex + ")]]";
    }

    @Override
    public boolean printf() {
        return false;
    }

    /** The driver compiles MSL with Metal's default options: fast math on. */
    @Override
    public boolean fastMath() {
        return true;
    }

    /** MSL rejects {@code goto} and labelled statements. */
    @Override
    public boolean gotos() {
        return false;
    }

    @Override
    public String functionPrefix() {
        return "static inline ";
    }

    @Override
    public String arrayQualifier(AddressSpace space) {
        return space == AddressSpace.SHARED ? "threadgroup " : "";
    }

    @Override
    public String pointee(AddressSpace space, String element) {
        return switch (space) {
            case GLOBAL -> "device " + element;
            case SHARED -> "threadgroup " + element;
            case CONSTANT -> "constant " + element;
            default -> "thread " + element; // MSL pointers always name their address space
        };
    }

    @Override
    public Optional<String> typeName(Type type, Set<Feature> features) {
        return switch (type) {
            case Type.FloatType f when f.kind() == Type.FloatType.Kind.F16 -> Optional.of("half");
            case Type.ObjectType o when o.equals(ApiTypes.MATRIX_8X8_FLOAT) -> Optional.of("simdgroup_float8x8");
            case Type.VectorType(var element, var length) when length > 4 -> throw new CEmitter.EmitException("TC-E4001",
                    "Metal vectors have at most 4 lanes (" + type.text() + ")");
            case Type.VectorType(var element, var length) when element instanceof Type.IntType(var bits) && bits > 1 -> Optional.of(integerName(bits) + length);
            case Type.VectorType(var element, var length) when element.equals(Type.F32) -> Optional.of("float" + length);
            case Type.VectorType(var element, var length) when element.equals(Type.F16) -> Optional.of("half" + length);
            default -> Optional.empty(); // no double, no bfloat16 before MSL 3.1
        };
    }

    @Override
    public String vector(String typeName, List<String> lanes) {
        return typeName + "(" + String.join(", ", lanes) + ")";
    }

    @Override
    public String bitcast(Type from, Type to, Set<Feature> features) {
        // as_type<T> reinterprets the bits of a value of the same size (half included).
        return "as_type<" + (to instanceof Type.IntType(var bits) ? integerName(bits) : to.equals(Type.F32) ? "float" : "half") + ">";
    }

    @Override
    public String saturatingToInt(Type from, Type to, Set<Feature> features) {
        // MSL leaves out-of-range and NaN conversions undefined: helpers with Java's rules (preamble).
        return to.equals(Type.I64) ? "tc_f2l" : "tc_f2i";
    }

    @Override
    public Optional<String> convertTo(Type from, Type to, String value, Set<Feature> features) {
        if (to.equals(Type.BF16)) {
            throw new CEmitter.EmitException("TC-E4001", "bfloat16 is not supported on the Metal target");
        }
        return Optional.empty(); // (half) rounds to nearest even
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
        return "return;";
    }

    @Override
    public List<String> deviceLaunch(Launch launch, Set<String> helpers) {
        throw new CEmitter.EmitException("TC-E4016", "Metal kernels cannot launch kernels");
    }

    @Override
    public List<String> preamble(Set<Feature> features) {
        var lines = new ArrayList<String>();
        lines.add("#include <metal_stdlib>");
        lines.add("using namespace metal;");
        // Java's float-to-integer conversions: NaN is 0, out-of-range values saturate. Guarded: a module
        // with several functions prints the preamble once per function.
        lines.add("#ifndef TC_F2I");
        lines.add("#define TC_F2I");
        lines.add("static inline int tc_f2i(float x) { return isnan(x) ? 0 : x >= 2147483647.0f ? 2147483647 : x <= -2147483648.0f ? (-2147483647 - 1) : (int) x; }");
        lines.add("static inline int tc_f2i(half x) { return tc_f2i((float) x); }");
        lines.add("static inline long tc_f2l(float x) { return isnan(x) ? 0 : x >= 9223372036854775807.0f ? 9223372036854775807L : x <= -9223372036854775808.0f ? (-9223372036854775807L - 1) : (long) x; }");
        lines.add("static inline long tc_f2l(half x) { return tc_f2l((float) x); }");
        lines.add("#endif");
        return lines;
    }
}
