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
package uk.ac.manchester.tornado.tc.ir;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** A compilation unit: the kernel and the device functions it calls. */
public final class IrModule {

    private final List<Function> functions = new ArrayList<>();

    public List<Function> functions() {
        return Collections.unmodifiableList(functions);
    }

    public Function add(Function function) {
        if (find(function.name()).isPresent()) {
            throw new IllegalArgumentException("duplicate function @" + function.name());
        }
        functions.add(function);
        return function;
    }

    public Optional<Function> find(String name) {
        return functions.stream().filter(f -> f.name().equals(name)).findFirst();
    }
}
