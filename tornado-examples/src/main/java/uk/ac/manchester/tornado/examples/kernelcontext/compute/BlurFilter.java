/*
 * Copyright (c) 2022, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.examples.kernelcontext.compute;

import java.awt.Color;
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

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid2D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;

/**
 * It applies a Blur filter to an input image. Algorithm taken from CUDA course CS344 in Udacity.
 *
 * <p>
 * Example borrowed from the Marawacc parallel programming framework with the permission from the author.
 * </p>
 * <p>
 * How to run?
 * </p>
 * <code>
 * $ tornado --threadInfo -m tornado.examples/uk.ac.manchester.tornado.examples.kernelcontext.compute.BlurFilter
 * </code>
 */
public class BlurFilter {
    // CHECKSTYLE:OFF
    public static void main(String[] args) {
        JFrame frame = new JFrame("Blur Image Filter Example with TornadoVM");

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                System.exit(0);
            }
        });

        frame.add(new BlurFilterImage());
        frame.pack();
        frame.setVisible(true);
    }

    @SuppressWarnings("serial")
    public static class BlurFilterImage extends Component {

        public static final boolean PARALLEL_COMPUTATION = Boolean.parseBoolean(System.getProperty("run:parallel", "True"));
        public static final int FILTER_WIDTH = 31;
        private static final String IMAGE_FILE = "/tmp/image.jpg";
        private BufferedImage image;

        public BlurFilterImage() {
            loadImage();
        }

        private static void channelConvolutionSequential(IntArray channel, IntArray channelBlurred, final int numRows, final int numCols, FloatArray filter, final int filterWidth) {
            // Dealing with an even width filter is trickier
            assert (filterWidth % 2 == 1);

            // For every pixel in the image
            for (int r = 0; r < numRows; ++r) {
                for (int c = 0; c < numCols; ++c) {
                    float result = 0.0f;
                    // For every value in the filter around the pixel (c, r)
                    for (int filter_r = -filterWidth / 2; filter_r <= filterWidth / 2; ++filter_r) {
                        for (int filter_c = -filterWidth / 2; filter_c <= filterWidth / 2; ++filter_c) {
                            // Find the global image position for this filter
                            // position
                            // clamp to boundary of the image
                            int image_r = Math.min(Math.max(r + filter_r, 0), (numRows - 1));
                            int image_c = Math.min(Math.max(c + filter_c, 0), (numCols - 1));

                            float image_value = (channel.get(image_r * numCols + image_c));
                            float filter_value = filter.get((filter_r + filterWidth / 2) * filterWidth + filter_c + filterWidth / 2);

                            result += image_value * filter_value;
                        }
                    }
                    channelBlurred.set(r * numCols + c, result > 255 ? 255 : (int) result);
                }
            }
        }

        private static void compute(KernelContext context, IntArray channel, IntArray channelBlurred, final int numRows, final int numCols, FloatArray filter, final int filterWidth) {
            // For every pixel in the image
            assert (filterWidth % 2 == 1);
            int r = context.globalIdy;
            int c = context.globalIdx;
            float result = 0.0f;
            for (int filter_r = -filterWidth / 2; filter_r <= filterWidth / 2; filter_r++) {
                for (int filter_c = -filterWidth / 2; filter_c <= filterWidth / 2; filter_c++) {
                    int image_r = Math.min(Math.max(r + filter_r, 0), (numRows - 1));
                    int image_c = Math.min(Math.max(c + filter_c, 0), (numCols - 1));
                    float image_value = (channel.get(image_r * numCols + image_c));
                    float filter_value = filter.get((filter_r + filterWidth / 2) * filterWidth + filter_c + filterWidth / 2);
                    result += image_value * filter_value;
                }
            }
            channelBlurred.set(r * numCols + c, result > 255 ? 255 : (int) result);
        }

        public void loadImage() {
            try {
                image = ImageIO.read(new File(IMAGE_FILE));
            } catch (IOException e) {
                throw new RuntimeException("Input file not found: " + IMAGE_FILE);
            }
        }

        private void parallelCompute() {

            int w = image.getWidth();
            int h = image.getHeight();

            IntArray redChannel = new IntArray(w * h);
            IntArray greenChannel = new IntArray(w * h);
            IntArray blueChannel = new IntArray(w * h);
            IntArray alphaChannel = new IntArray(w * h);

            IntArray redFilter = new IntArray(w * h);
            IntArray greenFilter = new IntArray(w * h);
            IntArray blueFilter = new IntArray(w * h);

            FloatArray filter = new FloatArray(w * h);
            for (int i = 0; i < w; i++) {
                for (int j = 0; j < h; j++) {
                    filter.set(i * h + j, 1.f / (FILTER_WIDTH * FILTER_WIDTH));
                }
            }

            // data initialisation
            for (int i = 0; i < w; i++) {
                for (int j = 0; j < h; j++) {
                    int rgb = image.getRGB(i, j);
                    alphaChannel.set(i * h + j, (rgb >> 24) & 0xFF);
                    redChannel.set(i * h + j, (rgb >> 16) & 0xFF);
                    greenChannel.set(i * h + j, (rgb >> 8) & 0xFF);
                    blueChannel.set(i * h + j, (rgb & 0xFF));
                }
            }

            long start = System.nanoTime();
            WorkerGrid workerGrid = new WorkerGrid2D(w, h);
            GridScheduler gridScheduler = new GridScheduler("blur.red", workerGrid);
            gridScheduler.addWorkerGrid("blur.green", workerGrid);
            gridScheduler.addWorkerGrid("blur.blue", workerGrid);
            KernelContext context = new KernelContext();
            TaskGraph parallelFilter = new TaskGraph("blur") //
                    .transferToDevice(DataTransferMode.FIRST_EXECUTION, redChannel, greenChannel, blueChannel, filter) //
                    .task("red", BlurFilterImage::compute, context, redChannel, redFilter, w, h, filter, FILTER_WIDTH) //
                    .task("green", BlurFilterImage::compute, context, greenChannel, greenFilter, w, h, filter, FILTER_WIDTH) //
                    .task("blue", BlurFilterImage::compute, context, blueChannel, blueFilter, w, h, filter, FILTER_WIDTH) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, redFilter, greenFilter, blueFilter);

            ImmutableTaskGraph immutableTaskGraph = parallelFilter.snapshot();
            TornadoExecutionPlan executor = new TornadoExecutionPlan(immutableTaskGraph);

            workerGrid.setGlobalWork(h, w, 1);
            workerGrid.setLocalWorkToNull();
            executor.withGridScheduler(gridScheduler) //
                    .execute();

            // now recombine into the output image - Alpha is 255 for no
            // transparency
            for (int i = 0; i < w; i++) {
                for (int j = 0; j < h; j++) {
                    Color c = new Color(redFilter.get(i * h + j), greenFilter.get(i * h + j), blueFilter.get(i * h + j), alphaChannel.get(i * h + j));
                    image.setRGB(i, j, c.getRGB());
                }
            }
            long end = System.nanoTime();
            System.out.println("Parallel Total time: \n\tns = " + (end - start) + "\n\tseconds = " + ((end - start) * 1e-9));

        }

        private void sequentialComputation() {

            int w = image.getWidth();
            int h = image.getHeight();

            IntArray redChannel = new IntArray(w * h);
            IntArray greenChannel = new IntArray(w * h);
            IntArray blueChannel = new IntArray(w * h);
            IntArray alphaChannel = new IntArray(w * h);

            IntArray redFilter = new IntArray(w * h);
            IntArray greenFilter = new IntArray(w * h);
            IntArray blueFilter = new IntArray(w * h);

            FloatArray filter = new FloatArray(w * h);
            for (int i = 0; i < w; i++) {
                for (int j = 0; j < h; j++) {
                    filter.set(i * h + j, 1.f / (FILTER_WIDTH * FILTER_WIDTH));
                }
            }

            // data initialisation
            for (int i = 0; i < w; i++) {
                for (int j = 0; j < h; j++) {
                    int rgb = image.getRGB(i, j);
                    alphaChannel.set(i * h + j, (rgb >> 24) & 0xFF);
                    redChannel.set(i * h + j, (rgb >> 16) & 0xFF);
                    greenChannel.set(i * h + j, (rgb >> 8) & 0xFF);
                    blueChannel.set(i * h + j, (rgb & 0xFF));
                }
            }

            long start = System.nanoTime();
            channelConvolutionSequential(redChannel, redFilter, w, h, filter, FILTER_WIDTH);
            channelConvolutionSequential(greenChannel, greenFilter, w, h, filter, FILTER_WIDTH);
            channelConvolutionSequential(blueChannel, blueFilter, w, h, filter, FILTER_WIDTH);

            // now recombine into the output image - Alpha is 255 for no
            // transparency
            for (int i = 0; i < w; i++) {
                for (int j = 0; j < h; j++) {
                    Color c = new Color(redFilter.get(i * h + j), greenFilter.get(i * h + j), blueFilter.get(i * h + j), alphaChannel.get(i * h + j));
                    image.setRGB(i, j, c.getRGB());
                }
            }
            long end = System.nanoTime();
            System.out.println("Sequential Total time: \n\tns = " + (end - start) + "\n\tseconds = " + ((end - start) * 1e-9));
        }

        @Override
        public void paint(Graphics g) {
            loadImage();
            if (PARALLEL_COMPUTATION) {
                parallelCompute();
            } else {
                sequentialComputation();
            }

            // draw the image
            g.drawImage(this.image, 0, 0, null);
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
