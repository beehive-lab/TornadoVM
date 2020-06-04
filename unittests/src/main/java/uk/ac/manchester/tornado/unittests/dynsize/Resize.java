/*
 * Copyright (c) 2020, APT Group, Department of Computer Science,
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

package uk.ac.manchester.tornado.unittests.dynsize;

import org.junit.Test;
import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;

import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;

public class Resize {

    public static void resize01(float[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = 1.0f;
        }
    }

    public float[] createArray(int numElements) {
        float[] a = new float[numElements];
        IntStream.range(0, numElements).sequential().forEach(i -> {
            a[i] = 10.0f;
        });
        return a;
    }

    @Test
    public void testDynamicSize01() {
        float[] a = createArray(4096);

        TaskSchedule ts = new TaskSchedule("s0") //
                .streamIn(a) //
                .task("t0", Resize::resize01, a) //
                .streamOut(a); //

        System.out.println("[APP] " + a);
        ts.execute();
        // Resize data
        float[] b = createArray(128);
        ts.updateData(a, b);

        ts.execute();

        for (float v : b) {
            assertEquals(1.0f, v, 0.001f);
        }
    }
}
