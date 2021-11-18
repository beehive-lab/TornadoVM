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

/**
 * Supported device module flags.
 */
public class ZeDeviceModuleFlags {

    /**
     * Device supports 16-bit floating-point operations.
     */
    public static final int ZE_DEVICE_MODULE_FLAG_FP16 = ZeConstants.ZE_BIT(0);

    /**
     * Device supports 64-bit floating-point operations.
     */
    public static final int ZE_DEVICE_MODULE_FLAG_FP64 = ZeConstants.ZE_BIT(1);

    /**
     * Device supports 64-bit atomic operations.
     */
    public static final int ZE_DEVICE_MODULE_FLAG_INT64_ATOMICS = ZeConstants.ZE_BIT(2);

    /**
     * Device supports four component dot product and accumulate operations.
     */
    public static final int ZE_DEVICE_MODULE_FLAG_DP4A = ZeConstants.ZE_BIT(3);

    public static final int ZE_DEVICE_MODULE_FLAG_FORCE_UINT32 = 0x7fffffff;
}
