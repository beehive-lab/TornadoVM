package uk.ac.manchester.tornado.drivers.spirv;

/**
 * Proxy Intermediate Class for Calling JNI methods that can dispatch SPIRV
 * code.
 * 
 * There are currently two ways:
 * 
 * - Via OpenCL
 * 
 * - Via Intel Level Zero
 * 
 * This class is the equivalent of OCL or PTX
 * 
 */
public class SPIRVProxy {

    private static SPIRVDispatcher dispatcher;

    static {
        // If some condition, then create either Level Zero or OCL Dispatcher
        dispatcher = new SPIRVLevelZeroDispatcher();
    }

    public static int getNumPlatforms() {
        return dispatcher.getNumPlatforms();
    }

}
