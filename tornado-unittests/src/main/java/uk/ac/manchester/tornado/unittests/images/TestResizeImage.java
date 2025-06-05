/*
 * Copyright (c) 2013-2020, 2022, APT Group, Department of Computer Science,
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

package uk.ac.manchester.tornado.unittests.images;

import static org.junit.Assert.assertEquals;
import static uk.ac.manchester.tornado.api.math.TornadoMath.clamp;

import java.util.Random;
import java.util.stream.IntStream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.images.ImageFloat;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.images.TestResizeImage
 * </code>
 *
 */
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
    public void testResizeImage() throws TornadoExecutionPlanException {
        final int numElementsX = 8;
        final int numElementsY = 8;

        final ImageFloat imageSource = new ImageFloat(numElementsX, numElementsY);
        final ImageFloat imageDst = new ImageFloat(numElementsX / 2, numElementsY / 2);

        final Random rand = new Random();

        for (int y = 0; y < numElementsY; y++) {
            for (int x = 0; x < numElementsX; x++) {
                imageSource.set(x, y, rand.nextFloat());
            }
        }

        final TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, imageSource) //
                .task("t0", TestResizeImage::resize, imageDst, imageSource, 2) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, imageDst);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withPreCompilation() //
                    .execute();
        }

        final int scale = 2;

        for (int i = 0; i < imageDst.X(); i++) {
            for (int j = 0; j < imageDst.Y(); j++) {

                int cx = clamp(scale * i, 0, imageSource.X() - 1);
                int cy = clamp(scale * j, 0, imageSource.Y() - 1);

                float center = imageSource.get(cx, cy);

                assertEquals(imageDst.get(i, j), center, 0.1);
            }
        }
    }

    @Test
    public void testResizeImageStreams() {
        final int numElementsX = 8;
        final int numElementsY = 8;

        final ImageFloat image1 = new ImageFloat(numElementsX, numElementsY);
        final ImageFloat image2 = new ImageFloat(numElementsX / 2, numElementsY / 2);

        final Random rand = new Random();

        for (int y = 0; y < numElementsY; y++) {
            for (int x = 0; x < numElementsX; x++) {
                image1.set(x, y, rand.nextFloat());
            }
        }

        IntStream.range(0, image2.X() * image2.Y()).parallel().forEach((index) -> {
            final int x = index % image2.X();
            final int y = index / image2.X();

            // co-ords of center pixel
            int cx = TornadoMath.clamp(2 * x, 0, image1.X() - 1);
            int cy = TornadoMath.clamp(2 * y, 0, image1.Y() - 1);

            final float center = image1.get(cx, cy);
            image2.set(x, y, center);
        });

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
