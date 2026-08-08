/*
 * Copyright (c) 2024, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.fuzz;

import java.util.ArrayList;
import java.util.List;

/**
 * A self-contained description of a failing case, enough for {@code JUnitEmitter}
 * to write a standalone regression test that embeds the kernel, the exact failing
 * inputs (as literals) and the golden expected output (as literals).
 */
public final class ReproSpec {

    /** One kernel parameter: a named TornadoVM off-heap array with concrete values. */
    public static final class Binding {
        public final String name;
        public final ElemType type;
        /** The backing native array object (IntArray/FloatArray/...). */
        public final Object array;
        public final boolean output;
        /** Whether the array is copied to the device before execution (true for inputs and accumulators). */
        public final boolean copyIn;

        public Binding(String name, ElemType type, Object array, boolean output, boolean copyIn) {
            this.name = name;
            this.type = type;
            this.array = array;
            this.output = output;
            this.copyIn = copyIn;
        }
    }

    /** Full source of the kernel method, named exactly {@code kernel}. First param is KernelContext. */
    public final String kernelSource;
    public final ElemType elemType;
    public final int globalWork;
    public final int localWork;
    public final List<Binding> bindings = new ArrayList<>();

    /** Golden expected values for the output array (from the JVM oracle). */
    public Object expectedOutput;
    public String outputName;

    /** Optional trailing scalar int kernel argument (e.g. localSize for reductions). */
    public String scalarArgName;
    public int scalarArgValue;

    public ReproSpec(String kernelSource, ElemType elemType, int globalWork, int localWork) {
        this.kernelSource = kernelSource;
        this.elemType = elemType;
        this.globalWork = globalWork;
        this.localWork = localWork;
    }

    public ReproSpec addInput(String name, ElemType type, Object array) {
        bindings.add(new Binding(name, type, array, false, true));
        return this;
    }

    public ReproSpec setOutput(String name, ElemType type, Object array, Object expected) {
        bindings.add(new Binding(name, type, array, true, false));
        this.outputName = name;
        this.expectedOutput = expected;
        return this;
    }

    /** Output that is also copied in (an accumulator seeded on the host, e.g. atomic add). */
    public ReproSpec setAccumulator(String name, ElemType type, Object array, Object expected) {
        bindings.add(new Binding(name, type, array, true, true));
        this.outputName = name;
        this.expectedOutput = expected;
        return this;
    }

    public ReproSpec withScalar(String name, int value) {
        this.scalarArgName = name;
        this.scalarArgValue = value;
        return this;
    }
}
