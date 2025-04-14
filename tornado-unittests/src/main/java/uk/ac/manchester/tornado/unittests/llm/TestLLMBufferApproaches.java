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
 * Combined test case that compares different approaches to buffer management:
 * 1. Direct arrays as fields
 * 2. StateFields encapsulation
 * 3. Method-scope variables
 */
public class TestLLMBufferApproaches extends TornadoTestBase {

    /**
     * StateFields class that encapsulates arrays for testing
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
        // Simple operation to avoid index issues
        for (int i = 0; i < dim; i++) {
            float sum = 0.0f;
            for (int j = 0; j < Math.min(dim, in.getSize()); j++) {
                sum += in.get(j);
            }
            out.set(i, sum * 0.01f * (i % weights.getSize()));
        }
    }

    // Test configuration variables
    private int dim;

    // APPROACH 1: Direct arrays as fields
    private FloatArray directX;
    private FloatArray directXb;
    private FloatArray directQ;
    private FloatArray directK;
    private FloatArray directV;
    private IntArray directPositionAndLayer;

    // APPROACH 2: StateFields encapsulation
    private StateFields state;

    // Shared weights for both approaches
    private FloatArray weights;

    @Before
    public void setUp() {
        // Configuration values
        dim = 512; // Reduced dimension for faster testing

        System.out.println("Model dimensions:");
        System.out.println("dim = " + dim);

        // Initialize APPROACH 1: Direct arrays as fields
        directX = new FloatArray(dim);
        directXb = new FloatArray(dim);
        directQ = new FloatArray(dim);
        directK = new FloatArray(dim);
        directV = new FloatArray(dim);
        directPositionAndLayer = new IntArray(2);

        // Initialize test values for direct arrays
        for (int i = 0; i < dim; i++) {
            directX.set(i, 0.01f * i);
        }
        directPositionAndLayer.set(0, 0);
        directPositionAndLayer.set(1, 0);

        // Initialize APPROACH 2: StateFields encapsulation
        state = new StateFields(dim);

        // Shared weights
        weights = new FloatArray(dim);
        for (int i = 0; i < dim; i++) {
            weights.set(i, 1.0f);
        }
    }

    /**
     * Helper method to verify output for direct arrays
     */
    private boolean verifyDirectOutput() {
        boolean hasOutput = false;
        System.out.println("Checking directXb values:");
        for (int i = 0; i < 10; i++) {
            System.out.println("directXb[" + i + "] = " + directXb.get(i));
            if (directXb.get(i) != 0.0f) {
                hasOutput = true;
            }
        }
        return hasOutput;
    }

    /**
     * Helper method to verify output for StateFields
     */
    private boolean verifyStateOutput() {
        boolean hasOutput = false;
        System.out.println("Checking state.wrapXb values:");
        for (int i = 0; i < 10; i++) {
            System.out.println("state.wrapXb[" + i + "] = " + state.wrapXb.get(i));
            if (state.wrapXb.get(i) != 0.0f) {
                hasOutput = true;
            }
        }
        return hasOutput;
    }

    /**
     * Helper method to verify output for method-scoped arrays
     */
    private boolean verifyOutput(FloatArray xb) {
        boolean hasOutput = false;
        System.out.println("Checking xb values:");
        for (int i = 0; i < 10; i++) {
            System.out.println("xb[" + i + "] = " + xb.get(i));
            if (xb.get(i) != 0.0f) {
                hasOutput = true;
            }
        }
        return hasOutput;
    }

    //============================================================
    // APPROACH 1: Tests using direct arrays as class fields
    //============================================================

    /**
     * Test APPROACH 1: Direct arrays with original persist/consume pattern
     */
    @Test
    public void testDirectArraysOriginalImplementation() throws TornadoExecutionPlanException {
        System.out.println("\n=== TESTING DIRECT ARRAYS ORIGINAL IMPLEMENTATION ===");

        // Task Graph 0: Buffer initialization
        TaskGraph lookUpBufferX = new TaskGraph("lookUpBufferX")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, directX, directPositionAndLayer)
                .task("forceUpdateXperToken", TestLLMBufferApproaches::touchBuffer, directX)
                .persistOnDevice(directX, directPositionAndLayer);

