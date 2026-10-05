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
package uk.ac.manchester.tornado.mlx;

import java.util.Arrays;

import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.LibraryTaskDescriptor;

/**
 * Apple MLX library tasks on the Metal backend. The factories live in one class per operation category
 * ({@link MlxArithmetic}, {@link MlxReductions}, {@link MlxLinearAlgebra}, ...); each builds a
 * {@link LibraryTaskDescriptor} consumed by {@code TaskGraph#libraryTask(String, ...)}:
 *
 * <pre>
 * taskGraph.libraryTask("add", MlxArithmetic::add, a, b, c);
 * </pre>
 *
 * <p>
 * Each task runs MLX's own Metal kernels in place on TornadoVM's buffers, with no MLX array and no
 * copy. A task whose arguments those kernels do not cover (a shape or layout outside what a
 * factory's documentation states) fails rather than falling back. The function name of each task is
 * the mlx-c name of the operation it runs, e.g. {@code mlx_add}.
 * </p>
 */
public final class Mlx {

    public static final String LIBRARY_NAME = "apple/mlx";

    private Mlx() {
    }

    /** All arguments are READ_ONLY except the output at {@code outputIndex}, which is WRITE_ONLY. */
    private static Access[] readOnlyExcept(int numArgs, int outputIndex) {
        Access[] accesses = new Access[numArgs];
        Arrays.fill(accesses, Access.READ_ONLY);
        accesses[outputIndex] = Access.WRITE_ONLY;
        return accesses;
    }

    /** A task of this library whose arguments are all read-only except {@code outputIndex}. */
    static LibraryTaskDescriptor task(String function, int outputIndex, Object... parameters) {
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY_NAME) //
                .withFunction(function) //
                .withParameters(parameters) //
                .withAccess(readOnlyExcept(parameters.length, outputIndex));
    }

    /** A task of this library whose arguments are all read-only except those in {@code outputs}. */
    static LibraryTaskDescriptor task(String function, int[] outputs, Object... parameters) {
        Access[] accesses = readOnlyExcept(parameters.length, outputs[0]);
        for (int output : outputs) {
            accesses[output] = Access.WRITE_ONLY;
        }
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY_NAME) //
                .withFunction(function) //
                .withParameters(parameters) //
                .withAccess(accesses);
    }
}
