/*
 * Copyright (c) 2013-2023, APT Group, Department of Computer Science,
 * The University of Manchester.
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
package uk.ac.manchester.tornado.api.common;

/**
 * Enum used to specify the accesses of parameters passed on
 * {@link uk.ac.manchester.tornado.api.common.TornadoFunctions.Task}. Note that
 * we use the {@link #position} field in the
 * {@link uk.ac.manchester.tornado.runtime.sketcher.TornadoSketcher#mergeAccesses}
 * method to combine different accesses.
 */
public enum Access {

    // @formatter:off
    NONE((byte) 0b00),        // Undefined
    READ_ONLY((byte) 0b01),   // Read only
    WRITE_ONLY((byte) 0b10),  // Write only
    READ_WRITE((byte) 0b11);  // Read-write
    // @formatter:on

    public final byte position;

    Access(byte position) {
        this.position = position;
    }

    private static final Access[] accessesArray = Access.values();

    public static Access[] asArray() {
        return accessesArray;
    }

}
