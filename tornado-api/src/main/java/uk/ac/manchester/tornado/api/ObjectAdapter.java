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
package uk.ac.manchester.tornado.api;

/**
 * Lets objects of another library stand in for TornadoVM objects: in task arguments, in transfers to
 * and from the device, and in on-demand copies back. An adapter maps such an object to a TornadoVM
 * object over the same memory (a {@code ByteArray} over a memory segment, for example), so a program
 * can pass its own data structures to {@link TaskGraph#task}, {@link TaskGraph#transferToDevice} and
 * {@link TornadoExecutionResult#transferToHost} as they are.
 *
 * <p>Adapters are found with {@link java.util.ServiceLoader} ({@code META-INF/services/uk.ac.manchester.tornado.api.ObjectAdapter}).
 * With none installed, objects are passed on unchanged.
 */
public interface ObjectAdapter {

    /**
     * The TornadoVM object standing for {@code object}, or null when this adapter does not handle it.
     * It must return the same TornadoVM object for the same object every time, since TornadoVM tracks
     * device buffers by identity.
     */
    Object adapt(Object object);

    /**
     * Called when a task receives an adapted object: {@code kernel} is the task's {@link java.lang.reflect.Method}
     * or its lambda, {@code parameter} the kernel's parameter index (a lambda's code not counted), and
     * {@code object} the original object. An adapter can record what the compiler needs to know about it,
     * such as its memory layout.
     */
    default void bound(Object kernel, int parameter, Object object) {
    }
}
