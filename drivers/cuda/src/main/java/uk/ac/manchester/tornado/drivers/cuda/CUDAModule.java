package uk.ac.manchester.tornado.drivers.cuda;

import uk.ac.manchester.tornado.runtime.domain.DomainTree;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

public class CUDAModule {
    public final byte[] nativeModule;
    public final String kernelFunctionName;
    public final DomainTree domain;
    public final int dims;

    public CUDAModule(byte[] source, String kernelFunctionName, TaskMetaData taskMetaData) {
        nativeModule = cuModuleLoadData(source);
        this.kernelFunctionName = kernelFunctionName;
        domain = taskMetaData.getDomain();
        dims = taskMetaData.getDims();
    }

    private native static byte[] cuModuleLoadData(byte[] source);
    private native static int cuFuncGetAttribute(String funcName, int attribute, byte[] module);


    public int maxThreadsPerBlock() {
        return cuFuncGetAttribute(kernelFunctionName, CUFunctionAttribute.MAX_THREADS_PER_BLOCK, nativeModule);
    }

    public int getNumberOfRegisters() {
        return cuFuncGetAttribute(kernelFunctionName, CUFunctionAttribute.NUM_REGS, nativeModule);
    }

    public boolean getIsPTXJITSuccess() {
        return nativeModule.length != 0;
    }

    private static class CUFunctionAttribute {
        public final static int MAX_THREADS_PER_BLOCK = 0;
        public final static int NUM_REGS = 4;
    }
}
