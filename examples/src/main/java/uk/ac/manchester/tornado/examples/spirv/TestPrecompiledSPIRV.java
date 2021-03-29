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
 * __kernel void copyTest(__global uchar *_heap_base)
 * {
 *    int i_8, i_7, i_1, i_2; 
 *    ulong ul_0, ul_6; 
 *    long l_3, l_5, l_4; 
 * 
 *    __global ulong *_frame = (__global ulong *) &_heap_base[0];
 * 
 *    ul_0  =  (ulong) _frame[3];
 *    i_1  =  get_global_id(0);
 *    i_2  =  i_1;
 *    l_3  =  (long) i_2;
 *    l_4  =  l_3 << 2;
 *    l_5  =  l_4 + 24L;
 *    ul_6  =  ul_0 + l_5;
 *    *((__global int *) ul_6)  =  10;
 * }
 * </code>
 * 
 */
public class TestPrecompiledSPIRV {

    public static void main(String[] args) {
        final int numElements = 256;
        int[] a = new int[numElements];
        int[] b = new int[numElements];
        Arrays.fill(a, 100);

        TornadoDevice defaultDevice = TornadoRuntime.getTornadoRuntime().getDriver(0).getDevice(0);
        System.out.println("DEFAULT DEVICE: " + defaultDevice);

        String filePath = "/tmp/testCopy.spv";

        // @formatter:off
        new TaskSchedule("s0")
                .prebuiltTask("t0",
                        "copyTest",
                        filePath,
                        new Object[] { a, b },
                        new Access[] { Access.READ, Access.WRITE },
                        defaultDevice,
                        new int[] { numElements, 1, 1 })
                .streamOut(b)
                .execute();
        // @formatter:on

        System.out.println("b: " + Arrays.toString(b));
    }
}
