/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
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
 * Authors: Juan Fumero
 *
 */
package uk.ac.manchester.tornado.runtime.tasks;

public enum TornadoGraphBitcodes {

    // @formatter:off
    LOAD_REF ((byte)1),
    LOAD_PRIM((byte)2),
    LAUNCH   ((byte)3),
    ARG_LIST ((byte)4),
    CONTEXT  ((byte)5);
    // @formatter:on

    byte index;

    TornadoGraphBitcodes(byte index) {
        this.index = index;
    }

    public byte index() {
        return index;
    }
}
