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
import java.util.Optional;
import java.util.Set;

import uk.ac.manchester.tornado.tc.ir.AddressSpace;
import uk.ac.manchester.tornado.tc.ir.Type;
import uk.ac.manchester.tornado.tc.ir.target.TargetCaps.Dialect;

/**
 * What a C-family target spells its own way. {@link CEmitter} is the C core shared by every target
 * (statements, precedence, element access, Java integer and comparison semantics); a dialect gives
 * the kernel and function headers, pointer and special types, bit-casts and conversions, the
 * preamble and target-only statements such as device launches.
 *
 * <p>Methods that spell a type or a conversion record the {@link Feature}s it needs, so the preamble
 * comes from what was used, never from scanning the emitted text.</p>
 */
sealed interface CDialect permits CudaDialect, OpenClDialect, MetalDialect {

    /** Something the preamble must provide. */
    enum Feature {
        /** Floating-point contraction (FMA formation by the device compiler) is not allowed: a strict-floating-point plan. */
        NO_CONTRACT,
        /** 16-bit floats. */
        HALF,
        /** bfloat16. */
        BFLOAT16,
        /** 8-bit floats (CUDA Tile). */
        FP8,
        /** CUDA Tile C++. */
        TILES,
        /** Reinterpreting half bits where the dialect has no built-in for it (OpenCL without full cl_khr_fp16). */
        HALF_BITS,
        /** Four-lane half vectors where the dialect has no built-in type (CUDA: a struct). */
        HALF4
    }

    /** One device launch ({@code KernelContext.launch}), its operands already printed. */
    record Launch(String child, String mode, List<String> global, List<String> globalAsUnsigned, List<String> local, List<String> arguments, String reportLabel, String shortName) {
    }

    static CDialect of(Dialect dialect) {
        return switch (dialect) {
            case CUDA -> new CudaDialect();
            case OPENCL -> new OpenClDialect();
            case METAL -> new MetalDialect();
        };
    }

    Dialect id();

    /** The integer type of {@code bits} (1 = boolean): {@code long long} in CUDA, {@code long} in OpenCL C. */
    String integerName(int bits);

    /** The unsigned integer type of {@code bits}. */
    String unsignedName(int bits);

    /** The suffix of a 64-bit integer literal: {@code LL} in CUDA, {@code L} in OpenCL C. */
    String longSuffix();

    /** The kernel's qualifiers up to its name: {@code extern "C" __global__ void }. */
    String kernelPrefix(boolean tiles);

    /** The runtime's leading kernel parameters (kernel context, constant, local and atomics regions). */
    String abiParameters();

    /** How many arguments {@link #abiParameters} binds: the kernel's own parameters start at this index. */
    default int abiParameterCount() {
        return 4;
    }

    /**
     * The declaration of the kernel parameter {@code name} at argument {@code index} ({@code declaration}
     * is the core's {@code type name}): Metal adds the buffer index the runtime binds it at.
     */
    default String kernelParameter(Type type, String declaration, String name, int index) {
        return declaration;
    }

    /** Parameters after the kernel's own (Metal's thread positions and grid size), from argument {@code nextIndex}; with a leading comma. */
    default String trailingKernelParameters(int nextIndex) {
        return "";
    }

    /** Whether the target can print from a kernel ({@code Debug.printf}). */
    /**
     * A value rounded to half (Java's HalfFloat and HAT's F16 round after every operation). A dialect
     * whose compiler may fuse across that rounding records what the kernel needs to forbid it.
     */
    default void halfRounding(Set<Feature> features) {
    }

    /**
     * The function for a half {@code +}, {@code -} or {@code *} ({@code op}: "+", "-", "*") that the
     * device compiler must not contract into an fma, or empty to print the C operator. Java rounds every
     * half operation; a dialect printing the operator records what forbids contraction.
     */
    default Optional<String> halfArithmetic(String op, Set<Feature> features) {
        halfRounding(features);
        return Optional.empty();
    }

    default boolean printf() {
        return true;
    }

    /**
     * Whether the target compiles with fast math, which assumes no NaN: {@code x != x} folds to false
     * and an unordered comparison to its ordered inverse. NaN tests are then spelled with {@code isnan}.
     */
    default boolean fastMath() {
        return false;
    }

    /** Whether the language has {@code goto}; without it the emitter leaves loops through a jump flag. */
    default boolean gotos() {
        return true;
    }

    /** The same regions as arguments, passed on by a device launch. */
    String abiArguments();

    /** A device function's qualifiers before its return type. */
    String functionPrefix();

    /** The qualifier of a kernel-local array declaration in {@code space} (with a trailing space). */
    String arrayQualifier(AddressSpace space);

    /**
     * The pointee of a pointer to {@code element} (an already spelled type) in {@code space}, with any
     * address-space qualifier: {@code float} on CUDA; the core adds the {@code *}.
     */
    String pointee(AddressSpace space, String element);

    /** A type the C core does not spell itself (half, bfloat16, vectors); empty if unsupported. */
    Optional<String> typeName(Type type, Set<Feature> features);

    /** A vector value from its lanes. */
    String vector(String typeName, List<String> lanes);

    /** The function reinterpreting the bits of {@code from} as {@code to}. */
    String bitcast(Type from, Type to, Set<Feature> features);

    /** Java's saturating float-to-integer conversion (NaN to 0). */
    String saturatingToInt(Type from, Type to, Set<Feature> features);

    /**
     * A conversion of {@code value} (of type {@code from}) the C cast does not express: to f16 or bf16,
     * rounding to nearest once (a double converts directly, never through float); else empty.
     */
    Optional<String> convertTo(Type from, Type to, String value, Set<Feature> features);

    /** A tile-programming expression ({@code tile.*} intrinsics; CUDA Tile on CUDA); TC-E4001 where the target has none. */
    String tileExpression(String name, uk.ac.manchester.tornado.tc.ir.Inst inst, List<String> operands);

    /** The type of a tile value; TC-E4001 where the target has none. */
    String tileType(Type.TileType type);

    /** The statement for a point control cannot reach. */
    String unreachable();

    /** The lines of a device launch; TC-E4001 where the target has none. */
    List<String> deviceLaunch(Launch launch, Set<String> helpers);

    /** The preamble lines (includes) for {@code features}. */
    List<String> preamble(Set<Feature> features);
}
