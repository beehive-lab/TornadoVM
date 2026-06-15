/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.api.types.matrix;

/**
 * Opaque handle for an 8x8 single-precision matrix fragment held in a SIMD group's
 * registers — the software view of a hardware matrix-unit fragment (Apple Metal's
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
