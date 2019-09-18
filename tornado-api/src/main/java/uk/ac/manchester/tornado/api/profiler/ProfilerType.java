/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2019, APT Group, School of Computer Science,
 * The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.api.profiler;

public enum ProfilerType {

    /**
     * Profile-type that helps to classify all timers
     */
    // @formatter:off
    GRAAL_COMPILE_TIME("Graal Compilation Time"),
    DRIVER_COMPILE_TIME("Driver Compilation Time"),
    TOTAL_COMPILE_TIME("Total Compiler Time"),
    COPY_IN_TIME("CopyIn Time"),
    COPY_OUT_TIME("CopyOut Time"),
    KERNEL_TIME("Kernel Time"),
    TOTAL_TIME("Total Time");
    // @formatter:on

    String description;

    ProfilerType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return this.description;
    }
}
