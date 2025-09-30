/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, APT Group, Department of Computer Science,
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

package uk.ac.manchester.tornado.runtime.common.enums;

import static uk.ac.manchester.tornado.runtime.common.TornadoOptions.OPENCL_BACKEND_PRIORITY;
import static uk.ac.manchester.tornado.runtime.common.TornadoOptions.PTX_BACKEND_PRIORITY;
import static uk.ac.manchester.tornado.runtime.common.TornadoOptions.SPIRV_BACKEND_PRIORITY;
import static uk.ac.manchester.tornado.runtime.common.TornadoOptions.METAL_BACKEND_PRIORITY;

/**
 * Used to prioritize one backend over another. The drivers will be sorted based
 * on their priority. The driver with the highest priority will become driver 0
 * (default driver).
 */

public enum TornadoBackends {

    PTX(PTX_BACKEND_PRIORITY, "implemented"), //
    OpenCL(OPENCL_BACKEND_PRIORITY, "implemented"), //
    Metal(METAL_BACKEND_PRIORITY, "work in progress"), //
    SPIRV(SPIRV_BACKEND_PRIORITY, "implemented"); //

    private final int priority;
    private final String status;

    TornadoBackends(int priority, String status) {
        this.priority = priority;
        this.status = status;
    }

    public int value() {
        return priority;
    }

    public String status() {
        return status;
    }
}
