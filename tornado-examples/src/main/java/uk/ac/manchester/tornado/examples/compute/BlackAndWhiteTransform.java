/*
 * Copyright (c) 2013-2023, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.examples.compute;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.JFrame;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;

/**
 * Program taken from the Marawacc parallel programming framework with the permission from the author.
 * <p>
 * It takes an input coloured input image and transforms it into a grey-scale image.
 * </p>
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado --jvm="-Drun::parallel=True" -m tornado.examples/uk.ac.manchester.tornado.examples.compute.BlackAndWhiteTransform
 * </code>
 */
public class BlackAndWhiteTransform {
    // CHECKSTYLE:OFF
    public static void main(String[] args) {
        JFrame frame = new JFrame("Image Grey-scale conversion example with TornadoVM");

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                System.exit(0);
            }
        });

        frame.add(new LoadImage());
        frame.pack();
        frame.setVisible(true);
    }

    public static class LoadImage extends Component {

        private static final int WARMING_UP_ITERATIONS = 15;

        private static final long serialVersionUID = 1L;
        private static final boolean PARALLEL_COMPUTATION = Boolean.parseBoolean(System.getProperty("run::parallel", "False"));
        private static final String IMAGE_FILE = "/tmp/image.jpg";
        private static TaskGraph taskGraph;
        private static TornadoExecutionPlan executor;
        private BufferedImage image;

        LoadImage() {
            try {
                image = ImageIO.read(new File(IMAGE_FILE));
            } catch (IOException e) {
                throw new RuntimeException("Input file not found: " + IMAGE_FILE);
            }
        }

        private static void compute(IntArray image, final int w, final int s) {
            for (@Parallel int i = 0; i < w; i++) {
                for (@Parallel int j = 0; j < s; j++) {
                    int rgb = image.get(i * s + j);
                    int alpha = (rgb >> 24) & 0xff;
                    int red = (rgb >> 16) & 0xFF;
                    int green = (rgb >> 8) & 0xFF;
                    int blue = (rgb & 0xFF);

                    int grayLevel = (red + green + blue) / 3;
                    int gray = (alpha << 24) | (grayLevel << 16) | (grayLevel << 8) | grayLevel;
                    image.set(i * s + j, gray);
                }
            }
        }

        private void writeImage(String fileName) {
            try {
                ImageIO.write(image, "jpg", new File("/tmp/" + fileName));
            } catch (IOException e) {
                throw new RuntimeException(STR."Input file not found: \{IMAGE_FILE}");
            }
        }

        private void parallelComputation(Graphics g) {
            int w = image.getWidth();
            int s = image.getHeight();

            IntArray imageRGB = new IntArray(w * s);

            // Data preparation - TornadoVM can't process BufferedImage types
            for (int i = 0; i < w; i++) {
                for (int j = 0; j < s; j++) {
                    int rgb = image.getRGB(i, j);
                    imageRGB.set(i * s + j, rgb);
                }
            }

            if (executor == null) {
                taskGraph = new TaskGraph("s0");
                taskGraph.transferToDevice(DataTransferMode.EVERY_EXECUTION, imageRGB) //
                        .task("t0", LoadImage::compute, imageRGB, w, s) //
                        .transferToHost(DataTransferMode.EVERY_EXECUTION, imageRGB);

                ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
                executor = new TornadoExecutionPlan(immutableTaskGraph);
            }

            long start, end;
            for (int z = 0; z < WARMING_UP_ITERATIONS; z++) {
                start = System.nanoTime();
                executor.execute();
                end = System.nanoTime();
                System.out.println(STR."Total TornadoVM time: \{end - start} (ns)");
            }

            // unmarshall data from IntArray to BufferedImage to draw on the screen
            for (int i = 0; i < w; i++) {
                for (int j = 0; j < s; j++) {
                    image.setRGB(i, j, imageRGB.get(i * s + j));
                }
            }

            // draw the image
            g.drawImage(this.image, 0, 0, null);

            writeImage("parallel.jpg");

        }

        private void sequentialComputation(Graphics g) {

            int w = image.getWidth();
            int s = image.getHeight();

            long start = 0, end = 0;
            for (int z = 0; z < WARMING_UP_ITERATIONS; z++) {
                start = System.nanoTime();
                for (int i = 0; i < w; i++) {
                    for (int j = 0; j < s; j++) {
                        int rgb = image.getRGB(i, j);
                        int alpha = (rgb >> 24) & 0xff;
                        int red = (rgb >> 16) & 0xFF;
                        int green = (rgb >> 8) & 0xFF;
                        int blue = (rgb & 0xFF);

                        int grayLevel = (red + green + blue) / 3;
                        int gray = (alpha << 24) | (grayLevel << 16) | (grayLevel << 8) | grayLevel;
                        image.setRGB(i, j, gray);
                    }
                }
                end = System.nanoTime();
                System.out.println(STR."Total sequential time: \{end - start} (ns)");
            }

            // draw the image
            g.drawImage(this.image, 0, 0, null);
            writeImage("sequential.jpg");
        }

        @Override
        public void paint(Graphics g) {
            if (PARALLEL_COMPUTATION) {
                parallelComputation(g);
            } else {
                sequentialComputation(g);
            }
        }

        @Override
        public Dimension getPreferredSize() {
            if (image == null) {
                return new Dimension(100, 100);
            } else {
                return new Dimension(image.getWidth(), image.getHeight());
            }
        }
    }
}
// CHECKSTYLE:ON
