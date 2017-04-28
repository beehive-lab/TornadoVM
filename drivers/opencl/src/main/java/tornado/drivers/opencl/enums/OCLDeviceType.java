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

public enum OCLDeviceType {
        Unknown (-1),
	CL_DEVICE_TYPE_DEFAULT(1 << 0),
	CL_DEVICE_TYPE_CPU(1 << 1),
	CL_DEVICE_TYPE_GPU(1 << 2),
	CL_DEVICE_TYPE_ACCELERATOR(1 << 3),
	CL_DEVICE_TYPE_CUSTOM(1 << 4),
	CL_DEVICE_TYPE_ALL(0xFFFFFFFF);

	private final long	value;

	OCLDeviceType(final long v) {
		value = v;
	}

	public long getValue() {
		return value;
	}

	public static final OCLDeviceType toDeviceType(final long v) {
		OCLDeviceType result = null;
		switch ((int) v) {
			case 1 << 0:
				result = OCLDeviceType.CL_DEVICE_TYPE_DEFAULT;
				break;
			case 1 << 1:
				result = OCLDeviceType.CL_DEVICE_TYPE_CPU;
				break;
			case 1 << 2:
				result = OCLDeviceType.CL_DEVICE_TYPE_GPU;
				break;
			case 1 << 3:
				result = OCLDeviceType.CL_DEVICE_TYPE_ACCELERATOR;
				break;
			case 1 << 4:
				result = OCLDeviceType.CL_DEVICE_TYPE_CUSTOM;
				break;
			case 0xFFFFFFFF:
				result = OCLDeviceType.CL_DEVICE_TYPE_ALL;
				break;
		}
		return result;
	}
}
