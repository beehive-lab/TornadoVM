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
package tornado.drivers.opencl.enums;

import uk.ac.manchester.tornado.api.enums.TornadoExecutionStatus;

public enum OCLCommandExecutionStatus {
	CL_UNKNOWN									 (0x4),
	 CL_COMPLETE                                 (0x0),
	 CL_RUNNING                                  (0x1),
	 CL_SUBMITTED                                (0x2),
	 CL_QUEUED                                   (0x3),
	 CL_ERROR									 (-1);

	private final int	value;

	OCLCommandExecutionStatus(final int v) {
		value = v;
	}

	public int getValue() {
		return value;
	}
	
	public static OCLCommandExecutionStatus toEnum(final int v) {
		OCLCommandExecutionStatus result = OCLCommandExecutionStatus.CL_UNKNOWN;
		switch (v) {
			case 0:
				result = OCLCommandExecutionStatus.CL_COMPLETE;
				break;
			case 1:
				result = OCLCommandExecutionStatus.CL_RUNNING;
				break;
			case 2:
				result = OCLCommandExecutionStatus.CL_SUBMITTED;
				break;
			case 3:
				result = OCLCommandExecutionStatus.CL_QUEUED;
				break;
			default:
				result = OCLCommandExecutionStatus.CL_ERROR;
		}
		return result;
	}
	
	public TornadoExecutionStatus toTornadoExecutionStatus(){
		TornadoExecutionStatus result = TornadoExecutionStatus.UNKNOWN;
		switch(this){
			case CL_COMPLETE:
				result = TornadoExecutionStatus.COMPLETE;
				break;
			case CL_QUEUED:
				result = TornadoExecutionStatus.QUEUED;
				break;
			case CL_RUNNING:
				result = TornadoExecutionStatus.RUNNING;
				break;
			case CL_SUBMITTED:
				result = TornadoExecutionStatus.SUBMITTED;
				break;
			default:
				result = TornadoExecutionStatus.ERROR;
				break;	
		}
		return result;
	}
}
