/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021, APT Group, Department of Computer Science,
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
 */
package uk.ac.manchester.tornado.drivers.common.utils;

public enum EventDescriptor {
    // @formatter:off
    DESC_SERIAL_KERNEL("kernel - serial"),
    DESC_PARALLEL_KERNEL("kernel - parallel"),
    DESC_WRITE_BYTE( "writeToDevice - byte[]"),
    DESC_WRITE_SHORT("writeToDevice - short[]"),
    DESC_WRITE_INT("writeToDevice - int[]"),
    DESC_WRITE_LONG("writeToDevice - long[]"),
    DESC_WRITE_FLOAT("writeToDevice - float[]"),
    DESC_WRITE_DOUBLE("writeToDevice - double[]"),
    DESC_WRITE_SEGMENT("writeToDevice - long"),
    DESC_READ_BYTE("readFromDevice - byte[]"),
    DESC_READ_SHORT("readFromDevice - short[]"),
    DESC_READ_INT("readFromDevice - int[]"),
    DESC_READ_LONG("readFromDevice - long[]"),
    DESC_READ_FLOAT("readFromDevice - float[]"),
    DESC_READ_DOUBLE("readFromDevice - double[]"),
    DESC_READ_SEGMENT("readFromDevice - long"),
    DESC_SYNC_MARKER("sync - marker"),
    DESC_SYNC_BARRIER("sync - barrier"),
    EVENT_NONE("none");
    // @formatter:on

    String nameDescription;

    EventDescriptor(String nameDescription) {
        this.nameDescription = nameDescription;
    }

    public String getNameDescription() {
        return nameDescription;
    }
}
