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
 * An SSA value: the result of an {@link Inst} or a {@link BlockArg}. Every value tracks its users;
 * a user appears once per operand slot that refers to the value.
 */
public abstract sealed class Value permits Inst, BlockArg {

    private Type type;
    private String name;
    private final List<Inst> users = new ArrayList<>();

    protected Value(Type type, String name) {
        this.type = type;
        this.name = name == null ? "" : name;
    }

    public Type type() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    /** Readability hint (usually the Java local's name); empty when unknown. Carries no semantics. */
    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? "" : name;
    }

    /** Users of this value, once per operand slot. */
    public List<Inst> users() {
        return Collections.unmodifiableList(users);
    }

    public boolean hasUses() {
        return !users.isEmpty();
    }

    /** Makes every user of this value refer to {@code replacement} instead. */
    public void replaceAllUsesWith(Value replacement) {
        if (replacement == this) {
            return;
        }
        for (var user : List.copyOf(users)) {
            user.replaceUsesOf(this, replacement);
        }
    }

    void addUser(Inst user) {
        users.add(user);
    }

    void removeUser(Inst user) {
        users.remove(user);
    }
}
