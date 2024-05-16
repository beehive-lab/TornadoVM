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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
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

    private final Logger logger;
    private final boolean isLogOptionEnabled = TornadoOptions.DEBUG || TornadoOptions.FULL_DEBUG;

    public TornadoLogger(Class<?> clazz) {
        if (clazz == null) {
            logger = Logger.getAnonymousLogger();
        } else {
            logger = Logger.getLogger(clazz.getName());
        }
    }

    public TornadoLogger() {
        this(null);
    }

    public void debug(final String msg) {
        if (isLogOptionEnabled) {
            logger.setLevel(Level.INFO);
            logger.info(msg);
        }
    }

    public void debug(final String pattern, final Object... args) {
        debug(String.format(pattern, args));
    }

    public void error(final String msg) {
        if (isLogOptionEnabled) {
            logger.setLevel(Level.SEVERE);
            logger.severe(msg);
        }
    }

    public void error(final String pattern, final Object... args) {
        if (isLogOptionEnabled) {
            error(String.format(pattern, args));
        }
    }

    public void fatal(final String msg) {
        if (isLogOptionEnabled) {
            logger.setLevel(Level.SEVERE);
            logger.severe(msg);
        }
    }

    public void fatal(final String pattern, final Object... args) {
        fatal(String.format(pattern, args));
    }

    public void info(final String msg) {
        if (isLogOptionEnabled) {
            logger.setLevel(Level.INFO);
            logger.info(msg);
        }
    }

    public void info(final String pattern, final Object... args) {
        info(String.format(pattern, args));
    }

    public void trace(final String msg) {
        if (isLogOptionEnabled) {
            logger.setLevel(Level.INFO);
            logger.info(msg);
        }
    }

    public void trace(final String pattern, final Object... args) {
        trace(String.format(pattern, args));
    }

    public void warn(final String msg) {
        if (isLogOptionEnabled) {
            logger.setLevel(Level.WARNING);
            logger.warning(msg);
        }
    }

    public void warn(final String msg, final Object... args) {
        trace(String.format(msg, args));
    }
}