        // Task Graph 1: RMS Norm
        TaskGraph rmsNormGraph = new TaskGraph("rmsnorm")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, directPositionAndLayer)
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, weights)
                .consumeFromDevice(lookUpBufferX.getTaskGraphName(), directX)
                .task("normalize", TestLLMBufferApproaches::simulateRMSNorm, directX, directXb, weights, dim)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, directXb)
                .persistOnDevice(directX, directXb, directPositionAndLayer);

        // Task Graph 2: QKV Matmuls
        TaskGraph qkvGraph = new TaskGraph("qkv")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, directXb, directPositionAndLayer)
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, weights)
                .task("qmatmul", TestLLMBufferApproaches::matrixVectorSimple, directXb, directQ, weights, dim)
                .task("kmatmul", TestLLMBufferApproaches::matrixVectorSimple, directXb, directK, weights, dim)
                .task("vmatmul", TestLLMBufferApproaches::matrixVectorSimple, directXb, directV, weights, dim)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, directQ, directK, directV);

        // Execute with withGraph() to avoid TornadoVM issue with execute()
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(
                lookUpBufferX.snapshot(),
                rmsNormGraph.snapshot(),
                qkvGraph.snapshot())) {

            System.out.println("Executing first graph (lookUpBufferX)...");
            executionPlan.withGraph(0).execute();
            System.out.println("First graph executed successfully");

            System.out.println("Executing second graph (rmsNormGraph)...");
            executionPlan.withGraph(1).execute();
            System.out.println("Second graph executed successfully");

            System.out.println("Executing third graph (qkvGraph)...");
            executionPlan.withGraph(2).execute();
            System.out.println("Third graph executed successfully");
        } catch (Exception e) {
            System.out.println("Error with direct arrays implementation: " + e.getMessage());
            e.printStackTrace();
        }

        // Verify output after RMS Norm
        boolean hasOutput = verifyDirectOutput();
        assertTrue("Output buffer should have non-zero values after RMS Norm", hasOutput);

        // Verify Q, K, V values
        System.out.println("Checking QKV values:");
        for (int i = 0; i < 5; i++) {
            System.out.println("directQ[" + i + "] = " + directQ.get(i));
            System.out.println("directK[" + i + "] = " + directK.get(i));
            System.out.println("directV[" + i + "] = " + directV.get(i));
        }
    }

    /**
     * Test APPROACH 1: Direct arrays with simplified approach
     */
    @Test
    public void testDirectArraysSimplified() throws TornadoExecutionPlanException {
        System.out.println("\n=== TESTING DIRECT ARRAYS SIMPLIFIED ===");

        // Create a simple task graph that combines everything
        TaskGraph combinedGraph = new TaskGraph("combined")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, directX, directPositionAndLayer)
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, weights)
                .task("normalize", TestLLMBufferApproaches::simulateRMSNorm, directX, directXb, weights, dim)
                .task("qmatmul", TestLLMBufferApproaches::matrixVectorSimple, directXb, directQ, weights, dim)
                .task("kmatmul", TestLLMBufferApproaches::matrixVectorSimple, directXb, directK, weights, dim)
                .task("vmatmul", TestLLMBufferApproaches::matrixVectorSimple, directXb, directV, weights, dim)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, directXb, directQ, directK, directV);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(combinedGraph.snapshot())) {
            executionPlan.execute();
            System.out.println("Combined graph executed successfully");

            // Verify output
            boolean hasOutput = verifyDirectOutput();
            assertTrue("Output buffer should have non-zero values", hasOutput);

            // Verify Q values
            System.out.println("Checking Q values:");
            for (int i = 0; i < 5; i++) {
                System.out.println("directQ[" + i + "] = " + directQ.get(i));
            }
        }
    }

    //============================================================
    // APPROACH 2: Tests using StateFields encapsulation
    //============================================================

    /**
     * Test APPROACH 2: StateFields with original persist/consume pattern
     */
    @Test
    public void testStateFieldsOriginalImplementation() throws TornadoExecutionPlanException {
        System.out.println("\n=== TESTING STATEFIELDS ORIGINAL IMPLEMENTATION ===");

        // Task Graph 0: Buffer initialization
        TaskGraph lookUpBufferX = new TaskGraph("lookUpBufferX")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, state.wrapX, state.positionAndLayer)
                .task("forceUpdateXperToken", TestLLMBufferApproaches::touchBuffer, state.wrapX)
                .persistOnDevice(state.wrapX, state.positionAndLayer);

        // Task Graph 1: RMS Norm
        TaskGraph rmsNormGraph = new TaskGraph("rmsnorm")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, state.positionAndLayer)
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, weights)
                .consumeFromDevice(lookUpBufferX.getTaskGraphName(), state.wrapX)
                .task("normalize", TestLLMBufferApproaches::simulateRMSNorm, state.wrapX, state.wrapXb, weights, dim)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, state.wrapXb)
                .persistOnDevice(state.wrapX, state.wrapXb, state.positionAndLayer);

        // Task Graph 2: QKV Matmuls
        TaskGraph qkvGraph = new TaskGraph("qkv")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, state.wrapXb, state.positionAndLayer)
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, weights)
                .task("qmatmul", TestLLMBufferApproaches::matrixVectorSimple, state.wrapXb, state.wrapQ, weights, dim)
                .task("kmatmul", TestLLMBufferApproaches::matrixVectorSimple, state.wrapXb, state.wrapK, weights, dim)
                .task("vmatmul", TestLLMBufferApproaches::matrixVectorSimple, state.wrapXb, state.wrapV, weights, dim)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, state.wrapQ, state.wrapK, state.wrapV);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(
                lookUpBufferX.snapshot(),
                rmsNormGraph.snapshot(),
                qkvGraph.snapshot())) {

            System.out.println("Executing first graph (lookUpBufferX)...");
            executionPlan.withGraph(0).execute();
            System.out.println("First graph executed successfully");

            System.out.println("Executing second graph (rmsNormGraph)...");
            executionPlan.withGraph(1).execute();
            System.out.println("Second graph executed successfully");

            System.out.println("Executing third graph (qkvGraph)...");
            executionPlan.withGraph(2).execute();
            System.out.println("Third graph executed successfully");
        } catch (Exception e) {
            System.out.println("Error with StateFields implementation: " + e.getMessage());
            e.printStackTrace();
        }

        // Verify output after RMS Norm
        boolean hasOutput = verifyStateOutput();
        assertTrue("Output buffer should have non-zero values after RMS Norm", hasOutput);

        // Verify Q, K, V values
        System.out.println("Checking QKV values:");
        for (int i = 0; i < 5; i++) {
            System.out.println("state.wrapQ[" + i + "] = " + state.wrapQ.get(i));
            System.out.println("state.wrapK[" + i + "] = " + state.wrapK.get(i));
            System.out.println("state.wrapV[" + i + "] = " + state.wrapV.get(i));
        }
    }

    /**
     * Test APPROACH 2: StateFields with simplified approach
     */
    @Test
    public void testStateFieldsSimplified() throws TornadoExecutionPlanException {
        System.out.println("\n=== TESTING STATEFIELDS SIMPLIFIED ===");

        // Create a simple task graph that combines everything
        TaskGraph combinedGraph = new TaskGraph("combined")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, state.wrapX, state.positionAndLayer)
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, weights)
                .task("normalize", TestLLMBufferApproaches::simulateRMSNorm, state.wrapX, state.wrapXb, weights, dim)
                .task("qmatmul", TestLLMBufferApproaches::matrixVectorSimple, state.wrapXb, state.wrapQ, weights, dim)
                .task("kmatmul", TestLLMBufferApproaches::matrixVectorSimple, state.wrapXb, state.wrapK, weights, dim)
                .task("vmatmul", TestLLMBufferApproaches::matrixVectorSimple, state.wrapXb, state.wrapV, weights, dim)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, state.wrapXb, state.wrapQ, state.wrapK, state.wrapV);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(combinedGraph.snapshot())) {
            executionPlan.execute();
            System.out.println("Combined graph executed successfully");

            // Verify output
            boolean hasOutput = verifyStateOutput();
            assertTrue("Output buffer should have non-zero values", hasOutput);

            // Verify Q values
            System.out.println("Checking Q values:");
            for (int i = 0; i < 5; i++) {
                System.out.println("state.wrapQ[" + i + "] = " + state.wrapQ.get(i));
            }
        }
    }

    //============================================================
    // APPROACH 3: Tests using method-scoped variables
    //============================================================

    /**
     * Test APPROACH 3: Method-scoped arrays with persist/consume pattern
     */
    @Test
    public void testMethodScopeArraysOriginalImplementation() throws TornadoExecutionPlanException {
        System.out.println("\n=== TESTING METHOD SCOPE ARRAYS ORIGINAL IMPLEMENTATION ===");

        // Create all arrays within method scope - not as class fields
        int localDim = 512;

        // Create arrays in method scope
        FloatArray wrapX = new FloatArray(localDim);
        FloatArray wrapXb = new FloatArray(localDim);
        FloatArray wrapQ = new FloatArray(localDim);
        FloatArray wrapK = new FloatArray(localDim);
        FloatArray wrapV = new FloatArray(localDim);
        IntArray positionAndLayer = new IntArray(2);
        FloatArray localWeights = new FloatArray(localDim);

        // Initialize with test values
        for (int i = 0; i < localDim; i++) {
            wrapX.set(i, 0.01f * i);
            localWeights.set(i, 1.0f);
        }
        positionAndLayer.set(0, 0);
        positionAndLayer.set(1, 0);

        System.out.println("Model dimensions:");
        System.out.println("localDim = " + localDim);

        // Task Graph 0: Buffer initialization
        TaskGraph lookUpBufferX = new TaskGraph("lookUpBufferX")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, wrapX, positionAndLayer)
                .task("forceUpdateXperToken", TestLLMBufferApproaches::touchBuffer, wrapX)
                .persistOnDevice(wrapX, positionAndLayer);

        // Task Graph 1: RMS Norm
        TaskGraph rmsNormGraph = new TaskGraph("rmsnorm")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, positionAndLayer)
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, localWeights)
                .consumeFromDevice(lookUpBufferX.getTaskGraphName(), wrapX)
                .task("normalize", TestLLMBufferApproaches::simulateRMSNorm, wrapX, wrapXb, localWeights, localDim)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, wrapXb)
                .persistOnDevice(wrapX, wrapXb, positionAndLayer);

        // Task Graph 2: QKV Matmuls
        TaskGraph qkvGraph = new TaskGraph("qkv")
                .consumeFromDevice("rmsnorm", wrapXb, wrapX, positionAndLayer)
