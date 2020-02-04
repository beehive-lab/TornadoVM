/*
 * Copyright (c) 2013-2019, APT Group, School of Computer Science,
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

package uk.ac.manchester.tornado.unittests.fails;

import org.junit.Test;
import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.TornadoDriver;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Test bad uses of the TornadoVM API. It should throw exceptions when possible
 * with the concrete problem.
 */
public class TestFails extends TornadoTestBase {

    private void reset() {
        for (int i = 0; i < TornadoRuntime.getTornadoRuntime().getNumDrivers(); i++) {
            final TornadoDriver driver = TornadoRuntime.getTornadoRuntime().getDriver(i);
            driver.getDefaultDevice().reset();
        }
    }

    @Test
    public void test() {
        // Call reset after warm-up
        float[] x = new float[100];
        float[] y = new float[100];

        TaskSchedule ts = new TaskSchedule("s0").streamIn(x).task("s0", (a, b) -> {
            for (int i = 0; i < 100; i++) {

            }
        }, x, y).streamOut(y);

        ts.warmup();

        reset();

        ts.execute();

    }
}
