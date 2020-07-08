package uk.ac.manchester.tornado.drivers.cuda;

import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.drivers.cuda.graal.PTXInstalledCode;
import uk.ac.manchester.tornado.drivers.cuda.runtime.CUDATornadoDevice;
import uk.ac.manchester.tornado.runtime.common.CallStack;
import uk.ac.manchester.tornado.runtime.common.DeviceObjectState;
import uk.ac.manchester.tornado.runtime.common.Tornado;
import uk.ac.manchester.tornado.runtime.tasks.GlobalObjectState;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

import java.util.ArrayList;

import static uk.ac.manchester.tornado.runtime.common.Tornado.getProperty;

public class CUDA {

    public static final String CUDA_JNI_LIBRARY = "tornado-cuda";

    public final static int CALL_STACK_LIMIT = Integer.parseInt(getProperty("tornado.cuda.callstack.limit", "8192"));
    private static final CUDAPlatform platform;
    private static boolean initialised = false;

    static {
        System.loadLibrary(CUDA_JNI_LIBRARY);

        initialise();
        platform = new CUDAPlatform();

        // add a shutdown hook to free-up all CUDA resources on VM exit
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                setName("CUDA-Cleanup-Thread");
                CUDA.cleanup();
            }
        });
    }

    private native static void cuInit();

    private static void initialise() {
        if (initialised) return;

        cuInit();
        initialised = true;
    }

    public static void cleanup() {
        platform.cleanup();
    }

    public static CUDAPlatform getPlatform() {
        return platform;
    }

    public static CUDATornadoDevice defaultDevice() {
        final int deviceIndex = Integer.parseInt(Tornado.getProperty("tornado.device", "0"));
        return new CUDATornadoDevice(deviceIndex);
    }

    public static void run(CUDATornadoDevice tornadoDevice, PTXInstalledCode openCLCode, TaskMetaData taskMeta, Access[] accesses, Object... parameters) {
        if (parameters.length != accesses.length) {
            throw new TornadoRuntimeException("[ERROR] Accesses and objects array should match in size");
        }

        // Copy-in variables
        ArrayList<DeviceObjectState> states = new ArrayList<>();
        for (int i = 0; i < accesses.length; i++) {
            Access access = accesses[i];
            Object object = parameters[i];

            GlobalObjectState globalState = new GlobalObjectState();
            DeviceObjectState deviceState = globalState.getDeviceState(tornadoDevice);

            switch (access) {
                case READ_WRITE:
                case READ:
                    tornadoDevice.ensurePresent(object, deviceState, null, 0, 0);
                    break;
                case WRITE:
                    tornadoDevice.ensureAllocated(object, 0, deviceState);
                default:
                    break;
            }
            states.add(deviceState);
        }

        // Create stack
        final int numArgs = parameters.length;
        CallStack stack = tornadoDevice.createStack(numArgs);
        for (int i = 0; i < numArgs; i++) {
            stack.push(parameters[i], states.get(i));
        }

        // Run the code
        openCLCode.launchWithoutDependencies(stack, taskMeta, 0);

        // Obtain the result
        for (int i = 0; i < accesses.length; i++) {
            Access access = accesses[i];
            switch (access) {
                case READ_WRITE:
                case WRITE:
                    Object object = parameters[i];
                    DeviceObjectState deviceState = states.get(i);
                    tornadoDevice.streamOutBlocking(object, 0, deviceState, null);
                default:
                    break;
            }
        }
    }
}
