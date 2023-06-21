/*
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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

package uk.ac.manchester.tornado.examples.polyglot.node;

import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;

import javax.imageio.ImageIO;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;

public class Mandelbrot {

    private static final int SIZE = 1024;

    private static void mandelbrot(int size, short[] output) {
        final int iterations = 10000;
        float space = 2.0f / size;

        for (@Parallel int i = 0; i < size; i++) {
            int indexIDX = i;
            for (@Parallel int j = 0; j < size; j++) {
                int indexJDX = j;
                float Zr = 0.0f;
                float Zi = 0.0f;
                float Cr = (1 * indexJDX * space - 1.5f);
                float Ci = (1 * indexIDX * space - 1.0f);
                float ZrN = 0;
                float ZiN = 0;
                int y = 0;
                for (int ii = 0; ii < iterations; ii++) {
                    if (ZiN + ZrN <= 4.0f) {
                        Zi = 2.0f * Zr * Zi + Ci;
                        Zr = 1 * ZrN - ZiN + Cr;
                        ZiN = Zi * Zi;
                        ZrN = Zr * Zr;
                        y++;
                    } else {
                        ii = iterations;
                    }

                }
                short r = (short) ((y * 255) / iterations);
                output[i * size + j] = r;
            }
        }
    }

    private static BufferedImage writeFile(short[] output, int size) {
        BufferedImage img = null;
        try {
            img = new BufferedImage(size, size, BufferedImage.TYPE_INT_BGR);
            WritableRaster write = img.getRaster();
            File outputFile = new File("./mandelbrot.png");
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    int colour = output[(i * size + j)];
                    write.setSample(i, j, 0, colour);
                }
            }
            ImageIO.write(img, "PNG", outputFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return img;
    }

    public static short[] sequential() {
        short[] result = new short[SIZE * SIZE];
        mandelbrot(SIZE, result);
        writeFile(result, SIZE);
        return result;
    }

    public static String getString() {
        return "Hello from Java - TornadoVM - Computing Mandelbrot";
    }

    public static short[] compute() {
        short[] result = new short[SIZE * SIZE];

        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", Mandelbrot::mandelbrot, SIZE, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executor = new TornadoExecutionPlan(immutableTaskGraph);
        executor.execute();

        writeFile(result, SIZE);

        return result;
    }
}
