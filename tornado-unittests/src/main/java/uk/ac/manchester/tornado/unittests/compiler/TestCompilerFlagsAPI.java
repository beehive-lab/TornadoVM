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
package uk.ac.manchester.tornado.unittests.compiler;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * How to run?
 *
 * <p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.compiler.TestCompilerFlagsAPI
 * </code>
 * </p>
 */
public class TestCompilerFlagsAPI extends TornadoTestBase {

    private static void foo(FloatArray data) {
        for (@Parallel int i = 0; i < data.getSize(); i++) {
            data.set(i, data.get(i) + 1);
        }
    }

    @Test
    public void test() throws TornadoExecutionPlanException {
        FloatArray data = new FloatArray(512);
        data.init(1.0f);

        TaskGraph taskGraph = new TaskGraph("init") //
                .task("foo", TestCompilerFlagsAPI::foo, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.withCompilerFlags(TornadoVMBackendType.OPENCL, "-cl-opt-disable") //
                    .withCompilerFlags(TornadoVMBackendType.PTX, "") //
                    .withCompilerFlags(TornadoVMBackendType.SPIRV, "-ze-opt-level 1") //
                    .execute();
        }

    }
}
