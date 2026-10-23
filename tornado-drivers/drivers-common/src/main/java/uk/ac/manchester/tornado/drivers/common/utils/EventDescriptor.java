/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021, APT Group, Department of Computer Science,
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
