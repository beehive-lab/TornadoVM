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
package uk.ac.manchester.tornado.benchmarks.dgemm;

import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.BenchmarkRunner;

public class Benchmark extends BenchmarkRunner {

    private int width;
    private int height;

    @Override
    public void parseArgs(String[] args) {
        if (args.length == 3) {
            iterations = Integer.parseInt(args[0]);
            width = Integer.parseInt(args[1]);
            height = Integer.parseInt(args[2]);

        } else {
            iterations = 20;
            width = 512;
            height = 512;
        }
    }

    @Override
    protected String getName() {
        return "dgemm";
    }

    @Override
    protected String getIdString() {
        return String.format("%s-%d-%d-%d", getName(), iterations, width, height);
    }

    @Override
    protected String getConfigString() {
        return String.format("width=%d, height=%d", width, height);
    }

    @Override
    protected BenchmarkDriver getJavaDriver() {
        return new DgemmJava(iterations, width, height);
    }

    @Override
    protected BenchmarkDriver getTornadoDriver() {
        return new DgemmTornado(iterations, width, height);
    }

}
