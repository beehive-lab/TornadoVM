/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.ptx.mm;

import static uk.ac.manchester.tornado.runtime.common.TornadoOptions.DEBUG;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.exceptions.TornadoMemoryException;
import uk.ac.manchester.tornado.api.exceptions.TornadoOutOfMemoryException;
import uk.ac.manchester.tornado.api.memory.XPUBuffer;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

public class FieldBuffer {

    private final Field field;
    private final XPUBuffer objectBuffer;
    private final TornadoLogger logger;

    public FieldBuffer(final Field field, final XPUBuffer objectBuffer) {
        this.objectBuffer = objectBuffer;
        this.field = field;
        this.logger = new TornadoLogger(getClass());
    }

    public void allocate(final Object ref, long batchSize, Access access) throws TornadoOutOfMemoryException, TornadoMemoryException {
        objectBuffer.allocate(getFieldValue(ref), batchSize, access);
    }

    public void deallocate() {
        objectBuffer.markAsFreeBuffer();
    }

    public int enqueueRead(long executionPlanId, final Object ref, final int[] events, boolean useDeps) {
        if (DEBUG) {
            logger.trace("fieldBuffer: enqueueRead* - field=%s, parent=0x%x, child=0x%x", field, ref.hashCode(), getFieldValue(ref).hashCode());
        }
        // TODO: Offset 0
        int eventId = objectBuffer.enqueueRead(executionPlanId, getFieldValue(ref), 0, (useDeps) ? events : null, useDeps);
        return (useDeps) ? eventId : -1;
    }

    public List<Integer> enqueueWrite(long executionPlanId, final Object ref, final int[] events, boolean useDeps) {
        if (DEBUG) {
            logger.trace("fieldBuffer: enqueueWrite* - field=%s, parent=0x%x, child=0x%x", field, ref.hashCode(), getFieldValue(ref).hashCode());
        }
        List<Integer> eventsIds = objectBuffer.enqueueWrite(executionPlanId, getFieldValue(ref), 0, 0, (useDeps) ? events : null, useDeps);
        return (useDeps) ? eventsIds : Collections.emptyList();
    }

    private Object getFieldValue(final Object container) {
        Object value = null;
        try {
            value = field.get(container);
        } catch (IllegalArgumentException | IllegalAccessException e) {
            logger.warn("Illegal access to field: name=%s, object=0x%x", field.getName(), container.hashCode());
        }
        return value;
    }

    public void read(long executionPlanId, final Object ref) {
        read(executionPlanId, ref, null, false);
    }

    public int read(long executionPlanId, final Object ref, int[] events, boolean useDeps) {
        if (DEBUG) {
            logger.debug("fieldBuffer: read - field=%s, parent=0x%x, child=0x%x", field, ref.hashCode(), getFieldValue(ref).hashCode());
        }
        // TODO: reading with offset != 0
        return objectBuffer.read(executionPlanId, getFieldValue(ref), 0, 0, events, useDeps);
    }

    public long toBuffer() {
        return objectBuffer.toBuffer();
    }

    public void write(long executionPlanId, final Object ref) {
        if (DEBUG) {
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
