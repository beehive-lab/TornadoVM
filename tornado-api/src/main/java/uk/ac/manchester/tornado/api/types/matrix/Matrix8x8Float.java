/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.api.types.matrix;

/**
 * Opaque handle for an 8x8 single-precision matrix fragment held in a SIMD group's
 * registers - the software view of a hardware matrix-unit fragment (Apple Metal's
 * {@code simdgroup_float8x8}).
 *
 * <p>On a GPU backend that supports matrix units, values of this type never exist as
 * heap objects: the {@code KernelContext.simdgroupMatrix*} intrinsics that produce and
 * consume them are replaced by hardware instructions, and the fragment lives in
 * registers as a single SSA value (the Metal backend maps this type to the
 * {@code SIMDGROUP_FLOAT8X8} kind, exactly as it maps {@code Float4} to {@code float4}).
 *
 * <p>The {@code float[64]} storage here is only used when a kernel runs on the JVM
 * (sequential reference execution / validation), where the same intrinsics fall back to
 * the plain-Java semantics in {@code KernelContext}. Row-major, {@code values[i*8 + j]}.
 */
public final class Matrix8x8Float {

    /** Used by the Metal backend to resolve this class to its {@code MetalKind}. */
    public static final Class<Matrix8x8Float> TYPE = Matrix8x8Float.class;

    /** Row-major 8x8 storage, only materialised for JVM (sequential) execution. */
    public final float[] values;

    public Matrix8x8Float() {
        this.values = new float[64];
    }

    public float get(int row, int col) {
        return values[row * 8 + col];
    }

    public void set(int row, int col, float value) {
        values[row * 8 + col] = value;
    }
}
