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
// * Test for the Feed-Forward Network layer using a single unified task graph.
// */
//public class TestUnifiedFFNLayer extends TornadoTestBase {
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
//     * RMSNorm implementation
//     */
//    public static void rmsNorm(FloatArray x, FloatArray weights, FloatArray output, float eps) {
//        // Calculate sum of squares
//        float sumSquares = 0.0f;
//        for (int i = 0; i < x.getSize(); i++) {
//            float val = x.get(i);
//            sumSquares += val * val;
//        }
//
//        // Calculate scaling factor
//        float mean = sumSquares / x.getSize();
//        float scale = 1.0f / TornadoMath.sqrt(mean + eps);
//
//        // Apply normalization
//        for (int i = 0; i < x.getSize(); i++)
//            output.set(i, weights.get(i) * scale * x.get(i));
//        }
//    }
//
//    /**
//     * Sequential implementation of the FFN layer for reference
//     */
//    public static void ffnLayerSequential(FloatArray x, FloatArray wo, FloatArray w1, FloatArray w2, FloatArray w3,
//            FloatArray rmsWeight, float rmsEps, int dim, int hiddenDim) {
//        // Create temporary buffers
//        FloatArray temp = new FloatArray(dim);
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
//    /**
//     * Test using a single unified task graph for the entire FFN layer.
//     */
//    @Test
//    public void testUnifiedFFNLayer() throws TornadoExecutionPlanException {
//        // Configuration parameters - small sizes for stability
//        final int dim = 16;
//        final int hiddenDim = 32;
//        final float rmsNormEps = 1e-5f;
//
//        System.out.println("Creating arrays with dim=" + dim + ", hiddenDim=" + hiddenDim);
//
//        // Input and output arrays
//        FloatArray x = new FloatArray(dim);
//
//        // Weights
//        FloatArray wo = new FloatArray(dim * dim);
//        FloatArray w1 = new FloatArray(hiddenDim * dim);
//        FloatArray w2 = new FloatArray(dim * hiddenDim);
//        FloatArray w3 = new FloatArray(hiddenDim * dim);
//        FloatArray rmsWeight = new FloatArray(dim);
//
//        // Intermediate buffers
//        FloatArray temp = new FloatArray(dim);
//        FloatArray hidden = new FloatArray(hiddenDim);
//        FloatArray hidden2 = new FloatArray(hiddenDim);
//
//        // Initialize with random data
//        Random random = new Random(42);
//        for (int i = 0; i < dim; i++) {
//            x.set(i, random.nextFloat() * 2 - 1);
//            rmsWeight.set(i, random.nextFloat() + 0.5f);
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
//        // Initialize intermediate buffers to zero
//        for (int i = 0; i < dim; i++) {
//            temp.set(i, 0.0f);
//        }
//
//        for (int i = 0; i < hiddenDim; i++) {
//            hidden.set(i, 0.0f);
//            hidden2.set(i, 0.0f);
//        }
//
//        // Create copy for sequential reference
//        FloatArray xSeq = new FloatArray(dim);
//        for (int i = 0; i < dim; i++) {
//            xSeq.set(i, x.get(i));
//        }
//
//        // Run sequential reference implementation
//        System.out.println("Running sequential reference implementation...");
//        ffnLayerSequential(xSeq, wo, w1, w2, w3, rmsWeight, rmsNormEps, dim, hiddenDim);
//
//        // Create unified task graph with all operations
//        System.out.println("Setting up unified task graph...");
//        TaskGraph taskGraph = new TaskGraph("ffn-layer")
//                // Transfer all arrays to device
//                .transferToDevice(DataTransferMode.FIRST_EXECUTION,
//                        x, temp, hidden, hidden2,
//                        wo, w1, w2, w3, rmsWeight)
//
//                // Step 1: Matrix multiplication and residual
//                .task("matmul1", TestUnifiedFFNLayer::matrixVectorSimple, x, temp, wo, dim, dim)
//                .task("residual1", TestUnifiedFFNLayer::addInPlace, temp, x)
//
//                // Step 2: RMSNorm
//                .task("rmsnorm", TestUnifiedFFNLayer::rmsNorm, x, rmsWeight, temp, rmsNormEps)
//
//                // Step 3: Parallel projections
//                .task("projection1", TestUnifiedFFNLayer::matrixVectorSimple, temp, hidden, w1, dim, hiddenDim)
//                .task("projection3", TestUnifiedFFNLayer::matrixVectorSimple, temp, hidden2, w3, dim, hiddenDim)
//
//                // Step 4: SiLU activation and element-wise multiply
//                .task("silu", TestUnifiedFFNLayer::mapInPlace, hidden)
//                .task("multiply", TestUnifiedFFNLayer::multiplyInPlace, hidden2, hidden)
//
//                // Step 5: Final projection and residual
//                .task("projection2", TestUnifiedFFNLayer::matrixVectorSimple, hidden, temp, w2, hiddenDim, dim)
//                .task("residual2", TestUnifiedFFNLayer::addInPlace, temp, x)
//
//                // Transfer output back to host
//                .transferToHost(DataTransferMode.EVERY_EXECUTION, x);
//
//        // Create execution plan
//        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
//
//        System.out.println("Executing unified FFN layer...");
//        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
//            executionPlan.execute();
//            System.out.println("Execution completed successfully");
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
//            System.out.printf("Index %2d: Expected=%.7f, Actual=%.7f, RelError=%.7f%n",
//                    i, expected, actual, relError);
//
//            if (relError > 1e-3f) {
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
//
//    /**
//     * Test with task-specific worker grids
//     */
//    @Test
//    public void testUnifiedFFNLayerWithTaskGrids() throws TornadoExecutionPlanException {
//        // Configuration parameters
//        final int dim = 16;
//        final int hiddenDim = 32;
//        final float rmsNormEps = 1e-5f;
//        final int localSize = 8;
//
//        System.out.println("Creating arrays with dim=" + dim + ", hiddenDim=" + hiddenDim);
//
//        // Input and output arrays
//        FloatArray x = new FloatArray(dim);
//
//        // Weights
//        FloatArray wo = new FloatArray(dim * dim);
//        FloatArray w1 = new FloatArray(hiddenDim * dim);
//        FloatArray w2 = new FloatArray(dim * hiddenDim);
//        FloatArray w3 = new FloatArray(hiddenDim * dim);
//        FloatArray rmsWeight = new FloatArray(dim);
//
//        // Intermediate buffers
//        FloatArray temp = new FloatArray(dim);
//        FloatArray hidden = new FloatArray(hiddenDim);
//        FloatArray hidden2 = new FloatArray(hiddenDim);
//
//        // Initialize with random data
//        Random random = new Random(42);
//        for (int i = 0; i < dim; i++) {
//            x.set(i, random.nextFloat() * 2 - 1);
//            rmsWeight.set(i, random.nextFloat() + 0.5f);
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
//        // Initialize intermediate buffers to zero
//        for (int i = 0; i < dim; i++) {
//            temp.set(i, 0.0f);
//        }
//
//        for (int i = 0; i < hiddenDim; i++) {
//            hidden.set(i, 0.0f);
//            hidden2.set(i, 0.0f);
//        }
//
//        // Create copy for sequential reference
//        FloatArray xSeq = new FloatArray(dim);
//        for (int i = 0; i < dim; i++) {
//            xSeq.set(i, x.get(i));
//        }
//
//        // Run sequential reference implementation
//        System.out.println("Running sequential reference implementation...");
//        ffnLayerSequential(xSeq, wo, w1, w2, w3, rmsWeight, rmsNormEps, dim, hiddenDim);
//
//        // Create unified task graph with all operations
//        System.out.println("Setting up unified task graph with worker grids...");
//        TaskGraph taskGraph = new TaskGraph("ffn-layer")
//                // Transfer all arrays to device
//                .transferToDevice(DataTransferMode.FIRST_EXECUTION,
//                        x, temp, hidden, hidden2,
//                        wo, w1, w2, w3, rmsWeight)
//
//                // Step 1: Matrix multiplication and residual
//                .task("matmul1", TestUnifiedFFNLayer::matrixVectorSimple, x, temp, wo, dim, dim)
//                .task("residual1", TestUnifiedFFNLayer::addInPlace, temp, x)
//
//                // Step 2: RMSNorm
//                .task("rmsnorm", TestUnifiedFFNLayer::rmsNorm, x, rmsWeight, temp, rmsNormEps)
//
//                // Step 3: Parallel projections
//                .task("projection1", TestUnifiedFFNLayer::matrixVectorSimple, temp, hidden, w1, dim, hiddenDim)
//                .task("projection3", TestUnifiedFFNLayer::matrixVectorSimple, temp, hidden2, w3, dim, hiddenDim)
//
//                // Step 4: SiLU activation and element-wise multiply
//                .task("silu", TestUnifiedFFNLayer::mapInPlace, hidden)
//                .task("multiply", TestUnifiedFFNLayer::multiplyInPlace, hidden2, hidden)
//
//                // Step 5: Final projection and residual
//                .task("projection2", TestUnifiedFFNLayer::matrixVectorSimple, hidden, temp, w2, hiddenDim, dim)
//                .task("residual2", TestUnifiedFFNLayer::addInPlace, temp, x)
//
//                // Transfer output back to host
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
//        // Set up grid scheduler for task-specific grids
//        GridScheduler gridScheduler = new GridScheduler();
//
//        // Assign dim-sized worker grid to dim-sized tasks
//        gridScheduler.setWorkerGrid("ffn-layer.matmul1", dimWorker);
//        gridScheduler.setWorkerGrid("ffn-layer.residual1", dimWorker);
//        gridScheduler.setWorkerGrid("ffn-layer.rmsnorm", dimWorker);
//        gridScheduler.setWorkerGrid("ffn-layer.projection2", dimWorker);
//        gridScheduler.setWorkerGrid("ffn-layer.residual2", dimWorker);
//
//        // Assign hidden-sized worker grid to hidden-sized tasks
//        gridScheduler.setWorkerGrid("ffn-layer.projection1", hiddenWorker);
//        gridScheduler.setWorkerGrid("ffn-layer.projection3", hiddenWorker);
//        gridScheduler.setWorkerGrid("ffn-layer.silu", hiddenWorker);
//        gridScheduler.setWorkerGrid("ffn-layer.multiply", hiddenWorker);
//
//        // Create execution plan
//        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
//
//        System.out.println("Executing unified FFN layer with worker grids...");
//        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
//            executionPlan.withGridScheduler(gridScheduler).execute();
//            System.out.println("Execution with worker grids completed successfully");
//        } catch (Exception e) {
//            System.out.println("Exception during execution with worker grids: " + e.getMessage());
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
//            System.out.printf("Index %2d: Expected=%.7f, Actual=%.7f, RelError=%.7f%n",
//                    i, expected, actual, relError);
//
//            if (relError > 1e-3f) {
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