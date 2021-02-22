package uk.ac.manchester.tornado.drivers.spirv;

import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

/**
 * Proxy Intermediate Class for Calling JNI methods that can dispatch SPIRV
 * code.
 * <p>
 * There are currently two ways:
 * <p>
 * - Via OpenCL
 * <p>
 * - Via Intel Level Zero
 * <p>
 * This class is the equivalent of OCL or PTX
 */
public class SPIRVProxy {

    private static SPIRVDispatcher dispatcher;

    static {
        try {
            if (TornadoOptions.USE_LEVELZERO_FOR_SPIRV) {
                dispatcher = new SPIRVLevelZeroDriver();
            } else {
                dispatcher = new SPIRVOpenCLDriver();
            }
        } catch (ExceptionInInitializerError e) {
            System.out.println("[ERROR] Level-Zero Initialization is not correct: " + e.getMessage());
        }
    }

    public static int getNumPlatforms() {
        return dispatcher.getNumPlatforms();
    }

    public static SPIRVPlatform getPlatform(int platformIndex) {
        return dispatcher.getPlatform(platformIndex);
    }
}
