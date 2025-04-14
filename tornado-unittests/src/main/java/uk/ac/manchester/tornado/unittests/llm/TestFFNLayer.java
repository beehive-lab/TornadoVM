//package uk.ac.manchester.tornado.unittests.llm;
//
//import static org.junit.Assert.assertEquals;
//
//import java.util.Random;
//
//import org.junit.Test;
//
//import uk.ac.manchester.tornado.api.GridScheduler;
//import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
//import uk.ac.manchester.tornado.api.KernelContext;
//import uk.ac.manchester.tornado.api.TaskGraph;
//import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
//import uk.ac.manchester.tornado.api.WorkerGrid;
//import uk.ac.manchester.tornado.api.WorkerGrid1D;
//import uk.ac.manchester.tornado.api.enums.DataTransferMode;
//import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
//import uk.ac.manchester.tornado.api.math.TornadoMath;
//import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
//import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;
//
///**
// * Simplified test for the Feed-Forward Network (FFN) layer
// * that uses a more basic implementation with fewer steps
// * to better isolate and debug issues.
// */
//public class TestFFNLayer extends TornadoTestBase {
//
//    /**
//     * Matrix-vector multiplication with bounds checking
//     */
//    public static void matrixVectorSimple(FloatArray x, FloatArray xout, FloatArray w, int n, int d) {
//        for (int i = 0; i < d; i++) {
//            float val = 0f;
//            for (int j = 0; j < n; j++) {
//                // w is a d×n matrix in row-major order, so index as i*n + j
//                if (i * n + j < w.getSize() && j < x.getSize()) {
//                    val += w.get(i * n + j) * x.get(j);
//                }
//            }
//            if (i < xout.getSize()) {
//                xout.set(i, val);
//            }
//        }
//    }
//
//    /**
//     * In-place addition
//     */
//    public static void addInPlace(FloatArray input, FloatArray output) {
//        int size = Math.min(input.getSize(), output.getSize());
//        for (int i = 0; i < size; i++) {
//            output.set(i, output.get(i) + input.get(i));
//        }
//    }
//
//    /**
//     * SiLU activation: x * sigmoid(x)
//     */
//    public static void mapInPlace(FloatArray input) {
//        for (int i = 0; i < input.getSize(); i++) {
//            float value = input.get(i);
//            float sigmoid = 1.0f / (1.0f + TornadoMath.exp(-value));
//            input.set(i, value * sigmoid);
//        }
//    }
//
//    /**
//     * Element-wise multiplication
//     */
//    public static void multiplyInPlace(FloatArray input, FloatArray output) {
//        int size = Math.min(input.getSize(), output.getSize());
//        for (int i = 0; i < size; i++) {
//            output.set(i, output.get(i) * input.get(i));
//        }
//    }
//
//    /**
//     * Simple RMSNorm implementation
//     */
//    public static void rmsnorm(KernelContext context, FloatArray x, FloatArray weights, FloatArray output, float eps, int localWork) {
//        int idx = context.globalIdx;
//
//        if (idx >= x.getSize()) return;
//
//        // All threads cooperate to compute sum of squares
//        float[] localSums = context.allocateFloatLocalArray(localWork);
//        float val = x.get(idx);
//        localSums[context.localIdx] = val * val;
//
//        // Parallel reduction to compute sum
//        for (int stride = context.localGroupSizeX / 2; stride > 0; stride /= 2) {
//            context.localBarrier();
//            if (context.localIdx < stride) {
//                localSums[context.localIdx] += localSums[context.localIdx + stride];
//            }
//        }
//
//        // Only one thread per work-group computes final value
//        context.localBarrier();
//        float scale = 0.0f;
//        if (context.localIdx == 0) {
//            float mean = localSums[0] / x.getSize();
//            scale = 1.0f / TornadoMath.sqrt(mean + eps);
//        }
//
//        // Broadcast scale to all threads
//        context.localBarrier();
//
//        // All threads apply normalization
//        output.set(idx, weights.get(idx) * scale * val);
//    }
//
//    /**
//     * Sequential implementation of the FFN layer for testing
//     */
//    public static void ffnLayerSequential(FloatArray x, FloatArray wo, FloatArray w1, FloatArray w2, FloatArray w3,
//            FloatArray rmsWeight, float rmsEps, int dim, int hiddenDim) {
//        // Create temporary buffers
//        FloatArray temp = new FloatArray(dim);
//        FloatArray temp2 = new FloatArray(dim);
//        FloatArray hidden = new FloatArray(hiddenDim);
//        FloatArray hidden2 = new FloatArray(hiddenDim);
//
//        // 1. Apply projection from attention output
//        matrixVectorSimple(x, temp, wo, dim, dim);
//
//        // 2. Residual connection
//        for (int i = 0; i < dim; i++) {
//            x.set(i, x.get(i) + temp.get(i));
//        }
//
//        // 3. Apply RMSNorm
//        float ss = 0.0f;
//        for (int i = 0; i < dim; i++) {
//            ss += x.get(i) * x.get(i);
//        }
//        ss /= dim;
//        ss += rmsEps;
//        ss = 1.0f / (float) Math.sqrt(ss);
//
//        for (int i = 0; i < dim; i++) {
//            temp.set(i, rmsWeight.get(i) * (ss * x.get(i)));
//        }
//
//        // 4. Apply first projections (w1 and w3)
//        matrixVectorSimple(temp, hidden, w1, dim, hiddenDim);
//        matrixVectorSimple(temp, hidden2, w3, dim, hiddenDim);
//
//        // 5. Apply SwiGLU activation
//        for (int i = 0; i < hiddenDim; i++) {
//            float val = hidden.get(i);
//            float sigmoid = 1.0f / (1.0f + (float) Math.exp(-val));
//            hidden.set(i, val * sigmoid);
//        }
//
//        // 6. Element-wise multiply
//        for (int i = 0; i < hiddenDim; i++) {
//            hidden.set(i, hidden.get(i) * hidden2.get(i));
//        }
//
//        // 7. Final projection
//        matrixVectorSimple(hidden, temp, w2, hiddenDim, dim);
//
//        // 8. Final residual
//        for (int i = 0; i < dim; i++) {
//            x.set(i, x.get(i) + temp.get(i));
//        }
//    }
//
//    @Test
//    public void testSimplifiedFFNLayer() throws TornadoExecutionPlanException {
//        // Configuration parameters - use smaller sizes for debugging
//        final int dim = 16;
//        final int hiddenDim = 32;
//        final float rmsNormEps = 1e-5f;
//        final int localSize = 8;
//
//        System.out.println("Creating arrays with dim=" + dim + ", hiddenDim=" + hiddenDim);
//
//        // Create arrays with known sizes
//        FloatArray x = new FloatArray(dim);
//        FloatArray wo = new FloatArray(dim * dim);
//        FloatArray w1 = new FloatArray(hiddenDim * dim);
//        FloatArray w2 = new FloatArray(dim * hiddenDim);
//        FloatArray w3 = new FloatArray(hiddenDim * dim);
//        FloatArray rmsWeight = new FloatArray(dim);
//
//        // Buffers for parallel implementation
//        FloatArray temp = new FloatArray(dim);
//        FloatArray hidden = new FloatArray(hiddenDim);
//        FloatArray hidden2 = new FloatArray(hiddenDim);
//
//        // Initialize with random data
//        Random random = new Random(42);
//        for (int i = 0; i < dim; i++) {
//            x.set(i, random.nextFloat() * 2 - 1);
//            rmsWeight.set(i, random.nextFloat() + 0.5f); // Weight values around 1
//
//            for (int j = 0; j < dim; j++) {
//                wo.set(i * dim + j, (float) ((random.nextFloat() * 0.4f - 0.2f) / Math.sqrt(dim)));
//            }
//
//            for (int j = 0; j < hiddenDim; j++) {
//                w2.set(i * hiddenDim + j, (float) ((random.nextFloat() * 0.4f - 0.2f) / Math.sqrt(hiddenDim)));
//            }
//        }
//
//        for (int i = 0; i < hiddenDim; i++) {
//            for (int j = 0; j < dim; j++) {
//                w1.set(i * dim + j, (float) ((random.nextFloat() * 0.4f - 0.2f) / Math.sqrt(dim)));
//                w3.set(i * dim + j, (float) ((random.nextFloat() * 0.4f - 0.2f) / Math.sqrt(dim)));
//            }
//        }
//
//        // Create a copy for sequential execution
//        FloatArray xSeq = new FloatArray(dim);
//        for (int i = 0; i < dim; i++) {
//            xSeq.set(i, x.get(i));
//        }
//
//        // Run sequential implementation
//        System.out.println("Running sequential implementation...");
//        ffnLayerSequential(xSeq, wo, w1, w2, w3, rmsWeight, rmsNormEps, dim, hiddenDim);
//        System.out.println("Sequential implementation completed");
//
//        // Create context for parallel implementation
//        KernelContext context = new KernelContext();
//
//        System.out.println("Setting up parallel implementation...");
//
//        // Create a simplified FFN pipeline with fewer stages
//        // Step 1: Matrix multiplication with wo and residual
//        TaskGraph tg1 = new TaskGraph("ffn-1")
//                .transferToDevice(DataTransferMode.FIRST_EXECUTION, x, wo)
//                .task("matmul", TestFFNLayer::matrixVectorSimple, x, temp, wo, dim, dim)
//                .task("residual", TestFFNLayer::addInPlace, temp, x)
//                .persistOnDevice(x, temp);
//
//        // Step 2: RMSNorm (simple implementation)
//        TaskGraph tg2 = new TaskGraph("ffn-2")
//                .consumeFromDevice(tg1.getTaskGraphName(), x)
//                .transferToDevice(DataTransferMode.FIRST_EXECUTION, rmsWeight)
//                .task("rmsnorm", TestFFNLayer::rmsnorm, context, x, rmsWeight, temp, rmsNormEps, localSize)
//                .persistOnDevice(temp);
//
//        // Step 3: First projections (w1 and w3)
//        TaskGraph tg3 = new TaskGraph("ffn-3")
//                .consumeFromDevice(tg2.getTaskGraphName(), temp)
//                .transferToDevice(DataTransferMode.FIRST_EXECUTION, w1, w3)
//                .task("projection1", TestFFNLayer::matrixVectorSimple, temp, hidden, w1, dim, hiddenDim)
//                .task("projection3", TestFFNLayer::matrixVectorSimple, temp, hidden2, w3, dim, hiddenDim)
//                .persistOnDevice(hidden, hidden2);
//
//        // Step 4: SwiGLU activation
//        TaskGraph tg4 = new TaskGraph("ffn-4")
//                .consumeFromDevice(tg3.getTaskGraphName(), hidden, hidden2)
//                .task("silu", TestFFNLayer::mapInPlace, hidden)
//                .task("multiply", TestFFNLayer::multiplyInPlace, hidden2, hidden)
//                .persistOnDevice(hidden);
//
//        // Step 5: Final projection and residual
//        TaskGraph tg5 = new TaskGraph("ffn-5")
//                .consumeFromDevice(tg4.getTaskGraphName(), hidden)
//                .consumeFromDevice(tg1.getTaskGraphName(), x)
//                .transferToDevice(DataTransferMode.FIRST_EXECUTION, w2)
//                .task("projection2", TestFFNLayer::matrixVectorSimple, hidden, temp, w2, hiddenDim, dim)
//                .task("final-residual", TestFFNLayer::addInPlace, temp, x)
//                .transferToHost(DataTransferMode.EVERY_EXECUTION, x);
//
//        // Create worker grids
//        WorkerGrid dimWorker = new WorkerGrid1D(dim);
//        dimWorker.setGlobalWork(dim, 1, 1);
//        dimWorker.setLocalWork(localSize, 1, 1);
//
//        WorkerGrid hiddenWorker = new WorkerGrid1D(hiddenDim);
//        hiddenWorker.setGlobalWork(hiddenDim, 1, 1);
//        hiddenWorker.setLocalWork(localSize, 1, 1);
//
//        // Create grid schedulers
//        GridScheduler gs1 = new GridScheduler();
//        gs1.setWorkerGrid("ffn-1.matmul", dimWorker);
//        gs1.setWorkerGrid("ffn-1.residual", dimWorker);
//
//        GridScheduler gs2 = new GridScheduler();
//        gs2.setWorkerGrid("ffn-2.rmsnorm", dimWorker);
//
//        GridScheduler gs3 = new GridScheduler();
//        gs3.setWorkerGrid("ffn-3.projection1", hiddenWorker);
//        gs3.setWorkerGrid("ffn-3.projection3", hiddenWorker);
//
//        GridScheduler gs4 = new GridScheduler();
//        gs4.setWorkerGrid("ffn-4.silu", hiddenWorker);
//        gs4.setWorkerGrid("ffn-4.multiply", hiddenWorker);
//
//        GridScheduler gs5 = new GridScheduler();
//        gs5.setWorkerGrid("ffn-5.projection2", dimWorker);
//        gs5.setWorkerGrid("ffn-5.final-residual", dimWorker);
//
//        // Create immutable task graphs
//        ImmutableTaskGraph itg1 = tg1.snapshot();
//        ImmutableTaskGraph itg2 = tg2.snapshot();
//        ImmutableTaskGraph itg3 = tg3.snapshot();
//        ImmutableTaskGraph itg4 = tg4.snapshot();
//        ImmutableTaskGraph itg5 = tg5.snapshot();
//
//        // Execute the pipeline
//        System.out.println("Executing parallel implementation...");
//        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(itg1, itg2, itg3, itg4, itg5)) {
//            executionPlan.withGraph(0).withGridScheduler(gs1).execute();
//            System.out.println("Task 1 (matmul + residual) completed");
//
//            executionPlan.withGraph(1).withGridScheduler(gs2).execute();
//            System.out.println("Task 2 (rmsnorm) completed");
//
//            executionPlan.withGraph(2).withGridScheduler(gs3).execute();
//            System.out.println("Task 3 (first projections) completed");
//
//            executionPlan.withGraph(3).withGridScheduler(gs4).execute();
//            System.out.println("Task 4 (SwiGLU) completed");
//
//            executionPlan.withGraph(4).withGridScheduler(gs5).execute();
//            System.out.println("Task 5 (final projection + residual) completed");
//        } catch (Exception e) {
//            System.out.println("Exception during execution: " + e.getMessage());
//            e.printStackTrace();
//        }
//
//        // Verify results
//        System.out.println("Comparing results...");
//        boolean match = true;
//        for (int i = 0; i < dim; i++) {
//            float expected = xSeq.get(i);
//            float actual = x.get(i);
//            float relError = Math.abs(expected - actual) / (Math.abs(expected) + 1e-10f);
//
//            if (relError > 1e-3f) {
//                System.out.printf("Mismatch at index %d: expected=%.8f, actual=%.8f, rel_error=%.8f%n",
//                        i, expected, actual, relError);
//                match = false;
//            }
//        }
//
//        if (match) {
//            System.out.println("All values match within tolerance!");
//        } else {
//            System.out.println("Some values don't match within tolerance!");
//        }
//
//        // Final JUnit assertion
//        for (int i = 0; i < dim; i++) {
//            float expected = xSeq.get(i);
//            float actual = x.get(i);
//            float tolerance = Math.max(1e-5f, Math.abs(expected) * 1e-3f);
//
//            assertEquals("Output mismatch at index " + i, expected, actual, tolerance);
//        }
//    }
//}