package uk.ac.manchester.tornado.unittests.spirv;

import static org.junit.Assert.assertArrayEquals;

import java.util.Arrays;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

public class MultipleRuns extends TornadoTestBase {

    @Test
    public void multipleRuns() {

        final int numElements = 512;
        int[] a = new int[numElements];

        final int iterations = 50;

        int[] expectedResult = new int[numElements];
        Arrays.fill(expectedResult, iterations * 50);

        TaskSchedule ts = new TaskSchedule("s0") //
                .streamIn(a) //
                .task("t0", TestKernels::addValue, a) //
                .streamOut(a); //

        for (int i = 0; i < iterations; i++) {
            ts.execute();
        }
        assertArrayEquals(expectedResult, a);
    }
}
