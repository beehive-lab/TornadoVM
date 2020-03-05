package uk.ac.manchester.tornado.drivers.cuda;

import uk.ac.manchester.tornado.runtime.domain.DomainTree;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

public class CUDAModule {
    public final byte[] moduleWrapper;
    public final String kernelFunctionName;
    public final DomainTree domain;
    public final int dims;

    public CUDAModule(byte[] source, String kernelFunctionName, TaskMetaData taskMetaData) {
        moduleWrapper = cuModuleLoadData(source);
        this.kernelFunctionName = kernelFunctionName;
        domain = taskMetaData.getDomain();
        dims = taskMetaData.getDims();
    }

    private native static byte[] cuModuleLoadData(byte[] source);
    private native static int cuFuncGetAttribute(String funcName, int attribute, byte[] module);
    private native static int calcMaximalBlockSize(int maxBlockSize, byte[] module, String funcName);

    public int getMaximalBlocks(int maxBlockSize) {
        return calcMaximalBlockSize(maxBlockSize, moduleWrapper, kernelFunctionName);
    }

    public int maxThreadsPerBlock() {
        return cuFuncGetAttribute(kernelFunctionName, CUFunctionAttribute.MAX_THREADS_PER_BLOCK, moduleWrapper);
    }

    public int getNumberOfRegisters() {
        return cuFuncGetAttribute(kernelFunctionName, CUFunctionAttribute.NUM_REGS, moduleWrapper);
    }

    public boolean getIsPTXJITSuccess() {
        return moduleWrapper.length != 0;
    }

    private static class CUFunctionAttribute {
        public final static int MAX_THREADS_PER_BLOCK = 0;
        public final static int NUM_REGS = 4;
    }
}
