/*
 * MIT License
 *
 * Copyright (c) 2021, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
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
        if (hasBeenResolved()) {
            System.out.println("Global Start: " + this.globalKernelStart + " (cycles)");
            System.out.println("Global End: " + this.globalKernelEnd + " (cycles)");
            System.out.println("Context Start: " + this.contextKernelStart + " (cycles)");
            System.out.println("Context End: " + this.contextKernelEnd + " (cycles)");
            System.out.println("Elapsed Time: " + this.kernelElapsedTime + " (ns)");
        } else {
            System.err.println("Timer has not been resolved yet: ");
        }
    }
}
