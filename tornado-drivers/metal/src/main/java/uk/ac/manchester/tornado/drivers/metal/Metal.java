/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, 2023 APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.metal;

import static uk.ac.manchester.tornado.runtime.common.TornadoOptions.VIRTUAL_DEVICE_ENABLED;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import uk.ac.manchester.tornado.api.TornadoTargetDevice;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.drivers.metal.graal.MetalInstalledCode;
import uk.ac.manchester.tornado.drivers.metal.runtime.MetalTornadoDevice;
import uk.ac.manchester.tornado.drivers.metal.virtual.VirtualDeviceDescriptor;
import uk.ac.manchester.tornado.drivers.metal.virtual.VirtualJSONParser;
import uk.ac.manchester.tornado.drivers.metal.virtual.VirtualMetalPlatform;
import uk.ac.manchester.tornado.runtime.common.KernelStackFrame;
import uk.ac.manchester.tornado.runtime.common.Tornado;
import uk.ac.manchester.tornado.runtime.common.XPUDeviceBufferState;
import uk.ac.manchester.tornado.runtime.tasks.DataObjectState;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskDataContext;

public class Metal {

    public static final String METAL_JNI_LIBRARY = "tornado-objc-metal";

    private static boolean initialised = false;
    // Indicates whether native Metal JNI bindings were successfully loaded and initialised.
    private static boolean nativeAvailable = false;

    private static final List<TornadoPlatformInterface> platforms = new ArrayList<>();

    public static final ByteOrder BYTE_ORDER = ByteOrder.LITTLE_ENDIAN;

    public static final int CL_TRUE = 1;
    public static final int CL_FALSE = 0;

