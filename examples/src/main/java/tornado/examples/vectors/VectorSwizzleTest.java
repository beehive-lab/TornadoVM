/* 
 * Copyright 2012 James Clarkson.
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
 */
package tornado.examples.vectors;

import tornado.collections.types.Float3;
import tornado.collections.types.VectorFloat3;
import tornado.runtime.api.TaskSchedule;

public class VectorSwizzleTest {

    private static void test(Float3 a,
            VectorFloat3 results) {
        Float3 b = new Float3(a.getZ(), a.getY(), a.getX());
        results.set(0, b);
    }

    public static void main(String[] args) {

        final Float3 value = new Float3(1f, 2f, 3f);
        System.out.printf("float3: %s\n", value.toString());

        System.out.printf("float3: %s\n", value.toString());

        final VectorFloat3 results = new VectorFloat3(1);

        //@formatter:off
        new TaskSchedule("s0")
                .task("t0", VectorSwizzleTest::test, value, results)
                .streamOut(results)
                .execute();
        //@formatter:on

        System.out.printf("result: %s\n", results.get(0).toString());

    }

}
