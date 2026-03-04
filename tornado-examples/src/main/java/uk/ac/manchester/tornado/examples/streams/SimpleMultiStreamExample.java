package uk.ac.manchester.tornado.examples.streams;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;

/**
 * Test designed to produce observable H2D / compute overlap when running with
 * the PTX multi-stream backend.
 *
 * <p>Expected GPU timeline (visible in Nsight Systems):
 * <pre>
 *   H2D stream:  [H2D(inputA0)][H2D(inputB0)][H2D(inputA1)][H2D(inputB1)]
 *   COMPUTE:                    [----kernel0----][----kernel1----]
 *                                         ^^^^^^^^^^^^^^^^^^^
 *                               H2D(inputA1) + H2D(inputB1) overlap with kernel0
 * </pre>
 *
 * <p>How to run with multi-stream enabled:
 * <pre>
 *   tornado \
 *     --jvm="-Dtornado.ptx.multistream=true -Dtornado.vm.deps=true" \
 *     -m tornado.examples/uk.ac.manchester.tornado.examples.streams.SimpleMultiStreamExample
 * </pre>
 */
public class SimpleMultiStreamExample {
    public static final int WARMUP = 100;
    public static final int ITERATIONS = 100;
    /** Number of FMA iterations per element to artificially raise arithmetic intensity. */
    private static final int COMPUTE_ITERATIONS = 512;
    /**
     * 16M floats = 64 MB per array. Large enough to make each H2D transfer and
     * each kernel execution individually visible as distinct bars in Nsight.
     */
    private static final int N = 32_000_000;//16_000_000;

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

    // Validation mirrors the GPU loop exactly
    private static float expectedResult(float x, float y, float alpha) {
        float val = alpha * x + y;
        for (int j = 0; j < COMPUTE_ITERATIONS; j++) {
            val = val * alpha + y;
        }
        return val;
    }

    /**
     * Two-task TaskGraph designed to expose H2D / compute overlap.
     *
     * <p>The TaskGraph interleaves transfers and tasks deliberately:
     * <ol>
     *   <li>H2D(inputA0, inputB0) — fills H2D stream</li>
     *   <li>LAUNCH kernel0 — COMPUTE stream waits on events from step 1, then starts</li>
     *   <li>H2D(inputA1, inputB1) — submitted to H2D stream while kernel0 is executing</li>
     *   <li>LAUNCH kernel1 — COMPUTE stream waits on events from step 3</li>
     * </ol>
     *
     * <p>Steps 3 and 2 run concurrently on separate streams — this is the overlap.
     */
    public static void main(String[] args) throws TornadoExecutionPlanException {
        // only for ptx backend
        FloatArray inputA0 = new FloatArray(N);
        FloatArray inputB0 = new FloatArray(N);
        FloatArray result0 = new FloatArray(N);

        FloatArray inputA1 = new FloatArray(N);
        FloatArray inputB1 = new FloatArray(N);
        FloatArray result1 = new FloatArray(N);

        inputA0.init(1.0f);
        inputB0.init(2.0f);
        inputA1.init(3.0f);
        inputB1.init(4.0f);

        final float alpha = 0.5f;

        // The interleaved ordering is intentional: transferToDevice for task1's inputs
        // appears AFTER task0's launch so that the interpreter submits H2D(A1/B1) to
        // the H2D stream while kernel0 is already executing on the COMPUTE stream.
        TaskGraph taskGraph = new TaskGraph("overlap")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, inputA0, inputB0)
                .task("t0", SimpleMultiStreamExample::computeKernel, inputA0, inputB0, result0, alpha)
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, inputA1, inputB1)
                .task("t1", SimpleMultiStreamExample::computeKernel, inputA1, inputB1, result1, alpha)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result0, result1);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        TornadoDevice device = TornadoExecutionPlan.getDevice(0, 0);
        executionPlan.withDevice(device).withPreCompilation();

        for (int i = 0; i < WARMUP; i++) {
            executionPlan.execute();
        }

        for (int i = 0; i < ITERATIONS; i++) {
            executionPlan.execute();
        }

        // validation
        for (int i = 0; i < N; i++) {
            float expected = expectedResult(inputA0.get(i), inputB0.get(i), alpha);
            if (result0.get(i) != expected) {
                System.out.println("[Error] result in position [" + i + "] should be = " + expected + ", but it's = " + result0.get(i));
                break;
            }
        }
    }
}
