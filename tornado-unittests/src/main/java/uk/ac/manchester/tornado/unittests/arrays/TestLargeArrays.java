package uk.ac.manchester.tornado.unittests.arrays;

import org.junit.Assume;
import org.junit.Test;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntimeProvider;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;

/**
 * How to test?
 *
 * <p>
 * <code>
 * tornado-test -V -J"-Dtornado.device.memory=3GB" uk.ac.manchester.tornado.unittests.arrays.TestLargeArrays
 * </code>
 * </p>
 */
public class TestLargeArrays extends TornadoTestBase {

    @Override
    public void before() {
        boolean hasRequiredDeviceMemory = checkDeviceMemory();
        System.out.println("TestLargeArrays 1");

        // Skip all tests if device memory requirement not met
        Assume.assumeTrue(
                "Skipping TestLargeArrays: requires > 3GB global memory",
                hasRequiredDeviceMemory
        );
    }

    public static boolean checkDeviceMemory() {
        long mem = TornadoRuntimeProvider.getTornadoRuntime()
                .getDefaultDevice()
                .getMaxGlobalMemory();

        return mem > 3L * 1024 * 1024 * 1024;
    }

    public static void addAccumulator(FloatArray a, float value) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, a.get(i) + value);
        }
    }

    @Test
    public void testLargeFloatArray() throws TornadoExecutionPlanException {
        //final int numElements = Integer.MAX_VALUE; // last overflow
        //final int numElements = 600000000; // overflow
        //final int numElements = 550000000; // overflow
        final int numElements = 540000000; // overflow
        //final int numElements = 535000000; // safe
        //final int numElements = 530000000; // safe
        //final int numElements = 525000000; // safe
        //final int numElements = 500500000; // safe
        FloatArray a = new FloatArray(numElements);

        IntStream.range(0, numElements).sequential().forEach(i -> {
            a.set(i, (float) Math.random());
        });

        FloatArray b = FloatArray.fromSegment(a.getSegment());
        float accumulator = 1.0f;

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("t0", TestLargeArrays::addAccumulator, a, accumulator) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < a.getSize(); i++) {
            assertEquals(b.get(i) + accumulator, a.get(i), 0.01f);
        }
    }
}
