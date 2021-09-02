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
 * Supported event pool creation flags.
 */
public class ZeEventPoolFlags {

    /**
     * Signals and waits are also visible to host
     */
    public static final int ZE_EVENT_POOL_FLAG_HOST_VISIBLE = ZeConstants.ZE_BIT(0);

    /**
     * Signals and waits may be shared across processes
     */
    public static final int ZE_EVENT_POOL_FLAG_IPC = ZeConstants.ZE_BIT(1);

    /**
     * Indicates all events in pool will contain kernel timestamps; cannot be
     * combined with {@link ZeEventPoolFlags#ZE_EVENT_POOL_FLAG_IPC}
     */
    public static final int ZE_EVENT_POOL_FLAG_KERNEL_TIMESTAMP = ZeConstants.ZE_BIT(2);

    public static final int ZE_EVENT_POOL_FLAG_FORCE_UINT32 = 0x7fffffff;
}
