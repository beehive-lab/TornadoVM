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
package uk.ac.manchester.tornado.tc.ir.analysis;

/** What {@link CountedLoop#analyse} found: a counted loop, or why the loop is not one. */
public sealed interface LoopShape permits CountedLoop, LoopShape.NotCounted {

    /** Why a loop is not counted, for passes that must explain a refusal. */
    enum Failure {
        /** Not exactly one entry edge and one back edge. */
        EDGES,
        /** The back edge does not pass {@code iv + step}. */
        INCREMENT,
        /** The step changes inside the loop. */
        STEP
    }

    /** Not a counted loop, and why. */
    record NotCounted(Failure failure) implements LoopShape {
    }
}
