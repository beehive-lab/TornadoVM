package uk.ac.manchester.tornado.runtime.interpreter;

import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.runtime.common.ColoursTerminal;
import uk.ac.manchester.tornado.runtime.common.TornadoAcceleratorDevice;

public class InterpreterUtilities {

    public InterpreterUtilities() {
    }

    static String debugHighLightBC(String bc) {
        return ColoursTerminal.RED + " " + bc + " " + ColoursTerminal.RESET;
    }

    static String debugHighLightHelper(String info) {
        return ColoursTerminal.BLUE + info + " " + ColoursTerminal.RESET;
    }

    static String debugDeviceBC(TornadoAcceleratorDevice device) {
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
