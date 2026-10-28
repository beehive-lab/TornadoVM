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
package tiles;

import java.lang.invoke.MethodHandles;
import java.util.Random;
import java.util.function.Supplier;

import hat.Accelerator;
import hat.Accelerator.Compute;
import hat.ComputeContext;
import hat.NDRange;
import hat.TileContext;
import hat.backend.Backend;
import hat.buffer.Tensor2DF32;
import hat.buffer.TensorF32;
import hat.types.Tile;
import jdk.incubator.code.Reflect;
import jdk.incubator.code.dialect.java.JavaType;

/**
 * HAT tile kernels (CUDA Tile) beyond HAT's own tests: tile arithmetic, 2D elementwise tiles,
 * transposes, row and column reductions, broadcasting, a matrix multiply with a bias epilogue, and
 * arange, reshape and permute. Each is checked against plain Java. (HAT passes a compute lambda only the values it
 * captures: tile sizes are variables, not literals, in the lambdas below.)
 *
 * <pre>
 * ./run.sh -d 1:0 build/examples tiles.TileExamples [size]
 * </pre>
 */
public class TileExamples {

    // ---- 1D: out = x * y + x
    @Reflect
    public static void mulAdd(TensorF32 x, TensorF32 y, TensorF32 out, int tile) {
        int pid = TileContext.BIDX();
        var tx = TileContext.load(x, pid, tile);
        var ty = TileContext.load(y, pid, tile);
        TileContext.store(out, pid, Tile.add(Tile.mul(tx, ty), tx));
    }

    @Reflect
    public static void mulAdd(ComputeContext cc, TensorF32 x, TensorF32 y, TensorF32 out, int tile) {
        cc.dispatchTile(NDRange.of1D(x.m(), tile), () -> mulAdd(x, y, out, tile));
    }

    // ---- 2D elementwise: c = (a - b) * (a + b)
    @Reflect
    public static void squaresDiff(Tensor2DF32 a, Tensor2DF32 b, Tensor2DF32 c, int tm, int tn) {
        var at = TileContext.index(TileContext.BIDX(), TileContext.BIDY());
        var ta = TileContext.load(a, at, TileContext.shape(tm, tn));
        var tb = TileContext.load(b, at, TileContext.shape(tm, tn));
        TileContext.store(c, at, Tile.mul(Tile.sub(ta, tb), Tile.add(ta, tb)));
    }

    @Reflect
    public static void squaresDiff(ComputeContext cc, Tensor2DF32 a, Tensor2DF32 b, Tensor2DF32 c, int tm, int tn) {
        cc.dispatchTile(grid2D(a.m(), a.n(), tm, tn), () -> squaresDiff(a, b, c, tm, tn));
    }

    // ---- transpose: b = a^T, tile by tile
    @Reflect
    public static void transpose(Tensor2DF32 a, Tensor2DF32 b, int tm, int tn) {
        int i = TileContext.BIDX();
        int j = TileContext.BIDY();
        var t = TileContext.load(a, TileContext.index(i, j), TileContext.shape(tm, tn));
        TileContext.store(b, TileContext.index(j, i), Tile.transpose(t));
    }

    @Reflect
    public static void transpose(ComputeContext cc, Tensor2DF32 a, Tensor2DF32 b, int tm, int tn) {
        cc.dispatchTile(grid2D(a.m(), a.n(), tm, tn), () -> transpose(a, b, tm, tn));
    }

    // ---- row sums: s[i] = sum_j a[i][j], one block of tm whole rows each
    @Reflect
    public static void rowSum(Tensor2DF32 a, Tensor2DF32 s, int tm, int n) {
        int i = TileContext.BIDX();
        var t = TileContext.load(a, TileContext.index(i, 0), TileContext.shape(tm, n));
        TileContext.store(s, TileContext.index(i, 0), Tile.sum(t, 1));
    }

    @Reflect
    public static void rowSum(ComputeContext cc, Tensor2DF32 a, Tensor2DF32 s, int tm, int n) {
        cc.dispatchTile(NDRange.of1D(a.m(), tm), () -> rowSum(a, s, tm, n));
    }

