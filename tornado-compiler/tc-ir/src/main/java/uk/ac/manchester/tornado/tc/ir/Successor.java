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

/**
 * One control-flow edge of a terminator: the target block and the values passed to its arguments.
 * Edits go through the owning {@link Inst} so that use lists stay consistent.
 */
public final class Successor {

    private Block target;
    private final List<Value> args;

    Successor(Block target, List<Value> args) {
        this.target = target;
        this.args = new ArrayList<>(args);
    }

    public Block target() {
        return target;
    }

    public List<Value> args() {
        return Collections.unmodifiableList(args);
    }

    void setTarget(Block target) {
        this.target = target;
    }

    List<Value> mutableArgs() {
        return args;
    }
}
