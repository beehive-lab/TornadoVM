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
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.runtime.common;

import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class TornadoLogger {

    private static Logger LOGGER;

    public TornadoLogger(Class<?> clazz) {
        LOGGER = Logger.getLogger(clazz == null ? this.getClass().getName() : clazz.getName());
        if (!Tornado.FULL_DEBUG) {
            LogManager.getLogManager().reset();
        }
    }

    public TornadoLogger() {
        this(null);
    }

    public static void debug(final String msg) {
        LOGGER.setLevel(Level.INFO);
        LOGGER.info(msg);
    }

    public static void debug(final String pattern, final Object... args) {
        debug(String.format(pattern, args));
    }

    public static void error(final String msg) {
        LOGGER.setLevel(Level.SEVERE);
        LOGGER.severe(msg);
    }

    public static void error(final String pattern, final Object... args) {
        error(String.format(pattern, args));
    }

    public static void fatal(final String msg) {
        LOGGER.setLevel(Level.SEVERE);
        LOGGER.severe(msg);
    }

    public static void fatal(final String pattern, final Object... args) {
        fatal(String.format(pattern, args));
    }

    public static void info(final String msg) {
        LOGGER.info(msg);
    }

    public static void info(final String pattern, final Object... args) {
        info(String.format(pattern, args));
    }

    public static void trace(final String msg) {
        LOGGER.setLevel(Level.INFO);
        LOGGER.info(msg);
    }

    public static void warn(final String msg) {
        LOGGER.setLevel(Level.WARNING);
        LOGGER.warning(msg);
    }

}
