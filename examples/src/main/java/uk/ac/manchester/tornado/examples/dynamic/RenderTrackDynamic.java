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

import java.util.Random;

import uk.ac.manchester.tornado.api.Policy;
import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.collections.types.Byte3;
import uk.ac.manchester.tornado.api.collections.types.Float3;
import uk.ac.manchester.tornado.api.collections.types.ImageByte3;
import uk.ac.manchester.tornado.api.collections.types.ImageFloat3;

public class RenderTrackDynamic {

    public static String executionType;
    public static int iterations;
    public static boolean VALIDATION = true;

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

    public static boolean validate(ImageFloat3 input, ImageByte3 output) {
        boolean validation = true;
        ImageByte3 validationOutput = new ImageByte3(output.X(), output.Y());

        renderTrack(validationOutput, input);

        for (int i = 0; i < validationOutput.Y(); i++) {
            for (int j = 0; j < validationOutput.X(); j++) {
                if ( (validationOutput.get(i,j).getX() != output.get(i,j).getX()) || (validationOutput.get(i,j).getY() != output.get(i,j).getY()) || (validationOutput.get(i,j).getZ() != output.get(i,j).getZ()) ) {
                    return false;
                }
            }
        }
        return validation;
    }

    public static void main(String[] args) {

        if (args.length < 3) {
            System.out.println("Usage: <elements> <mode:performance|end|sequential> <iterations>");
            System.exit(-1);
        }

        int n = 2048;
        int m = 2048;
        if (args.length > 2) {
            n = Integer.parseInt(args[0]);
            m = n;
        }

        executionType = args[1];
        iterations = Integer.parseInt(args[2]);
        long end;
        long start;

        ImageByte3 output = new ImageByte3(n, m);
        ImageFloat3 input = new ImageFloat3(n, m);

        Random r = new Random();
        for (int i = 0; i < input.X(); i++) {
            for (int j = 0; j < input.Y(); j++) {
                float value = (float) r.nextInt(10) * -1;
                input.set(i, j, new Float3(i, j, value));
            }
        }

        long startInit = System.nanoTime();
        TaskSchedule s0 = new TaskSchedule("s0").task("t0", RenderTrackDynamic::renderTrack, output, input).streamOut(output);
        long stopInit = System.nanoTime();
        System.out.println("Initialization time:  " + (stopInit - startInit) + " ns" + "\n");

        for (int i = 0; i < iterations; i++) {
            switch (executionType) {
                case "performance":
                    start = System.nanoTime();
                    s0.executeWithProfilerSequential(Policy.PERFORMANCE);
                    end = System.nanoTime();
                    break;
                case "end":
                    start = System.nanoTime();
                    s0.executeWithProfilerSequential(Policy.END_2_END);
                    end = System.nanoTime();
                    break;
                case "sequential":
                    System.gc();
                    start = System.nanoTime();
                    renderTrack(output, input);
                    end = System.nanoTime();
                    break;
                default:
                    start = System.nanoTime();
                    s0.execute();
                    end = System.nanoTime();
            }
            System.out.println("Total time:  " + (end - start) + " ns" + " \n");
        }

        if (VALIDATION) {
            if (validate(input, output)) {
                System.out.println("Validation: " + "SUCCESS " + "\n");
            } else {
                System.out.println("Validation: " + "FAIL " + "\n");
            }
        }
    }

}
