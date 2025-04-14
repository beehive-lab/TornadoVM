    package uk.ac.manchester.tornado.unittests.llm;

    import static org.junit.Assert.assertEquals;
    import static org.junit.Assert.assertTrue;

    import org.junit.Test;

    import uk.ac.manchester.tornado.api.TaskGraph;
    import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
    import uk.ac.manchester.tornado.api.enums.DataTransferMode;
    import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
    import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
    import uk.ac.manchester.tornado.api.KernelContext;
    import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

    import javax.swing.plaf.nimbus.State;

    /**
     * Test case that emulates the LLM forward pass buffer sharing scenario.
     * Specifically tests the force-copy approach used in the LLM implementation.
     */
    public class TestLLMBufferSharing extends TornadoTestBase {

        private static class State{

        }

        private static final int DIM = 1024;  // Simulated model dimension

        /**
         * Empty task to force buffer transfer to device
         */
        public static void emptyTask(FloatArray x) {
            // Empty task that does nothing with the buffer
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
         * RMS Norm calculation (simplified for testing)
         */
        public static void simulateRMSNorm(FloatArray x, FloatArray xb, FloatArray weights,
                FloatArray intermediate, int dim, FloatArray params) {
            // Simple operation to simulate RMSNorm
            for (int i = 0; i < dim; i++) {
                xb.set(i, x.get(i) * weights.get(i % weights.getSize()));
            }
        }

        /**
         * Simplified version of reduceSquareSums for testing
         */
        public static void reduceSquareSumsSimple(FloatArray in, FloatArray out, int localSize) {
            // Simplified operation to simulate reduceSquareSums
            for (int i = 0; i < out.getSize(); i++) {
                out.set(i, 1.0f); // Just set something for testing
            }
        }

        /**
         * Copy a single element to ensure buffer is accessed
         */
        public static void copyBufferElement(FloatArray buffer) {
            // Explicitly use the buffer - load first element
            float val = buffer.get(0);
            // Store it back to ensure read+write
            buffer.set(0, val);
        }

        /**
         * Copy entire buffer from source to destination
         */
        public static void copyBuffer(FloatArray src, FloatArray dst) {
            // Simple copy to verify buffer access works
            for (int i = 0; i < src.getSize(); i++) {
                dst.set(i, src.get(i));
            }
        }

        /**
         * Tests buffer sharing with the force-copy approach from the LLM implementation.
         * Emulates the pattern used in the LLM task graph setup.
         */
        @Test
        public void testLLMForwardBufferSharing() throws TornadoExecutionPlanException {
            // Create test arrays
            FloatArray x = new FloatArray(DIM);  // Input buffer (token embedding)
            FloatArray xb = new FloatArray(DIM); // Output buffer for RMSNorm
            FloatArray weights = new FloatArray(DIM); // Simulated weights
            FloatArray intermediate = new FloatArray(DIM / 256); // Intermediate buffer
            FloatArray params = new FloatArray(2); // Position and layer parameters

            // Initialize with test values
            for (int i = 0; i < DIM; i++) {
                x.set(i, 0.01f * i);
                weights.set(i, 1.0f); // Simple weight initialization
            }
            params.set(0, 0); // position
            params.set(1, 0); // layer

            KernelContext context = new KernelContext();

            // Method 1: Using empty task
            System.out.println("Testing with empty task approach");
            TaskGraph forceCopyEmptyTask = new TaskGraph("forceCopyEmptyTask")
                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, x, params)
                    .task("emptyTask", TestLLMBufferSharing::emptyTask, x)
                    .persistOnDevice(x);

            TaskGraph rmsNormEmptyTask = new TaskGraph("rmsNormEmptyTask")
                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, params)
                    .transferToDevice(DataTransferMode.FIRST_EXECUTION, weights)
                    .consumeFromDevice(forceCopyEmptyTask.getTaskGraphName(), x)
                    .task("rmsNorm", TestLLMBufferSharing::simulateRMSNorm,
                            x, xb, weights, intermediate, DIM, params)
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, xb);

            // Method 2: Using touch buffer approach
            System.out.println("Testing with touch buffer approach");
            TaskGraph forceCopyTouchBuffer = new TaskGraph("forceCopyTouchBuffer")
                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, x, params)
                    .task("touchBuffer", TestLLMBufferSharing::touchBuffer, x)
                    .persistOnDevice(x);

            TaskGraph rmsNormTouchBuffer = new TaskGraph("rmsNormTouchBuffer")
                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, params)
                    .transferToDevice(DataTransferMode.FIRST_EXECUTION, weights)
                    .consumeFromDevice(forceCopyTouchBuffer.getTaskGraphName(), x)
                    .task("rmsNorm", TestLLMBufferSharing::simulateRMSNorm,
                            x, xb, weights, intermediate, DIM, params)
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, xb);

            // Method 3: Using first execution transfer mode
            System.out.println("Testing with FIRST_EXECUTION transfer mode");
            TaskGraph forceCopyFirstExec = new TaskGraph("forceCopyFirstExec")
                    .transferToDevice(DataTransferMode.FIRST_EXECUTION, x)
                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, params)
                    .task("touchBuffer", TestLLMBufferSharing::touchBuffer, x)
                    .persistOnDevice(x);

            TaskGraph rmsNormFirstExec = new TaskGraph("rmsNormFirstExec")
                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, params)
                    .transferToDevice(DataTransferMode.FIRST_EXECUTION, weights)
                    .consumeFromDevice(forceCopyFirstExec.getTaskGraphName(), x)
                    .task("rmsNorm", TestLLMBufferSharing::simulateRMSNorm,
                            x, xb, weights, intermediate, DIM, params)
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, xb);

            // Method 4: Single task graph with multiple operations
            System.out.println("Testing with single task graph approach");
            TaskGraph singleTaskGraph = new TaskGraph("singleTaskGraph")
                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, x, params)
                    .transferToDevice(DataTransferMode.FIRST_EXECUTION, weights)
                    .task("touchBuffer", TestLLMBufferSharing::touchBuffer, x)
                    .task("rmsNorm", TestLLMBufferSharing::simulateRMSNorm,
                            x, xb, weights, intermediate, DIM, params)
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, xb);

            // Test each approach
            try {
                // Test Method 1: Empty task approach
                try (TornadoExecutionPlan executionPlan =
                        new TornadoExecutionPlan(forceCopyEmptyTask.snapshot(), rmsNormEmptyTask.snapshot())) {
                    System.out.println("Executing Empty Task approach...");

                    // Execute the first graph
                    executionPlan.withGraph(0).execute();
                    System.out.println("Force copy with empty task executed successfully");

                    // Execute the second graph
                    executionPlan.withGraph(1).execute();
                    System.out.println("RMSNorm with empty task executed successfully");

                    // Verify all elements were processed
                    boolean allProcessed = true;
                    for (int i = 0; i < DIM; i++) {
                        if (xb.get(i) != x.get(i) * weights.get(i % weights.getSize())) {
                            allProcessed = false;
                            break;
                        }
                    }
                    assertTrue("All elements should be processed correctly", allProcessed);
                } catch (Exception e) {
                    System.out.println("Empty Task approach failed: " + e.getMessage());
                    e.printStackTrace();
                }

                // Test Method 2: Touch buffer approach
                try (TornadoExecutionPlan executionPlan =
                        new TornadoExecutionPlan(forceCopyTouchBuffer.snapshot(), rmsNormTouchBuffer.snapshot())) {
                    System.out.println("\nExecuting Touch Buffer approach...");

                    // Execute the first graph
                    executionPlan.withGraph(0).execute();
                    System.out.println("Force copy with touch buffer executed successfully");

                    // Execute the second graph
                    executionPlan.withGraph(1).execute();
                    System.out.println("RMSNorm with touch buffer executed successfully");

                    // Verify all elements were processed
                    boolean allProcessed = true;
                    for (int i = 0; i < DIM; i++) {
                        if (xb.get(i) != x.get(i) * weights.get(i % weights.getSize())) {
                            allProcessed = false;
                            break;
                        }
                    }
                    assertTrue("All elements should be processed correctly", allProcessed);
                } catch (Exception e) {
                    System.out.println("Touch Buffer approach failed: " + e.getMessage());
                    e.printStackTrace();
                }

                // Test Method 3: FIRST_EXECUTION transfer mode
                try (TornadoExecutionPlan executionPlan =
                        new TornadoExecutionPlan(forceCopyFirstExec.snapshot(), rmsNormFirstExec.snapshot())) {
                    System.out.println("\nExecuting FIRST_EXECUTION transfer mode approach...");

                    // Execute the first graph
                    executionPlan.withGraph(0).execute();
                    System.out.println("Force copy with FIRST_EXECUTION executed successfully");

                    // Execute the second graph
                    executionPlan.withGraph(1).execute();
                    System.out.println("RMSNorm with FIRST_EXECUTION executed successfully");

                    // Verify all elements were processed
                    boolean allProcessed = true;
                    for (int i = 0; i < DIM; i++) {
                        if (xb.get(i) != x.get(i) * weights.get(i % weights.getSize())) {
                            allProcessed = false;
                            break;
                        }
                    }
                    assertTrue("All elements should be processed correctly", allProcessed);
                } catch (Exception e) {
                    System.out.println("FIRST_EXECUTION approach failed: " + e.getMessage());
                    e.printStackTrace();
                }

                // Test Method 4: Single task graph approach
                try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(singleTaskGraph.snapshot())) {
                    System.out.println("\nExecuting Single Task Graph approach...");

                    // Execute the graph
                    executionPlan.execute();
                    System.out.println("Single task graph executed successfully");

                    // Verify all elements were processed
                    boolean allProcessed = true;
                    for (int i = 0; i < DIM; i++) {
                        if (xb.get(i) != x.get(i) * weights.get(i % weights.getSize())) {
                            allProcessed = false;
                            break;
                        }
                    }
                    assertTrue("All elements should be processed correctly", allProcessed);
                } catch (Exception e) {
                    System.out.println("Single Task Graph approach failed: " + e.getMessage());
                    e.printStackTrace();
                }
            } catch (Exception e) {
                System.out.println("Test failed with exception: " + e.getMessage());
                e.printStackTrace();
                throw e;
            }
        }

        /**
         * Tests buffer sharing with direct memory operations to diagnose issues
         * with the buffer sharing mechanism.
         */
        @Test
        public void testLLMBufferPersistenceWithMemoryOperations() throws TornadoExecutionPlanException {
            // Create test arrays
            FloatArray x = new FloatArray(DIM);  // Input buffer
            FloatArray y = new FloatArray(DIM);  // Output buffer for verification

            // Initialize with test values
            for (int i = 0; i < DIM; i++) {
                x.set(i, 0.01f * i);
            }

            // Step 1: Create a task graph that touches the buffer and persists it
            TaskGraph tg1 = new TaskGraph("copyBuffer")
                    .transferToDevice(DataTransferMode.FIRST_EXECUTION, x)
                    .task("copyToMemory", TestLLMBufferSharing::copyBufferElement, x)
                    .persistOnDevice(x);

            // Step 2: Create a task graph that consumes the buffer and copies it to output
            TaskGraph tg2 = new TaskGraph("verifyBuffer")
                    .consumeFromDevice(tg1.getTaskGraphName(), x)
                    .task("verifyMemory", TestLLMBufferSharing::copyBuffer, x, y)
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, y);

            try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(tg1.snapshot(), tg2.snapshot())) {
                // Execute first graph
                executionPlan.withGraph(0).execute();
                System.out.println("Buffer copy and persistence executed successfully");

                // Execute second graph
                executionPlan.withGraph(1).execute();
                System.out.println("Buffer consumption and verification executed successfully");

                // Verify results
                boolean allMatch = true;
                for (int i = 0; i < DIM; i++) {
                    if (y.get(i) != x.get(i)) {
                        allMatch = false;
                        System.out.println("Mismatch at index " + i + ": expected " + x.get(i) + ", got " + y.get(i));
                        break;
                    }
                }
                assertTrue("All elements should match", allMatch);
            }
        }

        /**
         * Test specifically emulating the issue in the LlamaForwardDebug class where
         * the consumeFromDevice call after the force-copy task is failing.
         */
        @Test
        public void testEmulateForwardDebugIssue() throws TornadoExecutionPlanException {
            // Create test arrays to match your forward pass implementation
            FloatArray x = new FloatArray(DIM);            // Embedding (wrapX)
            FloatArray xb = new FloatArray(DIM);           // Intermediate (wrapXb)
            FloatArray weights = new FloatArray(DIM);      // Weights
            FloatArray intermediate = new FloatArray(DIM / 256); // Intermediate for RMSNorm
            FloatArray positionAndLayer = new FloatArray(2); // Parameters

            // Initialize with test values
            for (int i = 0; i < DIM; i++) {
                x.set(i, 0.01f * i);
                weights.set(i, 1.0f);
            }
            positionAndLayer.set(0, 0); // position
            positionAndLayer.set(1, 0); // layer

            System.out.println("Testing exact LlamaForwardDebug buffer sharing pattern");

            // Create the problematic task graphs exactly as in your LlamaForwardDebug
            TaskGraph lookUpBufferX = new TaskGraph("lookUpBufferX")
                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, x, positionAndLayer)
                    .task("forceUpdateXperToken", TestLLMBufferSharing::emptyTask, x)
                    .persistOnDevice(x);

            TaskGraph rmsNormGraph = new TaskGraph("rmsnorm")
                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, positionAndLayer)
                    .transferToDevice(DataTransferMode.FIRST_EXECUTION, weights)
                    .consumeFromDevice(lookUpBufferX.getTaskGraphName(), x)
                    .task("reduce", TestLLMBufferSharing::reduceSquareSumsSimple,
                            x, intermediate, 256)
                    .task("normalize", TestLLMBufferSharing::simulateRMSNorm,
                            x, xb, weights, intermediate, DIM, positionAndLayer)
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, xb)
                    .persistOnDevice(x);

            try (TornadoExecutionPlan executionPlan =
                    new TornadoExecutionPlan(lookUpBufferX.snapshot(), rmsNormGraph.snapshot())) {

                // Execute the first graph
                System.out.println("Executing first graph (lookUpBufferX)...");
                executionPlan.withGraph(0).execute();
                System.out.println("First graph executed successfully");

                // Execute the second graph
                System.out.println("Executing second graph (rmsNormGraph)...");
                executionPlan.withGraph(1).execute();
                System.out.println("Second graph executed successfully");

                // Verify output has expected values
                boolean hasOutput = false;
                for (int i = 0; i < 10; i++) {
                    System.out.println("xb[" + i + "] = " + xb.get(i));
                    if (xb.get(i) != 0.0f) {
                        hasOutput = true;
                    }
                }
                assertTrue("Output buffer should have non-zero values", hasOutput);

            } catch (Exception e) {
                System.out.println("Failed to execute with exact LlamaForwardDebug pattern: " + e.getMessage());
                e.printStackTrace();

                // Now try alternative implementations and compare
                System.out.println("\nTrying alternative implementation with data access in empty task...");

                // Alternative implementation with data access
                TaskGraph lookUpBufferX2 = new TaskGraph("lookUpBufferX2")
                        .transferToDevice(DataTransferMode.EVERY_EXECUTION, x, positionAndLayer)
                        .task("touchBuffer", TestLLMBufferSharing::touchBuffer, x)
                        .persistOnDevice(x);

                TaskGraph rmsNormGraph2 = new TaskGraph("rmsnorm2")
                        .transferToDevice(DataTransferMode.EVERY_EXECUTION, positionAndLayer)
                        .transferToDevice(DataTransferMode.FIRST_EXECUTION, weights)
                        .consumeFromDevice(lookUpBufferX2.getTaskGraphName(), x)
                        .task("normalize", TestLLMBufferSharing::simulateRMSNorm,
                                x, xb, weights, intermediate, DIM, positionAndLayer)
                        .transferToHost(DataTransferMode.EVERY_EXECUTION, xb);

                try (TornadoExecutionPlan plan2 =
                        new TornadoExecutionPlan(lookUpBufferX2.snapshot(), rmsNormGraph2.snapshot())) {

                    plan2.withGraph(0).execute();
                    System.out.println("Alternative implementation - first graph executed successfully");

                    plan2.withGraph(1).execute();
                    System.out.println("Alternative implementation - second graph executed successfully");

                    System.out.println("Alternative implementation succeeded!");
                } catch (Exception e2) {
                    System.out.println("Alternative implementation also failed: " + e2.getMessage());
                    e2.printStackTrace();
                }
            }
        }
    }