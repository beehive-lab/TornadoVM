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

import static uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeConstants.ZE_BIT;

public class ZeCommandListFlag {

    /**
     * The driver may reorder commands (e.g., kernels, copies) between barriers and
     * synchronization primitives. using this flag may increase Host overhead of
     * {@link LevelZeroCommandList#zeCommandListClose}. therefore, this flag should
     * **not** be set for low-latency usage-models.
     */
    public final static int ZE_COMMAND_LIST_FLAG_RELAXED_ORDERING = ZE_BIT(0);

    /**
     * driver may perform additional optimizations that increase execution
     * throughput. Using this flag may increase Host overhead of
     * {@link LevelZeroCommandList#zeCommandListClose} and
     * {@link LevelZeroCommandQueue#zeCommandQueueExecuteCommandLists}
     * zeCommandQueueExecuteCommandLists. therefore, this flag should **not** be set
     * for low-latency usage-models.
     */
    public final static int ZE_COMMAND_LIST_FLAG_MAXIMIZE_THROUGHPUT = ZE_BIT(1);

    /**
     * < command list should be optimized for submission to a single command queue
     * and device engine. driver **must** disable any implicit optimizations for
     * distributing work across multiple engines. this flag should be used when
     * applications want full control over multi-engine submission and scheduling.
     */
    public final static int ZE_COMMAND_LIST_FLAG_EXPLICIT_ONLY = ZE_BIT(2);

    public final static int ZE_COMMAND_LIST_FLAG_FORCE_UINT32 = 0x7fffffff;
}
