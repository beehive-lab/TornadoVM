package uk.ac.manchester.tornado.drivers.ptx;

import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

public class PTXModule {
    public final byte[] moduleWrapper;
    public final String kernelFunctionName;
    public final TaskMetaData metaData;
    private int maximalBlockSize;
    public final String javaName;
    private byte[] source;

    public PTXModule(String name, byte[] source, String kernelFunctionName, TaskMetaData taskMetaData) {
        moduleWrapper = cuModuleLoadData(source);
        this.source = source;
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

    public byte[] getSource() {
        return source;
    }

    public boolean getIsPTXJITSuccess() {
        return moduleWrapper.length != 0;
    }
}
