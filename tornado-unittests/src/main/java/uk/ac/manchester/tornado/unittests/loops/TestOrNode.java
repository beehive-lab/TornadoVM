package uk.ac.manchester.tornado.unittests.loops;

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
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;

import static org.junit.Assert.assertFalse;

/**
 * <p>
 * How to test?
 * </p>
 * <code>
 * tornado-test --printKernel -V uk.ac.manchester.tornado.unittests.loops.TestOrNode
 * </code>
 */
public class TestOrNode {

    private static void testKernel(KernelContext context, IntArray array) {
        int idx = context.globalIdx;

        int i = 1 - idx;
        int j = 1 + idx;

        boolean isInBounds = i < array.getSize() && j < array.getSize();
        array.set(idx, isInBounds ? 1 : 0);
    }

    @Test
    public void runKernel() {
        IntArray testArr = new IntArray(8);

        // When using the kernel-parallel API, we need to create a Grid and a Worker
        WorkerGrid workerGrid = new WorkerGrid1D(8);    // Create a 1D Worker
        GridScheduler gridScheduler = new GridScheduler("myCompute.mxm", workerGrid);  // Attach the worker to the Grid
        KernelContext context = new KernelContext();             // Create a context

        TaskGraph taskGraph = new TaskGraph("myCompute")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, testArr) // Transfer data from host to device only in the first execution
                .task("mxm", TestOrNode::testKernel, context, testArr)   // Each task points to an existing Java method
                .transferToHost(DataTransferMode.EVERY_EXECUTION, testArr);     // Transfer data from device to host

        // Create an immutable task-graph
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();

        // Create an execution plan from an immutable task-graph
        boolean caughtInternalException = false;
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler)
                    .execute();
        } catch (TornadoInternalError e) {
            System.out.println(e.getMessage());
            caughtInternalException = true;
        } catch (TornadoExecutionPlanException e) {
            throw new RuntimeException(e);
        }
        assertFalse(caughtInternalException);
    }
}
