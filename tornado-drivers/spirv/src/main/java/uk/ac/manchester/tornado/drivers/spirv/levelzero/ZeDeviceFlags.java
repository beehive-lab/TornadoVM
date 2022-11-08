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
 * Device module properties queried using
 * {@link LevelZeroDevice#zeDeviceGetModuleProperties}
 */
public class ZeDeviceFlags {

    /**
     * Supports denorms
     */
    public static final int ZE_DEVICE_FP_FLAG_DENORM = ZeConstants.ZE_BIT(0);

    /**
     * Supports INF and quiet NaNs.
     */
    public static final int ZE_DEVICE_FP_FLAG_INF_NAN = ZeConstants.ZE_BIT(1);

    /**
     * Supports rounding to nearest even rounding mode
     */
    public static final int ZE_DEVICE_FP_FLAG_ROUND_TO_NEAREST = ZeConstants.ZE_BIT(2);

    /**
     * Supports rounding to zero.
     */
    public static final int ZE_DEVICE_FP_FLAG_ROUND_TO_ZERO = ZeConstants.ZE_BIT(3);

    /**
     * Supports rounding to both positive and negative INF.
     */
    public static final int ZE_DEVICE_FP_FLAG_ROUND_TO_INF = ZeConstants.ZE_BIT(4);

    /**
     * Supports IEEE754-2008 fused multiply-add.
     */
    public static final int ZE_DEVICE_FP_FLAG_FMA = ZeConstants.ZE_BIT(5);

    /**
     * Supports rounding as defined by IEEE754 for divide and sqrt operations.
     */
    public static final int ZE_DEVICE_FP_FLAG_ROUNDED_DIVIDE_SQRT = ZeConstants.ZE_BIT(6);

    /**
     * Uses software implementation for basic floating-point operations.
     */
    public static final int ZE_DEVICE_FP_FLAG_SOFT_FLOAT = ZeConstants.ZE_BIT(7);

    public static final int ZE_DEVICE_FP_FLAG_FORCE_UINT32 = 0x7fffffff;

}
