package uk.ac.manchester.tornado.drivers.cuda;

import uk.ac.manchester.tornado.runtime.domain.DomainTree;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

public class CUDAModule {
    public final byte[] moduleWrapper;
    public final String kernelFunctionName;
    public final TaskMetaData metaData;
    private int maximalBlockSize;
    public final String javaName;

    public CUDAModule(String name, byte[] source, String kernelFunctionName, TaskMetaData taskMetaData) {
        moduleWrapper = cuModuleLoadData(source);
        this.kernelFunctionName = kernelFunctionName;
        metaData = taskMetaData;
        maximalBlockSize = -1;
        javaName = name;
    }

    private native static byte[] cuModuleLoadData(byte[] source);

    private native static int calcMaximalBlockSize(byte[] module, String funcName);

    public int getMaximalBlocks() {
        if (maximalBlockSize < 0) maximalBlockSize = calcMaximalBlockSize(moduleWrapper, kernelFunctionName);
        return maximalBlockSize;
    }

    public boolean getIsPTXJITSuccess() {
        return moduleWrapper.length != 0;
    }
}
