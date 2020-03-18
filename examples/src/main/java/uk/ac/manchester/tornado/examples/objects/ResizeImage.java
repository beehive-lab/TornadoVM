/*
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
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

package uk.ac.manchester.tornado.examples.objects;

import java.util.Random;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.collections.graphics.ImagingOps;
import uk.ac.manchester.tornado.api.collections.types.ImageFloat;

public class ResizeImage {

    public static void main(final String[] args) {
        final int numElementsX = (args.length == 2) ? Integer.parseInt(args[0]) : 8;
        final int numElementsY = (args.length == 2) ? Integer.parseInt(args[1]) : 8;

        System.out.printf("image: x=%d, y=%d\n", numElementsX, numElementsY);
        final ImageFloat image1 = new ImageFloat(numElementsX, numElementsY);
        final ImageFloat image2 = new ImageFloat(numElementsX / 2, numElementsY / 2);

        final Random rand = new Random();

        for (int y = 0; y < numElementsY; y++) {
            for (int x = 0; x < numElementsX; x++) {
                image1.set(x, y, rand.nextFloat());
            }
        }

        final TaskSchedule schedule = new TaskSchedule("s0").task("t0", ImagingOps::resizeImage, image2, image1, 2).streamOut(image2);

        schedule.warmup();

        if (image1.X() < 16 && image1.Y() < 16) {
            System.out.println("Before:");
            System.out.println(image1.toString());
        }

        final long start = System.nanoTime();
        schedule.execute();
        final long end = System.nanoTime();
        System.out.printf("time: %.9f s\n", (end - start) * 1e-9);

        /*
         * Ouput result to console
         */
        if (image2.X() < 16 && image2.Y() < 16) {
            System.out.println("Result:");
            System.out.println(image2.toString());
        }
    }
}