    static {
        if (VIRTUAL_DEVICE_ENABLED) {
            initializeVirtualPlatform();
        } else {
            // Initialize physical platform
            try {
                // Loading JNI Metal library. Prefer the SDK-installed native lib under $TORNADO_SDK/lib
                final String tornadoSdk = System.getenv("TORNADO_SDK");
                if (tornadoSdk != null) {
                    final String sdkLib = tornadoSdk + "/lib/libtornado-objc-metal.dylib";
                    try {
                        System.load(sdkLib);
                        nativeAvailable = true;
                    } catch (UnsatisfiedLinkError ex) {
                        // Fall back to system library lookup
                        System.loadLibrary(Metal.METAL_JNI_LIBRARY);
                        nativeAvailable = true;
                    }
                } else {
                    // Fall back to system library lookup when SDK not set
                    System.loadLibrary(Metal.METAL_JNI_LIBRARY);
                    nativeAvailable = true;
                }
            } catch (final UnsatisfiedLinkError e) {
                // Native JNI not available in this environment. Mark as unavailable and continue so the
                // runtime can decide a fallback (e.g., deopt to sequential execution).
                nativeAvailable = false;
            } catch (final Throwable e) {
                // Any other failure loading native bindings — mark unavailable.
                nativeAvailable = false;
            }

            if (nativeAvailable) {
                try {
                    initialise();
                } catch (final TornadoRuntimeException e) {
                    // If initialisation failed, mark native as unavailable and continue.
                    nativeAvailable = false;
                }

                // add a shutdown hook to free-up all Metal resources on VM exit
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    Thread.currentThread().setName("Metal-Cleanup-Thread");
                    Metal.cleanup();
                }));
            }
        }
    }

    /**
     * Returns true when native Metal JNI bindings were successfully loaded and initialised.
     */
    public static boolean isNativeAvailable() {
        return nativeAvailable;
    }

    static native boolean registerCallback();

    static native int mtGetPlatformCount();

    static native int mtGetPlatformIDs(long[] platformIds);

    public static void cleanup() {
        if (initialised) {
            platforms.forEach(TornadoPlatformInterface::cleanup);
        }
    }

    public static TornadoPlatformInterface getPlatform(int index) {
        return platforms.get(index);
    }

    public static int getNumPlatforms() {
        return platforms.size();
    }

    private static void initializeVirtualPlatform() {
        if (!initialised) {
            VirtualDeviceDescriptor info = VirtualJSONParser.getDeviceDescriptor();

            VirtualMetalPlatform platform = new VirtualMetalPlatform(info);
            platforms.add(platform);

            initialised = true;
        }
    }

    public static void initialise() {
        if (!initialised) {
            try {
                int numPlatforms = mtGetPlatformCount();
                long[] platformPointers = new long[numPlatforms];
                mtGetPlatformIDs(platformPointers);

                for (int i = 0; i < platformPointers.length; i++) {
                    MetalPlatform platform = new MetalPlatform(i, platformPointers[i]);
                    platforms.add(platform);
                }

                initialised = true;
            } catch (final Exception e) {
                throw new TornadoRuntimeException("[ERROR] Problem with Metal bindings");
            }
        }
    }

    /**
     * Execute an Metal code compiled by Tornado on the target device
     *
     * @param tornadoDevice
     *     Metal device to run the application.
     * @param openCLCode
     *     Metal code to run.
     * @param taskMeta
     *     TaskMetadata.
     * @param accesses
     *     Access of each parameter
     * @param parameters
     *     List of parameters.
     *
     */
    public static void run(Long executionContextId, MetalTornadoDevice tornadoDevice, MetalInstalledCode openCLCode, TaskDataContext taskMeta, Access[] accesses, Object... parameters) {
        if (parameters.length != accesses.length) {
            throw new TornadoRuntimeException("[ERROR] Accesses and objects array should match in size");
        }

        // Copy-in variables
        ArrayList<XPUDeviceBufferState> states = new ArrayList<>();
        for (int i = 0; i < accesses.length; i++) {
            Access access = accesses[i];
            Object object = parameters[i];

            DataObjectState dataObjectState = new DataObjectState();
            XPUDeviceBufferState deviceState = dataObjectState.getDeviceBufferState(tornadoDevice);

            switch (access) {
                case READ_WRITE -> {
                    tornadoDevice.allocate(object, 0, deviceState, Access.READ_WRITE);
                    tornadoDevice.ensurePresent(executionContextId, object, deviceState, null, 0, 0);
                }
                case READ_ONLY -> {
                    tornadoDevice.allocate(object, 0, deviceState, Access.READ_ONLY);
                    tornadoDevice.ensurePresent(executionContextId, object, deviceState, null, 0, 0);
                }
                case WRITE_ONLY -> tornadoDevice.allocate(object, 0, deviceState, Access.WRITE_ONLY);
                default -> {
                }
            }
            states.add(deviceState);
        }

        // Create call wrapper
        final int numArgs = parameters.length;
        KernelStackFrame callWrapper = tornadoDevice.createKernelStackFrame(executionContextId, numArgs, Access.NONE);
        callWrapper.reset();

        // Fill header of call callWrapper with empty values
        callWrapper.setKernelContext(new HashMap<>());

        // Pass arguments to the call callWrapper
        for (int i = 0; i < numArgs; i++) {
            callWrapper.addCallArgument(states.get(i).getXPUBuffer().toBuffer(), true);
        }

        // Run the code
        openCLCode.launchWithoutDependencies(executionContextId, callWrapper, null, taskMeta, 0);

        // Obtain the result
        for (int i = 0; i < accesses.length; i++) {
            Access access = accesses[i];
            switch (access) {
                case READ_WRITE:
                case WRITE_ONLY:
                    Object object = parameters[i];
                    XPUDeviceBufferState deviceState = states.get(i);
                    tornadoDevice.streamOutBlocking(executionContextId, object, 0, deviceState, null);
                    break;
                default:
                    break;
            }
        }
    }

    public static List<TornadoPlatformInterface> platforms() {
        return platforms;
    }

    public static void exploreAllPlatforms() {
        for (int platformIndex = 0; platformIndex < platforms.size(); platformIndex++) {
            final TornadoPlatformInterface platform = platforms.get(platformIndex);
            System.out.printf("[%d]: platform: %s\n", platformIndex, platform.getName());
            final MetalContextInterface context = platform.createContext();
            for (int deviceIndex = 0; deviceIndex < context.getNumDevices(); deviceIndex++) {
                MetalDeviceContext deviceContext = (MetalDeviceContext) context.createDeviceContext(deviceIndex);
                System.out.printf("\t[%d:%d] device: %s\n", platformIndex, deviceIndex, deviceContext.getDeviceName());
            }
        }
    }

    public static TornadoTargetDevice getDevice(int platformIndex, int deviceIndex) {
        final TornadoPlatformInterface platform = platforms.get(platformIndex);
        MetalDeviceContext deviceContext = (MetalDeviceContext) platform.createContext().createDeviceContext(deviceIndex);
        return deviceContext.getDevice();
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            exploreAllPlatforms();
        } else if (args.length == 2) {
            final int platformIndex = Integer.parseInt(args[0]);
            final int deviceIndex = Integer.parseInt(args[1]);
            TornadoTargetDevice device = getDevice(platformIndex, deviceIndex);
            System.out.println(device.getDeviceInfo());
        } else {
            System.out.println("usage: Metal <platform> <device>");
        }
    }
}
