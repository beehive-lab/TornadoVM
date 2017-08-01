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
package tornado.runtime.cache;

import java.util.BitSet;
import tornado.common.TornadoLogger;

public class GlobalCacheEntry extends TornadoLogger {

    private final BitSet devices;
    private final int[] localCacheIndex;

    public GlobalCacheEntry(int maxCaches) {
        devices = new BitSet(maxCaches);
        localCacheIndex = new int[maxCaches];
    }

    public int getLocalCacheEntryIndex(int cacheId) {
        if (devices.get(cacheId)) {
            return localCacheIndex[cacheId];
        }
        return -1;
    }

    public void setLocalCacheEntryIndex(int cacheId, int index) {
        if (index >= 0) {
            devices.set(cacheId);
        } else {
            devices.clear(cacheId);
        }
        localCacheIndex[cacheId] = index;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        if (devices.cardinality() > 0) {
            sb.append("global object cache entry:\n");
            for (int i = devices.nextSetBit(0); i >= 0; i = devices.nextSetBit(i + 1)) {
                sb.append(String.format("\tdevice=%d, local cache index=%d\n", i, localCacheIndex[i]));
            }
        } else {
            sb.append("no local copies exist");
        }
        return sb.toString();
    }

    public BitSet devices() {
        return devices;
    }

}
