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
    private boolean derivedName;
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
        this.derivedName = false;
    }

    /**
     * Names the value after another one ({@code a_data} after {@code a}): a name TC made, which the
     * structurizer does not treat as a Java variable the programmer named.
     */
    public void setDerivedName(String name) {
        setName(name);
        this.derivedName = !this.name.isEmpty();
    }

    /** Whether the name was made by TC ({@link #setDerivedName}) rather than taken from the source. */
    public boolean hasDerivedName() {
        return derivedName;
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
