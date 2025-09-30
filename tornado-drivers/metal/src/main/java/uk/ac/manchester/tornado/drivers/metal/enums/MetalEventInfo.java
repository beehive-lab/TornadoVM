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

public enum MetalEventInfo {
    CL_EVENT_COMMAND_QUEUE(0x11D0), CL_EVENT_COMMAND_TYPE(0x11D1), CL_EVENT_REFERENCE_COUNT(0x11D2), CL_EVENT_COMMAND_EXECUTION_STATUS(0x11D3), CL_EVENT_CONTEXT(0x11D4);

    private final int value;

    MetalEventInfo(final int v) {
        value = v;
    }

    public int getValue() {
        return value;
    }
}
