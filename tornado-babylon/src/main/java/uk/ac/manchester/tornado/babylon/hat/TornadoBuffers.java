/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.babylon.hat;

import java.lang.invoke.MethodHandles;

import optkl.ifacemapper.BoundSchema;
import optkl.ifacemapper.Buffer;
import optkl.ifacemapper.Schema;
import optkl.util.carriers.ArenaAndLookupCarrier;

/**
 * Allocates HAT interface-mapped buffers for TornadoVM programs, without a HAT {@code Accelerator}:
 *
 * <pre>{@code
 * Particles particles = TornadoBuffers.allocate(MethodHandles.lookup(), Particles.schema, n);
 * new TaskGraph("s").transferToDevice(DataTransferMode.FIRST_EXECUTION, particles)
 *         .task("move", Kernels::move, new KernelContext(), particles, dt) ...
 * }</pre>
 *
 * The memory has room for TornadoVM's array header in front, so TornadoVM uses it in place.
 */
public final class TornadoBuffers {

    private static final HeaderArena ARENA = new HeaderArena();

    private TornadoBuffers() {
    }

    /** A carrier for HAT's own factories ({@code F32Array.create(carrier, n)}). */
    public static ArenaAndLookupCarrier carrier(MethodHandles.Lookup lookup) {
        return ArenaAndLookupCarrier.of(lookup, ARENA);
    }

    /** A buffer of {@code schema} with the given array lengths ({@code arrayLen} fields, in order). */
    public static <T extends Buffer> T allocate(MethodHandles.Lookup lookup, Schema<T> schema, int... lengths) {
        return BoundSchema.of(carrier(lookup), schema, lengths).allocate();
    }
}
