/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.babylon.tc;

import java.lang.foreign.GroupLayout;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The bound memory layouts of the HAT buffers a kernel is launched with, by kernel method and
 * parameter index. A HAT buffer's layout depends on the instance (an S32Array's sequence length is
 * its size), so the backend records it before each launch and the frontend reads it while translating.
 */
public final class HatLayouts {

    private static final Map<Method, Map<Integer, GroupLayout>> LAYOUTS = new ConcurrentHashMap<>();

    private HatLayouts() {
    }

    public static void record(Method kernel, int parameter, GroupLayout layout) {
        LAYOUTS.computeIfAbsent(kernel, k -> new ConcurrentHashMap<>()).put(parameter, layout);
    }

    static GroupLayout layout(Method kernel, int parameter) {
        var forKernel = LAYOUTS.get(kernel);
        return forKernel == null ? null : forKernel.get(parameter);
    }
}
