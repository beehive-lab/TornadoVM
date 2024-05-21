package uk.ac.manchester.tornado.drivers.spirv;

import uk.ac.manchester.tornado.drivers.opencl.OCLKernel;

public class SPIRVOCLModule implements SPIRVModule {

    private long oclProgram;
    private OCLKernel kernel;
    private String entryPoint;
    private String pathToSPIRVBinary;

    public SPIRVOCLModule(long programPointer, OCLKernel kernel, String entryPoint, String pathToSPIRVBinary) {
        this.oclProgram = programPointer;
        this.kernel = kernel;
        this.entryPoint = entryPoint;
        this.pathToSPIRVBinary = pathToSPIRVBinary;
    }

    public long getOclProgram() {
        return oclProgram;
    }

    public OCLKernel getKernel() {
        return kernel;
    }

    public String getEntryPoint() {
        return entryPoint;
    }

    public String getPathToSPIRVBinary() {
        return pathToSPIRVBinary;
    }
}