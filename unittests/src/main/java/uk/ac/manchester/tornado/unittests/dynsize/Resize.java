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
import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.annotations.Parallel;

import java.util.Arrays;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;

public class Resize {

    public static void resize01(float[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = 1.0f;
        }
    }

    public static void resize02(float[] a, float[] b) {
        for (@Parallel int i = 0; i < a.length; i++) {
            b[i] = a[i] + 10;
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
        float[] a = createArray(256);

        TaskSchedule ts = new TaskSchedule("s0") //
                .streamIn(a) //
                .task("t0", Resize::resize01, a) //
                .streamOut(a); //

        ts.execute();
        // Resize data
        float[] b = createArray(512);

        // Update old reference for a new reference
        ts.updateReference(a, b);
        ts.execute();

        for (float v : b) {
            assertEquals(1.0f, v, 0.001f);
        }
    }

    @Test
    public void testDynamicSize02() {
        float[] a = createArray(256);

        TaskSchedule ts = new TaskSchedule("s0") //
                .streamIn(a) //
                .task("t0", Resize::resize01, a) //
                .streamOut(a); //
        ts.execute();

        // Resize data
        float[] b = createArray(512);

        // Update old reference for a new reference
        ts.updateReference(a, b);

        ts.execute();
        ts.execute();
        ts.execute();
        ts.execute();

        // Update old reference for a new reference
        float[] c = createArray(2048);
        ts.updateReference(b, c);
        ts.execute();

        for (float v : c) {
            assertEquals(1.0f, v, 0.001f);
        }
    }

    @Test
    public void testDynamicSize03() {
        float[] a = createArray(1024);
        float[] b = createArray(1024);

        TaskSchedule ts = new TaskSchedule("s0") //
                .streamIn(a) //
                .task("t0", Resize::resize02, a, b) //
                .streamOut(b); //
        ts.execute();

        // Resize data
        float[] c = createArray(512);
        float[] d = createArray(512);

        // Update multiple references
        ts.updateReference(a, c);
        ts.updateReference(b, d);

        ts.execute();

        for (float v : d) {
            assertEquals(20.0f, v, 0.001f);
        }
    }

    @Test
    public void testUpdateReferences() {
        float[] a = createArray(256);
        float[] b = createArray(256);

        TaskSchedule ts = new TaskSchedule("s0") //
                .streamIn(a) //
                .task("t0", Resize::resize02, a, b) //
                .streamOut(b); //
        ts.execute();

        float[] aux = createArray(256);

        // Interchange
        ts.updateReference(b, aux);
        ts.updateReference(a, b);
        ts.updateReference(aux, a);
        ts.execute();

        // Interchange again
        ts.updateReference(b, aux);
        ts.updateReference(a, b);
        ts.updateReference(aux, a);
        ts.execute();

        for (float v : b) {
            assertEquals(40.0f, v, 0.001f);
        }
    }

    @Test
    public void testUpdateReferencesWithGrid() {
        float[] a = createArray(256);
        float[] b = createArray(256);

        WorkerGrid workerGrid = new WorkerGrid1D(256);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", workerGrid);

        TaskSchedule ts = new TaskSchedule("s0") //
                .streamIn(a) //
                .task("t0", Resize::resize02, a, b) //
                .streamOut(b); //
        ts.execute(gridScheduler);

        float[] aux = createArray(256);

        // Interchange
        ts.updateReference(b, aux);
        ts.updateReference(a, b);
        ts.updateReference(aux, a);
        ts.execute(gridScheduler);

        // Interchange again
        ts.updateReference(b, aux);
        ts.updateReference(a, b);
        ts.updateReference(aux, a);
        ts.execute(gridScheduler);

        for (float v : b) {
            assertEquals(40.0f, v, 0.001f);
        }
    }

    @Test
    public void testUpdateReferenceCopyIn() {
        float[] a = createArray(256);
        float[] b = createArray(256);

        WorkerGrid workerGrid = new WorkerGrid1D(256);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", workerGrid);

        // Do not stream in 'a'
        TaskSchedule ts = new TaskSchedule("s0") //
                .task("t0", Resize::resize02, a, b) //
                .streamOut(b); //
        ts.execute(gridScheduler);

        float[] aux = createArray(256);
        Arrays.fill(aux, 15);

        // Update copy in variable 'a'. It should invalidate the buffer state on the
        // device and copy in the 'aux' array.
        ts.updateReference(a, aux);
        ts.execute(gridScheduler);

        for (float v : b) {
            assertEquals(25.0f, v, 0.001f);
        }
    }
}
