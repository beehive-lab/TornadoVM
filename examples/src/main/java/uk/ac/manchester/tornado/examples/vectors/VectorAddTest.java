/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018 APT Group, School of Computer Science, 
 * The University of Manchester
 *
 * This work is partially supported by EPSRC grants:
 * Anyscale EP/L000725/1 and PAMELA EP/K008730/1.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.examples.vectors;

import static uk.ac.manchester.tornado.collections.types.Float3.add;

import tornado.api.Parallel;
import tornado.runtime.api.TaskSchedule;
import uk.ac.manchester.tornado.collections.types.Float3;
import uk.ac.manchester.tornado.collections.types.VectorFloat3;

public class VectorAddTest {

    private static void test(VectorFloat3 a, VectorFloat3 b,
            VectorFloat3 results) {
        for (@Parallel int i = 0; i < a.getLength(); i++) {
            results.set(i, add(a.get(i), b.get(i)));
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

        System.out.printf("vector<float3>: %s\n", a.toString());

        System.out.printf("vector<float3>: %s\n", b.toString());

        //@formatter:off
        new TaskSchedule("s0")
                .task("t0", VectorAddTest::test, a, b, results)
                .streamOut(results)
                .execute();
        //@formatter:on

        System.out.printf("result: %s\n", results.toString());

    }

}
