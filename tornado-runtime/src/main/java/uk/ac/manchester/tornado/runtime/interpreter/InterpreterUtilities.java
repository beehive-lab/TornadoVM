/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2023, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
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
        } else if (tornadoVMBackend == TornadoVMBackendType.SPIRV) {
            return ColoursTerminal.PURPLE + " " + device + " " + ColoursTerminal.RESET;
        } else if (tornadoVMBackend == TornadoVMBackendType.PTX) {
            return ColoursTerminal.GREEN + " " + device + " " + ColoursTerminal.RESET;
        }
        return ColoursTerminal.YELLOW + " " + device + " " + ColoursTerminal.RESET;
    }
}
