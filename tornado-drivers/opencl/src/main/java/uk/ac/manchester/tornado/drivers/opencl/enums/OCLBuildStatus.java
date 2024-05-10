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
package uk.ac.manchester.tornado.drivers.opencl.enums;

public enum OCLBuildStatus {
    CL_BUILD_SUCCESS(0), //
    CL_BUILD_NONE(-1), //
    CL_BUILD_ERROR(-2), //
    CL_BUILD_IN_PROGRESS(-3), //
    CL_BUILD_UNKNOWN(-4);

    private final int buildStatusCode;

    OCLBuildStatus(final int v) {
        buildStatusCode = v;
    }

    public int getBuildStatusCode() {
        return buildStatusCode;
    }

    public static OCLBuildStatus toEnum(final int errorCode) {
        return switch (errorCode) {
            case 0 -> OCLBuildStatus.CL_BUILD_SUCCESS;
            case -1 -> OCLBuildStatus.CL_BUILD_NONE;
            case -2 -> OCLBuildStatus.CL_BUILD_ERROR;
            case -3 -> OCLBuildStatus.CL_BUILD_IN_PROGRESS;
            default -> null;
        };
    }

}
