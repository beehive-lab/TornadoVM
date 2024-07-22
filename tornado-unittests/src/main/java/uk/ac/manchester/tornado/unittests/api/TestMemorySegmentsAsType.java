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

package uk.ac.manchester.tornado.unittests.api;

import org.junit.Test;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * <p>
 * How to run.
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.api.TestMemorySegmentsAsType
 * </code>
 */
public class TestMemorySegmentsAsType extends TornadoTestBase {
    private final int numElements = 256;

    private static void getMemorySegment(MemorySegment a) {
        float test = a.getAtIndex(ValueLayout.JAVA_FLOAT, 5);
    }

    @Test(expected = TornadoRuntimeException.class)
    public void testMemorySegmentAsInput() throws TornadoExecutionPlanException {
        MemorySegment segment;
        long segmentByteSize = numElements * ValueLayout.JAVA_FLOAT.byteSize();

        segment = Arena.ofAuto().allocate(segmentByteSize, 1);
        segment.setAtIndex(JAVA_INT, 0, numElements);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, segment) //
                .task("t0", TestMemorySegmentsAsType::getMemorySegment, segment) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, segment);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }
    }

}