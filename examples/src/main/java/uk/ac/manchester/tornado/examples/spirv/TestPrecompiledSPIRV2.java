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
 * tornado --printBytecodes --debug -Dtornado.spirv.levelzero.memoryAlloc.shared=True uk.ac.manchester.tornado.examples.spirv.TestPrecompiledSPIRV2
 * </code>
 * 
 * OpenCL kernel (see below)
 * 
 * How to generate SPIRV?
 * 
 * <code>
 *     $ ~/bin/scripts/spirv-util.sh pre2
 *     $ cp pre2.spv /tmp/
 * </code>
 * 
 */

// @formatter:off
// CODE:
//    #pragma OPENCL EXTENSION cl_khr_int64_base_atomics : enable
//            __kernel void sum(__global uchar *_heap_base, ulong _frame_base)
//            {
//            ulong ul_1, ul_2, ul_0, ul_12, ul_10, ul_8;
//            int i_14, i_15, i_13, i_3, i_4, i_11, i_9;
//            long l_5, l_6, l_7;
//
//            __global ulong *_frame = (__global ulong *) &_heap_base[_frame_base];
//
//
//            // BLOCK 0
//            ul_0  =  (ulong) _frame[3];
//            ul_1  =  (ulong) _frame[4];
//            ul_2  =  (ulong) _frame[5];
//            i_3  =  get_global_id(0);
//            // BLOCK 1 MERGES [0 2 ]
//            i_4  =  i_3;
//            for(;i_4 < 256;)  {
//        // BLOCK 2
//        l_5  =  (long) i_4;
//        l_6  =  l_5 << 2;
//        l_7  =  l_6 + 24L;
//        ul_8  =  ul_1 + l_7;
//        i_9  =  *((__global int *) ul_8);
//        ul_10  =  ul_2 + l_7;
//        i_11  =  *((__global int *) ul_10);
//        ul_12  =  ul_0 + l_7;
//        i_13  =  i_9 + i_11;
//        *((__global int *) ul_12)  =  i_13;
//        i_14  =  get_global_size(0);
//        i_15  =  i_14 + i_4;
//        i_4  =  i_15;
//        }  // B2
//        // BLOCK 3
//        return;
//        }  //  kernel
// @formatter:on

public class TestPrecompiledSPIRV2 {

    private static boolean PRINT = true;

    public static void main(String[] args) {
        final int numElements = 256;
        int[] a = new int[numElements];
        int[] b = new int[numElements];
        int[] c = new int[numElements];

        Arrays.fill(b, 100);
        Arrays.fill(c, 150);

        TornadoDevice defaultDevice = TornadoRuntime.getTornadoRuntime().getDriver(0).getDevice(0);
        System.out.println("DEFAULT DEVICE: " + defaultDevice);

        String filePath = "/tmp/pre2.spv";

        // @formatter:off
        TaskSchedule ts = new TaskSchedule("s0")
                .streamIn(a, b, c)
                .prebuiltTask("t0",
                        "sum",
                        filePath,
                        new Object[] { a, b, c },
                        new Access[] { Access.WRITE, Access.READ, Access.READ },
                        defaultDevice,
                        new int[] { numElements, 1, 1 })
                .streamOut(a);
        // @formatter:on
        ts.execute();

        if (PRINT) {
            System.out.println("a: " + Arrays.toString(a));
        }
        int[] result = new int[numElements];
        Arrays.fill(result, 250);
        System.out.println("Result correct? " + Arrays.equals(a, result));
    }
}
