/*
 * Copyright (c) 2013-2019, APT Group, School of Computer Science,
 * The University of Manchester.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *    http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
