import uk.ac.manchester.tornado.api.tile.*;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;

public class TileFallbackCheck {

    // the kernel, exactly as a user would write it for the accelerator
    static void mm(TileContext tc, HalfFloatArray a, HalfFloatArray b, FloatArray c, int m, int n, int k) {
        PartitionView av = tc.partition(tc.view(a, m, k), 32, 32);
        PartitionView bv = tc.partition(tc.view(b, k, n), 32, 32);
        PartitionView cv = tc.partition(tc.view(c, m, n), 32, 32);
        Tile acc = tc.zeros(DType.F32, 32, 32);
        for (int t = 0; t < k / 32; t++) {
            acc = tc.mma(av.load(tc.bidX, t), bv.load(t, tc.bidY), acc);
        }
        cv.store(acc, tc.bidX, tc.bidY);
    }

    public static void main(String[] args) {
        int m = 64, n = 64, k = 64;
        HalfFloatArray a = new HalfFloatArray(m * k);
        HalfFloatArray b = new HalfFloatArray(k * n);
        FloatArray c = new FloatArray(m * n);
        java.util.Random rnd = new java.util.Random(7);
        for (int i = 0; i < m * k; i++) a.set(i, new HalfFloat((rnd.nextFloat() - 0.5f)));
        for (int i = 0; i < k * n; i++) b.set(i, new HalfFloat((rnd.nextFloat() - 0.5f)));

        // host drives the tile-block grid; on device this is the WorkerGrid
        TileContext tc = new TileContext();
        tc.setBlockCount(m / 32, n / 32, 1);
        for (int bx = 0; bx < m / 32; bx++) {
            for (int by = 0; by < n / 32; by++) {
                tc.setBlockIndex(bx, by, 0);
                mm(tc, a, b, c, m, n, k);
            }
        }

        double maxErr = 0;
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                double ref = 0;
                for (int p = 0; p < k; p++) ref += a.get(i * k + p).getFloat32() * b.get(p * n + j).getFloat32();
                maxErr = Math.max(maxErr, Math.abs(ref - c.get(i * n + j)));
            }
        }
        System.out.println("GEMM 64x64x64 via TileContext, max abs error vs naive = " + maxErr);
        if (maxErr > 1e-3) { System.out.println("FAIL"); System.exit(1); }

        // guards
        try { tc.zeros(DType.F32, 48, 32); System.out.println("FAIL: non-power-of-two accepted"); System.exit(1); }
        catch (IllegalArgumentException e) { System.out.println("guard shape  : " + e.getMessage()); }
        try { tc.mma(tc.zeros(DType.F16,32,32), tc.zeros(DType.F16,32,32), tc.zeros(DType.S32,32,32)); System.out.println("FAIL: bad acc accepted"); System.exit(1); }
        catch (IllegalArgumentException e) { System.out.println("guard dtype  : " + e.getMessage()); }
        try { PartitionView pv = tc.partition(tc.view(c, 64, 64), 32, 32); pv.load(9, 0); System.out.println("FAIL: OOB accepted"); System.exit(1); }
        catch (IndexOutOfBoundsException e) { System.out.println("guard bounds : " + e.getMessage()); }
        System.out.println("PASS");
    }
}