    // ---- column sums: m[j] = sum_i a[i][j], one block of tn whole columns each
    @Reflect
    public static void colSum(Tensor2DF32 a, Tensor2DF32 mx, int m, int tn) {
        int j = TileContext.BIDX();
        var t = TileContext.load(a, TileContext.index(0, j), TileContext.shape(m, tn));
        TileContext.store(mx, TileContext.index(0, j), Tile.sum(t, 0));
    }

    @Reflect
    public static void colSum(ComputeContext cc, Tensor2DF32 a, Tensor2DF32 mx, int m, int tn) {
        cc.dispatchTile(NDRange.of1D(a.n(), tn), () -> colSum(a, mx, m, tn));
    }

    // ---- broadcasting: each row minus its sum (a (tm, n) tile and a (tm, 1) tile)
    @Reflect
    public static void centerRows(Tensor2DF32 a, Tensor2DF32 out, int tm, int n) {
        int i = TileContext.BIDX();
        var t = TileContext.load(a, TileContext.index(i, 0), TileContext.shape(tm, n));
        TileContext.store(out, TileContext.index(i, 0), Tile.sub(t, Tile.sum(t, 1)));
    }

    @Reflect
    public static void centerRows(ComputeContext cc, Tensor2DF32 a, Tensor2DF32 out, int tm, int n) {
        cc.dispatchTile(NDRange.of1D(a.m(), tm), () -> centerRows(a, out, tm, n));
    }

    // ---- matrix multiply with a bias row added to every row: c = a b + bias
    @Reflect
    public static void matmulBias(Tensor2DF32 a, Tensor2DF32 b, Tensor2DF32 bias, Tensor2DF32 c, int tm, int tn, int tk) {
        int i = TileContext.BIDX();
        int j = TileContext.BIDY();
        var acc = Tile.zeros(tm, tn);
        int kTiles = Tile.numTiles(a, 1, TileContext.shape(tm, tk));
        for (int k : TileContext.irange(kTiles)) {
            var ta = TileContext.load(a, TileContext.index(i, k), TileContext.shape(tm, tk));
            var tb = TileContext.load(b, TileContext.index(k, j), TileContext.shape(tk, tn));
            acc = Tile.mma(ta, tb, acc);
        }
        var row = TileContext.load(bias, TileContext.index(0, j), TileContext.shape(1, tn));
        TileContext.store(c, TileContext.index(i, j), Tile.add(acc, row));
    }

    @Reflect
    public static void matmulBias(ComputeContext cc, Tensor2DF32 a, Tensor2DF32 b, Tensor2DF32 bias, Tensor2DF32 c, int tm, int tn, int tk) {
        cc.dispatchTile(grid2D(a.m(), b.n(), tm, tn), () -> matmulBias(a, b, bias, c, tm, tn, tk));
    }

    /** Tile extents are powers of two (CUDA Tile): a whole row or column is loaded padded to one, with zeros. */
    private static int pow2(int n) {
        return Integer.highestOneBit(n - 1) << 1;
    }

    // ---- arange: out[i] = i mod tile, the position inside each block (HAT's checker does not yet
    // let an arange tile meet a loaded one in arithmetic)
    @Reflect
    public static void ramp(TensorF32 x, TensorF32 out, int tile) {
        int pid = TileContext.BIDX();
        TileContext.store(out, pid, Tile.arange(tile, JavaType.FLOAT));
    }

    @Reflect
    public static void ramp(ComputeContext cc, TensorF32 x, TensorF32 out, int tile) {
        cc.dispatchTile(NDRange.of1D(x.m(), tile), () -> ramp(x, out, tile));
    }

    // ---- reshape: a flat block of tm * tn values seen as tm rows of tn, summed per row
    @Reflect
    public static void blockRowSums(TensorF32 flat, Tensor2DF32 sums, int tm, int tn) {
        int pid = TileContext.BIDX();
        var block = Tile.reshape(TileContext.load(flat, pid, tm * tn), TileContext.shape(tm, tn));
        TileContext.store(sums, TileContext.index(pid, 0), Tile.sum(block, 1));
    }

    @Reflect
    public static void blockRowSums(ComputeContext cc, TensorF32 flat, Tensor2DF32 sums, int tm, int tn) {
        cc.dispatchTile(NDRange.of1D(flat.m(), tm * tn), () -> blockRowSums(flat, sums, tm, tn));
    }

