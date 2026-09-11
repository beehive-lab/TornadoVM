/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2023, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.runtime.interpreter;

import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

public class InterpreterUtilities {

    private static final boolean COLOUR_ENABLED = resolveColourPolicy();

    public InterpreterUtilities() {
    }

    /**
     * Resolves {@code tornado.print.bytecodes.color}. The default (auto) keeps the escape codes out of
     * redirected output, which is where the log usually ends up when it is being read carefully.
     */
    private static boolean resolveColourPolicy() {
        String policy = TornadoOptions.BYTECODE_LOG_COLOUR;
        if ("always".equalsIgnoreCase(policy)) {
            return true;
        }
        if ("never".equalsIgnoreCase(policy)) {
            return false;
        }
        return System.console() != null && System.getenv("NO_COLOR") == null;
    }

    static boolean isColourEnabled() {
        return COLOUR_ENABLED;
    }
}
