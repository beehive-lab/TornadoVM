/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package tornado.examples.openbitset;

import java.util.Random;
import org.apache.lucene.util.OpenBitSet;
import tornado.runtime.api.TaskSchedule;

public class OpenBitSetExample {

    public static OpenBitSet genBitSet(int numWords) {
        long[] bits = new long[numWords];
        Random rand = new Random();
        for (int i = 0; i < numWords; i++) {
            bits[i] = rand.nextLong();
        }

        return new OpenBitSet(bits, numWords);
    }

    public static final void main(String[] args) {

        final int numElements = 64;
        OpenBitSet a = genBitSet(numElements);
        OpenBitSet b = genBitSet(numElements);

        TaskSchedule s0 = new TaskSchedule("s0")
                .task("intersect", OpenBitSet::intersectionCount, a, b);

        s0.execute();

        long value = s0.getReturnValue("intersect");
        System.out.printf("value = %d (%d)\n", value, OpenBitSet.intersectionCount(a, b));

    }

}
