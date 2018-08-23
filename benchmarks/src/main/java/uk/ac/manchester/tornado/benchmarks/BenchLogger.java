/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018, APT Group, School of Computer Science,
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
package uk.ac.manchester.tornado.benchmarks;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BenchLogger {

    private final Logger logger;

    public BenchLogger(Class<?> clazz) {
        logger = LogManager.getLogger(clazz == null ? this.getClass() : clazz);
    }

    public BenchLogger() {
        this(null);
    }

    public final void debug(final String msg) {
        logger.debug(msg);
    }

    public final void debug(final String pattern, final Object... args) {
        debug(String.format(pattern, args));
    }

    public final void error(final String msg) {
        logger.error(msg);
    }

    public final void error(final String pattern, final Object... args) {
        error(String.format(pattern, args));
    }

    public final void fatal(final String msg) {
        logger.fatal(msg);
    }

    public final void fatal(final String pattern, final Object... args) {
        fatal(String.format(pattern, args));
    }

    public final void info(final String msg) {
        logger.info(msg);
    }

    public final void info(final String pattern, final Object... args) {
        info(String.format(pattern, args));
    }

    public final void trace(final String msg) {
        logger.trace(msg);
    }

    public final void trace(final String pattern, final Object... args) {
        trace(String.format(pattern, args));
    }

    public final void warn(final String msg) {
        logger.warn(msg);
    }

    public final void warn(final String pattern, final Object... args) {
        warn(String.format(pattern, args));
    }

}
