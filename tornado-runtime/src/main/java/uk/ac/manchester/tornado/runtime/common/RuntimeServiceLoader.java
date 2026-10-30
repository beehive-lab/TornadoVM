/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2026, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.runtime.common;

import java.util.ServiceLoader;

/**
 * Finds the service providers of the TornadoVM runtime (backends, kernel analyses) independently of
 * the calling thread's context class loader. Applications that embed TornadoVM (Spark executors,
 * application servers, plugin hosts) often touch the runtime first from a thread whose context
 * class loader cannot see the TornadoVM modules; looking providers up through that loader finds
 * none of them.
 */
public final class RuntimeServiceLoader {

    private RuntimeServiceLoader() {
    }

    /**
     * Loads the providers of {@code service} visible from the TornadoVM runtime: from the module
     * layer of the runtime module when it is a named module, or else from the class loader that
     * loaded the runtime.
     */
    public static <S> ServiceLoader<S> load(Class<S> service) {
        Module module = RuntimeServiceLoader.class.getModule();
        ModuleLayer layer = module.getLayer();
        if (module.isNamed() && layer != null) {
            return ServiceLoader.load(layer, service);
        }
        ClassLoader loader = RuntimeServiceLoader.class.getClassLoader();
        return ServiceLoader.load(service, loader != null ? loader : ClassLoader.getSystemClassLoader());
    }
}
