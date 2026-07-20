/*
 * Copyright (c) 2024, APT Group, Department of Computer Science,
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

import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;

/**
 * jTile - a minimal, cuTile/Tilus-inspired <b>tile</b> programming surface for TornadoVM.
 *
 * <p>
 * Milestone M1 models a tile at its finest granularity: each work-item owns one lane
 * (the element at {@link KernelContext#globalIdx}), and the operations below -
 * {@link #load}, {@link #store}, {@link #add}, {@link #mul}, {@link #scale} - form the
 * elementwise tile vocabulary. They are plain scalar helpers that inline into the per-thread
 * kernel graph, so they JIT-compile on every TornadoVM backend with no new compiler support.
 * </p>
 *
 * <p>
 * Multi-element shared/register tiles (a work-group cooperatively owning a {@code tileSize}-wide
 * strip, and 2-D tensor-core tiles) require the tile value to survive across operations, which
 * TornadoVM cannot yet trace through method boundaries for local memory. That is the subject of
 * milestone M2 (tensor-core GEMM, reusing the existing MMA intrinsics) and the Phase-2 compiler
 * work (first-class {@code Tile} IR nodes). See the jTile design plan.
 * </p>
 *
 * <p>
 * Example (SAXPY, {@code c = alpha*a + b}), launched with global work {@code N}:
 * </p>
 *
 * <pre>
 * public static void saxpy(KernelContext ctx, FloatArray a, FloatArray b, FloatArray c, float alpha) {
 *     float ta = Tile.load(ctx, a);
 *     float tb = Tile.load(ctx, b);
 *     Tile.store(ctx, c, Tile.add(Tile.scale(ta, alpha), tb));
 * }
 * </pre>
 */
public final class Tile {

    private Tile() {
    }

    /**
     * Loads this work-item's lane of {@code src} (element {@link KernelContext#globalIdx}).
     */
    public static float load(KernelContext context, FloatArray src) {
        return src.get(context.globalIdx);
    }

    /**
     * Stores {@code value} into this work-item's lane of {@code dst} (element
     * {@link KernelContext#globalIdx}).
     */
    public static void store(KernelContext context, FloatArray dst, float value) {
        dst.set(context.globalIdx, value);
    }

    /**
     * Element-wise tile sum, {@code x + y}.
     */
    public static float add(float x, float y) {
        return x + y;
    }

    /**
     * Element-wise tile product, {@code x * y}.
     */
    public static float mul(float x, float y) {
        return x * y;
    }

    /**
     * Scales a tile lane by a scalar, {@code x * s}.
     */
    public static float scale(float x, float s) {
        return x * s;
    }
}