    // ---- permute: the (1, 0) permutation of a 2D tile is its transpose
    @Reflect
    public static void permuteT(Tensor2DF32 a, Tensor2DF32 b, int tm, int tn) {
        int i = TileContext.BIDX();
        int j = TileContext.BIDY();
        var t = TileContext.load(a, TileContext.index(i, j), TileContext.shape(tm, tn));
        TileContext.store(b, TileContext.index(j, i), Tile.permute(t, TileContext.shape(1, 0)));
    }

    @Reflect
    public static void permuteT(ComputeContext cc, Tensor2DF32 a, Tensor2DF32 b, int tm, int tn) {
        cc.dispatchTile(grid2D(a.m(), a.n(), tm, tn), () -> permuteT(a, b, tm, tn));
    }

    private static NDRange grid2D(int m, int n, int tm, int tn) {
        return NDRange.NDRange2D.of(NDRange.Global2D.of(m, n), NDRange.Local2D.of(tm, tn), NDRange.Tile2D.of(tm, tn), NDRange.Warp2D.of(false, false));
    }

    // ---------------------------------------------------------------- host side

    private static int failures;

    private static void check(String name, float[] expected, Supplier<float[]> actual, float tolerance) {
        float[] got;
        try {
            got = actual.get();
        } catch (RuntimeException e) {
            failures++;
            System.out.printf("%-14s FAIL  %s%n", name, e.getMessage() == null ? e : e.getMessage().lines().findFirst().orElse(""));
            if (Boolean.getBoolean("tiles.trace")) {
                e.printStackTrace();
            }
            return;
        }
        double worst = 0;
        int at = -1;
        for (int i = 0; i < expected.length; i++) {
            double d = Math.abs(got[i] - expected[i]) / Math.max(1.0, Math.abs(expected[i]));
            if (d > worst) {
                worst = d;
                at = i;
            }
        }
        boolean ok = worst <= tolerance;
        if (!ok) {
            failures++;
        }
        System.out.printf("%-14s %s  max relative error %.1e%s%n", name, ok ? "PASS" : "FAIL", worst, ok ? "" : " at " + at + " (" + got[at] + " vs " + expected[at] + ")");
    }

    private static float[] values(TensorF32 t) {
        float[] v = new float[t.m()];
        for (int i = 0; i < v.length; i++) {
            v[i] = t.array(i);
        }
        return v;
    }

    private static float[] values(Tensor2DF32 t) {
        float[] v = new float[t.m() * t.n()];
        for (int i = 0; i < v.length; i++) {
            v[i] = t.array(i);
        }
        return v;
    }

    private static Tensor2DF32 random(Accelerator acc, int m, int n, Random r) {
        var t = Tensor2DF32.create(acc, m, n);
        for (int i = 0; i < m * n; i++) {
            t.array(i, r.nextFloat() + 0.1f);
        }
        return t;
    }