//                .transferToDevice(DataTransferMode.EVERY_EXECUTION, positionAndLayer)
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, localWeights, wrapQ, wrapQ, wrapV)
                .task("qmatmul", TestLLMBufferApproaches::matrixVectorSimple, wrapXb, wrapQ, localWeights, localDim)
                .task("kmatmul", TestLLMBufferApproaches::matrixVectorSimple, wrapXb, wrapK, localWeights, localDim)
                .task("vmatmul", TestLLMBufferApproaches::matrixVectorSimple, wrapXb, wrapV, localWeights, localDim)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, wrapQ, wrapK, wrapV);

        // Execute all graphs in a single execution plan
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(
                lookUpBufferX.snapshot(),
                rmsNormGraph.snapshot(),
                qkvGraph.snapshot())) {

            System.out.println("Executing first graph (lookUpBufferX)...");
            executionPlan.withGraph(0).execute();
            System.out.println("First graph executed successfully");

            System.out.println("Executing second graph (rmsNormGraph)...");
            executionPlan.withGraph(1).execute();
            System.out.println("Second graph executed successfully");

            System.out.println("Executing third graph (qkvGraph)...");
            executionPlan.withGraph(2).execute();
            System.out.println("Third graph executed successfully");
        }

        // Verify output after RMS Norm
        boolean hasOutput = verifyOutput(wrapXb);
        assertTrue("Output buffer should have non-zero values after RMS Norm", hasOutput);

        // Verify Q, K, V values
        System.out.println("Checking QKV values:");
        for (int i = 0; i < 5; i++) {
            System.out.println("wrapQ[" + i + "] = " + wrapQ.get(i));
            System.out.println("wrapK[" + i + "] = " + wrapK.get(i));
            System.out.println("wrapV[" + i + "] = " + wrapV.get(i));
        }
    }

    /**
     * Test APPROACH 3: Method-scoped StateFields
     */
    @Test
        public void testMethodScopeStateFields() throws TornadoExecutionPlanException {
            System.out.println("\n=== TESTING METHOD SCOPE STATEFIELDS ===");

            // Create a local state instance
            int localDim = 512;
            StateFields localState = new StateFields(localDim);

            // Create weights locally
            FloatArray localWeights = new FloatArray(localDim);
            for (int i = 0; i < localDim; i++) {
                localWeights.set(i, 1.0f);
            }

        // Task Graph 0: Buffer initialization
        TaskGraph lookUpBufferX = new TaskGraph("lookUpBufferX")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, localState.wrapX, localState.positionAndLayer)
                .task("forceUpdateXperToken", TestLLMBufferApproaches::touchBuffer, localState.wrapX)
                .persistOnDevice(localState.wrapX, localState.positionAndLayer);

        // Task Graph 1: RMS Norm
        TaskGraph rmsNormGraph = new TaskGraph("rmsnorm")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, localState.positionAndLayer)
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, localWeights)
                .consumeFromDevice(lookUpBufferX.getTaskGraphName(), localState.wrapX)
                .task("normalize", TestLLMBufferApproaches::simulateRMSNorm, localState.wrapX, localState.wrapXb, localWeights, localDim)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, localState.wrapXb)
                .persistOnDevice(localState.wrapX, localState.wrapXb, localState.positionAndLayer);

        // Task Graph 2: QKV Matmuls
        TaskGraph qkvGraph = new TaskGraph("qkv")
                .consumeFromDevice(rmsNormGraph.getTaskGraphName(), localState.wrapXb, localState.positionAndLayer)
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, localWeights, localState.wrapK, localState.wrapQ, localState.wrapV)
                .task("qmatmul", TestLLMBufferApproaches::matrixVectorSimple, localState.wrapXb, localState.wrapQ, localWeights, localDim)
                .task("kmatmul", TestLLMBufferApproaches::matrixVectorSimple, localState.wrapXb, localState.wrapK, localWeights, localDim)
                .task("vmatmul", TestLLMBufferApproaches::matrixVectorSimple, localState.wrapXb, localState.wrapV, localWeights, localDim)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, localState.wrapQ, localState.wrapK, localState.wrapV);
