package tornado.examples.memory;

import java.util.Arrays;
import tornado.common.DeviceObjectState;
import tornado.drivers.opencl.OpenCL;
import tornado.drivers.opencl.runtime.OCLDeviceMapping;
import tornado.runtime.api.GlobalObjectState;

import static tornado.runtime.TornadoRuntime.getTornadoRuntime;

public class DataMovementTest {

    private static void printArray(int[] array) {
        System.out.printf("array = [");
        for (int value : array) {
            System.out.printf("%d ", value);
        }
        System.out.println("]");
    }

    public static void main(String[] args) {

        int size = args.length == 1 ? Integer.parseInt(args[0]) : 16;
        int[] array = new int[size];
        Arrays.setAll(array, (index) -> index);
        printArray(array);

        OCLDeviceMapping device = OpenCL.defaultDevice();

        GlobalObjectState state = getTornadoRuntime().resolveObject(array);
        DeviceObjectState deviceState = state.getDeviceState(device);

        int writeEvent = device.ensurePresent(array, deviceState);
        if (writeEvent != -1) {
            device.resolveEvent(writeEvent).waitOn();
        }

        Arrays.fill(array, -1);
        printArray(array);

        int readEvent = device.streamOut(array, deviceState, null);
        device.resolveEvent(readEvent).waitOn();

        printArray(array);

//		System.out.printf("write: %.4e s\n",writeTask.getExecutionTime());
//		System.out.printf("read : %.4e s\n",readTask.getExecutionTime());
    }

}
