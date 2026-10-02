/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package uk.ac.manchester.tornado.api.memory;

/**
 * Where the host side of a TornadoVM array lives. Choose it when the array is created, with the
 * {@code allocate(int, HostMemoryType)} factory of the array type (e.g.
 * {@code FloatArray.allocate(n, HostMemoryType.PINNED)}).
 *
 * <p>
 * Page-locked memory is a limited system resource and is slow to allocate, so allocate such arrays
 * once and reuse them. On a backend that has no page-locked memory, or when it cannot be allocated,
 * the array is created in ordinary memory and behaves exactly the same, only without the speed-up.
 * </p>
 */
public enum HostMemoryType {

    /**
     * Ordinary, pageable memory: the default for arrays created with a constructor. The CUDA backend
     * page-locks it when the array is first used on the device ({@code -Dtornado.cuda.host.pinning}).
     */
    PAGEABLE,

    /**
     * Page-locked (pinned) memory from the start. Transfers to and from the device run at full DMA
     * speed and asynchronously, with nothing to lock on first use.
     */
    PINNED,

    /**
     * Page-locked memory mapped into the device's address space (zero-copy). Kernels read and write it
     * in place, over the bus, so the array is never copied: transfers of it cost nothing. Best for data a
     * kernel touches once per launch, such as streaming input or a result the host reads back; data
     * that kernels read many times is faster in device memory.
     */
    MAPPED
}
