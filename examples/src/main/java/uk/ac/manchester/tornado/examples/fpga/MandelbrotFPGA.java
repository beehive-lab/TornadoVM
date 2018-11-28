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

package uk.ac.manchester.tornado.examples.fpga;

import static java.lang.Integer.parseInt;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;

import javax.imageio.ImageIO;
import javax.swing.*;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;

public class MandelbrotFPGA {
    public static int sizes;

    public static final boolean USE_TORNADO = true;

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

        private static void mandelbrotTornado(int[] size, short[] output) {
            final int iterations = 10000;
            float space = 2.0f / size[0];

            for (@Parallel int i = 0; i < size[0]; i++) {
                int indexIDX = i;
                for (int j = 0; j < size[0]; j++) {

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
                    short r = (short) ((y * size[0] - 1) / iterations);
                    output[i * size[0] + j] = r;
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
            if (!USE_TORNADO) {
                short[] mandelbrotSequential = mandelbrotSequential(sizes);
                this.image = writeFile(mandelbrotSequential, sizes);
            } else {
                short[] result = new short[sizes * sizes];
                TaskSchedule s0 = new TaskSchedule("s0");

                int[] sizesDyn = new int[1];
                sizesDyn[0] = sizes;
                s0.task("t0", MandelbrotImage::mandelbrotTornado, sizesDyn, result);
                s0.streamOut(result).execute();
                // s0.executeWithProfilerSequential(Policy.PERFORMANCE);
                this.image = writeFile(result, sizes);
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

    public static void main(String[] args) {
        JFrame frame = new JFrame("Mandelbrot Example within Tornado");
        sizes = parseInt(args[0]);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                System.exit(0);
            }
        });

        frame.add(new MandelbrotImage());
        frame.pack();
        frame.setVisible(true);
    }
}