/* 
 * Copyright 2012 James Clarkson.
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
 */
package tornado.common;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TornadoLogger {

	private final Logger	logger;

	public TornadoLogger(Class<?> clazz){
		logger = LogManager.getLogger(clazz == null ? this.getClass() : clazz);
	}
	
	public TornadoLogger(){
		this(null);
	}

	public void debug(final String msg) {
		logger.debug(msg);
	}

	public void debug(final String pattern, final Object... args) {
		debug(String.format(pattern, args));
	}

	public void error(final String msg) {
		logger.error(msg);
	}

	public void error(final String pattern, final Object... args) {
		error(String.format(pattern, args));
	}

	public void fatal(final String msg) {
		logger.fatal(msg);
	}

	public void fatal(final String pattern, final Object... args) {
		fatal(String.format(pattern, args));
	}

	public void info(final String msg) {
		logger.info(msg);
	}

	public void info(final String pattern, final Object... args) {
		info(String.format(pattern, args));
	}

	public void trace(final String msg) {
		logger.trace(msg);
	}

	public void trace(final String pattern, final Object... args) {
		trace(String.format(pattern, args));
	}

	public void warn(final String msg) {
		logger.warn(msg);
	}

	public void warn(final String pattern, final Object... args) {
		warn(String.format(pattern, args));
	}

}
