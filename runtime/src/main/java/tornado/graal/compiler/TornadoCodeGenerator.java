/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018 APT Group, School of Computer Science, 
 * The University of Manchester
 *
 * This work is partially supported by EPSRC grants:
 * Anyscale EP/L000725/1 and PAMELA EP/K008730/1.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Authors: James Clarkson
 *
 */
package tornado.graal.compiler;

import tornado.common.TornadoLogger;

public final class TornadoCodeGenerator {

    public static final TornadoLogger log = new TornadoLogger(TornadoCodeGenerator.class);

    public static final void debug(final String msg) {
        log.debug(msg);
    }

    public static final void debug(final String pattern, final Object... args) {
        debug(String.format(pattern, args));
    }

    public static final void error(final String msg) {
        log.error(msg);
    }

    public static final void error(final String pattern, final Object... args) {
        error(String.format(pattern, args));
    }

    public static final void fatal(final String msg) {
        log.fatal(msg);
    }

    public static final void fatal(final String pattern, final Object... args) {
        fatal(String.format(pattern, args));
    }

    public static final void info(final String msg) {
        log.info(msg);
    }

    public static final void info(final String pattern, final Object... args) {
        info(String.format(pattern, args));
    }

    public static final void trace(final String msg) {
        log.trace(msg);
    }

    public static final void trace(final String pattern, final Object... args) {
        trace(String.format(pattern, args));
    }

    public static final void warn(final String msg) {
        log.warn(msg);
    }

    public static final void warn(final String pattern, final Object... args) {
        warn(String.format(pattern, args));
    }
}
