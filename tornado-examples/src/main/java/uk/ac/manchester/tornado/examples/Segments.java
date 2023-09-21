package uk.ac.manchester.tornado.examples;

import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;

public class Segments {

    static final int SIZE_BYTES = Integer.parseInt(System.getProperty("size.mb", "64")) * 10;
    static final int ARRAY_SIZE = SIZE_BYTES /8;

    public static void addConstantMemSeg(MemorySegment segment) {
        for (@Parallel int i = 0; i < ARRAY_SIZE; i++) {
            segment.setAtIndex(JAVA_LONG, i, segment.getAtIndex(JAVA_LONG, i) + 100);
        }
    }

    public static void addConstantArray(long[] array) {
        for (@Parallel int i = 0; i < ARRAY_SIZE; i++) {
            array[i] = array[i] + 100;
        }
    }

    public static void main (String[] args) {
        String driverAndDevice = System.getProperty("sArray.t0.device", "0:0");
        int driverNo = Integer.parseInt(driverAndDevice.split(":")[0]);
        int deviceNo = Integer.parseInt(driverAndDevice.split(":")[1]);

        TornadoDevice device = TornadoRuntime.getTornadoRuntime().getDriver(driverNo).getDevice(deviceNo);

        // ?? Subtract 24 because the array header will be included
        long[] array = new long[ARRAY_SIZE];

        //MemorySegment segment = TornadoRuntime.getTornadoRuntime().getPinnedBuffer(device, SIZE_BYTES);
        MemorySegment segment = Arena.ofAuto().allocate(SIZE_BYTES, 1);//TornadoRuntime.getTornadoRuntime().getPinnedBuffer(device, SIZE_BYTES);

        for (int i = 0; i < ARRAY_SIZE; i++) {
            array[i] = i;
            segment.setAtIndex(JAVA_LONG, i, i);
        }

        TaskGraph tsMemSeg;

        tsMemSeg = new TaskGraph("sMemseg")
                .task("t0", Segments::addConstantMemSeg, segment)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, segment);

        ImmutableTaskGraph immutableTaskGraph = tsMemSeg.snapshot();

        TornadoExecutionPlan tornadoExecutor = new TornadoExecutionPlan(immutableTaskGraph);
        //tornadoExecutor.withWarmUp();
        tornadoExecutor.execute();
        for (int i = 0; i < ARRAY_SIZE; i++) {
            System.out.println(segment.getAtIndex(JAVA_LONG, i));
        }


    }
}
