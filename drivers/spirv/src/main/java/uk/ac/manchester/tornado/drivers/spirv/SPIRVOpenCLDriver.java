package uk.ac.manchester.tornado.drivers.spirv;

import uk.ac.manchester.tornado.drivers.opencl.OpenCL;
import uk.ac.manchester.tornado.drivers.opencl.TornadoPlatform;

import java.util.ArrayList;
import java.util.List;

/**
 * Proof of Concept plugin in the OpenCL driver for SPIRV dispatch.
 * 
 * Initially we focus on the level-zero. However we will keep this place-holder
 * for later use of OpenCL as a SPIRV dispatcher.
 * 
 */
public class SPIRVOpenCLDriver implements SPIRVDispatcher {

    private List<SPIRVPlatform> spirvPlatforms;

    public SPIRVOpenCLDriver() {
        int numOpenCLPlatforms = OpenCL.getNumPlatforms();
        System.out.println("NUM OPENCL PLATFORMS: " + numOpenCLPlatforms);
        spirvPlatforms = new ArrayList<>();
        for (int platformIndex = 0; platformIndex < numOpenCLPlatforms; platformIndex++) {
            TornadoPlatform oclPlatform = OpenCL.getPlatform(platformIndex);
            SPIRVOpenCLPlatform spirvOCLPlatform = new SPIRVOpenCLPlatform(platformIndex, oclPlatform);
            spirvPlatforms.add(spirvOCLPlatform);
        }
    }

    @Override
    public int getNumPlatforms() {
        return OpenCL.getNumPlatforms();
    }

    @Override
    public SPIRVPlatform getPlatform(int index) {
        return spirvPlatforms.get(index);
    }
}
