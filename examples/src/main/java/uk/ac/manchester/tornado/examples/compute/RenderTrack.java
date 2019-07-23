/*
 * Copyright (c) 2013-2019, APT Group, School of Computer Science,
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
package uk.ac.manchester.tornado.examples.compute;

import java.util.ArrayList;
import java.util.Random;

import uk.ac.manchester.tornado.api.Policy;
import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.collections.types.Byte3;
import uk.ac.manchester.tornado.api.collections.types.Float3;
import uk.ac.manchester.tornado.api.collections.types.ImageByte3;
import uk.ac.manchester.tornado.api.collections.types.ImageFloat3;
import uk.ac.manchester.tornado.examples.reductions.Stats;

public class RenderTrack {

    public static void renderTrack(ImageByte3 output, ImageFloat3 input) {
        for (@Parallel int y = 0; y < input.Y(); y++) {
            for (@Parallel int x = 0; x < input.X(); x++) {
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

    public static void main(String[] args) {

        int n = 2048;
        int m = 2048;
        if (args.length > 1) {
            n = Integer.parseInt(args[0]);
            m = Integer.parseInt(args[1]);
        }

        ImageByte3 output = new ImageByte3(n, m);
        ImageFloat3 input = new ImageFloat3(n, m);

        Random r = new Random();
        for (int i = 0; i < input.X(); i++) {
            for (int j = 0; j < input.Y(); j++) {
                float value = (float) r.nextInt(10) * -1;
                input.set(i, j, new Float3(i, j, value));
            }
        }

        TaskSchedule task = new TaskSchedule("s0").task("t0", RenderTrack::renderTrack, output, input).streamOut(output);
        ArrayList<Long> timers = new ArrayList<>();
        task.warmup();
        for (int i = 0; i < 10; i++) {
            long start = System.nanoTime();
            task.executeWithProfilerSequential(Policy.PERFORMANCE);
            long end = System.nanoTime();
            timers.add((end - start));
        }

        System.out.println("Median TotalTime: " + Stats.computeMedian(timers));

    }

}
