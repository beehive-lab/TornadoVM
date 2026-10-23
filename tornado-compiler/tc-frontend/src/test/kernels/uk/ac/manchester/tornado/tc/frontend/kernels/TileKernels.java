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
