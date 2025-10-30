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
open module tornado.examples {
    requires transitive java.desktop;
    requires transitive tornado.api;
    requires org.graalvm.polyglot;
    requires jdk.incubator.vector;

    exports uk.ac.manchester.tornado.examples;
    exports uk.ac.manchester.tornado.examples.arrays;
    exports uk.ac.manchester.tornado.examples.common;
    exports uk.ac.manchester.tornado.examples.compute;
    exports uk.ac.manchester.tornado.examples.fft;
    exports uk.ac.manchester.tornado.examples.flatmap;
    exports uk.ac.manchester.tornado.examples.kernelcontext.compute;
    exports uk.ac.manchester.tornado.examples.kernelcontext.matrices;
    exports uk.ac.manchester.tornado.examples.kernelcontext.reductions;
    exports uk.ac.manchester.tornado.examples.matrices;
    exports uk.ac.manchester.tornado.examples.polyglot;
    exports uk.ac.manchester.tornado.examples.reductions;
    exports uk.ac.manchester.tornado.examples.vectors;
}
