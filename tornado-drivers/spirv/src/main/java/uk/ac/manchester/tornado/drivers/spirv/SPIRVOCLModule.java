package uk.ac.manchester.tornado.drivers.spirv;

public class SPIRVOCLModule implements SPIRVModule {

    private final long oclProgram;
    private final long kernelPointer;
    private final String entryPoint;
    private final String pathToSPIRVBinary;

    public SPIRVOCLModule(long programPointer, long kernel, String entryPoint, String pathToSPIRVBinary) {
        this.oclProgram = programPointer;
        this.kernelPointer = kernel;
        this.entryPoint = entryPoint;
        this.pathToSPIRVBinary = pathToSPIRVBinary;
    }

    public long getKernelPointer() {
        return kernelPointer;
    }

    public long getProgramPointer() {
        return oclProgram;
    }

    @Override
    public String getEntryPoint() {
        return entryPoint;
    }

    @Override
    public String getPathToSPIRVBinary() {
        return pathToSPIRVBinary;
    }
}
