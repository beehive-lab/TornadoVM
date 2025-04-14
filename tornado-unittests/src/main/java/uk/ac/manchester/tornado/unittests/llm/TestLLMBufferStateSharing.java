package uk.ac.manchester.tornado.unittests.llm;

import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Test case that specifically reproduces the buffer sharing pattern in LLM forward pass
 * with persist/consume operations using State-like fields structure.
 *
 * Simplified version based on TestLLMBufferSharing.
 */
public class TestLLMBufferStateSharing extends TornadoTestBase {

    /**
     * Simplified State class structure that mimics the actual implementation
     */
    private static class StateFields {
        public final FloatArray wrapX;
        public final FloatArray wrapXb;
        public final FloatArray wrapQ;
        public final FloatArray wrapK;
        public final FloatArray wrapV;
        public final IntArray positionAndLayer;

        public StateFields(int dim) {
            this.wrapX = new FloatArray(dim);
            this.wrapXb = new FloatArray(dim);
            this.wrapQ = new FloatArray(dim);
            this.wrapK = new FloatArray(dim);
            this.wrapV = new FloatArray(dim);
            this.positionAndLayer = new IntArray(2);

            // Initialize with test values
            for (int i = 0; i < dim; i++) {
                wrapX.set(i, 0.01f * i);
            }
            positionAndLayer.set(0, 0); // position
            positionAndLayer.set(1, 0); // layer
        }
    }

    /**
     * Touch buffer to ensure it's properly accessed
     */
    public static void touchBuffer(FloatArray x) {
        // Actually access the buffer
        float dummy = x.get(0);
        if (dummy > Float.MAX_VALUE) {
            x.set(0, dummy);  // Will never execute but prevents optimization
        }
    }

    /**
     * Simplified RMS Norm calculation for testing
     */
    public static void simulateRMSNorm(FloatArray x, FloatArray xb, FloatArray weights, int dim) {
        // Simple operation to simulate RMSNorm without using complex kernels
        for (int i = 0; i < dim; i++) {
            xb.set(i, x.get(i) * weights.get(i % weights.getSize()));
        }
    }

    /**
     * Simplified matrix multiplication for testing
     */
    public static void matrixVectorSimple(FloatArray in, FloatArray out, FloatArray weights, int dim) {
        for (int i = 0; i < dim; i++) {
            float sum = 0.0f;
            for (int j = 0; j < dim; j++) {
                sum += in.get(j) * weights.get(i * dim + j);
            }
            out.set(i, sum);
        }
    }

    // Test configuration variables
    private int dim;
    private float rmsNormEps;
    private FloatArray weights;
    private StateFields state;

    @Before
    public void setUp() {
        // Configuration values that match your model setup
        dim = 1024; // Reduced dimension size for testing
        rmsNormEps = 1e-5f;

        // Print dimensions
        System.out.println("Model dimensions:");
        System.out.println("dim = " + dim);

        // Create weights
        weights = new FloatArray(dim);
        for (int i = 0; i < dim; i++) {
            weights.set(i, 1.0f);
        }

        // Create state with fields
        state = new StateFields(dim);
    }

    /**
     * Helper method to verify output
     */
    private boolean verifyOutput() {
        boolean hasOutput = false;
        System.out.println("Checking wrapXb values:");
        for (int i = 0; i < 10; i++) {
            System.out.println("wrapXb[" + i + "] = " + state.wrapXb.get(i));
            if (state.wrapXb.get(i) != 0.0f) {
                hasOutput = true;
            }
        }

        return hasOutput;
    }

    /**
     * Test the original implementation with separate task graphs and consume/persist pattern
     */
    @Test
    public void testOriginalImplementation() throws TornadoExecutionPlanException {
        System.out.println("\n=== TESTING ORIGINAL IMPLEMENTATION ===");

        // Task Graph 0: Buffer initialization - forces copying of wrapX
        TaskGraph lookUpBufferX = new TaskGraph("lookUpBufferX")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, state.wrapX, state.positionAndLayer)
                .task("forceUpdateXperToken", TestLLMBufferStateSharing::touchBuffer, state.wrapX)
                .persistOnDevice(state.wrapX, state.positionAndLayer);

