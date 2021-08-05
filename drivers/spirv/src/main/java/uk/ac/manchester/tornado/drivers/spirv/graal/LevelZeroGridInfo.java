package uk.ac.manchester.tornado.drivers.spirv.graal;

import java.util.Arrays;

import uk.ac.manchester.tornado.drivers.spirv.SPIRVDeviceContext;

public class LevelZeroGridInfo {

    SPIRVDeviceContext deviceContext;
    public final long[] localWork;

    public LevelZeroGridInfo(SPIRVDeviceContext deviceContext, long[] localWork) {
        this.deviceContext = deviceContext;
        this.localWork = localWork;
    }

    public boolean checkGridDimensions() {
        long[] blockMaxWorkGroupSize = deviceContext.getDevice().getDeviceMaxWorkGroupSize();
        long maxWorkGroupSize = Arrays.stream(blockMaxWorkGroupSize).sum();
        long totalThreads = Arrays.stream(localWork).reduce(1, (a, b) -> a * b);
        return totalThreads <= maxWorkGroupSize;
    }
}
