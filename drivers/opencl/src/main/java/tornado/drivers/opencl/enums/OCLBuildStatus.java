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

public enum OCLBuildStatus {
	CL_BUILD_SUCCESS(0),
	CL_BUILD_NONE(-1),
	CL_BUILD_ERROR(-2),
	CL_BUILD_IN_PROGRESS(-3),
	CL_BUILD_UNKNOWN(-4);

	private final int	value;

	OCLBuildStatus(final int v) {
		value = v;
	}

	public int getValue() {
		return value;
	}

	public static OCLBuildStatus toEnum(final int v) {
		OCLBuildStatus result = OCLBuildStatus.CL_BUILD_UNKNOWN;
		switch (v) {
			case 0:
				result = OCLBuildStatus.CL_BUILD_SUCCESS;
				break;
			case -1:
				result = OCLBuildStatus.CL_BUILD_NONE;
				break;
			case -2:
				result = OCLBuildStatus.CL_BUILD_ERROR;
				break;
			case -3:
				result = OCLBuildStatus.CL_BUILD_IN_PROGRESS;
				break;
		}
		return result;
	}

}
