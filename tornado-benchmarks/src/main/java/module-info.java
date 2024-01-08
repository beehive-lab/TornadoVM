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
module tornado.benchmarks {
    requires org.apache.commons.lang3;
    requires java.logging;
    requires jmh.core;

    requires transitive tornado.api;
    requires transitive tornado.matrices;

    exports uk.ac.manchester.tornado.benchmarks;
    exports uk.ac.manchester.tornado.benchmarks.addImage;
    exports uk.ac.manchester.tornado.benchmarks.blackscholes;
    exports uk.ac.manchester.tornado.benchmarks.blurFilter;
    exports uk.ac.manchester.tornado.benchmarks.convolvearray;
    exports uk.ac.manchester.tornado.benchmarks.convolveimage;
    exports uk.ac.manchester.tornado.benchmarks.dft;
    exports uk.ac.manchester.tornado.benchmarks.dgemm;
    exports uk.ac.manchester.tornado.benchmarks.dotimage;
    exports uk.ac.manchester.tornado.benchmarks.dotvector;
    exports uk.ac.manchester.tornado.benchmarks.euler;
    exports uk.ac.manchester.tornado.benchmarks.hilbert;
    exports uk.ac.manchester.tornado.benchmarks.mandelbrot;
    exports uk.ac.manchester.tornado.benchmarks.montecarlo;
    exports uk.ac.manchester.tornado.benchmarks.nbody;
    exports uk.ac.manchester.tornado.benchmarks.rotateimage;
    exports uk.ac.manchester.tornado.benchmarks.rotatevector;
    exports uk.ac.manchester.tornado.benchmarks.saxpy;
    exports uk.ac.manchester.tornado.benchmarks.sgemm;
    exports uk.ac.manchester.tornado.benchmarks.sgemv;
    exports uk.ac.manchester.tornado.benchmarks.spmv;
    exports uk.ac.manchester.tornado.benchmarks.stencil;
}
