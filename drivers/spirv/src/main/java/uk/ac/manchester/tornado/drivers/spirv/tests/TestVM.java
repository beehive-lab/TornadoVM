package uk.ac.manchester.tornado.drivers.spirv.tests;

import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVBackend;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVDriver;
import uk.ac.manchester.tornado.drivers.spirv.runtime.SPIRVTornadoDevice;
import uk.ac.manchester.tornado.runtime.TornadoCoreRuntime;
import uk.ac.manchester.tornado.runtime.common.DeviceObjectState;
import uk.ac.manchester.tornado.runtime.tasks.GlobalObjectState;

public class TestVM {

    public TornadoDevice invokeSPIRVBackend() {

        // Get Tornado Runtime
        TornadoCoreRuntime tornadoRuntime = TornadoCoreRuntime.getTornadoRuntime();

        // Get the backend from TornadoVM
        SPIRVBackend spirvBackend = tornadoRuntime.getDriver(SPIRVDriver.class).getDefaultBackend();

        // Get a Device
        TornadoDevice device = tornadoRuntime.getDriver(SPIRVDriver.class).getDefaultDevice();

        System.out.println("Selecting Device: " + device.getPhysicalDevice().getDeviceName());

        System.out.println("BACKEND: " + spirvBackend);

        return device;

    }

    public void runWithTornadoVM(SPIRVTornadoDevice device, int[] a, int[] b, int[] c) {

        System.out.println("Running Runtime For Buffer creation and copy");

        // We allocate buffer A
        GlobalObjectState stateA = new GlobalObjectState();
        DeviceObjectState objectStateA = stateA.getDeviceState(device);

        // We allocate buffer B
        GlobalObjectState stateB = new GlobalObjectState();
        DeviceObjectState objectStateB = stateB.getDeviceState(device);

        // We allocate buffer C
        GlobalObjectState stateC = new GlobalObjectState();
        DeviceObjectState objectStateC = stateC.getDeviceState(device);

        // Copy-in buffer
        device.ensurePresent(a, objectStateA, null, 0, 0);

        // Allocate buffer B
        device.ensureAllocated(b, 0, objectStateB);

        // Stream IN
        device.streamIn(c, 0, 0, objectStateC, null);

    }

    public void test() {
        TornadoDevice device = invokeSPIRVBackend();
        int[] a = new int[64];
        int[] b = new int[64];
        int[] c = new int[64];

        if (device instanceof SPIRVTornadoDevice) {
            runWithTornadoVM((SPIRVTornadoDevice) device, a, b, c);
        }
    }

    public static void main(String[] args) {
        System.out.print("Running Native: uk.ac.manchester.tornado.drivers.spirv.tests.TestVM");
        new TestVM().test();
    }
}
