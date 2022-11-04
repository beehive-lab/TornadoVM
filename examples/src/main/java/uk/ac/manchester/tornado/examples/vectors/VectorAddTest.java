/*
 * Copyright (c) 2013-2020, 2022, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package uk.ac.manchester.tornado.examples.vectors;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.collections.types.Float3;
import uk.ac.manchester.tornado.api.collections.types.VectorFloat3;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;

/**
 * Test Using the Profiler
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 *     tornado --enableProfiler console --jvm="-Dtornado.profiler=True" -m tornado.examples/uk.ac.manchester.tornado.examples.vectors.VectorAddTest
 * </code>
 *
 */
public class VectorAddTest {

    private static void test(VectorFloat3 a, VectorFloat3 b, VectorFloat3 results) {
        for (@Parallel int i = 0; i < a.getLength(); i++) {
            results.set(i, Float3.add(a.get(i), b.get(i)));
        }
    }

    public static void main(String[] args) {

        final VectorFloat3 a = new VectorFloat3(4);
        final VectorFloat3 b = new VectorFloat3(4);
        final VectorFloat3 results = new VectorFloat3(4);

        for (int i = 0; i < 4; i++) {
            a.set(i, new Float3(i, i, i));
            b.set(i, new Float3(2 * i, 2 * i, 2 * i));
        }

        System.out.printf("vector<float3>: %s\n", a);
        System.out.printf("vector<float3>: %s\n", b);

        TaskGraph s0 = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", VectorAddTest::test, a, b, results) //
                .transferToHost(results);

        s0.execute();

        System.out.println("Profiler kernel: " + s0.getDeviceKernelTime());
        System.out.println("Profiler copyOut: " + s0.getDeviceReadTime());
        System.out.println("Profiler copyIn: " + s0.getDeviceWriteTime());

        System.out.printf("result: %s\n", results);

        s0.execute();

        System.out.println("Profiler kernel: " + s0.getDeviceKernelTime());
        System.out.println("Profiler copyOut: " + s0.getDeviceReadTime());
        System.out.println("Profiler copyIn: " + s0.getDeviceWriteTime());

    }
}
