/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.runtime.graal;

import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

public final class TornadoLIRGenerator {

    public static final TornadoLogger log = new TornadoLogger(TornadoLIRGenerator.class);

    public static void debug(final String msg) {
        log.debug(msg);
    }

    public static void debug(final String pattern, final Object... args) {
        debug(String.format(pattern, args));
    }

    public static void error(final String msg) {
        log.error(msg);
    }

    public static void error(final String pattern, final Object... args) {
        error(String.format(pattern, args));
    }

    public static void fatal(final String msg) {
        log.fatal(msg);
    }

    public static void fatal(final String pattern, final Object... args) {
        fatal(String.format(pattern, args));
    }

    public static void info(final String msg) {
        log.info(msg);
    }

    public static void info(final String pattern, final Object... args) {
        info(String.format(pattern, args));
    }

    public static void trace(final String msg) {
        log.trace(msg);
    }

    public static void trace(final String pattern, final Object... args) {
        trace(String.format(pattern, args));
    }

    public static void warn(final String msg) {
        log.warn(msg);
    }

    public static void warn(final String pattern, final Object... args) {
        warn(String.format(pattern, args));
    }
}
