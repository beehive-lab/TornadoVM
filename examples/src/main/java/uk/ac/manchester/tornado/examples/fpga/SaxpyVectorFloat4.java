/*
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
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

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.collections.types.Float4;
import uk.ac.manchester.tornado.api.collections.types.VectorFloat4;

public class SaxpyVectorFloat4 {

    public static void saxpy(float alpha, VectorFloat4 x, VectorFloat4 y, VectorFloat4 b) {
        for (@Parallel int i = 0; i < x.getLength(); i++) {
            Float4 temp = Float4.mult(x.get(i), alpha);
            y.set(i, Float4.add(temp, b.get(i)));
        }
    }

    public static void main(String[] args) {

        if (args.length < 1) {
            System.out.println("Usage: <elements> ");
            System.exit(-1);
        }

        int numElements = Integer.parseInt(args[0]);

        float alpha = 2f;
        VectorFloat4 vectorA = new VectorFloat4(numElements);
        VectorFloat4 vectorB = new VectorFloat4(numElements);
        VectorFloat4 vectorC = new VectorFloat4(numElements);
        VectorFloat4 results = new VectorFloat4(numElements);

        vectorA.fill(450f);
        vectorB.fill(0);
        vectorC.fill(20);

        // @formatter:off
        TaskSchedule s0 = new TaskSchedule("s0")
                .task("t0", SaxpyVectorFloat4::saxpy, alpha, vectorA, vectorB, vectorC)
                .streamOut(vectorB);
        // @formatter:on

        for (int idx = 0; idx < 10; idx++) {
            s0.execute();
            saxpy(alpha, vectorA, results, vectorC);

            System.out.println("Checking result");
            boolean wrongResult = false;

            for (int i = 0; i < vectorB.getLength(); i++) {

                if (Math.abs(vectorB.get(i).getW() - results.get(i).getW()) > 0.1) {
                    wrongResult = true;
                } else if (Math.abs(vectorB.get(i).getX() - results.get(i).getX()) > 0.1) {
                    wrongResult = true;
                }
                if (Math.abs(vectorB.get(i).getZ() - results.get(i).getZ()) > 0.1) {
                    wrongResult = true;
                }
                if (Math.abs(vectorB.get(i).getY() - results.get(i).getY()) > 0.1) {
                    wrongResult = true;
                }
            }

            if (!wrongResult) {
                System.out.println("Test success");
            } else {
                System.out.println("Result is wrong");
            }
        }
    }
}
