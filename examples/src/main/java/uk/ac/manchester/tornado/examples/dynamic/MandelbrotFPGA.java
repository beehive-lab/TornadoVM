/*
 * Copyright (c) 2013-2018, APT Group, School of Computer Science,
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

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;

import javax.imageio.ImageIO;
import javax.swing.JFrame;

import uk.ac.manchester.tornado.api.Policy;
import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;

public class MandelbrotFPGA {

    public static int sizes;
    public static final boolean VALIDATE = true;
    public static final boolean USE_TORNADO = true;
    public static final boolean PRINT_IMAGE = false;

    private static void mandelbrotTornado(int size, short[] output) {
        final int iterations = 10000;
        float space = 2.0f / size;

        for (@Parallel int i = 0; i < size; i++) {
            int indexIDX = i;
            for (int j = 0; j < size; j++) {

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
                short r = (short) ((y * size - 1) / iterations);
                output[i * size + j] = r;
            }
        }
    }

    @SuppressWarnings("serial")
    public static class MandelbrotImage extends Component {

        private BufferedImage image;

        public MandelbrotImage() {
        }

        private static short[] mandelbrotSequential(int size) {
            final int iterations = 10000;
            float space = 2.0f / size;

            short[] result = new short[size * size];

            for (int i = 0; i < size; i++) {
                int indexIDX = i;
                for (int j = 0; j < size; j++) {

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
                    result[i * size + j] = r;
                }
            }
            return result;
        }

        private static void mandelbrotTornado(int size, short[] output) {
            final int iterations = 10000;
            float space = 2.0f / size;

            for (@Parallel int i = 0; i < size; i++) {
                int indexIDX = i;
                for (int j = 0; j < size; j++) {

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
                    short r = (short) ((y * size - 1) / iterations);
                    output[i * size + j] = r;
                }
            }
        }

        private static BufferedImage writeFile(short[] output, int size) {
            BufferedImage img = null;
            try {
                img = new BufferedImage(size, size, BufferedImage.TYPE_INT_BGR);
                WritableRaster write = img.getRaster();
                File outputFile = new File("/tmp/mandelbrot.png");

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

        @Override
        public void paint(Graphics g) {

            System.out.println("Counter");
            if (!USE_TORNADO) {
                short[] mandelbrotSequential = mandelbrotSequential(sizes);
                this.image = writeFile(mandelbrotSequential, sizes);
            } else {
                short[] result = new short[sizes * sizes];
                TaskSchedule s0 = new TaskSchedule("s0");

                s0.task("t0", MandelbrotImage::mandelbrotTornado, sizes, result);
                s0.streamOut(result);
                s0.execute();
                // s0.executeWithProfilerSequential(Policy.PERFORMANCE);
                this.image = writeFile(result, sizes);

                System.out.println(result);
            }
            // draw the image
            g.drawImage(this.image, 0, 0, null);
        }

        @Override
        public Dimension getPreferredSize() {
            if (image == null) {
                return new Dimension(sizes, sizes);
            } else {
                return new Dimension(image.getWidth(), image.getHeight());
            }
        }
    }

    private static BufferedImage writeFile(short[] output, int size) {
        BufferedImage img = null;
        try {
            img = new BufferedImage(size, size, BufferedImage.TYPE_INT_BGR);
            WritableRaster write = img.getRaster();
            String workingDir = System.getProperty("user.dir");
            File outputFile = new File(workingDir + "/mande.png");

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

    public static void main(String[] args) {
        sizes = Integer.parseInt(args[0]);
        String executionType = args[1];
        int iterations = Integer.parseInt(args[2]);

        if (PRINT_IMAGE) {
            JFrame frame = new JFrame("Mandelbrot Example within Tornado");
            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent event) {
                    System.exit(0);
                }
            });

            frame.add(new MandelbrotImage());
            frame.pack();
            frame.setVisible(true);
        } else {
            short[] result = new short[sizes * sizes];
            TaskSchedule graph = new TaskSchedule("s0");
            long end,start;

            graph.task("t0", MandelbrotFPGA::mandelbrotTornado, sizes, result).streamOut(result);

            for (int i = 0; i < iterations; i++) {
                switch (executionType) {
                    case "performance":
                        start = System.nanoTime();
                        graph.executeWithProfilerSequential(Policy.PERFORMANCE);
                        end = System.nanoTime();
                        break;
                    case "end":
                        start = System.nanoTime();
                        graph.executeWithProfilerSequential(Policy.END_2_END);
                        end = System.nanoTime();
                        break;
                    default:
                        start = System.nanoTime();
                        graph.execute();
                        end = System.nanoTime();
                }
                System.out.println("End to end time:  " + (end - start) + " ns" + " \n");
                end = 0;
                start = 0l;
            }
            writeFile(result, sizes);
        }
    }
}
