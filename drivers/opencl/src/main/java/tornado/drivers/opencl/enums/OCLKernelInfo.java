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

public enum OCLKernelInfo {

	CL_KERNEL_FUNCTION_NAME(0x1190), CL_KERNEL_NUM_ARGS(0x1191), CL_KERNEL_REFERENCE_COUNT(
			0x1192), CL_KERNEL_CONTEXT(0x1193), CL_KERNEL_PROGRAM(0x1194), CL_KERNEL_ATTRIBUTES(
			0x1195);

	private final int	value;

	OCLKernelInfo(final int v) {
		value = v;
	}

	public int getValue() {
		return value;
	}
}
