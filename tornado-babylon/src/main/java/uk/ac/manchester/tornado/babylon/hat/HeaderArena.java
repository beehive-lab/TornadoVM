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

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;

/**
 * The arena HAT allocates its buffers from when the backend is TornadoVM. Each allocation has room for
 * a TornadoVM array header just before it, so a HAT buffer can be passed to TornadoVM as a
 * {@code ByteArray} in place ({@link #tornadoSegment}), with no copy in or out on each dispatch.
 */
final class HeaderArena implements Arena {

    /** Bytes reserved before each allocation: the TornadoVM header, ending at an aligned address. */
    private static final long RESERVED = 64;

    private final Arena delegate = Arena.ofShared();
    /** Every HeaderArena's allocations: start address to TornadoVM header + data. */
    private static final Map<Long, MemorySegment> tornadoSegments = new ConcurrentHashMap<>();

    @Override
    public MemorySegment allocate(long byteSize, long byteAlignment) {
        long alignment = Math.max(byteAlignment, RESERVED);
        var whole = delegate.allocate(alignment + byteSize, alignment);
        var data = whole.asSlice(alignment, byteSize);
        // header + data: what ByteArray.fromSegmentShallow wraps (it writes the length into the header)
        tornadoSegments.put(data.address(), whole.asSlice(alignment - TornadoNativeArray.ARRAY_HEADER, TornadoNativeArray.ARRAY_HEADER + byteSize));
        return data;
    }

    /** For a buffer allocated here (starting at {@code segment}), its memory with the TornadoVM header in front. */
    Optional<MemorySegment> tornadoSegment(MemorySegment segment) {
        return find(segment);
    }

    /** For a buffer allocated in any HeaderArena, its memory with the TornadoVM header in front. */
    static Optional<MemorySegment> find(MemorySegment segment) {
        var whole = tornadoSegments.get(segment.address());
        return whole != null && whole.byteSize() - TornadoNativeArray.ARRAY_HEADER == segment.byteSize() ? Optional.of(whole) : Optional.empty();
    }

    @Override
    public MemorySegment.Scope scope() {
        return delegate.scope();
    }

    @Override
    public void close() {
        delegate.close();
    }
}
