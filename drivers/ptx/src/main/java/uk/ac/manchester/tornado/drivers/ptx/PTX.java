/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.ptx;

import java.util.ArrayList;
import java.util.HashMap;

import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.drivers.ptx.graal.PTXInstalledCode;
import uk.ac.manchester.tornado.drivers.ptx.runtime.PTXTornadoDevice;
import uk.ac.manchester.tornado.runtime.common.DeviceObjectState;
import uk.ac.manchester.tornado.runtime.common.KernelArgs;
import uk.ac.manchester.tornado.runtime.common.Tornado;
import uk.ac.manchester.tornado.runtime.tasks.GlobalObjectState;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

public class PTX {
    public static final String PTX_JNI_LIBRARY = "tornado-ptx";

    private static final PTXPlatform platform;
    private static boolean initialised = false;

    static {
        System.loadLibrary(PTX_JNI_LIBRARY);

        initialise();
        platform = new PTXPlatform();

        // add a shutdown hook to free-up all CUDA resources on VM exit
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                setName("PTX-Cleanup-Thread");
                PTX.cleanup();
            }
        });
    }

    private static native long cuInit();

    private static void initialise() {
        if (initialised) {
            return;
        }

        cuInit();
        initialised = true;
    }

    public static void cleanup() {
        platform.cleanup();
    }

    public static PTXPlatform getPlatform() {
        return platform;
    }

    public static PTXTornadoDevice defaultDevice() {
        final int deviceIndex = Integer.parseInt(Tornado.getProperty("tornado.ptx.device", "0"));
        return new PTXTornadoDevice(deviceIndex);
    }

    public static void run(PTXTornadoDevice tornadoDevice, PTXInstalledCode openCLCode, TaskMetaData taskMeta, Access[] accesses, Object... parameters) {
        if (parameters.length != accesses.length) {
            throw new TornadoRuntimeException("[ERROR] Accesses and objects array should match in size");
        }

        // Copy-in variables
        ArrayList<DeviceObjectState> states = new ArrayList<>();
        for (int i = 0; i < accesses.length; i++) {
            Access access = accesses[i];
            Object object = parameters[i];

            GlobalObjectState globalState = new GlobalObjectState();
            DeviceObjectState deviceState = globalState.getDeviceState(tornadoDevice);

            switch (access) {
                case READ_WRITE:
                case READ:
                    tornadoDevice.allocate(object, 0, deviceState);
                    tornadoDevice.ensurePresent(object, deviceState, null, 0, 0);
                    break;
                case WRITE:
                    tornadoDevice.allocate(object, 0, deviceState);
                    break;
                default:
                    break;
            }
            states.add(deviceState);
        }

        // Create call wrapper
        final int numArgs = parameters.length;
        KernelArgs callWrapper = tornadoDevice.createCallWrapper(numArgs);
        callWrapper.reset();

        // Fill header of call callWrapper with empty values
        callWrapper.setKernelContext(new HashMap<>());

        // Pass arguments to the call callWrapper
        for (int i = 0; i < numArgs; i++) {
            callWrapper.addCallArgument(states.get(i).getObjectBuffer().toBuffer(), true);
        }

        // Run the code
        openCLCode.launchWithoutDependencies(callWrapper, null, taskMeta, 0);

        // Obtain the result
        for (int i = 0; i < accesses.length; i++) {
            Access access = accesses[i];
            switch (access) {
                case READ_WRITE:
                case WRITE:
                    Object object = parameters[i];
                    DeviceObjectState deviceState = states.get(i);
                    tornadoDevice.streamOutBlocking(object, 0, deviceState, null);
                    break;
                default:
                    break;
            }
        }
    }
}
