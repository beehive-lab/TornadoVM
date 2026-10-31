/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.cuda.mm;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

import uk.ac.manchester.tornado.api.memory.XPUBuffer;
import uk.ac.manchester.tornado.api.options.DebugFlags;
import uk.ac.manchester.tornado.runtime.common.FieldLayout;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

public class FieldBuffer {

    private final Field field;
    private final XPUBuffer objectBuffer;
    private final TornadoLogger logger;

    public FieldBuffer(final Field field, final XPUBuffer objectBuffer) {
        this.objectBuffer = objectBuffer;
        this.field = field;
        this.logger = new TornadoLogger(this.getClass());
    }

    public int enqueueRead(long executionPlanId, final Object ref, final int[] events, boolean useDeps) {
        if (DebugFlags.DEBUG.get()) {
            logger.trace("fieldBuffer: enqueueRead* - field=%s, parent=0x%x, child=0x%x", field, ref.hashCode(), getFieldValue(ref).hashCode());
        }
        // TODO: Offset 0
        final int event = objectBuffer.enqueueRead(executionPlanId, getFieldValue(ref), 0, (useDeps) ? events : null, useDeps);
        return useDeps ? event : -1;
    }

    public List<Integer> enqueueWrite(long executionPlanId, final Object ref, final int[] events, boolean useDeps) {
        if (DebugFlags.DEBUG.get()) {
            logger.trace("fieldBuffer: enqueueWrite* - field=%s, parent=0x%x, child=0x%x", field, ref.hashCode(), getFieldValue(ref).hashCode());
        }
        List<Integer> fieldEvents = objectBuffer.enqueueWrite(executionPlanId, getFieldValue(ref), 0, 0, (useDeps) ? events : null, useDeps);
        return fieldEvents == null ? Collections.emptyList() : fieldEvents;
    }

    private Object getFieldValue(final Object container) {
        return FieldLayout.get(field, container);
    }

    public void read(long executionPlanId, final Object ref) {
        read(executionPlanId, ref, null, false);
    }

    public int read(long executionPlanId, final Object ref, int[] events, boolean useDeps) {
        if (DebugFlags.DEBUG.get()) {
            logger.debug("fieldBuffer: read - field=%s, parent=0x%x, child=0x%x", field, ref.hashCode(), getFieldValue(ref).hashCode());
        }
        // TODO: reading with offset != 0
        return objectBuffer.read(executionPlanId, getFieldValue(ref), 0, 0, events, useDeps);
    }

    public void write(long executionPlanId, final Object ref) {
        if (DebugFlags.DEBUG.get()) {
            logger.trace("fieldBuffer: write - field=%s, parent=0x%x, child=0x%x", field, ref.hashCode(), getFieldValue(ref).hashCode());
        }
        objectBuffer.write(executionPlanId, getFieldValue(ref));
    }

    public long size() {
        return objectBuffer.size();
    }

    void setBuffer(XPUBuffer.XPUBufferWrapper bufferWrapper) {
        objectBuffer.setBuffer(bufferWrapper);
    }

    long getBufferOffset() {
        return objectBuffer.getBufferOffset();
    }

}
