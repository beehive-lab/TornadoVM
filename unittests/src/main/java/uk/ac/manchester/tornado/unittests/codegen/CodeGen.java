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
package uk.ac.manchester.tornado.unittests.codegen;

import java.util.stream.IntStream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

public class CodeGen extends TornadoTestBase {

    public static void cascadeKernel(int grayIntegralImage[], int imageWidth, int imageHeight, int resultsXY[]) {
        for (@Parallel int y = 0; y < imageHeight; y++) {
            for (@Parallel int x = 0; x < imageWidth; x++) {
                int gradient = grayIntegralImage[(y * imageWidth) + x];
            }
        }
    }

    @Test
    public void test01() {

        TaskSchedule ts = new TaskSchedule("foo");

        int imageWidth = 512;
        int imageHeight = 512;
        int[] grayIntegralImage = new int[imageHeight * imageWidth];
        int[] resultsXY = new int[imageHeight * imageWidth];

        IntStream.range(0, imageHeight * imageHeight).forEach(x -> grayIntegralImage[x] = x);

        ts.task("bar", CodeGen::cascadeKernel, grayIntegralImage, imageWidth, imageHeight, resultsXY) //
                .streamOut(resultsXY);

        ts.execute();
    }
}
