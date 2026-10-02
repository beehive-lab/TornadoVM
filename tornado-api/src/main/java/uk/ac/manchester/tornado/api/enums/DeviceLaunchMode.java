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
package uk.ac.manchester.tornado.api.enums;

/**
 * Which stream a device-side kernel launch goes into (CUDA Dynamic Parallelism, CDP2).
 *
 * <p>
 * A parent kernel cannot wait for the kernels it launches. The whole tree of launches has
 * completed by the time the host sees the parent complete.
 * </p>
 */
public enum DeviceLaunchMode {

    /**
     * The launching thread block's default stream. Children launched by the same block run in
     * launch order; they may run concurrently with the rest of the parent grid, so they must not
     * depend on writes made by other parent blocks.
     */
    DEFAULT,

    /**
     * Runs after the parent grid, and all work it launched into other streams, has completed
     * ({@code cudaStreamTailLaunch}). The child sees every write made by the parent grid.
     */
    TAIL,

    /**
     * Runs independently of the parent and of any other launch ({@code cudaStreamFireAndForget}).
     */
    FIRE_AND_FORGET
}
