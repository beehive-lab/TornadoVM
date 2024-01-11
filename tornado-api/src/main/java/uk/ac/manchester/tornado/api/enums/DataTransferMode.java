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
package uk.ac.manchester.tornado.api.enums;

/**
 * Enumerate to specify the mode in which data will be copied to/from the host
 * to/from the device.
 */
public class DataTransferMode {

    /**
     * Flag to copy data between host <-> device only during the first execution. If
     * the task-graph is executed multiple times, all data set with this flag will
     * remain on the device as a Read-Only data.
     */
    public static final int FIRST_EXECUTION = 0;

    /**
     * Flag to copy data between host <-> device every time the execute method of a
     * task-graph ({@link uk.ac.manchester.tornado.api.TaskGraph}) is invoked.
     */
    public static final int EVERY_EXECUTION = 1;

    /**
     * Flag to indicate that copy out of buffers (device -> host) are handled by the
     * programmer rather than the TornadoVM runtime system. If this flag is used,
     * developers must manually transfer the data from the device to the host by
     * invoking
     * {@link uk.ac.manchester.tornado.api.TornadoExecutionResult#transferToHost(Object...)}
     * method.
     */
    public static final int UNDER_DEMAND = 2;
}
