package uk.ac.manchester.tornado.drivers.cuda;

public class CUDAModule {
    public final byte[] nativeModule;
    public final String kernelFunctionName;

    public CUDAModule(byte[] source, String kernelFunctionName) {
        nativeModule = cuModuleLoadData(source);
        this.kernelFunctionName = kernelFunctionName;
    }

    private native static byte[] cuModuleLoadData(byte[] source);
    private native static int cuFuncGetAttribute(String funcName, int attribute, byte[] module);


    public int maxThreadsPerBlock() {
        return cuFuncGetAttribute(kernelFunctionName, CUFunctionAttribute.MAX_THREADS_PER_BLOCK, nativeModule);
    }

    public int getNumberOfRegisters() {
        return cuFuncGetAttribute(kernelFunctionName, CUFunctionAttribute.NUM_REGS, nativeModule);
    }

    private static class CUFunctionAttribute {
        public final static int MAX_THREADS_PER_BLOCK = 0;
        public final static int NUM_REGS = 4;
    }
}
