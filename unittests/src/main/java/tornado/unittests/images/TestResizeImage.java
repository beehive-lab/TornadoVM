/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2017 APT Group, School of Computer Science,
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
 */

package tornado.unittests.images;

import static org.junit.Assert.assertEquals;
import static tornado.collections.math.TornadoMath.clamp;

import java.util.Random;

import org.junit.Test;

import tornado.api.Parallel;
import tornado.collections.types.ImageFloat;
import tornado.runtime.api.TaskSchedule;
import tornado.unittests.common.TornadoTestBase;

public class TestResizeImage extends TornadoTestBase {

    public static void resize(ImageFloat dest, ImageFloat src, int scaleFactor) {

        for (@Parallel int y = 0; y < dest.Y(); y++) {
            for (@Parallel int x = 0; x < dest.X(); x++) {

                // co-ords of center pixel
                int cx = clamp(scaleFactor * x, 0, src.X() - 1);
                int cy = clamp(scaleFactor * y, 0, src.Y() - 1);

                float center = src.get(cx, cy);
                dest.set(x, y, center);
            }
        }
    }

    @Test
    public void testResize() {
        final int numElementsX = 8;
        final int numElementsY = 8;

        // System.out.printf("image: x=%d, y=%d\n", numElementsX, numElementsY);
        final ImageFloat image1 = new ImageFloat(numElementsX, numElementsY);
        final ImageFloat image2 = new ImageFloat(numElementsX / 2, numElementsY / 2);

        final Random rand = new Random();

        for (int y = 0; y < numElementsY; y++) {
            for (int x = 0; x < numElementsX; x++) {
                image1.set(x, y, rand.nextFloat());
            }
        }

        final TaskSchedule schedule = new TaskSchedule("s0").task("t0", TestResizeImage::resize, image2, image1, 2).streamOut(image2);

        schedule.warmup();

        schedule.execute();

        final int scale = 2;

        for (int i = 0; i < image2.X(); i++) {
            for (int j = 0; j < image2.Y(); j++) {

                int cx = clamp(scale * i, 0, image1.X() - 1);
                int cy = clamp(scale * j, 0, image1.Y() - 1);

                float center = image1.get(cx, cy);

                assertEquals(image2.get(i, j), center, 0.1);
            }
        }
    }
}