        // Task Graph 1: RMS Norm with persist/consume pattern - simplified to avoid kernel context issues
        TaskGraph rmsNormGraph = new TaskGraph("rmsnorm")
                .consumeFromDevice(lookUpBufferX.getTaskGraphName(), state.wrapX)
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, state.positionAndLayer)
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, weights)
                .task("normalize", TestLLMBufferStateSharing::simulateRMSNorm,
                        state.wrapX, state.wrapXb, weights, dim)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, state.wrapXb)
                .persistOnDevice(state.wrapX, state.wrapXb, state.positionAndLayer);

        // Task Graph 2: QKV Matmuls to test further buffer sharing
        TaskGraph qkvGraph = new TaskGraph("qkv")
                .consumeFromDevice(rmsNormGraph.getTaskGraphName(), state.wrapX, state.wrapXb)
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, state.positionAndLayer)
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, weights)
                .task("qmatmul", TestLLMBufferStateSharing::matrixVectorSimple,
                        state.wrapXb, state.wrapQ, weights, dim)
                .task("kmatmul", TestLLMBufferStateSharing::matrixVectorSimple,
                        state.wrapXb, state.wrapK, weights, dim)
                .task("vmatmul", TestLLMBufferStateSharing::matrixVectorSimple,
                        state.wrapXb, state.wrapV, weights, dim)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, state.wrapQ, state.wrapK, state.wrapV)
                .persistOnDevice(state.wrapQ, state.wrapK, state.wrapV, state.positionAndLayer);

        try (TornadoExecutionPlan executionPlan =
                new TornadoExecutionPlan(
                        lookUpBufferX.snapshot(),
                        rmsNormGraph.snapshot(),
                        qkvGraph.snapshot())) {

            // Execute the first graph
            System.out.println("Executing first graph (lookUpBufferX)...");
            executionPlan.withGraph(0).execute();
            System.out.println("First graph executed successfully");

            // Execute the second graph
            System.out.println("Executing second graph (rmsNormGraph)...");
            executionPlan.withGraph(1).execute();
            System.out.println("Second graph executed successfully");

            // Execute the third graph
            System.out.println("Executing third graph (qkvGraph)...");
            executionPlan.withGraph(2).execute();
            System.out.println("Third graph executed successfully");

            // Verify output has expected values
            boolean hasOutput = verifyOutput();

            System.out.println("Checking wrapQ values:");
            for (int i = 0; i < 10; i++) {
                System.out.println("wrapQ[" + i + "] = " + state.wrapQ.get(i));
            }

            assertTrue("Output buffer should have non-zero values", hasOutput);
        }
    }

    /**
     * Test alternative implementation using touchBuffer instead of emptyTask
     */
    @Test
    public void testAlternative1_TouchBuffer() throws TornadoExecutionPlanException {
        System.out.println("\n=== TESTING ALTERNATIVE 1: TOUCH BUFFER ===");

        // Version 1: Using touchBuffer instead of emptyTask
        TaskGraph lookUpBufferX_alt1 = new TaskGraph("lookUpBufferX_alt1")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, state.wrapX, state.positionAndLayer)
                .task("touchBuffer", TestLLMBufferStateSharing::touchBuffer, state.wrapX)
                .persistOnDevice(state.wrapX, state.positionAndLayer);

        // Same RMS Norm graph as original, but consuming from the alternative lookUpBufferX
        TaskGraph rmsNormGraph = new TaskGraph("rmsnorm")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, state.positionAndLayer)
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, weights)
                .consumeFromDevice(lookUpBufferX_alt1.getTaskGraphName(), state.wrapX)
                .task("normalize", TestLLMBufferStateSharing::simulateRMSNorm,
                        state.wrapX, state.wrapXb, weights, dim)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, state.wrapXb)
                .persistOnDevice(state.wrapX, state.wrapXb, state.positionAndLayer);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(
                lookUpBufferX_alt1.snapshot(),
                rmsNormGraph.snapshot())) {

            executionPlan.withGraph(0).execute();
            System.out.println("First graph (touchBuffer) executed successfully");

            executionPlan.withGraph(1).execute();
            System.out.println("Second graph executed successfully");

            // Verify output has expected values
            boolean hasOutput = verifyOutput();
            assertTrue("Output buffer should have non-zero values", hasOutput);
            System.out.println("Alternative 1 succeeded!");
        }
    }

    /**
     * Test alternative implementation using explicit transfer instead of consume
     */
    @Test
    public void testAlternative2_ExplicitTransfer() throws TornadoExecutionPlanException {
        System.out.println("\n=== TESTING ALTERNATIVE 2: EXPLICIT TRANSFER ===");

        // Version 2: Using explicit transfer instead of consume
        TaskGraph rmsNormGraph_alt2 = new TaskGraph("rmsnorm_alt2")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, state.wrapX, state.positionAndLayer)
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, weights)
                .task("normalize", TestLLMBufferStateSharing::simulateRMSNorm,
                        state.wrapX, state.wrapXb, weights, dim)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, state.wrapXb)
                .persistOnDevice(state.wrapX, state.wrapXb, state.positionAndLayer);

        try (TornadoExecutionPlan executionPlan =
                new TornadoExecutionPlan(rmsNormGraph_alt2.snapshot())) {

            executionPlan.execute();

            // Verify output has expected values
            boolean hasOutput = verifyOutput();
            assertTrue("Output buffer should have non-zero values", hasOutput);
            System.out.println("Explicit transfer approach executed successfully");
            System.out.println("Alternative 2 succeeded!");
        }
    }

    /**
     * Test alternative implementation using a combined graph approach
     */
    @Test
    public void testAlternative3_CombinedGraph() throws TornadoExecutionPlanException {
        System.out.println("\n=== TESTING ALTERNATIVE 3: COMBINED GRAPH ===");

        // Version 3: Single task graph approach
        TaskGraph combinedGraph = new TaskGraph("combined")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION,
                        state.wrapX, state.positionAndLayer)
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, weights)
                .task("touchBuffer", TestLLMBufferStateSharing::touchBuffer, state.wrapX)
                .task("normalize", TestLLMBufferStateSharing::simulateRMSNorm,
                        state.wrapX, state.wrapXb, weights, dim)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, state.wrapXb);

        try (TornadoExecutionPlan executionPlan =
                new TornadoExecutionPlan(combinedGraph.snapshot())) {

            executionPlan.execute();

            // Verify output has expected values
            boolean hasOutput = verifyOutput();
            assertTrue("Output buffer should have non-zero values", hasOutput);
            System.out.println("Combined graph executed successfully");
            System.out.println("Alternative 3 succeeded!");
        }
    }

    /**
     * Test full pipeline with the touchBuffer implementation
     */
    @Test
    public void testFullPipelineTouchBuffer() throws TornadoExecutionPlanException {
        System.out.println("\n=== TESTING FULL PIPELINE WITH TOUCH BUFFER ===");

        // First graph with touchBuffer
        TaskGraph lookUpBufferX = new TaskGraph("lookUpBufferX")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, state.wrapX, state.positionAndLayer)
                .task("touchBuffer", TestLLMBufferStateSharing::touchBuffer, state.wrapX)
                .persistOnDevice(state.wrapX, state.positionAndLayer);

        // RMS Norm graph
        TaskGraph rmsNormGraph = new TaskGraph("rmsnorm")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, state.positionAndLayer)
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, weights)
                .consumeFromDevice(lookUpBufferX.getTaskGraphName(), state.wrapX)
                .task("normalize", TestLLMBufferStateSharing::simulateRMSNorm,
                        state.wrapX, state.wrapXb, weights, dim)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, state.wrapXb)
                .persistOnDevice(state.wrapX, state.wrapXb, state.positionAndLayer);

        // QKV Matmuls graph
        TaskGraph qkvGraph = new TaskGraph("qkv")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, state.positionAndLayer)
                .consumeFromDevice(rmsNormGraph.getTaskGraphName(), state.wrapX, state.wrapXb)
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, weights)
                .task("qmatmul", TestLLMBufferStateSharing::matrixVectorSimple,
                        state.wrapXb, state.wrapQ, weights, dim)
                .task("kmatmul", TestLLMBufferStateSharing::matrixVectorSimple,
                        state.wrapXb, state.wrapK, weights, dim)
                .task("vmatmul", TestLLMBufferStateSharing::matrixVectorSimple,
                        state.wrapXb, state.wrapV, weights, dim)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, state.wrapQ, state.wrapK, state.wrapV)
                .persistOnDevice(state.wrapQ, state.wrapK, state.wrapV, state.positionAndLayer);

        try (TornadoExecutionPlan executionPlan =
                new TornadoExecutionPlan(
                        lookUpBufferX.snapshot(),
                        rmsNormGraph.snapshot(),
                        qkvGraph.snapshot())) {

            // Execute the first graph
            System.out.println("Executing first graph (lookUpBufferX)...");
            executionPlan.withGraph(0).execute();
            System.out.println("First graph executed successfully");

            // Execute the second graph
            System.out.println("Executing second graph (rmsNormGraph)...");
            executionPlan.withGraph(1).execute();
            System.out.println("Second graph executed successfully");

            // Execute the third graph
            System.out.println("Executing third graph (qkvGraph)...");
            executionPlan.withGraph(2).execute();
            System.out.println("Third graph executed successfully");

            // Verify output has expected values
            boolean hasOutput = verifyOutput();

            System.out.println("Checking QKV values:");
            for (int i = 0; i < 5; i++) {
                System.out.println("wrapQ[" + i + "] = " + state.wrapQ.get(i));
                System.out.println("wrapK[" + i + "] = " + state.wrapK.get(i));
                System.out.println("wrapV[" + i + "] = " + state.wrapV.get(i));
            }

            assertTrue("Output buffer should have non-zero values", hasOutput);
            System.out.println("Full pipeline with TouchBuffer succeeded!");
        }
    }
}