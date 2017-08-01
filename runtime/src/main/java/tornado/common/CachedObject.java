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

public interface CachedObject {

    public long toBuffer();

    public long getBufferOffset();

//    public long toAbsoluteAddress();
//
//    public long toRelativeAddress();
//    public void read(Object ref);
//
//    public void read(Object ref, int[] events, boolean useDeps);
//
//    public void write(Object ref);
//
//    public int enqueueRead(Object ref, int[] events, boolean useDeps);
//
//    public int enqueueWrite(Object ref, int[] events, boolean useDeps);
//
//    public void allocate(Object ref) throws TornadoOutOfMemoryException;
//
//    public int getAlignment();
//
//    public boolean isValid();
//
//    public void invalidate();
//
//    public void printHeapTrace();
    public long size();

    public int getSerialiserIndex();

    public int getLocalCacheIndex();

    public void setLocalCacheIndex(int value);

}
