/* 
 * Copyright 2012 James Clarkson.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tornado.benchmarks.addvector;

import tornado.benchmarks.BenchmarkRunner;
import tornado.benchmarks.BenchmarkDriver;

public class Benchmark extends BenchmarkRunner {

    private int size;

    @Override
    protected String getName() {
        return "add-vector";
    }

    @Override
    protected String getIdString() {
        return String.format("%s-%d-%d", getName(), iterations, size);
    }

    @Override
    protected String getConfigString() {
        return String.format("size=%d", size);
    }

    @Override
    protected BenchmarkDriver getJavaDriver() {
        return new AddJava(iterations, size);
    }

    @Override
    protected BenchmarkDriver getTornadoDriver() {
        return new AddTornado(iterations, size);
    }

    @Override
    public void parseArgs(String[] args) {
        if (args.length == 2) {
            iterations = Integer.parseInt(args[0]);
            size = Integer.parseInt(args[1]);

        } else {
            iterations = 100;
            size = 76800; //4194304
        }
    }

}
