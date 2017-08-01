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
package tornado.common;

import tornado.runtime.cache.TornadoByteBuffer;

public interface TornadoMemoryProvider {

    public long getCallStackSize();

    public long getCallStackAllocated();

    public long getCallStackRemaining();

    public long getHeapSize();

    public long getHeapRemaining();

    public long getHeapAllocated();

    public boolean isInitialised();

    public void reset();

    public long toAbsoluteAddress(TornadoByteBuffer buffer);

    public long toRelativeAddress(TornadoByteBuffer buffer);

    public long toAbsoluteAddress(CachedObject object);

    public long toRelativeAddress(CachedObject object);

}
