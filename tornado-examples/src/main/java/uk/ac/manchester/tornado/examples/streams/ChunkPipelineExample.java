package uk.ac.manchester.tornado.examples.streams;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;

/**
 * Demonstrates H2D / compute overlap through a software pipeline across M chunks.
 *
 * <p>A total dataset of N elements is split into M equal chunks. The TaskGraph
 * interleaves one H2D transfer and one kernel launch per batch:
 *
 * <pre>
 *   [H2D b0] [H2D b1] [H2D b2] ... [H2D bM-1]      <- H2D stream (in-order)
 *        [kernel b0] [kernel b1] ... [kernel bM-1]   <- COMPUTE stream (in-order)
 *             ^^^^^^^^^^^^^^^^^^^^^^
 *             steady-state overlap: kernel(bi) runs concurrently with H2D(bi+1)
 * </pre>
 *
 * <p>This pattern is only possible with multi-stream execution. Each kernel receives
 * a fine-grained {@code cuStreamWaitEvent} on its own H2D event only — it does not
 * wait for subsequent chunks' transfers to complete before launching.
 *
 * <p>How to run with multi-stream enabled:
 * <pre>
 *   tornado \
 *   --jvm="-Dtornado.ptx.multistream=true -Dtornado.vm.deps=true" \
 *   -m tornado.examples/uk.ac.manchester.tornado.examples.streams.ChunkPipeline [totalSize] [numBatches]
 * </pre>
 *
 * <p>For Nsight Systems profiling:
 * <pre>
 *   nsys profile --trace=cuda --output=pipeline \
 *     tornado \
 *     -Dtornado.ptx.multistream=true \
 *     -Dtornado.vm.deps=true \
 *     uk.ac.manchester.tornado.examples.compute.ChunkPipeline
 * </pre>
 */
public class ChunkPipelineExample {

    /** Total number of floats across all batches. */
    private static final int DEFAULT_TOTAL_SIZE = 640_000_000;

    /** Number of pipeline stages (batches). */
    private static final int DEFAULT_NUM_CHUNKS = 8;

    /**
     * FMA rounds per element. Raises arithmetic intensity without adding memory traffic:
     * memory footprint is fixed at 2 reads + 1 write per element regardless of this value.
     * Tune upward to make kernels longer than the H2D transfers for maximum overlap visibility.
     */
    private static final int COMPUTE_ITERATIONS = 512;

    private static final int WARMUP_ITERATIONS = 100;
    private static final int TIMED_ITERATIONS  = 100;

    // -------------------------------------------------------------------------
    // Kernel
    // -------------------------------------------------------------------------

    /**
     * A compute-intensive kernel: use {@code COMPUTE_ITERATIONS} to increase workload.
     */
    public static void computeKernel(FloatArray x, FloatArray y, FloatArray result, float alpha) {
        for (@Parallel int i = 0; i < result.getSize(); i++) {
            float xi = x.get(i);
            float yi = y.get(i);
            float val = alpha * xi + yi;
            for (int j = 0; j < COMPUTE_ITERATIONS; j++) {
                val = val * alpha + yi;
            }
            result.set(i, val);
        }
    }

    // -------------------------------------------------------------------------
    // Validation helper — mirrors the kernel exactly on the CPU
    // -------------------------------------------------------------------------

    private static float expectedValue(float x, float y, float alpha) {
        float val = alpha * x + y;
        for (int j = 0; j < COMPUTE_ITERATIONS; j++) {
            val = val * alpha + y;
        }
        return val;
    }

    // -------------------------------------------------------------------------
    // Main
    // -------------------------------------------------------------------------

