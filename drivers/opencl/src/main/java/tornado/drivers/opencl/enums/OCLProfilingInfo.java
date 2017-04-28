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
package tornado.drivers.opencl.enums;

public enum OCLProfilingInfo {
	CL_PROFILING_COMMAND_QUEUED(0x1280),
	CL_PROFILING_COMMAND_SUBMIT(0x1281),
	CL_PROFILING_COMMAND_START(0x1282),
	CL_PROFILING_COMMAND_END(0x1283);

	private final int	value;

	OCLProfilingInfo(final int v) {
		value = v;
	}

	public int getValue() {
		return value;
	}
}
