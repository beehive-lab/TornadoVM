package uk.ac.manchester.tornado.drivers.spirv;

public class SPIRVOCLModule implements SPIRVModule {

    private long oclProgram;
    private long kernel;
    private String entryPoint;
    private String pathToSPIRVBinary;

    public SPIRVOCLModule(long programPointer, long kernel, String entryPoint, String pathToSPIRVBinary) {
        this.oclProgram = programPointer;
        this.kernel = kernel;
        this.entryPoint = entryPoint;
        this.pathToSPIRVBinary = pathToSPIRVBinary;
    }

    public long getOpenCLProgramPointer() {
        return oclProgram;
    }

    public long getKernelPointer() {
        return kernel;
    }

    public String getEntryPoint() {
        return entryPoint;
    }

    public String getPathToSPIRVBinary() {
        return pathToSPIRVBinary;
    }
}