    public static void main(String[] args) throws TornadoExecutionPlanException {
        int totalSize  = DEFAULT_TOTAL_SIZE;
        int numChunks = DEFAULT_NUM_CHUNKS;

        if (args.length >= 1) totalSize  = Integer.parseInt(args[0]);
        if (args.length >= 2) numChunks = Integer.parseInt(args[1]);

        if (totalSize % numChunks != 0) {
            throw new IllegalArgumentException("totalSize must be divisible by numChunks");
        }

        final int   chunkSize = totalSize / numChunks;
        final float alpha     = 0.5f;
        final float mbPerChunk = (2.0f * chunkSize * Float.BYTES) / (1024 * 1024);

        System.out.printf("=== Pipelined Chunk Overlap ===%n");
        System.out.printf("  Total elements : %,d%n",  totalSize);
        System.out.printf("  Chunks         : %d%n",   numChunks);
        System.out.printf("  Elements/chunk : %,d%n",  chunkSize);
        System.out.printf("  H2D/chunk      : %.1f MB (x + y arrays)%n", mbPerChunk);
        System.out.printf("  Compute iters  : %d FMA rounds/element%n",  COMPUTE_ITERATIONS);
        System.out.println();
        System.out.println("Expected Nsight timeline (multi-stream):");
        System.out.println("  H2D:     [c0][c1][c2][c3]...");
        System.out.println("  COMPUTE:      [c0][c1][c2][c3]...");
        System.out.println("  (kernel ci overlaps with H2D ci+1 in steady state)");
        System.out.println();

        // ------------------------------------------------------------------
        // Allocate and initialise per-chunk arrays
        // ------------------------------------------------------------------
        FloatArray[] chunkX      = new FloatArray[numChunks];
        FloatArray[] chunkY      = new FloatArray[numChunks];
        FloatArray[] chunkResult = new FloatArray[numChunks];

        for (int b = 0; b < numChunks; b++) {
            chunkX[b]      = new FloatArray(chunkSize);
            chunkY[b]      = new FloatArray(chunkSize);
            chunkResult[b] = new FloatArray(chunkSize);
            chunkX[b].init(b + 1.0f);          // x values differ per chunk for easy validation
            chunkY[b].init((b + 1) * 2.0f);    // y values differ per chunk
        }

        // ------------------------------------------------------------------
        // Build the TaskGraph
        //
        // Deliberate interleaving: transferToDevice(ci) immediately precedes
        // task(ci) in the bytecode. The interpreter emits:
        //   H2D(c0), LAUNCH(c0), H2D(c1), LAUNCH(c1), ...
        //
        // With multi-stream:
        //   - H2D(ci) → H2D stream, produces event_i
        //   - LAUNCH(ci) → COMPUTE stream, cuStreamWaitEvent on event_i only
        //
        // So kernel(c0) starts as soon as H2D(c0) is done. By then H2D(c1)
        // is already queued on the H2D stream and runs concurrently with kernel(c0).
        //
        // All D2H transfers are issued after all kernels.
        // TODO: This is a potential optimization opportunity.
        // ------------------------------------------------------------------
        TaskGraph tg = new TaskGraph("pipeline");

        for (int c = 0; c < numChunks; c++) {
            tg.transferToDevice(DataTransferMode.EVERY_EXECUTION, chunkX[c], chunkY[c])
                    .task("t" + c, ChunkPipelineExample::computeKernel,
                            chunkX[c], chunkY[c], chunkResult[c], alpha)
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, chunkResult[c]);
        }

        ImmutableTaskGraph itg = tg.snapshot();

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(itg)) {

            // Warmup: let JIT compiler & runtime reach steady state
            System.out.printf("Warming up (%d iterations)...%n", WARMUP_ITERATIONS);
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                plan.execute();
            }

            // Iterations
            System.out.printf("Running (%d iterations)...%n", TIMED_ITERATIONS);
            long totalNs = 0;
            for (int i = 0; i < TIMED_ITERATIONS; i++) {
                long t0 = System.nanoTime();
                plan.execute();
                long t1 = System.nanoTime();
                long ms = (t1 - t0);
                totalNs += ms;
            }
            System.out.printf("Average iteration  : %.2f ms%n%n", totalNs / (double) TIMED_ITERATIONS / 1e6);
        }

        // ------------------------------------------------------------------
        // Validation — spot-check first and last element of every batch
        // ------------------------------------------------------------------
        System.out.println("Validating...");
        boolean passed = true;
        final float tolerance = 1e-3f;

        for (int b = 0; b < numChunks; b++) {
            float x        = chunkX[b].get(0);
            float y        = chunkY[b].get(0);
            float expected = expectedValue(x, y, alpha);

            float first = chunkResult[b].get(0);
            float last  = chunkResult[b].get(chunkSize - 1);

            if (Math.abs(expected - first) > tolerance || Math.abs(expected - last) > tolerance) {
                System.out.printf("  FAIL batch %d : expected=%.6f  first=%.6f  last=%.6f%n",
                        b, expected, first, last);
                passed = false;
            } else {
                System.out.printf("  PASS batch %d : result=%.6f%n", b, first);
            }
        }

        System.out.println();
        System.out.println(passed ? "RESULT: PASSED" : "RESULT: FAILED");
    }
}

