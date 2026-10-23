/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2023, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.runtime.interpreter;

import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.runtime.common.ColoursTerminal;
import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;

public class InterpreterUtilities {

    public InterpreterUtilities() {
    }

    static String debugHighLightBC(String bc) {
        return ColoursTerminal.RED + " " + bc + " " + ColoursTerminal.RESET;
    }

    static String debugHighLightNonExecBC(String bc) {
        return ColoursTerminal.YELLOW + " " + bc + " " + ColoursTerminal.RESET;
    }

    static String debugHighLightHelper(String info) {
        return ColoursTerminal.BLUE + info + " " + ColoursTerminal.RESET;
    }

    static String debugDeviceBC(TornadoXPUDevice device) {
        TornadoVMBackendType tornadoVMBackend = device.getTornadoVMBackend();
        if (tornadoVMBackend == TornadoVMBackendType.OPENCL) {
            return ColoursTerminal.CYAN + " " + device + " " + ColoursTerminal.RESET;
        } else if (tornadoVMBackend == TornadoVMBackendType.CUDA) {
            return ColoursTerminal.GREEN + " " + device + " " + ColoursTerminal.RESET;
        }
        return ColoursTerminal.YELLOW + " " + device + " " + ColoursTerminal.RESET;
    }
}
