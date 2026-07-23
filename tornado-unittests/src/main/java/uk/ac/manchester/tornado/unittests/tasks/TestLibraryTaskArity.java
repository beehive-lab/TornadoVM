/*
 * Copyright (c) 2024, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.unittests.tasks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Arrays;

import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.LibraryTaskDescriptor;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Validates the high-arity {@link TaskGraph#libraryTask} overloads (19 and 20
 * arguments) added for the hybrid (native library) API. Library binding factory
 * methods run at task-graph construction time and produce a
 * {@link LibraryTaskDescriptor}; no device provider is required to build and
 * snapshot the graph, so these tests are backend-agnostic.
 *
 * <p>
 * How to test?
 *
 * <p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.tasks.TestLibraryTaskArity
 * </code>
 * </p>
 */
public class TestLibraryTaskArity extends TornadoTestBase {

    // The output array sits at parameter index 0; every other parameter is read-only.
    private static Access[] writeOnlyFirst(int numArgs) {
        Access[] accesses = new Access[numArgs];
        Arrays.fill(accesses, Access.READ_ONLY);
        accesses[0] = Access.WRITE_ONLY;
        return accesses;
    }

    // Dummy library binding with 19 parameters, mirroring a real cuBLAS-style factory method.
    public static LibraryTaskDescriptor bind19(IntArray out, int a1, int a2, int a3, int a4, int a5, int a6, int a7, int a8, int a9, int a10, int a11, int a12, int a13, int a14, int a15, int a16,
            int a17, int a18) {
        return new LibraryTaskDescriptor() //
                .withLibrary("test") //
                .withFunction("bind19") //
                .withParameters(new Object[] { out, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18 }) //
                .withAccess(writeOnlyFirst(19));
    }

    // Dummy library binding with 20 parameters.
    public static LibraryTaskDescriptor bind20(IntArray out, int a1, int a2, int a3, int a4, int a5, int a6, int a7, int a8, int a9, int a10, int a11, int a12, int a13, int a14, int a15, int a16,
            int a17, int a18, int a19) {
        return new LibraryTaskDescriptor() //
                .withLibrary("test") //
                .withFunction("bind20") //
                .withParameters(new Object[] { out, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18, a19 }) //
                .withAccess(writeOnlyFirst(20));
    }

    @Test
    public void testLibraryTask19() {
        IntArray out = new IntArray(16);
        TaskGraph taskGraph = new TaskGraph("lt19") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, out) //
                .libraryTask("t19", TestLibraryTaskArity::bind19, out, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        assertNotNull(immutableTaskGraph);
        // 19 parameters captured in the descriptor (1 array + 18 scalars).
        assertEquals(19, bind19(out, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18).getParameters().length);
    }

    @Test
    public void testLibraryTask20() {
        IntArray out = new IntArray(16);
        TaskGraph taskGraph = new TaskGraph("lt20") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, out) //
                .libraryTask("t20", TestLibraryTaskArity::bind20, out, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        assertNotNull(immutableTaskGraph);
        // 20 parameters captured in the descriptor (1 array + 19 scalars).
        assertEquals(20, bind20(out, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19).getParameters().length);
    }
}
