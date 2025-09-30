/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.metal.enums;

import uk.ac.manchester.tornado.api.enums.TornadoExecutionStatus;

public enum MetalCommandExecutionStatus {

    // @formatter:off
	 CL_UNKNOWN (0x4),
	 CL_COMPLETE(0x0),
	 CL_RUNNING (0x1),
	 CL_SUBMITTED (0x2),
	 CL_QUEUED (0x3),
	 CL_ERROR (-1);
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
                result = MetalCommandExecutionStatus.CL_COMPLETE;
                break;
            case 1:
                result = MetalCommandExecutionStatus.CL_RUNNING;
                break;
            case 2:
                result = MetalCommandExecutionStatus.CL_SUBMITTED;
                break;
            case 3:
                result = MetalCommandExecutionStatus.CL_QUEUED;
                break;
            default:
                result = MetalCommandExecutionStatus.CL_ERROR;
        }
        return result;
    }

    public TornadoExecutionStatus toTornadoExecutionStatus() {
        TornadoExecutionStatus result = TornadoExecutionStatus.UNKNOWN;
        switch (this) {
            case CL_COMPLETE:
                result = TornadoExecutionStatus.COMPLETE;
                break;
            case CL_QUEUED:
                result = TornadoExecutionStatus.QUEUED;
                break;
            case CL_RUNNING:
                result = TornadoExecutionStatus.RUNNING;
                break;
            case CL_SUBMITTED:
                result = TornadoExecutionStatus.SUBMITTED;
                break;
            default:
                result = TornadoExecutionStatus.ERROR;
                break;
        }
        return result;
    }
}
