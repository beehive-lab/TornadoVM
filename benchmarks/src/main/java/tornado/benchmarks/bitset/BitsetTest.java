/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package tornado.benchmarks.bitset;

import java.util.Random;
import org.apache.lucene.util.LongBitSet;
import tornado.drivers.opencl.runtime.OCLTornadoDevice;
import tornado.runtime.api.TaskSchedule;
import tornado.runtime.cache.TornadoObjectCache;

public class BitsetTest {

    public static final int intersectionCount(int numWords, LongBitSet a, LongBitSet b) {
        final long[] aBits = a.getBits();
        final long[] bBits = b.getBits();
        int sum = 0;
        for (int i = 0; i < numWords; i++) {
//            Debug.printf("0x%d\n", aBits[i]);
            sum += Long.bitCount(aBits[i] & bBits[i]);
        }
        return sum;
    }

    public static final void main(String[] args) {

        final int numWords = Integer.parseInt(args[0]);

        final Random rand = new Random(7);
        final long[] aBits = new long[numWords];
        final long[] bBits = new long[numWords];
        for (int i = 0; i < aBits.length; i++) {
            aBits[i] = rand.nextLong();
            bBits[i] = rand.nextLong();
        }

        final LongBitSet a = new LongBitSet(aBits, numWords * 8);
        final LongBitSet b = new LongBitSet(bBits, numWords * 8);

        TaskSchedule s0 = new TaskSchedule("s0")
                .task("t0", BitsetTest::intersectionCount, numWords, a, b);

        s0.execute();

        TornadoObjectCache.print();

        ((OCLTornadoDevice) s0.getDeviceForTask("t0")).dumpMemory("mem.dump");

        final long value = s0.getReturnValue("t0");
        System.out.printf("value = 0x%x, %d\n", value, value);

        final long ref = intersectionCount(numWords, a, b);
        System.out.printf("ref   = 0x%x, %d\n", ref, ref);

    }

}
