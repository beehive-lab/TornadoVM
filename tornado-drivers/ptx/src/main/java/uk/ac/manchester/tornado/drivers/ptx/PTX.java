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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
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
import uk.ac.manchester.tornado.runtime.common.KernelStackFrame;
import uk.ac.manchester.tornado.runtime.common.Tornado;
import uk.ac.manchester.tornado.runtime.common.XPUDeviceBufferState;
import uk.ac.manchester.tornado.runtime.tasks.DataObjectState;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskDataContext;

public class PTX {
    public static final String PTX_JNI_LIBRARY = "tornado-ptx";

    private static final PTXPlatform platform;
    private static boolean initialised = false;

    public static long SHUTDOW_THREAD_ID_HOOK;

    static {
        System.loadLibrary(PTX_JNI_LIBRARY);

        initialise();
        platform = new PTXPlatform();

        // add a shutdown hook to free-up all CUDA resources on VM exit
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                setName("PTX-Cleanup-Thread");
                SHUTDOW_THREAD_ID_HOOK = Thread.currentThread().threadId();
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

    public static void run(PTXTornadoDevice tornadoDevice, PTXInstalledCode openCLCode, TaskDataContext taskMeta, Access[] accesses, Object... parameters) {
        if (parameters.length != accesses.length) {
            throw new TornadoRuntimeException("[ERROR] Accesses and objects array should match in size");
        }

        final long executionPlanId = 0;

        // Copy-in variables
        ArrayList<XPUDeviceBufferState> states = new ArrayList<>();
        for (int i = 0; i < accesses.length; i++) {
            Access access = accesses[i];
            Object object = parameters[i];

            DataObjectState globalState = new DataObjectState();
            XPUDeviceBufferState deviceState = globalState.getDeviceBufferState(tornadoDevice);

            switch (access) {
                case READ_WRITE:
                    tornadoDevice.allocate(object, 0, deviceState, Access.READ_WRITE);
                    tornadoDevice.ensurePresent(executionPlanId, object, deviceState, null, 0, 0);
                case READ_ONLY:
                    tornadoDevice.allocate(object, 0, deviceState, Access.READ_ONLY);
                    tornadoDevice.ensurePresent(executionPlanId, object, deviceState, null, 0, 0);
                    break;
                case WRITE_ONLY:
                    tornadoDevice.allocate(object, 0, deviceState, Access.WRITE_ONLY);
                    break;
                default:
                    break;
            }
            states.add(deviceState);
        }

        // Create call wrapper
        final int numArgs = parameters.length;
        KernelStackFrame callWrapper = tornadoDevice.createKernelStackFrame(executionPlanId, numArgs, Access.NONE);
        callWrapper.reset();

        // Fill header of call callWrapper with empty values
        callWrapper.setKernelContext(new HashMap<>());

        // Pass arguments to the call callWrapper
        for (int i = 0; i < numArgs; i++) {
            callWrapper.addCallArgument(states.get(i).getXPUBuffer().toBuffer(), true);
        }

        // Run the code
        openCLCode.launchWithoutDependencies(executionPlanId, callWrapper, null, taskMeta, 0);

        // Obtain the result
        for (int i = 0; i < accesses.length; i++) {
            Access access = accesses[i];
            switch (access) {
                case READ_WRITE:
                case WRITE_ONLY:
                    Object object = parameters[i];
                    XPUDeviceBufferState deviceState = states.get(i);
                    tornadoDevice.streamOutBlocking(executionPlanId, object, 0, deviceState, null);
                    break;
                default:
                    break;
            }
        }
    }
}
