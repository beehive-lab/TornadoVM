/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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

/**
 * Thrown when a library task takes its arguments but the library has no kernel for them on the
 * current device - for example an MLX operation on an Apple GPU generation the MLX library tasks
 * do not support.
 *
 * <p>Deliberately extends {@link RuntimeException} rather than
 * {@link TornadoRuntimeException}, as {@link TornadoDeviceMMANotSupported} does: the exception
 * reaches the caller intact, so the unit-test harness can report Unsupported instead of
 * Failed.</p>
 */
public class TornadoLibraryTaskNotSupported extends RuntimeException {

    public TornadoLibraryTaskNotSupported(final String msg) {
        super(msg);
    }

}
