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
package uk.ac.manchester.tornado.tc.frontend.kernels;

import uk.ac.manchester.tornado.api.tile.DType;
import uk.ac.manchester.tornado.api.tile.PartitionView;
import uk.ac.manchester.tornado.api.tile.Tile;
import uk.ac.manchester.tornado.api.tile.TileContext;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;

/** CUDA Tile kernels: a GEMM with a loop-carried accumulator, and a masked row reduction. */
public final class TileKernels {

    private TileKernels() {
    }

    public static void gemm(TileContext tc, HalfFloatArray a, HalfFloatArray b, FloatArray c, int n) {
        PartitionView av = tc.partition(tc.view(a, n, n), 32, 32);
        PartitionView bv = tc.partition(tc.view(b, n, n), 32, 32);
        PartitionView cv = tc.partition(tc.view(c, n, n), 32, 32);
        Tile acc = tc.zeros(DType.F32, 32, 32);
        for (int k = 0; k < n / 32; k++) {
            acc = tc.mma(av.load(tc.bidX(), k), bv.load(k, tc.bidY()), acc);
        }
        cv.store(acc, tc.bidX(), tc.bidY());
    }

    public static void positiveRowSum(TileContext tc, FloatArray in, FloatArray out, int rows) {
        PartitionView iv = tc.partition(tc.view(in, rows, 64), 1, 64);
        PartitionView ov = tc.partition(tc.view(out, rows, 1), 1, 1);
        Tile x = iv.load(tc.bidX(), 0);
        Tile kept = tc.select(tc.greaterThan(x, 0.0), x, tc.zeros(DType.F32, 1, 64));
        ov.store(tc.sum(kept, 1), tc.bidX(), 0);
    }
}
