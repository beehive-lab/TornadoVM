/*
 * Copyright (c) 2013-2018, APT Group, School of Computer Science,
 * The University of Manchester.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *    http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */

package uk.ac.manchester.tornado.examples.fpga;

import java.util.Arrays;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;

/**
 * Initialize an FPGA kernel. It performs a copy-in and copy-out of the same
 * array to check device buffers and offset within Tornado.
 * 
 * <p>
 * In the pre-compiled mode:
 * <p>
 * <code>
 * tornado --debug -Ds0.t0.device=0:1 \
 * -Dtornado.precompiled.binary=path/to/lookupBufferAddress,s0.t0.device=0:1
 * -Dtornado.opencl.userelative=True uk.ac.manchester.tornado.examples.fpga.InitFPGA
 * </code>
 * </p>
 * </p>
 * <p>
 * In Full JIT Mode (debug):
 * <p>
 * <code>
 * env CL_CONTEXT_EMULATOR_DEVICE_INTELFPGA=1 tornado  
 * -Ds0.t0.device=0:1 
 * -Dtornado.assembler.removeloops=true 
 * -Dtornado.opencl.accelerator.fpga=true 
 * -Dtornado.fpga.emulation=true  
 * -Dtornado.fpga.flags=v,report  
 * -Dtornado.opencl.userelative=True 
 * -Dtornado.print.kernel=True
 *  uk.ac.manchester.tornado.examples.fpga.InitFPGA
 * </code>
 * </p>
 * </p>
 * 
 * <p>
 * In Full JIT Mode:
 * <p>
 * <code>
 * tornado  
 * -Ds0.t0.device=0:1 
 * -Dtornado.assembler.removeloops=true 
 * -Dtornado.opencl.accelerator.fpga=true   
 * -Dtornado.fpga.flags=v,report  
 * -Dtornado.opencl.userelative=True 
 * -Dtornado.print.kernel=True
 *  uk.ac.manchester.tornado.examples.fpga.InitFPGA
 * </code>
 * </p>
 * </p>
 * 
 * 
 */
public class InitFPGA {

    public static void init(float[] x) {
        for (@Parallel int i = 0; i < x.length; i++) {
            x[i] = x[i] + 100;
        }
    }

    public static void main(String[] args) {
        int numElements = 256;
        float[] x = new float[numElements];

        Arrays.fill(x, 10);

        // @formatter:off
        TaskSchedule s0 = new TaskSchedule("s0")
                .streamIn(x)
                .task("t0", InitFPGA::init, x)
                .streamOut(x);
        // @formatter:on

        s0.execute();

        boolean wrongResult = false;
        for (int i = 0; i < x.length; i++) {
            if (x[i] != 110) {
                wrongResult = true;
                break;
            }
        }
        if (!wrongResult) {
            System.out.println("Test success");
        } else {
            System.out.println("Result is wrong");
        }
    }
}