//                .persistOnDevice(localState.wrapXb, localState.wrapQ, localState.wrapK, localState.wrapV, localState.positionAndLayer);

            // Execute all graphs in a single execution plan
            try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(
                    lookUpBufferX.snapshot(),
                    rmsNormGraph.snapshot(),
                    qkvGraph.snapshot())) {

                System.out.println("Executing first graph (lookUpBufferX)...");
                executionPlan.withGraph(0).execute();
                System.out.println("First graph executed successfully");

                System.out.println("Executing second graph (rmsNormGraph)...");
                executionPlan.withGraph(1).execute();
                System.out.println("Second graph executed successfully");

                System.out.println("Executing third graph (qkvGraph)...");
                executionPlan.withGraph(2).execute();
                System.out.println("Third graph executed successfully");
            }

            // Verify output
            System.out.println("Checking localState.wrapXb values:");
            boolean hasOutput = false;
            for (int i = 0; i < 10; i++) {
                System.out.println("localState.wrapXb[" + i + "] = " + localState.wrapXb.get(i));
                if (localState.wrapXb.get(i) != 0.0f) {
                    hasOutput = true;
                }
            }
            assertTrue("Output buffer should have non-zero values", hasOutput);

            // Verify Q, K, V values
            System.out.println("Checking QKV values:");
            for (int i = 0; i < 5; i++) {
                System.out.println("localState.wrapQ[" + i + "] = " + localState.wrapQ.get(i));
                System.out.println("localState.wrapK[" + i + "] = " + localState.wrapK.get(i));
                System.out.println("localState.wrapV[" + i + "] = " + localState.wrapV.get(i));
            }
        }

    //============================================================
    // APPROACH 4: Explicit copying between graphs
    //============================================================

    /**
     * Test APPROACH 4: Direct arrays with explicit copying
     */
    @Test
    public void testDirectArraysWithExplicitCopying() throws TornadoExecutionPlanException {
        System.out.println("\n=== TESTING DIRECT ARRAYS WITH EXPLICIT COPYING ===");

        // Task Graph 0: Touch the buffer
        TaskGraph touchGraph = new TaskGraph("touch")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, directX, directPositionAndLayer)
                .task("touchBuffer", TestLLMBufferApproaches::touchBuffer, directX)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, directX);

        // Execute the first graph
        try (TornadoExecutionPlan executionPlan1 = new TornadoExecutionPlan(touchGraph.snapshot())) {
            System.out.println("Executing touch graph...");
            executionPlan1.execute();
            System.out.println("Touch graph executed successfully");
        }

        // Task Graph 1: RMS Norm with explicit transfers
        TaskGraph rmsNormGraph = new TaskGraph("rmsnorm")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, directX, directPositionAndLayer)
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, weights)
                .task("normalize", TestLLMBufferApproaches::simulateRMSNorm, directX, directXb, weights, dim)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, directXb);

        // Execute the second graph
        try (TornadoExecutionPlan executionPlan2 = new TornadoExecutionPlan(rmsNormGraph.snapshot())) {
            System.out.println("Executing RMS Norm graph...");
            executionPlan2.execute();
            System.out.println("RMS Norm graph executed successfully");
        }

        // Verify output after RMS Norm
        boolean hasOutput = verifyDirectOutput();
        assertTrue("Output buffer should have non-zero values after RMS Norm", hasOutput);

        // Task Graph 2: QKV Matmuls with explicit transfers
        TaskGraph qkvGraph = new TaskGraph("qkv")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, directXb, directPositionAndLayer)
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, weights)
                .task("qmatmul", TestLLMBufferApproaches::matrixVectorSimple, directXb, directQ, weights, dim)
                .task("kmatmul", TestLLMBufferApproaches::matrixVectorSimple, directXb, directK, weights, dim)
                .task("vmatmul", TestLLMBufferApproaches::matrixVectorSimple, directXb, directV, weights, dim)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, directQ, directK, directV);

        // Execute the third graph
        try (TornadoExecutionPlan executionPlan3 = new TornadoExecutionPlan(qkvGraph.snapshot())) {
            System.out.println("Executing QKV graph...");
            executionPlan3.execute();
            System.out.println("QKV graph executed successfully");
        }

        // Verify Q, K, V values
        System.out.println("Checking QKV values:");
        for (int i = 0; i < 5; i++) {
            System.out.println("directQ[" + i + "] = " + directQ.get(i));
            System.out.println("directK[" + i + "] = " + directK.get(i));
            System.out.println("directV[" + i + "] = " + directV.get(i));
        }
    }

    /**
     * Test APPROACH 4: StateFields with explicit copying
     */
    @Test
    public void testStateFieldsWithExplicitCopying() throws TornadoExecutionPlanException {
        System.out.println("\n=== TESTING STATEFIELDS WITH EXPLICIT COPYING ===");

        // Task Graph 0: Touch the buffer
        TaskGraph touchGraph = new TaskGraph("touch")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, state.wrapX, state.positionAndLayer)
                .task("touchBuffer", TestLLMBufferApproaches::touchBuffer, state.wrapX)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, state.wrapX);

        // Execute the first graph
        try (TornadoExecutionPlan executionPlan1 = new TornadoExecutionPlan(touchGraph.snapshot())) {
            System.out.println("Executing touch graph...");
            executionPlan1.execute();
            System.out.println("Touch graph executed successfully");
        }

        // Task Graph 1: RMS Norm with explicit transfers
        TaskGraph rmsNormGraph = new TaskGraph("rmsnorm")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, state.wrapX, state.positionAndLayer)
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, weights)
                .task("normalize", TestLLMBufferApproaches::simulateRMSNorm, state.wrapX, state.wrapXb, weights, dim)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, state.wrapXb);

        // Execute the second graph
        try (TornadoExecutionPlan executionPlan2 = new TornadoExecutionPlan(rmsNormGraph.snapshot())) {
            System.out.println("Executing RMS Norm graph...");
            executionPlan2.execute();
            System.out.println("RMS Norm graph executed successfully");
        }

        // Verify output after RMS Norm
        boolean hasOutput = verifyStateOutput();
        assertTrue("Output buffer should have non-zero values after RMS Norm", hasOutput);

        // Task Graph 2: QKV Matmuls with explicit transfers
        TaskGraph qkvGraph = new TaskGraph("qkv")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, state.wrapXb, state.positionAndLayer)
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, weights)
                .task("qmatmul", TestLLMBufferApproaches::matrixVectorSimple, state.wrapXb, state.wrapQ, weights, dim)
                .task("kmatmul", TestLLMBufferApproaches::matrixVectorSimple, state.wrapXb, state.wrapK, weights, dim)
                .task("vmatmul", TestLLMBufferApproaches::matrixVectorSimple, state.wrapXb, state.wrapV, weights, dim)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, state.wrapQ, state.wrapK, state.wrapV);

        // Execute the third graph
        try (TornadoExecutionPlan executionPlan3 = new TornadoExecutionPlan(qkvGraph.snapshot())) {
            System.out.println("Executing QKV graph...");
            executionPlan3.execute();
            System.out.println("QKV graph executed successfully");
        }

        // Verify Q, K, V values
        System.out.println("Checking QKV values:");
        for (int i = 0; i < 5; i++) {
            System.out.println("state.wrapQ[" + i + "] = " + state.wrapQ.get(i));
            System.out.println("state.wrapK[" + i + "] = " + state.wrapK.get(i));
            System.out.println("state.wrapV[" + i + "] = " + state.wrapV.get(i));
        }
    }

    //============================================================
    // COMPARATIVE TEST: Execute multiple approaches in sequence
    //============================================================

    /**
     * Run a comprehensive test that compares all approaches
     */
    @Test
    public void testCompareAllApproaches() throws TornadoExecutionPlanException {
        System.out.println("\n=== COMPARATIVE TEST: ALL APPROACHES ===");
        System.out.println("Running simplified tests to compare all approaches...");

        // Simple combined task for direct arrays
        TaskGraph directGraph = new TaskGraph("direct")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, directX)
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, weights)
                .task("normalizeDirectX", TestLLMBufferApproaches::simulateRMSNorm, directX, directXb, weights, dim)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, directXb);

        // Simple combined task for stateFields
        TaskGraph stateGraph = new TaskGraph("state")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, state.wrapX)
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, weights)
                .task("normalizeStateX", TestLLMBufferApproaches::simulateRMSNorm, state.wrapX, state.wrapXb, weights, dim)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, state.wrapXb);

        // Simple combined task for method-scope arrays
        FloatArray localX = new FloatArray(dim);
        FloatArray localXb = new FloatArray(dim);
        for (int i = 0; i < dim; i++) {
            localX.set(i, 0.01f * i);
        }

        TaskGraph localGraph = new TaskGraph("local")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, localX)
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, weights)
                .task("normalizeLocalX", TestLLMBufferApproaches::simulateRMSNorm, localX, localXb, weights, dim)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, localXb);

        // Execute each approach separately
        try (TornadoExecutionPlan directPlan = new TornadoExecutionPlan(directGraph.snapshot())) {
            System.out.println("Executing direct arrays graph...");
            directPlan.execute();
            boolean directSuccess = verifyDirectOutput();
            System.out.println("Direct arrays test " + (directSuccess ? "PASSED" : "FAILED"));
        } catch (Exception e) {
            System.out.println("Direct arrays test FAILED with error: " + e.getMessage());
        }

        try (TornadoExecutionPlan statePlan = new TornadoExecutionPlan(stateGraph.snapshot())) {
            System.out.println("Executing StateFields graph...");
            statePlan.execute();
            boolean stateSuccess = verifyStateOutput();
            System.out.println("StateFields test " + (stateSuccess ? "PASSED" : "FAILED"));
        } catch (Exception e) {
            System.out.println("StateFields test FAILED with error: " + e.getMessage());
        }

        try (TornadoExecutionPlan localPlan = new TornadoExecutionPlan(localGraph.snapshot())) {
            System.out.println("Executing method-scope arrays graph...");
            localPlan.execute();
            boolean localSuccess = verifyOutput(localXb);
            System.out.println("Method-scope arrays test " + (localSuccess ? "PASSED" : "FAILED"));
        } catch (Exception e) {
            System.out.println("Method-scope arrays test FAILED with error: " + e.getMessage());
        }
    }

    @Test
    public void testComplexLLMDataflow() throws TornadoExecutionPlanException {
        System.out.println("\n=== TESTING COMPLEX LLM DATAFLOW ===");

        // Create arrays for the full LLM pipeline
        int localDim = 512;
        int seqLength = 8;
        int numHeads = 4;
        int headDim = localDim / numHeads;

        // Input embeddings and model parameters
        FloatArray inputEmbeddings = new FloatArray(localDim);
        FloatArray attentionWeights = new FloatArray(localDim * 3); // For Q, K, V projections
        FloatArray attentionOutput = new FloatArray(localDim);
        FloatArray ffnWeights1 = new FloatArray(localDim * 4); // Expansion weights
        FloatArray ffnWeights2 = new FloatArray(localDim * 4); // Projection weights
        FloatArray layerNormWeights1 = new FloatArray(localDim);
        FloatArray layerNormWeights2 = new FloatArray(localDim);

        // Intermediate buffers
        FloatArray normOutput1 = new FloatArray(localDim);
        FloatArray qkvProjected = new FloatArray(localDim * 3);
        FloatArray queryHeads = new FloatArray(numHeads * headDim);
        FloatArray keyHeads = new FloatArray(numHeads * headDim);
        FloatArray valueHeads = new FloatArray(numHeads * headDim);
        FloatArray ffnIntermediate = new FloatArray(localDim * 4);
        FloatArray ffnOutput = new FloatArray(localDim);
        FloatArray normOutput2 = new FloatArray(localDim);
        FloatArray residualOutput = new FloatArray(localDim);

        // Context information
        IntArray positionAndLayer = new IntArray(2);

        // Initialize with test values
        for (int i = 0; i < localDim; i++) {
            inputEmbeddings.set(i, 0.01f * i);
            layerNormWeights1.set(i, 1.0f);
            layerNormWeights2.set(i, 1.0f);
        }

        for (int i = 0; i < localDim * 3; i++) {
            attentionWeights.set(i, 0.5f);
        }

        for (int i = 0; i < localDim * 4; i++) {
            ffnWeights1.set(i, 0.25f);
            ffnWeights2.set(i, 0.25f);
        }

        positionAndLayer.set(0, 0); // Position
        positionAndLayer.set(1, 0); // Layer

        System.out.println("Model dimensions:");
        System.out.println("localDim = " + localDim);
        System.out.println("numHeads = " + numHeads);
        System.out.println("headDim = " + headDim);

        // Task Graph 1: First Layer Norm
        TaskGraph layerNorm1Graph = new TaskGraph("layerNorm1")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, inputEmbeddings, positionAndLayer)
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, layerNormWeights1)
                .task("normalize1", TestLLMBufferApproaches::simulateRMSNorm,
                        inputEmbeddings, normOutput1, layerNormWeights1, localDim)
                .persistOnDevice(inputEmbeddings, normOutput1, positionAndLayer);

        // Task Graph 2: QKV Projection
        TaskGraph qkvProjectionGraph = new TaskGraph("qkvProjection")
                .consumeFromDevice(layerNorm1Graph.getTaskGraphName(), normOutput1, positionAndLayer)
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, attentionWeights, queryHeads, keyHeads, valueHeads)
                .task("qProjection", TestLLMBufferApproaches::matrixVectorSimple,
                        normOutput1, queryHeads, attentionWeights, localDim)
                .task("kProjection", TestLLMBufferApproaches::matrixVectorBatch,
                        normOutput1, keyHeads, attentionWeights, localDim, localDim)
                .task("vProjection", TestLLMBufferApproaches::matrixVectorBatch,
                        normOutput1, valueHeads, attentionWeights, localDim, localDim * 2)
                .persistOnDevice(queryHeads, keyHeads, valueHeads, normOutput1, positionAndLayer);

        // Task Graph 3: Attention computation (simplified)
        TaskGraph attentionGraph = new TaskGraph("attention")
                .consumeFromDevice(qkvProjectionGraph.getTaskGraphName(),
                        queryHeads, keyHeads, valueHeads, positionAndLayer)
                .task("simpleAttention", TestLLMBufferApproaches::simulateAttention,
                        queryHeads, keyHeads, valueHeads, attentionOutput, numHeads, headDim)
                .persistOnDevice(attentionOutput, normOutput1, positionAndLayer);

        // Task Graph 4: Residual connection with input
        TaskGraph residualGraph1 = new TaskGraph("residual1")
                .consumeFromDevice(attentionGraph.getTaskGraphName(),
                        attentionOutput, inputEmbeddings, positionAndLayer)
                .task("residual1", TestLLMBufferApproaches::addResidual,
                        attentionOutput, inputEmbeddings, residualOutput, localDim)
                .persistOnDevice(residualOutput, positionAndLayer);

        // Task Graph 5: Second Layer Norm
        TaskGraph layerNorm2Graph = new TaskGraph("layerNorm2")
                .consumeFromDevice(residualGraph1.getTaskGraphName(), residualOutput, positionAndLayer)
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, layerNormWeights2)
                .task("normalize2", TestLLMBufferApproaches::simulateRMSNorm,
                        residualOutput, normOutput2, layerNormWeights2, localDim)
                .persistOnDevice(residualOutput, normOutput2, positionAndLayer);

        // Task Graph 6: FFN first part (expansion)
        TaskGraph ffnExpansionGraph = new TaskGraph("ffnExpansion")
                .consumeFromDevice(layerNorm2Graph.getTaskGraphName(), normOutput2, positionAndLayer)
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, ffnWeights1)
                .task("ffnExpand", TestLLMBufferApproaches::matrixVectorBatch,
                        normOutput2, ffnIntermediate, ffnWeights1, localDim, 0)
                .persistOnDevice(ffnIntermediate, residualOutput, positionAndLayer);

        // Task Graph 7: FFN second part (projection)
        TaskGraph ffnProjectionGraph = new TaskGraph("ffnProjection")
                .consumeFromDevice(ffnExpansionGraph.getTaskGraphName(),
                        ffnIntermediate, positionAndLayer)
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, ffnWeights2)
                .task("ffnProject", TestLLMBufferApproaches::matrixVectorBatch,
                        ffnIntermediate, ffnOutput, ffnWeights2, localDim * 4, 0)
                .persistOnDevice(ffnOutput, residualOutput, positionAndLayer);

        // Task Graph 8: Final residual connection
        TaskGraph residualGraph2 = new TaskGraph("residual2")
                .consumeFromDevice(ffnProjectionGraph.getTaskGraphName(),
                        ffnOutput, residualOutput, positionAndLayer)
                .task("residual2", TestLLMBufferApproaches::addResidual,
                        ffnOutput, residualOutput, residualOutput, localDim)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, residualOutput);

        // Execute all graphs in a single execution plan
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(
                layerNorm1Graph.snapshot(),
                qkvProjectionGraph.snapshot(),
                attentionGraph.snapshot(),
                residualGraph1.snapshot(),
                layerNorm2Graph.snapshot(),
                ffnExpansionGraph.snapshot(),
                ffnProjectionGraph.snapshot(),
                residualGraph2.snapshot())) {

            for (int graphIndex = 0; graphIndex < 8; graphIndex++) {
                System.out.println("Executing graph " + graphIndex + "...");
                executionPlan.withGraph(graphIndex).execute();
                System.out.println("Graph " + graphIndex + " executed successfully");
            }
        }

        // Verify output for residual connection
        System.out.println("Checking final output values:");
        boolean hasOutput = false;
        for (int i = 0; i < 10; i++) {
            System.out.println("residualOutput[" + i + "] = " + residualOutput.get(i));
            if (residualOutput.get(i) != 0.0f) {
                hasOutput = true;
            }
        }
        assertTrue("Final output buffer should have non-zero values", hasOutput);
    }

    // Add these utility methods to your test class
    public static void simulateAttention(FloatArray query, FloatArray key, FloatArray value,
            FloatArray output, int numHeads, int headDim) {
        // Simple mock attention - just copies values for testing
        for (int h = 0; h < numHeads; h++) {
            for (int i = 0; i < headDim; i++) {
                int idx = h * headDim + i;
                // Simple weighted sum simulation
                output.set(idx, query.get(idx) * 0.5f + key.get(idx) * 0.2f + value.get(idx) * 0.3f);
            }
        }
    }

    public static void matrixVectorBatch(FloatArray input, FloatArray output,
            FloatArray weights, int dim, int offset) {
        // Simulate matrix-vector multiplication with offset in weight matrix
        for (int i = 0; i < output.getSize(); i++) {
            float sum = 0.0f;
            for (int j = 0; j < dim; j++) {
                sum += input.get(j) * weights.get(offset + i * dim + j);
            }
            output.set(i, sum);
        }
    }

    public static void addResidual(FloatArray input1, FloatArray input2,
            FloatArray output, int dim) {
        // Add two vectors (residual connection)
        for (int i = 0; i < dim; i++) {
            output.set(i, input1.get(i) + input2.get(i));
        }
    }
}