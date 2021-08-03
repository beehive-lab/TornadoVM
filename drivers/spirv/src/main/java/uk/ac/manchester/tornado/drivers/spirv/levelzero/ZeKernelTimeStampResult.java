package uk.ac.manchester.tornado.drivers.spirv.levelzero;

public class ZeKernelTimeStampResult {

    private long globalKernelStart;
    private long globalKernelEnd;

    private long contextKernelStart;
    private long contextKernelEnd;

    private long timerResolution;
    private long kernelDurationCycles;

    private double kernelElapsedTime;

    private long ptrKernelTimeStampResult;

    private int hasBeenResolved;

    private ZeDeviceProperties deviceProperties;

    public ZeKernelTimeStampResult(ZeDeviceProperties deviceProperties) {
        this.ptrKernelTimeStampResult = -1;
        this.deviceProperties = deviceProperties;
    }

    private native int resolve_native(LevelZeroByteBuffer timeBuffer, ZeKernelTimeStampResult result);

    public void resolve(LevelZeroByteBuffer timeBuffer) {
        hasBeenResolved = resolve_native(timeBuffer, this);

        timerResolution = deviceProperties.getTimerResolution();
        kernelDurationCycles = contextKernelEnd - contextKernelStart;
        if (deviceProperties.getStype() == Ze_Structure_Type.ZE_STRUCTURE_TYPE_DEVICE_PROPERTIES_1_2) {
            kernelElapsedTime = kernelDurationCycles * (1000000000.0 / (double) timerResolution);
        } else {
            kernelElapsedTime = kernelDurationCycles * timerResolution;
        }
    }

    public boolean hasBeenResolved() {
        return hasBeenResolved > 0;
    }

    public long getGlobalKernelStart() {
        return globalKernelStart;
    }

    public long getGlobalKernelEnd() {
        return globalKernelEnd;
    }

    public long getContextKernelStart() {
        return contextKernelStart;
    }

    public long getContextKernelEnd() {
        return contextKernelEnd;
    }

    public long getTimerResolution() {
        return timerResolution;
    }

    public long getKernelDurationCycles() {
        return kernelDurationCycles;
    }

    public double getKernelElapsedTime() {
        return kernelElapsedTime;
    }

    public long getPtrKernelTimeStampResult() {
        return ptrKernelTimeStampResult;
    }

    public void printTimers() {
        System.out.println("Global Start: " + this.globalKernelStart + " (cycles)");
        System.out.println("Global End: " + this.globalKernelEnd + " (cycles)");
        System.out.println("Context Start: " + this.contextKernelStart + " (cycles)");
        System.out.println("Context End: " + this.contextKernelEnd + " (cycles)");
        System.out.println("Elapsed Time: " + this.kernelElapsedTime + " (ns)");
    }
}
