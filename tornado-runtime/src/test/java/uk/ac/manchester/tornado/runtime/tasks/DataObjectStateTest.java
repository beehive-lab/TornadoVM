/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.runtime.tasks;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;

import org.junit.jupiter.api.Test;

import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;

/** Locking an object's buffer on every device, without a list of the devices. */
public class DataObjectStateTest {

    private static TornadoXPUDevice device() {
        return (TornadoXPUDevice) Proxy.newProxyInstance(DataObjectStateTest.class.getClassLoader(), new Class<?>[] { TornadoXPUDevice.class }, (proxy, method, args) -> switch (method
                .getName()) {
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> null;
        });
    }

    @Test
    public void aLockAppliesToDevicesSeenBeforeAndAfter() {
        TornadoXPUDevice before = device();
        TornadoXPUDevice after = device();
        DataObjectState state = new DataObjectState();
        assertFalse(state.getDeviceBufferState(before).isLockedBuffer());
        state.lockOnEveryDevice();
        assertTrue(state.getDeviceBufferState(before).isLockedBuffer());
        assertTrue(state.getDeviceBufferState(after).isLockedBuffer());
    }

    @Test
    public void unlockingOneDeviceLeavesTheOthersLocked() {
        TornadoXPUDevice one = device();
        TornadoXPUDevice other = device();
        DataObjectState state = new DataObjectState();
        state.lockOnEveryDevice();
        state.getDeviceBufferState(one).setLockBuffer(false);
        assertFalse(state.getDeviceBufferState(one).isLockedBuffer());
        assertTrue(state.getDeviceBufferState(other).isLockedBuffer());
    }

    @Test
    public void aCloneKeepsTheLockAndClearDropsIt() {
        DataObjectState state = new DataObjectState();
        state.lockOnEveryDevice();
        assertTrue(state.clone().getDeviceBufferState(device()).isLockedBuffer());
        state.clear();
        assertFalse(state.getDeviceBufferState(device()).isLockedBuffer());
    }
}
