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

import java.lang.foreign.GroupLayout;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

import optkl.ifacemapper.Buffer;
import optkl.ifacemapper.MappableIface;
import uk.ac.manchester.tornado.api.ObjectAdapter;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.babylon.tc.HatLayouts;

/**
 * HAT's interface-mapped buffers ({@link Buffer}: interfaces with a {@code Schema}, structs included)
 * as TornadoVM objects, in any task graph: a buffer stands for a {@code ByteArray} over its own
 * memory, and a kernel parameter that receives one gets its layout, so TC compiles the kernel's
 * accessors ({@code universe.body(i).x()}) from its code model. Installed as a TornadoVM
 * {@link ObjectAdapter} service; buffers come from {@link TornadoBuffers} or an {@code Accelerator}
 * on {@link TornadoHatBackend}.
 */
public final class HatObjectAdapter implements ObjectAdapter {

    private static final Map<Object, ByteArray> OBJECTS = Collections.synchronizedMap(new IdentityHashMap<>());

    /** The TornadoVM object for {@code buffer}: the same one every time, over its memory in place. */
    static ByteArray objectFor(Buffer buffer) {
        return OBJECTS.computeIfAbsent(buffer, b -> {
            var segment = MappableIface.getMemorySegment(buffer);
            return ByteArray.fromSegmentShallow(HeaderArena.find(segment).orElseThrow(() -> new IllegalArgumentException(
                    "a HAT buffer passed to TornadoVM must be allocated with TornadoBuffers or by an Accelerator on TornadoHatBackend")));
        });
    }

    @Override
    public Object adapt(Object object) {
        return object instanceof Buffer buffer ? objectFor(buffer) : null;
    }

    @Override
    public void bound(Object kernel, int parameter, Object object) {
        Method method = kernel instanceof Method m ? m : kernel instanceof java.io.Serializable lambda ? HatTasks.method(lambda) : null;
        if (method != null && object instanceof Buffer buffer) {
            HatLayouts.record(method, parameter, (GroupLayout) MappableIface.getLayout(buffer));
        }
    }
}
