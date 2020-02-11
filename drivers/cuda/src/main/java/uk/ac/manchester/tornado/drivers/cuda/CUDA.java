package uk.ac.manchester.tornado.drivers.cuda;

import java.util.ArrayList;
import java.util.List;

public class CUDA {

    public static final String CUDA_JNI_LIBRARY = "tornado-cuda";

    private static final CUDAPlatform platform;
    private static boolean initialised = false;

    static {
        System.loadLibrary(CUDA_JNI_LIBRARY);

        initialise();
        platform = new CUDAPlatform();

        // add a shutdown hook to free-up all OpenCL resources on VM exit
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
}
