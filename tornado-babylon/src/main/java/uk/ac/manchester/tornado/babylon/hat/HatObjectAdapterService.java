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

import uk.ac.manchester.tornado.api.ObjectAdapter;

/**
 * The {@link ObjectAdapter} service this module registers: {@link HatObjectAdapter} when HAT is on the
 * class path, nothing otherwise. It names no HAT type itself, so it loads without HAT (a TornadoVM
 * program that uses only code-model kernels).
 */
public final class HatObjectAdapterService implements ObjectAdapter {

    private final ObjectAdapter delegate;

    public HatObjectAdapterService() {
        ObjectAdapter adapter = null;
        try {
            Class.forName("optkl.ifacemapper.Buffer", false, HatObjectAdapterService.class.getClassLoader());
            adapter = (ObjectAdapter) Class.forName("uk.ac.manchester.tornado.babylon.hat.HatObjectAdapter").getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException | LinkageError e) {
            // no HAT: no interface-mapped buffers to adapt
        }
        this.delegate = adapter;
    }

    @Override
    public Object adapt(Object object) {
        return delegate == null ? null : delegate.adapt(object);
    }

    @Override
    public void bound(Object kernel, int parameter, Object object) {
        if (delegate != null) {
            delegate.bound(kernel, parameter, object);
        }
    }
}
