package uk.ac.manchester.tornado.drivers.cuda;

import java.util.ArrayList;
import java.util.List;

public class CUDA {

    public static final String CUDA_JNI_LIBRARY = "tornado-cuda";

    private static final List<CUDAPlatform> platforms = new ArrayList<>();
    private static boolean initialised = false;

    static {
        System.loadLibrary(CUDA_JNI_LIBRARY);

        initialise();

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
        for (CUDAPlatform platform : platforms) {
            platform.cleanup();
        }
    }

    public static int getNumPlatforms() {
        return 1;
    }

    public static CUDAPlatform getPlatform(int index) {
        return new CUDAPlatform(0, 0);
    }
}
