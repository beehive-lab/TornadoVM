/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester.
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
package uk.ac.manchester.tornado.api.tile;

/**
 * Element type of a {@link Tile}.
 *
 * <p>
 * Each constant carries the CUDA Tile C++ spelling used by the code generator and the
 * element width in bytes. The set is a superset of the operand types reachable through
 * the hand-written {@code mma.sync} emitter, because CUDA Tile selects the instruction.
 * </p>
 */
public enum DType {

    F16("__half", 2),
    BF16("__nv_bfloat16", 2),
    F32("float", 4),
    F64("double", 8),
    TF32("__nv_tf32", 4),
    FP8_E4M3("__nv_fp8_e4m3", 1),
    FP8_E5M2("__nv_fp8_e5m2", 1),
    S8("signed char", 1),
    S32("int", 4);

    private final String cppType;
    private final int bytes;

    DType(String cppType, int bytes) {
        this.cppType = cppType;
        this.bytes = bytes;
    }

    /**
     * @return the CUDA Tile C++ element type, for example {@code __half}.
     */
    public String getCppType() {
        return cppType;
    }

    /**
     * @return the width of one element in bytes.
     */
    public int getBytes() {
        return bytes;
    }

    /**
     * Whether {@code ct::element_cast} accepts this conversion.
     *
     * <p>
     * It accepts every pair of scalar element types, which was established by compiling each
     * combination rather than inferred. An earlier version of this method rejected narrowing
     * conversions such as f32 to f16; that was wrong, and came from a probe that had failed for
     * an unrelated reason - a missing {@code cuda_fp16.h} - and was read as a type constraint.
     * Narrowing still loses precision, but CUDA Tile permits it, so the API does too and leaves
     * the choice to the caller.
     * </p>
     *
     * @param target
     *     element type to convert to
     * @return true; retained so the rule has one documented home if a future toolkit restricts it
     */
    public boolean canConvertTo(DType target) {
        return target != null;
    }

    /**
     * Spells out a {@code ct::tile} type, for example
     * {@code ct::tile<float, ct::shape<64, 64>>}.
     *
     * <p>
     * This lives here because both the API ({@link Tile#toCppType()}) and the CUDA code
     * generator need the identical spelling: the generator declares a variable with it and the
     * API reports it in diagnostics, so two copies would let a declaration and its description
     * drift apart.
     * </p>
     *
     * @param dtype
     *     element type
     * @param shape
     *     tile extents, innermost last
     * @return the C++ type
     */
    public static String tileCppType(DType dtype, int[] shape) {
        StringBuilder builder = new StringBuilder("ct::tile<");
        builder.append(dtype.getCppType()).append(", ct::shape<");
        for (int i = 0; i < shape.length; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(shape[i]);
        }
        return builder.append(">>").toString();
    }

    /**
     * Accumulator rules taken from the CUDA Tile {@code mmaf} / {@code mmai} tables. The
     * accumulator type must equal the result type, so this reports the accumulator that a
     * multiply-accumulate over this operand type is allowed to produce.
     *
     * @param accumulator
     *     candidate accumulator type
     * @return true when this operand type may accumulate into {@code accumulator}
     */
    public boolean canAccumulateInto(DType accumulator) {
        return switch (this) {
            case F16, FP8_E4M3, FP8_E5M2 -> accumulator == F16 || accumulator == F32;
            case BF16, TF32 -> accumulator == F32;
            case F32 -> accumulator == F32;
            case F64 -> accumulator == F64;
            case S8 -> accumulator == S32;
            case S32 -> accumulator == S32;
        };
    }
}
