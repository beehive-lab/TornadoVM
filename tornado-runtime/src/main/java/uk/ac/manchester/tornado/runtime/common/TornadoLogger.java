/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, 2023, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.runtime.common;

import java.util.logging.Level;
import java.util.logging.Logger;

public class TornadoLogger {

    private static Logger logger;

    static boolean isLogOptionEnabled = Tornado.DEBUG || Tornado.FULL_DEBUG;

    public TornadoLogger(Class<?> clazz) {
        logger = Logger.getLogger(clazz == null ? this.getClass().getName() : clazz.getName());
    }

    public TornadoLogger() {
        this(null);
    }

    public static void debug(final String msg) {
        if (isLogOptionEnabled) {
            logger.setLevel(Level.INFO);
            logger.info(msg);
        }
    }

    public static void debug(final String pattern, final Object... args) {
        debug(String.format(pattern, args));
    }

    public static void error(final String msg) {
        if (isLogOptionEnabled) {
            logger.setLevel(Level.SEVERE);
            logger.severe(msg);
        }
    }

    public static void error(final String pattern, final Object... args) {
        if (isLogOptionEnabled) {
            error(String.format(pattern, args));
        }
    }

    public static void fatal(final String msg) {
        if (isLogOptionEnabled) {
            logger.setLevel(Level.SEVERE);
            logger.severe(msg);
        }
    }

    public static void fatal(final String pattern, final Object... args) {
        fatal(String.format(pattern, args));
    }

    public static void info(final String msg) {
        if (isLogOptionEnabled) {
            logger.setLevel(Level.INFO);
            logger.info(msg);
        }
    }

    public static void info(final String pattern, final Object... args) {
        info(String.format(pattern, args));
    }

    public static void trace(final String msg) {
        if (isLogOptionEnabled) {
            logger.setLevel(Level.INFO);
            logger.info(msg);
        }
    }

    public static void warn(final String msg) {
        if (isLogOptionEnabled) {
            logger.setLevel(Level.WARNING);
            logger.warning(msg);
        }
    }

}
