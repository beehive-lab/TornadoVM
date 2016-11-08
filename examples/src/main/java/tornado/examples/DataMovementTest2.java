package tornado.examples;

import java.util.Random;
import tornado.collections.types.ImageFloat;
import tornado.common.DeviceObjectState;
import tornado.drivers.opencl.OpenCL;
import tornado.drivers.opencl.runtime.OCLDeviceMapping;
import tornado.runtime.api.GlobalObjectState;

import static tornado.runtime.TornadoRuntime.getTornadoRuntime;

public class DataMovementTest2 {

    private static void printArray(int[] array) {
        System.out.printf("array = [");
        for (int value : array) {
            System.out.printf("%d ", value);
        }
        System.out.println("]");
    }

    public static void main(String[] args) {

        int sizeX = args.length == 2 ? Integer.parseInt(args[0]) : 16;
        int sizeY = args.length == 2 ? Integer.parseInt(args[1]) : 16;

        ImageFloat image = new ImageFloat(sizeX, sizeY);
        final Random rand = new Random();

        for (int y = 0; y < sizeY; y++) {
            for (int x = 0; x < sizeX; x++) {
                image.set(x, y, rand.nextFloat());
            }
        }

        System.out.println("Before: ");
        System.out.printf(image.toString());

        OCLDeviceMapping device = OpenCL.defaultDevice();

        GlobalObjectState state = getTornadoRuntime().resolveObject(image);
        DeviceObjectState deviceState = state.getDeviceState(device);

        int writeEvent = device.ensurePresent(image, deviceState);
        if (writeEvent != -1) {
            device.resolveEvent(writeEvent).waitOn();
        }

        image.fill(-1);
        System.out.println("Reset: ");
        System.out.printf(image.toString());

        int readEvent = device.streamOut(image, deviceState, null);
        device.resolveEvent(readEvent).waitOn();

        System.out.println("After: ");
        System.out.printf(image.toString());

//		System.out.printf("write: %.4e s\n",writeTask.getExecutionTime());
//		System.out.printf("read : %.4e s\n",readTask.getExecutionTime());
    }

}
