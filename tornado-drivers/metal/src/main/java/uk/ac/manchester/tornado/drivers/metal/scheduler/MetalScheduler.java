/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.metal.scheduler;

import uk.ac.manchester.tornado.api.options.TaskFlags;
import uk.ac.manchester.tornado.drivers.metal.MetalDeviceContext;
import uk.ac.manchester.tornado.drivers.metal.enums.MetalDeviceType;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

/**
 * Factory for Metal kernel schedulers.
 *
 * <p>Metal is an Apple-exclusive API. GPU dispatches always use {@link MetalAppleGPUScheduler},
 * which is tuned for Apple Silicon SIMD group semantics.
 */
public class MetalScheduler {

    public static MetalKernelScheduler instanceScheduler(MetalDeviceType type, final MetalDeviceContext context) {
        switch (type) {
            case METAL_DEVICE_TYPE_GPU -> {
                return new MetalAppleGPUScheduler(context);
            }
            case METAL_DEVICE_TYPE_ACCELERATOR -> {
                return new MetalCPUScheduler(context);
            }
            case METAL_DEVICE_TYPE_CPU -> {
                return TaskFlags.BLOCK_SCHEDULER.get() ? new MetalCPUScheduler(context) : new MetalAppleGPUScheduler(context);
            }
            default -> new TornadoLogger().fatal("No scheduler available for device: %s", context);
        }
        return null;
    }

    public static MetalKernelScheduler create(final MetalDeviceContext context) {
        if (context.getDevice().getDeviceType() != null) {
            MetalDeviceType type = context.getDevice().getDeviceType();
            return instanceScheduler(type, context);
        }
        return null;
    }
}
