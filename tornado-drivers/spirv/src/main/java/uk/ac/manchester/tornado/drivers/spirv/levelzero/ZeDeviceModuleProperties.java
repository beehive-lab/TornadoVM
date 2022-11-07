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

import java.util.Arrays;

/**
 * Device module properties queried using
 * {@link LevelZeroDevice#zeDeviceGetModuleProperties}
 */
public class ZeDeviceModuleProperties {

    private int stype;
    private long pNext;
    private int spirvVersionSupported;
    private int flags;
    private int fp16flags;
    private int fp32flags;
    private int fp64flags;
    private int maxArgumentsSize;
    private int printBufferSize;
    private int[] nativeKernelSupported;

    public ZeDeviceModuleProperties() {
        this.stype = Ze_Structure_Type.ZE_STRUCTURE_TYPE_DEVICE_MODULE_PROPERTIES;
        this.pNext = -1;
    }

    /**
     * Pointer to extension-specific structure
     * 
     * @return native pointer;
     */
    public long getpNext() {
        return pNext;
    }

    /**
     * Maximum supported SPIR-V version. Returns zero if SPIR-V is not supported.
     * Contains major and minor attributes, use ZE_MAJOR_VERSION and
     * ZE_MINOR_VERSION.
     * 
     * @return int
     */
    public int getSpirvVersionSupported() {
        return spirvVersionSupported;
    }

    /**
     * 
     * @return 0 or a valid combination of {@link ZeDeviceModuleFlags}
     */
    public int getFlags() {
        return flags;
    }

    /**
     * Capabilities for half-precision floating-point operations.
     * 
     * @return 0 (if {@link ZeDeviceModuleFlags#ZE_DEVICE_MODULE_FLAG_FP16} is not
     *         set) or a combination of {@link ZeDeviceFlags}.
     */
    public int getFp16flags() {
        return fp16flags;
    }

    /**
     * Capabilities for single-precision floating-point operations.
     *
     * @return a combination of {@link ZeDeviceFlags}.
     */
    public int getFp32flags() {
        return fp32flags;
    }

    /**
     * Capabilities for double-precision floating-point operations.
     *
     * @return 0 (if {@link ZeDeviceModuleFlags#ZE_DEVICE_MODULE_FLAG_FP64} is not
     *         set) or a combination of {@link ZeDeviceFlags}.
     */
    public int getFp64flags() {
        return fp64flags;
    }

    /**
     * Maximum kernel argument size that is supported.
     * 
     * @return int
     */
    public int getMaxArgumentsSize() {
        return maxArgumentsSize;
    }

    /**
     * Maximum size of internal buffer that holds output of printf calls from
     * kernel.
     * 
     * @return int
     */
    public int getPrintBufferSize() {
        return printBufferSize;
    }

    /**
     * Compatibility UUID of supported native kernel. UUID may or may not be the
     * same across driver release, devices, or operating systems. Application is
     * responsible for ensuring UUID matches before creating module using previously
     * created native kernel.
     * 
     * @return int[]
     */
    public int[] getNativeKenrelSupported() {
        return nativeKernelSupported;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("=========================\n");
        builder.append("Device Module Properties\n");
        builder.append("=========================\n");
        builder.append("Type                  : " + ZeUtils.zeTypeToString(stype) + "\n");
        builder.append("pNext                 : " + pNext + "\n");
        builder.append("spirvVersionSupported :  " + spirvVersionSupported + "\n");
        builder.append("flags                 : " + ZeUtils.zeDeviceModuleFlagsToString(flags) + "\n");
        builder.append("fp16flags             : " + ZeUtils.zeDeviceFPXXtoString(fp16flags) + "\n");
        builder.append("fp32flags             : " + ZeUtils.zeDeviceFPXXtoString(fp32flags) + "\n");
        builder.append("fp64flags             : " + ZeUtils.zeDeviceFPXXtoString(fp64flags) + "\n");
        builder.append("maxArgumentsSize      : " + maxArgumentsSize + "\n");
        builder.append("printBufferSize       : " + printBufferSize + "\n");
        builder.append("nativeKernelSupported : " + Arrays.toString(nativeKernelSupported) + "\n");
        return builder.toString();
    }
}
