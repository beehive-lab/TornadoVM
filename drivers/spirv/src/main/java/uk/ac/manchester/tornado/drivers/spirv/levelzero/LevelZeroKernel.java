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

public class LevelZeroKernel {

    private ZeKernelDescriptor kernelDesc;
    private ZeKernelHandle kernelHandle;
    private LevelZeroModule module;

    public LevelZeroKernel(ZeKernelDescriptor kernelDesc, ZeKernelHandle kernelHandle, LevelZeroModule module) {
        this.kernelDesc = kernelDesc;
        this.kernelHandle = kernelHandle;
        this.module = module;
    }

    public ZeKernelHandle getKernelHandle() {
        return this.kernelHandle;
    }

    native int zeKernelSuggestGroupSize_native(long ptrZeKernelHandle, int globalSizeX, int globalSizeY, int globalSizeZ, int[] groupSizeX, int[] groupSizeY, int[] groupSizeZ);

    public int zeKernelSuggestGroupSize(long ptrZeKernelHandle, int globalSizeX, int globalSizeY, int globalSizeZ, int[] groupSizeX, int[] groupSizeY, int[] groupSizeZ) {
        int result = zeKernelSuggestGroupSize_native(ptrZeKernelHandle, globalSizeX, globalSizeY, globalSizeZ, groupSizeX, groupSizeY, groupSizeZ);
        return result;
    }

    native int zeKernelSetGroupSize_native(long ptrZeKernelHandle, int groupSizeX, int groupSizeY, int groupSizeZ);

    public int zeKernelSetGroupSize(long ptrZeKernelHandle, int[] groupSizeX, int[] groupSizeY, int[] groupSizeZ) {
        return zeKernelSetGroupSize_native(ptrZeKernelHandle, groupSizeX[0], groupSizeY[0], groupSizeZ[0]);
    }

    private native int zeKernelSetArgumentValue_nativePtrArg(long ptrZeKernelHandle, int argIndex, int argSize, long ptrBuffer);

    public int zeKernelSetArgumentValue(long ptrZeKernelHandle, int argIndex, int argSize, LevelZeroBufferInteger argValue) {
        return zeKernelSetArgumentValue_nativePtrArg(ptrZeKernelHandle, argIndex, argSize, (argValue == null) ? -1 : argValue.getPtrBuffer());
    }

    public int zeKernelSetArgumentValue(long ptrZeKernelHandle, int argIndex, int argSize, long ptrBuffer) {
        return zeKernelSetArgumentValue_nativePtrArg(ptrZeKernelHandle, argIndex, argSize, ptrBuffer);
    }

    private native int zeKernelSetCacheConfig_native(long ptrZeKernelHandle, int zeCacheConfigFlagLargeSlm);

    /**
     * Sets the preferred cache configuration.
     *
     * @param ptrZeKernelHandle
     * @param flag
     */
    public int zeKernelSetCacheConfig(long ptrZeKernelHandle, int flag) {
        return zeKernelSetCacheConfig_native(ptrZeKernelHandle, flag);
    }
}