    public static void main(String[] args) {
        int size = args.length > 0 ? Integer.parseInt(args[0]) : 256;
        var accelerator = new Accelerator(MethodHandles.lookup(), Backend.FIRST);
        var r = new Random(7);

        // x * y + x
        int n = size * size;
        var x = TensorF32.create(accelerator, n);
        var y = TensorF32.create(accelerator, n);
        var out = TensorF32.create(accelerator, n);
        float[] e1 = new float[n];
        for (int i = 0; i < n; i++) {
            x.array(i, r.nextFloat());
            y.array(i, r.nextFloat());
            e1[i] = x.array(i) * y.array(i) + x.array(i);
        }
        check("mulAdd", e1, () -> {
            int tile = 256;
            accelerator.compute((@Reflect Compute) cc -> mulAdd(cc, x, y, out, tile));
            return values(out);
        }, 1e-6f);

        int m = size;
        int k = size;
        var a = random(accelerator, m, k, r);
        var b = random(accelerator, m, k, r);

        // (a - b) * 0.5 + 1
        var c = Tensor2DF32.create(accelerator, m, k);
        float[] e2 = new float[m * k];
        for (int i = 0; i < m * k; i++) {
            e2[i] = (a.array(i) - b.array(i)) * (a.array(i) + b.array(i));
        }
        check("squaresDiff", e2, () -> {
            int tm = 32;
            int tn = 64;
            accelerator.compute((@Reflect Compute) cc -> squaresDiff(cc, a, b, c, tm, tn));
            return values(c);
        }, 1e-6f);

        // transpose
        var at = Tensor2DF32.create(accelerator, k, m);
        float[] e3 = new float[m * k];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < k; j++) {
                e3[j * m + i] = a.array(i * k + j);
            }
        }
        check("transpose", e3, () -> {
            int tm = 32;
            int tn = 64;
            accelerator.compute((@Reflect Compute) cc -> transpose(cc, a, at, tm, tn));
            return values(at);
        }, 0f);

        // row sums and normalised rows
        var s = Tensor2DF32.create(accelerator, m, 1);
        var norm = Tensor2DF32.create(accelerator, m, k);
        float[] e4 = new float[m];
        float[] e6 = new float[m * k];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < k; j++) {
                e4[i] += a.array(i * k + j);
            }
            for (int j = 0; j < k; j++) {
                e6[i * k + j] = a.array(i * k + j) - e4[i];
            }
        }
        check("rowSum", e4, () -> {
            int tm = 16;
            int width = pow2(k);
            accelerator.compute((@Reflect Compute) cc -> rowSum(cc, a, s, tm, width));
            return values(s);
        }, 1e-5f);
        check("centerRows", e6, () -> {
            int tm = 16;
            int width = pow2(k);
            accelerator.compute((@Reflect Compute) cc -> centerRows(cc, a, norm, tm, width));
            return values(norm);
        }, 1e-5f);

        // column maxima
        var mx = Tensor2DF32.create(accelerator, 1, k);
        float[] e5 = new float[k];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < k; j++) {
                e5[j] += a.array(i * k + j);
            }
        }
        check("colSum", e5, () -> {
            int tn = 32;
            int height = pow2(m);
            accelerator.compute((@Reflect Compute) cc -> colSum(cc, a, mx, height, tn));
            return values(mx);
        }, 1e-5f);

        // matmul + bias
        var bt = random(accelerator, k, m, r);
        var bias = random(accelerator, 1, m, r);
        var mm = Tensor2DF32.create(accelerator, m, m);
        float[] e7 = new float[m * m];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < m; j++) {
                float sum = 0;
                for (int q = 0; q < k; q++) {
                    sum += a.array(i * k + q) * bt.array(q * m + j);
                }
                e7[i * m + j] = sum + bias.array(j);
            }
        }
        check("matmulBias", e7, () -> {
            int tm = 64;
            int tn = 64;
            int tk = 32;
            accelerator.compute((@Reflect Compute) cc -> matmulBias(cc, a, bt, bias, mm, tm, tn, tk));
            return values(mm);
        }, 1e-3f);

        // arange
        var ramped = TensorF32.create(accelerator, n);
        float[] e8 = new float[n];
        for (int i = 0; i < n; i++) {
            e8[i] = i % 256;
        }
        check("ramp (arange)", e8, () -> {
            int tile = 256;
            accelerator.compute((@Reflect Compute) cc -> ramp(cc, x, ramped, tile));
            return values(ramped);
        }, 1e-6f);

        // reshape: blocks of 16 x 64 values of x, one row sum per 64
        var blockSums = Tensor2DF32.create(accelerator, n / 64, 1);
        float[] e9 = new float[n / 64];
        for (int i = 0; i < n; i++) {
            e9[i / 64] += x.array(i);
        }
        check("blockRowSums (reshape)", e9, () -> {
            int tm = 16;
            int tn = 64;
            accelerator.compute((@Reflect Compute) cc -> blockRowSums(cc, x, blockSums, tm, tn));
            return values(blockSums);
        }, 1e-5f);

        // permute (1, 0)
        var permuted = Tensor2DF32.create(accelerator, k, m);
        check("permuteT (permute)", e3, () -> {
            int tm = 32;
            int tn = 64;
            accelerator.compute((@Reflect Compute) cc -> permuteT(cc, a, permuted, tm, tn));
            return values(permuted);
        }, 0f);

        System.out.println(failures == 0 ? "TileExamples: all passed" : "TileExamples: " + failures + " failed");
        if (failures != 0) {
            System.exit(1);
        }
    }
}
