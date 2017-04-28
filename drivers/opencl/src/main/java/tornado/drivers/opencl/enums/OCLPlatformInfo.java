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

public enum OCLPlatformInfo {

	CL_PLATFORM_PROFILE(0x0900), CL_PLATFORM_VERSION(0x0901), CL_PLATFORM_NAME(
			0x0902), CL_PLATFORM_VENDOR(0x0903), CL_PLATFORM_EXTENSIONS(0x0904);

	private final int	value;

	OCLPlatformInfo(final int v) {
		value = v;
	}

	public int getValue() {
		return value;
	}
}
