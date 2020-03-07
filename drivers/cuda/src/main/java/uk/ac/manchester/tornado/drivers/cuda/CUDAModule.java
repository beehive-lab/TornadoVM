package uk.ac.manchester.tornado.drivers.cuda;

import uk.ac.manchester.tornado.runtime.domain.DomainTree;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

public class CUDAModule {
    public final byte[] moduleWrapper;
    public final String kernelFunctionName;
    public final DomainTree domain;
    public final int dims;
    private int maximalBlockSize;

    public CUDAModule(byte[] source, String kernelFunctionName, TaskMetaData taskMetaData) {
        moduleWrapper = cuModuleLoadData(source);
        this.kernelFunctionName = kernelFunctionName;
        domain = taskMetaData.getDomain();
        dims = taskMetaData.getDims();
        maximalBlockSize = -1;
    }

    private native static byte[] cuModuleLoadData(byte[] source);

    private native static int calcMaximalBlockSize(int maxBlockSize, byte[] module, String funcName);

    public int getMaximalBlocks(int maxBlockSize) {
        if (maximalBlockSize < 0) maximalBlockSize = calcMaximalBlockSize(maxBlockSize, moduleWrapper, kernelFunctionName);
        return maximalBlockSize;
    }

    public boolean getIsPTXJITSuccess() {
        return moduleWrapper.length != 0;
    }
}
