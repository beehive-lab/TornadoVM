/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.metal.scheduler;

import uk.ac.manchester.tornado.drivers.metal.MetalDeviceContext;
import uk.ac.manchester.tornado.drivers.metal.enums.MetalDeviceType;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

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
                return TornadoOptions.USE_BLOCK_SCHEDULER ? new MetalCPUScheduler(context) : new MetalAppleGPUScheduler(context);
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
