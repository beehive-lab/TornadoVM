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
package uk.ac.manchester.tornado.drivers.metal.enums;

import uk.ac.manchester.tornado.api.enums.TornadoExecutionStatus;

public enum MetalCommandExecutionStatus {

    // @formatter:off
	 METAL_UNKNOWN (0x4),
	 METAL_COMPLETE(0x0),
	 METAL_RUNNING (0x1),
	 METAL_SUBMITTED (0x2),
	 METAL_QUEUED (0x3),
	 METAL_ERROR (-1);
    // @formatter:on

    private final int value;

    MetalCommandExecutionStatus(final int v) {
        value = v;
    }

    public int getValue() {
        return value;
    }

    public static MetalCommandExecutionStatus createMetalCommandExecutionStatus(final int v) {
        MetalCommandExecutionStatus result;
        switch (v) {
            case 0:
                result = MetalCommandExecutionStatus.METAL_COMPLETE;
                break;
            case 1:
                result = MetalCommandExecutionStatus.METAL_RUNNING;
                break;
            case 2:
                result = MetalCommandExecutionStatus.METAL_SUBMITTED;
                break;
            case 3:
                result = MetalCommandExecutionStatus.METAL_QUEUED;
                break;
            default:
                result = MetalCommandExecutionStatus.METAL_ERROR;
        }
        return result;
    }

    public TornadoExecutionStatus toTornadoExecutionStatus() {
        TornadoExecutionStatus result = TornadoExecutionStatus.UNKNOWN;
        switch (this) {
            case METAL_COMPLETE:
                result = TornadoExecutionStatus.COMPLETE;
                break;
            case METAL_QUEUED:
                result = TornadoExecutionStatus.QUEUED;
                break;
            case METAL_RUNNING:
                result = TornadoExecutionStatus.RUNNING;
                break;
            case METAL_SUBMITTED:
                result = TornadoExecutionStatus.SUBMITTED;
                break;
            default:
                result = TornadoExecutionStatus.ERROR;
                break;
        }
        return result;
    }
}
