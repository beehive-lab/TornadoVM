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
package uk.ac.manchester.tornado.api.exceptions;

import uk.ac.manchester.tornado.api.internal.annotations.CompilerIntrinsic;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;

public class Debug {

    /**
     * prints a message from all threads.
     *
     * @param msg
     *     format string as per OpenCL spec
     * @param args
     *     arguments to format
     */
    @CompilerIntrinsic
    public static void printf(String msg, Object... args) {
        shouldNotReachHere();
    }

}
