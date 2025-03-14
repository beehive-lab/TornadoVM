package uk.ac.manchester.tornado.unittests.llm;

import org.junit.Test;
import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

import java.util.Random;

import static org.junit.Assert.assertEquals;

public class TestWeightedSum extends TornadoTestBase {

    /**
     * A simplified version of the computeWeightedSum function for easier debugging
     */
    public static void computeWeightedSumDebug(KernelContext context, int pos, int seqLen, FloatArray attScores, FloatArray valueCache, FloatArray output, int kvDim, int kvMul, int headSize, int loff, FloatArray debugData) {
        int h = context.groupIdx;         // Head index
        int threadId = context.localIdx;  // Thread ID within work group
        int blockDim = context.localGroupSizeX;  // Work group size

        // Attention scores offset for this head
        int attOffset = h * seqLen;

        // Output offset for this head
        int outputOffset = h * headSize;

        // For debugging - store information about the thread
        int debugOffset = h * blockDim * 5 + threadId * 5;
        debugData.set(debugOffset, h);                     // Head index
        debugData.set(debugOffset + 1, threadId);          // Thread ID
        debugData.set(debugOffset + 2, attOffset);         // Attention scores offset
        debugData.set(debugOffset + 3, outputOffset);      // Output offset
        debugData.set(debugOffset + 4, blockDim);          // Block dimension

        // Calculate weighted sum for each head dimension
        for (int i = threadId; i < headSize; i += blockDim) {
            float val = 0.0f;
            for (int t = 0; t <= pos; t++) {
                // Get the value vector for this head and timestep
                int valueOffset = loff + t * kvDim + (h / kvMul) * headSize;

                // Get the attention weight for this timestep
                float a = attScores.get(attOffset + t);

                val += a * valueCache.get(valueOffset + i);
            }
            output.set(outputOffset + i, val);
        }
    }

    /**
     * Sequential reference implementation of weighted sum calculation
     */
    private void computeWeightedSumSequential(int numHeads, int seqLen, int pos, int headSize,
            int kvDim, int kvMul, int loff,
            FloatArray attScores, FloatArray valueCache, FloatArray output) {
        for (int h = 0; h < numHeads; h++) {
            int attOffset = h * seqLen;
            int outputOffset = h * headSize;

            for (int i = 0; i < headSize; i++) {
                float val = 0.0f;
                for (int t = 0; t <= pos; t++) {
                    int valueOffset = loff + t * kvDim + (h / kvMul) * headSize;
                    float a = attScores.get(attOffset + t);
                    val += a * valueCache.get(valueOffset + i);
                }
                output.set(outputOffset + i, val);
            }
        }
    }

