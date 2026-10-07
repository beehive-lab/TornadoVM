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
package uk.ac.manchester.tornado.unittests.tile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.List;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.tile.TileContext;
import uk.ac.manchester.tornado.api.types.arrays.BFloat16Array;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.arrays.Int8Array;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * The {@link TileContext#view} overloads and the compiler plugins that lower them have to agree.
 *
 * <p>
 * The CUDA backend registers a {@code view} plugin of rank 1, 2 and 3 for every array type a tile
 * can be read from. A plugin whose method does not exist is harmless at run time, but when
 * assertions are on in Graal's {@code graphbuilderconf} package, Graal checks every registration as
 * the backend starts, and a missing method stops the backend from starting at all. That is what
 * happens to an application started outside the {@code tornado} launcher with a blanket
 * {@code -ea} -- a Gradle or Maven test run, an IDE: {@code NoSuchMethodError} for
 * {@code view(ByteArray, int, int, int)}, then for vector {@code set} overloads.
 *
 * <p>
 * How to run (tornado-test enables exactly that check, and none of Graal's other assertions, which
 * TornadoVM keeps off on purpose):
 *
 * <code>
 * tornado-test -V -J"-ea:tornado.graal.compiler.nodes.graphbuilderconf..." uk.ac.manchester.tornado.unittests.tile.TestTileViewOverloads
 * </code>
 */
public class TestTileViewOverloads extends TornadoTestBase {

    /** The array types the CUDA backend registers {@code view} plugins for. */
    private static final List<Class<?>> VIEWABLE = List.of(FloatArray.class, HalfFloatArray.class, BFloat16Array.class, IntArray.class, Int8Array.class, DoubleArray.class,
            ByteArray.class);

    public static void copy(IntArray input, IntArray output) {
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            output.set(i, input.get(i) + 1);
        }
    }

    /** Every viewable array type has a view of rank 1, 2 and 3. */
    @Test
    public void testEveryViewableTypeHasEveryRank() {
        for (Class<?> type : VIEWABLE) {
            for (int rank = 1; rank <= 3; rank++) {
                Class<?>[] parameters = new Class<?>[rank + 1];
                parameters[0] = type;
                for (int r = 1; r <= rank; r++) {
                    parameters[r] = int.class;
                }
                try {
                    TileContext.class.getMethod("view", parameters);
                } catch (NoSuchMethodException e) {
                    fail("TileContext has no rank-" + rank + " view(" + type.getSimpleName() + ", ...), which the CUDA backend registers a plugin for");
                }
            }
        }
    }

    /**
     * A plain kernel on the default device. With the plugin check enabled, starting the backend is
     * what checks every registered plugin against the API; this only has to get that far and run.
     */
    @Test
    public void testBackendStartsWithAssertionsEnabled() throws TornadoExecutionPlanException {
        IntArray input = IntArray.fromElements(1, 2, 3, 4);
        IntArray output = new IntArray(4);
        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestTileViewOverloads::copy, input, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.execute();
        }
        for (int i = 0; i < 4; i++) {
            assertEquals(i + 2, output.get(i));
        }
    }
}
