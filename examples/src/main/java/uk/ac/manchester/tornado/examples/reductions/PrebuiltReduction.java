/*
 * Copyright (c) 2019, APT Group, School of Computer Science,
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
package uk.ac.manchester.tornado.examples.reductions;

import java.util.Arrays;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;

public class PrebuiltReduction {

    public void compute(int size) {

        float[] a = new float[size];
        float[] b = new float[128];

        Arrays.fill(a, 2.0f);

        TornadoDevice defaultDevice = TornadoRuntime.getTornadoRuntime().getDefaultDevice();

        // @formatter:off
        new TaskSchedule("s0")
            .prebuiltTask("t0", 
                        "reductionAddFloats", 
                        "/tmp/reduce.cl",
                        new Object[] { a, b },
                        new Access[] { Access.READ, Access.READ_WRITE}, 
                        defaultDevice,
                        new int[] { size })
            .streamOut(b)
            .execute();
        // @formatter:on

        System.out.println(Arrays.toString(b));
    }

    public static void main(String[] args) {
        int size = 2048;
        if (args.length > 0) {
            size = Integer.parseInt(args[0]);
        }
        new PrebuiltReduction().compute(size);
    }
}