    @Test
    public void testDebugWeightedSumCalculation() throws TornadoExecutionPlanException {
        final int numHeads = 4;
        final int seqLen = 16;
        final int pos = 15;
        final int headSize = 32;
        final int localSize = 16;
        final int kvDim = headSize * numHeads;
        final int kvMul = 1;
        final int loff = 0;

        // Allocate data arrays with known values
        FloatArray attScores = new FloatArray(numHeads * seqLen);
        FloatArray valueCache = new FloatArray(seqLen * kvDim);
        FloatArray output = new FloatArray(numHeads * headSize);
        FloatArray expectedOutput = new FloatArray(numHeads * headSize);
        FloatArray debugData = new FloatArray(numHeads * localSize * 5); // Additional debug information

        // Initialize with controlled values
        Random random = new Random(42);

        // Print head 0, dimension 0 values for clarity
        System.out.println("Initializing test data:");

        // Initialize attention scores - use softmax-normalized values
        // Each head will have attention focused on a different position
        for (int h = 0; h < numHeads; h++) {
            int attOffset = h * seqLen;
            float sum = 0.0f;

            // Generate random values and calculate their sum
            for (int t = 0; t <= pos; t++) {
                float value = random.nextFloat();
                attScores.set(attOffset + t, value);
                sum += value;
            }

            // Normalize to create a proper probability distribution
            for (int t = 0; t <= pos; t++) {
                attScores.set(attOffset + t, attScores.get(attOffset + t) / sum);
            }
        }

        // Initialize value cache with controlled values
        for (int t = 0; t <= pos; t++) {
            for (int h = 0; h < numHeads; h++) {
                for (int i = 0; i < headSize; i++) {
                    int valueOffset = loff + t * kvDim + (h / kvMul) * headSize + i;
                    float value = random.nextFloat() * 2 - 1; // Values between -1 and 1
                    valueCache.set(valueOffset, value);
                }
            }
        }

        // Do a special case for debugging - make head 0, dimension 0 values easier to track
        // Set first value in first sequence position to a specific value for tracking
        valueCache.set(loff + 0 * kvDim + 0 * headSize + 0, 1.0f);

        // Print attention weights for head 0
        System.out.println("Attention weights for head 0:");
        for (int t = 0; t <= pos; t++) {
            System.out.printf("Position %d: %.6f%n", t, attScores.get(0 * seqLen + t));
        }

        // Print the first few value vectors for head 0
        System.out.println("First few values for head 0, dimension 0:");
        for (int t = 0; t <= Math.min(pos, 3); t++) {
            int valueOffset = loff + t * kvDim + (0 / kvMul) * headSize + 0;
            System.out.printf("Position %d: %.6f%n", t, valueCache.get(valueOffset));
        }

        // Calculate expected results sequentially
        computeWeightedSumSequential(numHeads, seqLen, pos, headSize, kvDim, kvMul, loff, attScores, valueCache, expectedOutput);

        // Create kernel context
        KernelContext context = new KernelContext();

        // Set up test
        int globalSize = numHeads * localSize;
        WorkerGrid worker = new WorkerGrid1D(globalSize);
        worker.setGlobalWork(globalSize, 1, 1);
        worker.setLocalWork(localSize, 1, 1);
        GridScheduler gridScheduler = new GridScheduler("s0.weightedSum", worker);

        // Define task for computing weighted sum
        TaskGraph taskGraph = new TaskGraph("s0")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, attScores, valueCache)
                .task("weightedSum", TestWeightedSum::computeWeightedSumDebug,
                        context, pos, seqLen, attScores, valueCache, output, kvDim, kvMul, headSize, loff, debugData)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output, debugData);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler).execute();
        }

        // Print the expected and actual values for head 0, dimension 0
        System.out.println("\nResults comparison for head 0, dimension 0:");
        System.out.printf("Expected: %.6f, Actual: %.6f%n",
                expectedOutput.get(0), output.get(0));

        // Print the first thread's debug data for each head
        System.out.println("\nDebug data for first thread of each head:");
        for (int h = 0; h < numHeads; h++) {
            int debugOffset = h * localSize * 5;
            System.out.printf("Head %d: headIdx=%.0f, threadId=%.0f, attOffset=%.0f, outputOffset=%.0f, blockDim=%.0f%n",
                    h,
                    debugData.get(debugOffset),
                    debugData.get(debugOffset + 1),
                    debugData.get(debugOffset + 2),
                    debugData.get(debugOffset + 3),
                    debugData.get(debugOffset + 4));
        }

        // Compare the first few elements of each head
        System.out.println("\nComparison of first few elements for each head:");
        for (int h = 0; h < numHeads; h++) {
            for (int i = 0; i < Math.min(headSize, 3); i++) {
                int idx = h * headSize + i;
                System.out.printf("Head %d, Dim %d: Expected=%.6f, Actual=%.6f, Diff=%.6f%n",
                        h, i,
                        expectedOutput.get(idx),
                        output.get(idx),
                        Math.abs(expectedOutput.get(idx) - output.get(idx)));
            }
            System.out.println();
        }

        // Verify results
        for (int h = 0; h < numHeads; h++) {
            for (int i = 0; i < headSize; i++) {
                int idx = h * headSize + i;
                assertEquals(String.format("Weighted sum mismatch at head %d, dimension %d", h, i),
                        expectedOutput.get(idx), output.get(idx),
                        Math.abs(expectedOutput.get(idx) * 1e-4f));
            }
        }
    }

    /**
     * Test to compare the original weighted sum function with our debug version
     */
    @Test
    public void testCompareOriginalAndDebugVersions() throws TornadoExecutionPlanException {
        final int numHeads = 4;
        final int seqLen = 16;
        final int pos = 15;
        final int headSize = 32;
        final int localSize = 16;
        final int kvDim = headSize * numHeads;
        final int kvMul = 1;
        final int loff = 0;

        // Allocate data arrays
        FloatArray attScores = new FloatArray(numHeads * seqLen);
        FloatArray valueCache = new FloatArray(seqLen * kvDim);
        FloatArray outputOriginal = new FloatArray(numHeads * headSize);
        FloatArray outputDebug = new FloatArray(numHeads * headSize);
        FloatArray debugData = new FloatArray(numHeads * localSize * 5);

        // Initialize with random but deterministic data
        Random random = new Random(42);
        for (int i = 0; i < attScores.getSize(); i++) {
            attScores.set(i, random.nextFloat());
        }
        for (int i = 0; i < valueCache.getSize(); i++) {
            valueCache.set(i, random.nextFloat() * 2 - 1);
        }

        // Normalize attention scores to sum to 1 for each head
        for (int h = 0; h < numHeads; h++) {
            int attOffset = h * seqLen;
            float sum = 0.0f;

            for (int t = 0; t <= pos; t++) {
                sum += attScores.get(attOffset + t);
            }

            for (int t = 0; t <= pos; t++) {
                attScores.set(attOffset + t, attScores.get(attOffset + t) / sum);
            }
        }

        // Create kernel context
        KernelContext context = new KernelContext();

        // Set up global and local sizes
        int globalSize = numHeads * localSize;

        // Test original implementation
        WorkerGrid worker1 = new WorkerGrid1D(globalSize);
        worker1.setGlobalWork(globalSize, 1, 1);
        worker1.setLocalWork(localSize, 1, 1);
        GridScheduler gridScheduler1 = new GridScheduler("s0.weightedSum", worker1);

        TaskGraph taskGraph1 = new TaskGraph("s0")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, attScores, valueCache)
                .task("weightedSum", TestMultiHeadAttention::computeWeightedSum,
                        context, pos, seqLen, attScores, valueCache, outputOriginal, kvDim, kvMul, headSize, loff)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outputOriginal);

        ImmutableTaskGraph immutableTaskGraph1 = taskGraph1.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph1)) {
            executionPlan.withGridScheduler(gridScheduler1).execute();
        }

        // Test debug implementation
        WorkerGrid worker2 = new WorkerGrid1D(globalSize);
        worker2.setGlobalWork(globalSize, 1, 1);
        worker2.setLocalWork(localSize, 1, 1);
        GridScheduler gridScheduler2 = new GridScheduler("s1.weightedSum", worker2);

        TaskGraph taskGraph2 = new TaskGraph("s1")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, attScores, valueCache)
                .task("weightedSum", TestWeightedSum::computeWeightedSumDebug,
                        context, pos, seqLen, attScores, valueCache, outputDebug, kvDim, kvMul, headSize, loff, debugData)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outputDebug, debugData);

        ImmutableTaskGraph immutableTaskGraph2 = taskGraph2.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph2)) {
            executionPlan.withGridScheduler(gridScheduler2).execute();
        }

        // Compare results
        System.out.println("Comparing original and debug implementations:");
        boolean allMatch = true;
        for (int h = 0; h < numHeads; h++) {
            for (int i = 0; i < headSize; i++) {
                int idx = h * headSize + i;
                float orig = outputOriginal.get(idx);
                float debug = outputDebug.get(idx);
                float diff = Math.abs(orig - debug);

                if (diff > 1e-5f) {
                    System.out.printf("Mismatch at head %d, dim %d: Original=%.6f, Debug=%.6f, Diff=%.6f%n",
                            h, i, orig, debug, diff);
                    allMatch = false;
                }
            }
        }

        if (allMatch) {
            System.out.println("All values match between original and debug implementations!");
        }

        // Check for specific element that failed in the main test
        System.out.println("\nSpecific check for head 0, dimension 0:");
        System.out.printf("Original: %.6f, Debug: %.6f%n",
                outputOriginal.get(0), outputDebug.get(0));

        // Verify debug data to ensure correct head index and thread assignment
        System.out.println("\nChecking head indices from debug data:");
        for (int h = 0; h < numHeads; h++) {
            int threadId = 0; // First thread in each work group
            int debugOffset = h * localSize * 5 + threadId * 5;
            float headIdx = debugData.get(debugOffset);
            System.out.printf("Expected head index %d, got %.0f%n", h, headIdx);
            assertEquals("Head index mismatch", h, (int)headIdx);
        }
    }
}
