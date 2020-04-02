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

package uk.ac.manchester.tornado.examples.dynamic;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.*;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;

/**
 * Program taken from the Marawacc parallel programming framework with the
 * permission from the author.
 *
 * It takes an input coloured input image and transforms it into a grey-scale
 * image.
 *
 * How to run?
 *
 * <code>
 * $ tornado uk.ac.manchester.tornado.examples.dynamic.GrayscaleMT INPUT_IMAGES.jpg
 * </code>
 *
 *
 */
public class GrayscaleMT {

    public static class LoadImage extends Component {

        private static final long serialVersionUID = 1L;
        private BufferedImage image;

        private static final boolean PARALLEL_COMPUTATION = true;
        private static final boolean MT = true;

        private static TaskSchedule tornadoTask;

        public LoadImage(String imageFile) {
            try {
                image = ImageIO.read(new File(imageFile));
            } catch (IOException e) {
                throw new RuntimeException("Input file not found: " + imageFile);
            }
        }

        private static void compute2D(int[] image, final int w, final int s) {
            for (@Parallel int i = 0; i < w; i++) {
                for (@Parallel int j = 0; j < s; j++) {
                    int rgb = image[i * s + j];
                    int alpha = (rgb >> 24) & 0xff;
                    int red = (rgb >> 16) & 0xFF;
                    int green = (rgb >> 8) & 0xFF;
                    int blue = (rgb & 0xFF);

                    int grayLevel = (red + green + blue) / 3;
                    int gray = (alpha << 24) | (grayLevel << 16) | (grayLevel << 8) | grayLevel;

                    image[i * s + j] = gray;
                }
            }
        }

        private static void compute1D(int[] image, final int w, final int s) {
            for (@Parallel int i = 0; i < w * s; i++) {
                int rgb = image[i];
                int alpha = (rgb >> 24) & 0xff;
                int red = (rgb >> 16) & 0xFF;
                int green = (rgb >> 8) & 0xFF;
                int blue = (rgb & 0xFF);

                int grayLevel = (red + green + blue) / 3;
                int gray = (alpha << 24) | (grayLevel << 16) | (grayLevel << 8) | grayLevel;

                image[i] = gray;
            }
        }

        private void parallelComputation(Graphics g) {
            int w = image.getWidth();
            int s = image.getHeight();

            int[] imageRGB = new int[w * s];

            long start = System.nanoTime();
            for (int i = 0; i < w; i++) {
                for (int j = 0; j < s; j++) {
                    int rgb = image.getRGB(i, j);
                    imageRGB[i * s + j] = rgb;
                }
            }

            if (tornadoTask == null) {
                tornadoTask = new TaskSchedule("s0");
                tornadoTask.streamIn(imageRGB).task("t0", LoadImage::compute1D, imageRGB, w, s).streamOut(imageRGB);

            }
            long taskStart = System.nanoTime();
            tornadoTask.execute();
            long taskEnd = System.nanoTime();

            // unmarshall
            for (int i = 0; i < w; i++) {
                for (int j = 0; j < s; j++) {
                    image.setRGB(i, j, imageRGB[i * s + j]);
                }
            }

            long end = System.nanoTime();
            System.out.println("Total time: " + (end - start) + " (ns)");
            System.out.println("Task time: " + (taskEnd - taskStart) + " (ns)");

            // draw the image
            g.drawImage(this.image, 0, 0, null);
        }

        private void sequentialComputation(Graphics g) {

            int w = image.getWidth();
            int s = image.getHeight();

            long start = System.nanoTime();
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
            long end = System.nanoTime();
            System.out.println("Sequential Total time: " + (end - start) + " (ns)");

            // draw the image
            g.drawImage(this.image, 0, 0, null);
        }

        private void multithreadedComputation(Graphics g) throws InterruptedException {

            int w = image.getWidth();
            int s = image.getHeight();

            int[] imageRGB = new int[w * s];

            for (int i = 0; i < w; i++) {
                for (int j = 0; j < s; j++) {
                    int rgb = image.getRGB(i, j);
                    imageRGB[i * s + j] = rgb;
                }
            }

            int maxThreadCount = Runtime.getRuntime().availableProcessors();
            Thread[] th = new Thread[maxThreadCount];

            long start = System.nanoTime();
            int balk = imageRGB.length / (maxThreadCount);
            for (int idx = 0; idx < maxThreadCount; idx++) {
                final int current = idx;
                int lowBound = current * balk;
                int upperBound = (current + 1) * balk;
                if(current==maxThreadCount-1) {
                    upperBound = imageRGB.length;
                }
                int finalUpperBound = upperBound;
                th[idx] = new Thread(() -> {
                    for (int kc = lowBound; kc < finalUpperBound; kc++) {
                        int rgb = imageRGB[kc];
                        int alpha = (rgb >> 24) & 0xff;
                        int red = (rgb >> 16) & 0xFF;
                        int green = (rgb >> 8) & 0xFF;
                        int blue = (rgb & 0xFF);

                        int grayLevel = (red + green + blue) / 3;
                        int gray = (alpha << 24) | (grayLevel << 16) | (grayLevel << 8) | grayLevel;

                        imageRGB[kc] = gray;
                    }
                });
            }

            for (int i = 0; i < maxThreadCount; i++) {
                th[i].start();
            }
            for (int i = 0; i < maxThreadCount; i++) {
                th[i].join();
            }

            long end = System.nanoTime();
            System.out.println("Multithreaded Total time: " + (end - start) + " (ns)");

            // unmarshall
            for (int i = 0; i < w; i++) {
                for (int j = 0; j < s; j++) {
                    image.setRGB(i, j, imageRGB[i * s + j]);
                }
            }

            // draw the image
            g.drawImage(this.image, 0, 0, null);
        }

        @Override
        public void paint(Graphics g) {
            if (PARALLEL_COMPUTATION && !MT) {
                parallelComputation(g);
                sequentialComputation(g);
            } else if (MT) {
                try {
                    multithreadedComputation(g);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                // sequentialComputation(g);
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

    public static void main(String[] args) {
        JFrame frame = new JFrame("Image Grey-scale conversion example with TornadoVM");

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                System.exit(0);
            }
        });

        String image = args[0];

        frame.add(new LoadImage(image));
        frame.pack();
        frame.setVisible(true);
    }
}