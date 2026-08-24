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
package uk.ac.manchester.tornado.runtime.jvmci.reflection;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Fixture hierarchy for the field / member-resolution tests: an annotated field, inherited
 * members across a superclass, and an interface default method — the shapes
 * {@link ReflectionMembers} must resolve the way JVM resolution rules do. The exact members are
 * asserted by the tests, so do not change them without updating those tests.
 */
class ReflectionFieldSample extends ReflectionFieldSampleBase implements ReflectionFieldSampleOps {

    @Retention(RetentionPolicy.RUNTIME)
    @interface Marker {
        String value();
    }

    @Marker("payload")
    double gamma;

    static long staticTotal;

    ReflectionFieldSample(int seed) {
        baseValue = seed;
    }
}

/** Superclass contributing an inherited field and an inherited method. */
class ReflectionFieldSampleBase {
    int baseValue;

    int scale(int factor) {
        return baseValue * factor;
    }
}

/** Interface contributing a default method (resolved after the superclass chain). */
interface ReflectionFieldSampleOps {
    default int op(int x) {
        return x + 1;
    }
}
