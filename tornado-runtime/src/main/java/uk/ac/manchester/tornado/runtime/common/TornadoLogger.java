/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, 2023, APT Group, Department of Computer Science,
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
        if (isLogOptionEnabled) {
            debug(String.format(pattern, args));
        }
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
        if (isLogOptionEnabled) {
            fatal(String.format(pattern, args));
        }
    }

    public void info(final String msg) {
        if (isLogOptionEnabled) {
            logger.setLevel(Level.INFO);
            logger.info(msg);
        }
    }

    public void info(final String pattern, final Object... args) {
        if (isLogOptionEnabled) {
            info(String.format(pattern, args));
        }
    }

    public void trace(final String msg) {
        if (isLogOptionEnabled) {
            logger.setLevel(Level.INFO);
            logger.info(msg);
        }
    }

    public void trace(final String pattern, final Object... args) {
        if (isLogOptionEnabled) {
            trace(String.format(pattern, args));
        }
    }

    public void warn(final String msg) {
        if (isLogOptionEnabled) {
            logger.setLevel(Level.WARNING);
            logger.warning(msg);
        }
    }

    public void warn(final String msg, final Object... args) {
        if (isLogOptionEnabled) {
            trace(String.format(msg, args));
        }
    }
}
