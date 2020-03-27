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

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.collections.types.ImageFloat;

public class ImageFill {

    public static void main(final String[] args) {
        final int numElementsX = (args.length == 2) ? Integer.parseInt(args[0]) : 8;
        final int numElementsY = (args.length == 2) ? Integer.parseInt(args[1]) : 8;

        System.out.printf("image: x=%d, y=%d\n", numElementsX, numElementsY);
        final ImageFloat image = new ImageFloat(numElementsX, numElementsY);
        image.fill(-1f);

        final TaskSchedule graph = new TaskSchedule("s0").task("t0", image::fill, 1f).streamOut(image);

        System.out.println("Before:");
        System.out.println(image.toString());

        /*
         * Fill the array
         */
        graph.execute();
        /*
         * Ouput result to console
         */
        System.out.println("Result:");
        System.out.println(image.toString());

    }
}
