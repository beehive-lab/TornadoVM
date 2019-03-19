package uk.ac.manchester.tornado.unittests.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.stream.IntStream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.unittests.arrays.TestArrays;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

public class TestAPI extends TornadoTestBase {

    @Test
    public void testSyncObject() {

        final int N = 1024;
        int size = 20;

        int[] data = new int[N];

        IntStream.range(0, N).parallel().forEach(idx -> {
            data[idx] = size;
        });

        TaskSchedule s0 = new TaskSchedule("s0");
        assertNotNull(s0);

        s0.task("t0", TestArrays::addAccumulator, data, 1);

        s0.execute();

        s0.syncObject(data);

        for (int i = 0; i < N; i++) {
            assertEquals(21, data[i], 0.0001);

        }
    }

    @Test
    public void testSyncObjects() {

        final int N = 128;
        int size = 20;

        int[] data = new int[N];

        IntStream.range(0, N).parallel().forEach(idx -> {
            data[idx] = size;
        });

        TaskSchedule s0 = new TaskSchedule("s0");
        assertNotNull(s0);

        s0.task("t0", TestArrays::addAccumulator, data, 1);

        s0.execute();

        s0.syncObjects(data);

        for (int i = 0; i < N; i++) {
            assertEquals(21, data[i], 0.0001);

        }
    }

    @Test
    public void testWarmUp() {

        final int N = 128;
        int size = 20;

        int[] data = new int[N];

        IntStream.range(0, N).parallel().forEach(idx -> {
            data[idx] = size;
        });

        TaskSchedule s0 = new TaskSchedule("s0");
        assertNotNull(s0);

        s0.task("t0", TestArrays::addAccumulator, data, 1);

        s0.warmup();

        s0.execute();

        s0.syncObject(data);

        for (int i = 0; i < N; i++) {
            assertEquals(21, data[i], 0.0001);

        }
    }

}
