package uk.ac.manchester.tornado.drivers.opencl.tests;

import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.drivers.opencl.OCLCodeCache;
import uk.ac.manchester.tornado.drivers.opencl.OCLCommandQueue;
import uk.ac.manchester.tornado.drivers.opencl.OCLContext;
import uk.ac.manchester.tornado.drivers.opencl.OCLDevice;
import uk.ac.manchester.tornado.drivers.opencl.OCLDeviceContext;
import uk.ac.manchester.tornado.drivers.opencl.OCLDriver;
import uk.ac.manchester.tornado.drivers.opencl.OCLPlatform;
import uk.ac.manchester.tornado.drivers.opencl.OpenCL;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLInstalledCode;
import uk.ac.manchester.tornado.drivers.opencl.graal.backend.OCLBackend;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLCompilationResult;
import uk.ac.manchester.tornado.drivers.opencl.runtime.OCLTornadoDevice;
import uk.ac.manchester.tornado.runtime.TornadoCoreRuntime;
import uk.ac.manchester.tornado.runtime.common.CallStack;
import uk.ac.manchester.tornado.runtime.common.DeviceObjectState;
import uk.ac.manchester.tornado.runtime.tasks.GlobalObjectState;
import uk.ac.manchester.tornado.runtime.tasks.meta.ScheduleMetaData;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

public class TestOpenCLNative {

    // @formatter:off
    private static final String OPENCL_KERNEL = "_kernel void saxpy(__global float *a, \n" + 
            "                    __global float *b, \n" + 
            "                     __global float *c) {    \n" +  
            "    c[idx]  =  a[idx] + b[idx];\n" +   
            "\n" + 
            "}";
    // @formatter:on

    public static void main(String[] args) {
        int numPlatforms = OpenCL.getNumPlatforms();

        OCLPlatform platform = OpenCL.getPlatform(0);

        // Create context for the platform
        OCLContext oclContext = platform.createContext();

        int numDevices = oclContext.getNumDevices();

        OCLDevice device = OpenCL.getDevice(0, 0);

        OCLTornadoDevice tornadoDevice = (OCLTornadoDevice) TornadoRuntime.getTornadoRuntime().getDriver(0).getDevice(0);

        // Create command queue
        oclContext.createCommandQueue(0);

        OCLCommandQueue queue = oclContext.queues()[0];

        // 1. Compile the code:
        OCLDeviceContext deviceContext = oclContext.createDeviceContext(0);
        OCLCodeCache codeCache = new OCLCodeCache(deviceContext);

        TornadoCoreRuntime tornadoRuntime = TornadoCoreRuntime.getTornadoRuntime();
        OCLBackend backend = tornadoRuntime.getDriver(OCLDriver.class).getDefaultBackend();
        ScheduleMetaData scheduleMeta = new ScheduleMetaData("oclbackend");
        TaskMetaData meta = new TaskMetaData(scheduleMeta, "saxpy");
        new OCLCompilationResult("internal", "saxpy", meta, backend);

        byte[] source = OPENCL_KERNEL.getBytes();
        OCLInstalledCode code = codeCache.installSource(meta, "saxpy", "saxpy", source);

        if (code.getKernel() == null) {
            System.out.println("NULL KERNEL");
        }

        String generatedSourceCode = code.getGeneratedSourceCode();
        System.out.println("Compiled code: " + generatedSourceCode);

        // input data
        final int N = 128;
        int[] a = new int[N];
        int[] b = new int[N];
        double[] c = new double[N];

        GlobalObjectState stateA = new GlobalObjectState();
        DeviceObjectState objectStateA = stateA.getDeviceState(tornadoDevice);

        GlobalObjectState stateB = new GlobalObjectState();
        DeviceObjectState objectStateB = stateB.getDeviceState(tornadoDevice);

        GlobalObjectState stateC = new GlobalObjectState();
        DeviceObjectState objectStateC = stateC.getDeviceState(tornadoDevice);
        // Copy-IN A
        tornadoDevice.ensurePresent(a, objectStateA, null, 0, 0);
        // Copy-IN B
        tornadoDevice.ensurePresent(b, objectStateB, null, 0, 0);
        // Alloc C
        tornadoDevice.ensureAllocated(c, 0, objectStateC);

        // Create stack
        CallStack stack = tornadoDevice.createStack(3);
        stack.push(a, objectStateA);
        stack.push(b, objectStateB);
        stack.push(c, objectStateC);

        // Run the code
        code.launchWithoutDeps(stack, meta, 0);

        // Obtain the result
        tornadoDevice.streamOutBlocking(c, 0, objectStateC, null);

    }

}
