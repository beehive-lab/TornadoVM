package uk.ac.manchester.tornado.examples.spirv;

import java.util.Arrays;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;

/**
 * How to run?
 *
 * <code>
 * tornado --printBytecodes --debug -Dtornado.spirv.levelzero.memoryAlloc.shared=True uk.ac.manchester.tornado.examples.spirv.TestPrecompiledSPIRV
 * </code>
 * 
 * Running this kernel:
 * 
 * <code>
 *  __kernel void copyTest(__global uchar *_heap_base, ulong _frame_base)
 * {
 * ulong ul_0, ul_6; 
 * long l_4, l_5, l_3; 
 * int i_1, i_2, i_7, i_8; 
 *
 * __global ulong *_frame = (__global ulong *) &_heap_base[_frame_base];
 *
 *
 * // BLOCK 0
 * ul_0  =  (ulong) _frame[3];
 * i_1  =  get_global_id(0);
 * // BLOCK 1 MERGES [0 2 ]
 * i_2  =  i_1;
 * for(;i_2 < 256;)  {
 * // BLOCK 2
 * l_3  =  (long) i_2;
 * l_4  =  l_3 << 2;
 * l_5  =  l_4 + 24L;
 * ul_6  =  ul_0 + l_5;
 *  *((__global int *) ul_6)  =  333;
 * i_7  =  get_global_size(0);
 * i_8  =  i_7 + i_2;
 * i_2  =  i_8;
 * }  // B2
 * // BLOCK 3
 * return;
 * }  //  kernel
 * </code>
 * 
 * How to generate SPIRV?
 * 
 * <code>
 *     $ ~/bin/scripts/spirv-util.sh precompiled
 *     $ cp precompiled.spv /tmp/testCopy.spv
 * </code>
 * 
 */
public class TestPrecompiledSPIRV {

    private static boolean PRINT = false;

    public static void main(String[] args) {
        final int numElements = 256;
        int[] a = new int[numElements];

        TornadoDevice defaultDevice = TornadoRuntime.getTornadoRuntime().getDriver(0).getDevice(0);
        System.out.println("DEFAULT DEVICE: " + defaultDevice);

        String filePath = "/tmp/testCopy.spv";

        // @formatter:off
        TaskSchedule ts = new TaskSchedule("s0")
                .streamIn(a)
                .prebuiltTask("t0",
                        "copyTest",
                        filePath,
                        new Object[] { a },
                        new Access[] { Access.WRITE },
                        defaultDevice,
                        new int[] { numElements, 1, 1 })
                .streamOut(a);
        // @formatter:on
        ts.execute();

        if (PRINT) {
            System.out.println("a: " + Arrays.toString(a));
        }
        int[] result = new int[numElements];
        Arrays.fill(result, 333);
        System.out.println("Result correct? " + Arrays.equals(a, result));
    }
}
