/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018, APT Group, School of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.examples.paralleliser;

import static uk.ac.manchester.tornado.api.collections.math.TornadoMath.clamp;

import java.util.Random;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.collections.types.ImageFloat;

public class ResizeImage {

    public static final void resize(ImageFloat dest,
            ImageFloat src, int scaleFactor) {

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

    public static void main(final String[] args) {
        final int numElementsX = (args.length == 2) ? Integer.parseInt(args[0])
                : 8;
        final int numElementsY = (args.length == 2) ? Integer.parseInt(args[1])
                : 8;

        System.out.printf("image: x=%d, y=%d\n", numElementsX, numElementsY);
        final ImageFloat image1 = new ImageFloat(numElementsX, numElementsY);
        final ImageFloat image2 = new ImageFloat(numElementsX / 2, numElementsY / 2);

        final Random rand = new Random();

        for (int y = 0; y < numElementsY; y++) {
            for (int x = 0; x < numElementsX; x++) {
                image1.set(x, y, rand.nextFloat());
            }
        }

        final TaskSchedule schedule = new TaskSchedule("s0")
                .task("t0", ResizeImage::resize, image2, image1, 2)
                .streamOut(image2);

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
