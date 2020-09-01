package uk.ac.manchester.tornado.drivers.ptx;

import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

public class PTXModule {
    public final byte[] moduleWrapper;
    public final String kernelFunctionName;
    public final TaskMetaData metaData;
    private int maxBlockSize;
    public final String javaName;
    private final byte[] source;

    public PTXModule(String name, byte[] source, String kernelFunctionName, TaskMetaData taskMetaData) {
        moduleWrapper = cuModuleLoadData(source);
        this.source = source;
        this.kernelFunctionName = kernelFunctionName;
        metaData = taskMetaData;
        maxBlockSize = -1;
        javaName = name;
    }

    private native static byte[] cuModuleLoadData(byte[] source);

    private native static int cuOccupancyMaxPotentialBlockSize(byte[] module, String funcName);

    public int getMaxThreadBlocks() {
        if (maxBlockSize < 0) maxBlockSize = cuOccupancyMaxPotentialBlockSize(moduleWrapper, kernelFunctionName);
        return maxBlockSize;
    }

    public byte[] getSource() {
        return source;
    }

    public boolean isPTXJITSuccess() {
        return moduleWrapper.length != 0;
    }
}
