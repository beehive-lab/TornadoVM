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
package tornado.graal.backend;

import com.oracle.graal.compiler.target.Backend;
import com.oracle.graal.phases.util.Providers;
import tornado.common.RuntimeUtilities;

public abstract class TornadoBackend<P extends Providers> extends Backend {

    // % of global memory to allocate:q
    public static final long DEFAULT_HEAP_ALLOCATION = RuntimeUtilities
            .parseSize(System
                    .getProperty(
                            "tornado.heap.allocation",
                            "512MB"));

    public final static boolean ENABLE_EXCEPTIONS = Boolean
            .parseBoolean(System
                    .getProperty(
                            "tornado.exceptions",
                            "False"));

    protected TornadoBackend(Providers providers) {
        super(providers);
    }

    public abstract String decodeDeopt(long value);

 
    @Override
    public Providers getProviders() {
        return super.getProviders();
    }

}
