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
 * Test case that reproduces the buffer sharing pattern in LLM forward pass
 * using StateFields class to hold arrays.
 */
public class TestDirectArrays extends TornadoTestBase {

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


    public static void touchBuffer(FloatArray x) {
        // Actually access the buffer
        float dummy = x.get(0);
        if (dummy > Float.MAX_VALUE) {
            x.set(0, dummy);  // Will never execute but prevents optimization
        }
    }


    public static void simulateRMSNorm(FloatArray x, FloatArray xb, FloatArray weights, int dim) {
        // Simple operation to simulate RMSNorm without using complex kernels
        for (int i = 0; i < dim; i++) {
            xb.set(i, x.get(i) * weights.get(i % weights.getSize()));
        }
    }


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
    private FloatArray weights;
    private StateFields state;

    @Before
    public void setUp() {
        // Configuration values
        dim = 1024; // Reduced dimension size for testing

        // Print dimensions
        System.out.println("Model dimensions:");
        System.out.println("dim = " + dim);

        // Create state with fields
        state = new StateFields(dim);

        // Create weights separately
        weights = new FloatArray(dim);
        for (int i = 0; i < dim; i++) {
            weights.set(i, 1.0f);
        }
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

    @Test
    public void testStateFieldsOriginalImplementation() throws TornadoExecutionPlanException {
        System.out.println("\n=== TESTING STATE FIELDS ORIGINAL IMPLEMENTATION ===");

        // Task Graph 0: Buffer initialization - forces copying of wrapX
        TaskGraph lookUpBufferX = new TaskGraph("lookUpBufferX")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, state.wrapX, state.positionAndLayer)
                .task("forceUpdateXperToken", TestDirectArrays::touchBuffer, state.wrapX)
                .persistOnDevice(state.wrapX, state.positionAndLayer);

        // Task Graph 1: RMS Norm
        TaskGraph rmsNormGraph = new TaskGraph("rmsnorm")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, state.positionAndLayer)
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, weights)
                .consumeFromDevice(lookUpBufferX.getTaskGraphName(), state.wrapX)
                .task("normalize", TestDirectArrays::simulateRMSNorm, state.wrapX, state.wrapXb, weights, dim)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, state.wrapXb)
                .persistOnDevice(state.wrapX, state.wrapXb, state.positionAndLayer);

        // Task Graph 2: QKV Matmuls - separate from the consume chain
        TaskGraph qkvGraph = new TaskGraph("qkv")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, state.wrapXb, state.positionAndLayer)
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, weights)
                .task("qmatmul", TestDirectArrays::matrixVectorSimple, state.wrapXb, state.wrapQ, weights, dim)
                .task("kmatmul", TestDirectArrays::matrixVectorSimple, state.wrapXb, state.wrapK, weights, dim)
                .task("vmatmul", TestDirectArrays::matrixVectorSimple, state.wrapXb, state.wrapV, weights, dim)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, state.wrapQ, state.wrapK, state.wrapV);

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
        boolean hasOutput = verifyOutput();
        assertTrue("Output buffer should have non-zero values after RMS Norm", hasOutput);

        // Verify Q, K, V values
        System.out.println("Checking QKV values:");
        for (int i = 0; i < 5; i++) {
            System.out.println("wrapQ[" + i + "] = " + state.wrapQ.get(i));
            System.out.println("wrapK[" + i + "] = " + state.wrapK.get(i));
            System.out.println("wrapV[" + i + "] = " + state.wrapV.get(i));
        }
    }


    @Test
    public void testStateFieldsSimplified() throws TornadoExecutionPlanException {
        System.out.println("\n=== TESTING STATE FIELDS SIMPLIFIED ===");

        // Create a simple task graph that combines everything
        TaskGraph combinedGraph = new TaskGraph("combined")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, state.wrapX, state.positionAndLayer)
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, weights)
                .task("normalize", TestDirectArrays::simulateRMSNorm, state.wrapX, state.wrapXb, weights, dim)
                .task("qmatmul", TestDirectArrays::matrixVectorSimple, state.wrapXb, state.wrapQ, weights, dim)
                .task("kmatmul", TestDirectArrays::matrixVectorSimple, state.wrapXb, state.wrapK, weights, dim)
                .task("vmatmul", TestDirectArrays::matrixVectorSimple, state.wrapXb, state.wrapV, weights, dim)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, state.wrapXb, state.wrapQ, state.wrapK, state.wrapV);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(combinedGraph.snapshot())) {
            executionPlan.execute();
            System.out.println("Combined graph executed successfully");

            // Verify output
            boolean hasOutput = verifyOutput();
            assertTrue("Output buffer should have non-zero values", hasOutput);

            // Verify Q values
            System.out.println("Checking Q values:");
            for (int i = 0; i < 5; i++) {
                System.out.println("wrapQ[" + i + "] = " + state.wrapQ.get(i));
            }
        }
    }

    @Test
    public void testStateFieldsWithExplicitCopying() throws TornadoExecutionPlanException {
        System.out.println("\n=== TESTING STATE FIELDS WITH EXPLICIT COPYING ===");

        // Task Graph 0: Touch the buffer
        TaskGraph touchGraph = new TaskGraph("touch")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, state.wrapX, state.positionAndLayer)
                .task("touchBuffer", TestDirectArrays::touchBuffer, state.wrapX)
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
                .task("normalize", TestDirectArrays::simulateRMSNorm, state.wrapX, state.wrapXb, weights, dim)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, state.wrapXb);

        // Execute the second graph
        try (TornadoExecutionPlan executionPlan2 = new TornadoExecutionPlan(rmsNormGraph.snapshot())) {
            System.out.println("Executing RMS Norm graph...");
            executionPlan2.execute();
            System.out.println("RMS Norm graph executed successfully");
        }

        // Verify output after RMS Norm
        boolean hasOutput = verifyOutput();
        assertTrue("Output buffer should have non-zero values after RMS Norm", hasOutput);

        // Task Graph 2: QKV Matmuls with explicit transfers
        TaskGraph qkvGraph = new TaskGraph("qkv")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, state.wrapXb, state.positionAndLayer)
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, weights)
                .task("qmatmul", TestDirectArrays::matrixVectorSimple, state.wrapXb, state.wrapQ, weights, dim)
                .task("kmatmul", TestDirectArrays::matrixVectorSimple, state.wrapXb, state.wrapK, weights, dim)
                .task("vmatmul", TestDirectArrays::matrixVectorSimple, state.wrapXb, state.wrapV, weights, dim)
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
            System.out.println("wrapQ[" + i + "] = " + state.wrapQ.get(i));
            System.out.println("wrapK[" + i + "] = " + state.wrapK.get(i));
            System.out.println("wrapV[" + i + "] = " + state.wrapV.get(i));
        }
    }

    /**
     * Test with a new StateFields instance created for each method call to simulate
     * the method scope approach but with StateFields structure
     */
    @Test
    public void testMethodScopeStateFields() throws TornadoExecutionPlanException {
        System.out.println("\n=== TESTING METHOD SCOPE STATE FIELDS ===");

        // Create all arrays within method scope
        int localDim = 512; // Smaller dimension to avoid memory issues

        // Create a local state instance
        StateFields localState = new StateFields(localDim);

        // Create weights locally
        FloatArray localWeights = new FloatArray(localDim);
        for (int i = 0; i < localDim; i++) {
            localWeights.set(i, 1.0f);
        }

        System.out.println("Model dimensions:");
        System.out.println("dim = " + localDim);

        // Task Graph 0: Buffer initialization
        TaskGraph lookUpBufferX = new TaskGraph("lookUpBufferX")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, localState.wrapX, localState.positionAndLayer)
                .task("forceUpdateXperToken", TestDirectArrays::touchBuffer, localState.wrapX)
                .persistOnDevice(localState.wrapX, localState.positionAndLayer);

        // Task Graph 1: RMS Norm
        TaskGraph rmsNormGraph = new TaskGraph("rmsnorm")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, localState.positionAndLayer)
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, localWeights)
                .consumeFromDevice(lookUpBufferX.getTaskGraphName(), localState.wrapX)
                .task("normalize", TestDirectArrays::simulateRMSNorm, localState.wrapX, localState.wrapXb, localWeights, localDim)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, localState.wrapXb)
                .persistOnDevice(localState.wrapX, localState.wrapXb, localState.positionAndLayer);

        // Task Graph 2: QKV Matmuls
        TaskGraph qkvGraph = new TaskGraph("qkv")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, localState.wrapXb, localState.positionAndLayer)
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, localWeights)
                .task("qmatmul", TestDirectArrays::matrixVectorSimple, localState.wrapXb, localState.wrapQ, localWeights, localDim)
                .task("kmatmul", TestDirectArrays::matrixVectorSimple, localState.wrapXb, localState.wrapK, localWeights, localDim)
                .task("vmatmul", TestDirectArrays::matrixVectorSimple, localState.wrapXb, localState.wrapV, localWeights, localDim)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, localState.wrapQ, localState.wrapK, localState.wrapV);

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
        System.out.println("Checking wrapXb values:");
        boolean hasOutput = false;
        for (int i = 0; i < 10; i++) {
            System.out.println("wrapXb[" + i + "] = " + localState.wrapXb.get(i));
            if (localState.wrapXb.get(i) != 0.0f) {
                hasOutput = true;
            }
        }
        assertTrue("Output buffer should have non-zero values", hasOutput);

        // Verify Q, K, V values
        System.out.println("Checking QKV values:");
        for (int i = 0; i < 5; i++) {
            System.out.println("wrapQ[" + i + "] = " + localState.wrapQ.get(i));
            System.out.println("wrapK[" + i + "] = " + localState.wrapK.get(i));
            System.out.println("wrapV[" + i + "] = " + localState.wrapV.get(i));
        }
    }
}