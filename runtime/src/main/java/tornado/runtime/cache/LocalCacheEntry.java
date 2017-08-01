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

import tornado.common.CachedObject;
import tornado.common.TornadoLogger;

import static tornado.runtime.cache.LocalCacheEntry.LocalCacheState.INVALID;

public class LocalCacheEntry extends TornadoLogger {

    public static enum LocalCacheState {
        MODIFIED, EXCLUSIVE, SHARED, INVALID;
    }

    private LocalCacheState state;
    private final CachedObject allocation;
    private int lastUpdate;

    public LocalCacheEntry(CachedObject allocation) {
        this.allocation = allocation;
        this.state = INVALID;
        this.lastUpdate = -1;
    }

    @Override
    public String toString() {

        return String.format("%s %s", state, allocation.toString());
    }

    public LocalCacheState getState() {
        return state;
    }

    public void setState(LocalCacheState value) {
        state = value;
    }

    public CachedObject getAllocation() {
        return allocation;
    }

    public int getLastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(int value) {
        lastUpdate = value;
    }

}
