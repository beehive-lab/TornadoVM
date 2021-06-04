/*
 * Copyright (c) 2021, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.examples.kernelcontext.compute;

import java.util.ArrayList;
import java.util.Random;

import uk.ac.manchester.tornado.api.GridTask;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid2D;
import uk.ac.manchester.tornado.api.collections.types.Byte3;
import uk.ac.manchester.tornado.api.collections.types.Float3;
import uk.ac.manchester.tornado.api.collections.types.ImageByte3;
import uk.ac.manchester.tornado.api.collections.types.ImageFloat3;
import uk.ac.manchester.tornado.examples.reductions.Stats;

public class RenderTrack {

    private static final boolean CHECK_RESULT = true;

    public static void renderTrackJava(ImageByte3 output, ImageFloat3 input) {
        for (int y = 0; y < input.Y(); y++) {
            for (int x = 0; x < input.X(); x++) {
                Byte3 pixel = null;
                final int result = (int) input.get(x, y).getS2();
                switch (result) {
                    case 1: // ok GREY
                        pixel = new Byte3((byte) 128, (byte) 128, (byte) 128);
                        break;
                    case -1: // no input BLACK
                        pixel = new Byte3((byte) 0, (byte) 0, (byte) 0);
                        break;
                    case -2: // not in image RED
                        pixel = new Byte3((byte) 255, (byte) 0, (byte) 0);
                        break;
                    case -3: // no correspondence GREEN
                        pixel = new Byte3((byte) 0, (byte) 255, (byte) 0);
                        break;
                    case -4: // too far away BLUE
                        pixel = new Byte3((byte) 0, (byte) 0, (byte) 255);
                        break;
                    case -5: // wrong normal YELLOW
                        pixel = new Byte3((byte) 255, (byte) 255, (byte) 0);
                        break;
                    default:
                        pixel = new Byte3((byte) 255, (byte) 128, (byte) 128);
                        break;
                }
                output.set(x, y, pixel);
            }
        }
    }

    public static void renderTrack(KernelContext context, ImageByte3 output, ImageFloat3 input) {
        int x = context.globalIdx;
        int y = context.globalIdy;

        Byte3 pixel = null;
        final int result = (int) input.get(x, y).getS2();
        switch (result) {
            case 1: // ok GREY
                pixel = new Byte3((byte) 128, (byte) 128, (byte) 128);
                break;
            case -1: // no input BLACK
                pixel = new Byte3((byte) 0, (byte) 0, (byte) 0);
                break;
            case -2: // not in image RED
                pixel = new Byte3((byte) 255, (byte) 0, (byte) 0);
                break;
            case -3: // no correspondence GREEN
                pixel = new Byte3((byte) 0, (byte) 255, (byte) 0);
                break;
            case -4: // too far away BLUE
                pixel = new Byte3((byte) 0, (byte) 0, (byte) 255);
                break;
            case -5: // wrong normal YELLOW
                pixel = new Byte3((byte) 255, (byte) 255, (byte) 0);
                break;
            default:
                pixel = new Byte3((byte) 255, (byte) 128, (byte) 128);
                break;
        }
        output.set(x, y, pixel);
    }

    public static void main(String[] args) {

        int n = 2048;
        int m = 2048;
        if (args.length > 1) {
            n = Integer.parseInt(args[0]);
            m = Integer.parseInt(args[1]);
        }

        ImageByte3 outputTornadoVM = new ImageByte3(n, m);
        ImageByte3 outputJava = new ImageByte3(n, m);
        ImageFloat3 input = new ImageFloat3(n, m);

        Random r = new Random();
        for (int i = 0; i < input.X(); i++) {
            for (int j = 0; j < input.Y(); j++) {
                float value = (float) r.nextInt(10) * -1;
                input.set(i, j, new Float3(i, j, value));
            }
        }

        WorkerGrid workerGrid = new WorkerGrid2D(n, m);
        GridTask gridTask = new GridTask("s0.t0", workerGrid);
        KernelContext context = new KernelContext();
        // [Optional] Set the global work group
        workerGrid.setGlobalWork(n, m, 1);
        // [Optional] Set the local work group
        workerGrid.setLocalWork(32, 32, 1);

        TaskSchedule task = new TaskSchedule("s0").task("t0", RenderTrack::renderTrack, context, outputTornadoVM, input).streamOut(outputTornadoVM);
        ArrayList<Long> timers = new ArrayList<>();
        // task.warmup();
        for (int i = 0; i < 10; i++) {
            long start = System.nanoTime();
            task.execute(gridTask);
            long end = System.nanoTime();
            timers.add((end - start));
        }

        if (CHECK_RESULT) {
            renderTrackJava(outputJava, input);
            for (int x = 0; x < n; x++) {
                for (int y = 0; y < m; y++) {
                    if (outputJava.get(x, y).getX() != outputTornadoVM.get(x, y).getX() || outputJava.get(x, y).getY() != outputTornadoVM.get(x, y).getY()
                            || outputJava.get(x, y).getZ() != outputTornadoVM.get(x, y).getZ()) {
                        System.out.println(
                                "Result is not correct: outputTornadoVM[" + x + "][" + y + "]: " + outputTornadoVM.get(x, y) + " !=  outputJava[" + x + "][" + y + "]: " + outputJava.get(x, y));
                        break;
                    }
                }
            }
        }

        System.out.println("Median TornadoVM Task Time: " + Stats.computeMedian(timers));

    }

}
