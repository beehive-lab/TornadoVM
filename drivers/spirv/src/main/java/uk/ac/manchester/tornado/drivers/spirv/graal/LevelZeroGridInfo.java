package uk.ac.manchester.tornado.drivers.spirv.graal;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import uk.ac.manchester.tornado.drivers.opencl.OCLFPGAScheduler;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVDeviceContext;

public class LevelZeroGridInfo {

    SPIRVDeviceContext deviceContext;
    public final long[] localWork;

    public LevelZeroGridInfo(SPIRVDeviceContext deviceContext, long[] localWork) {
        this.deviceContext = deviceContext;
        this.localWork = localWork;
    }

    public boolean checkGridDimensions() {
        if (deviceContext.isPlatformFPGA()) {
            List<Boolean> localGroupComparison = IntStream.range(0, localWork.length).mapToObj(i -> localWork[i] == OCLFPGAScheduler.LOCAL_WORK_SIZE[i]).collect(Collectors.toList());
            if (localGroupComparison.contains(Boolean.FALSE)) {
                return false;
            } else {
                return true;
            }
        }
        long[] blockMaxWorkGroupSize = deviceContext.getDevice().getDeviceMaxWorkGroupSize();
        long maxWorkGroupSize = Arrays.stream(blockMaxWorkGroupSize).sum();
        long totalThreads = Arrays.stream(localWork).reduce(1, (a, b) -> a * b);
        return totalThreads <= maxWorkGroupSize;
    }
}
