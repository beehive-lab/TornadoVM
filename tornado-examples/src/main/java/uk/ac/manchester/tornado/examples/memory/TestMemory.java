package uk.ac.manchester.tornado.examples.memory;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;

/**
 * Stresses device allocation by copying larger {@code FloatArray} buffers
 * (about 1.5 GB up to 2 GB). May fail on OpenCL devices with a smaller cap.
 * <p>
 * Each size builds a {@code TaskGraph}, copies the input on every execution,
 * runs a {@code @Parallel} {@code moveData} task, and copies the output back.
 * </p>
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado --jvm="-Dtornado.device.memory=4GB" -m tornado.examples/uk.ac.manchester.tornado.examples.memory.TestMemory
 * </code>
 */
public class TestMemory {

    public static void moveData(FloatArray inputArray, FloatArray outputArray) {
        for (@Parallel int i = 0; i < inputArray.getSize(); i++) {
            outputArray.set(i, inputArray.get(i));
        }
    }

    public static void stressDataAllocationTest(int dataSizeFactor) throws TornadoExecutionPlanException {
        System.out.println("Allocating size: " + (dataSizeFactor * 4) + " (bytes)");
        FloatArray inputArray = new FloatArray(dataSizeFactor);
        FloatArray outputArray = new FloatArray(dataSizeFactor);
        inputArray.init(0.1f);
        TaskGraph taskGraph = new TaskGraph("memory" + dataSizeFactor) //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, inputArray) //
                .task("stress", TestMemory::moveData, inputArray, outputArray) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outputArray);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }
    }

    /**
     * Depending on the device, this test is expected to fail when using the
     * OpenCL backend.
     */

    public static void main(String[] args) {
        // Starting in ~1.5GB and move up to ~2GB
        for (int i = 400; i < 500; i += 10) {
            int size = 1024 * 1024 * i;
            try {
                stressDataAllocationTest(size);
            } catch (TornadoExecutionPlanException e) {
                e.printStackTrace();
            }
        }
    }
}
