/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package tornado.examples.objects;

import java.util.Arrays;
import tornado.api.Parallel;
import tornado.runtime.api.TaskSchedule;

public class InterfaceExample {

    interface BinaryOp {

        public int apply(int a, int b);
    }

    static class AddOp implements BinaryOp {

        @Override
        public int apply(int a, int b) {
            return a + b;
        }
    }

    static class SubOp implements BinaryOp {

        @Override
        public int apply(int a, int b) {
            return a + b;
        }
    }

    public static void run(BinaryOp[] ops, int[] a, int[] b, int[] c) {
        for (@Parallel int i = 0; i < ops.length; i++) {
            c[i] = ops[i].apply(a[i], b[i]);
        }
    }

    public static void main(String[] args) {

        BinaryOp[] ops = new BinaryOp[8];
        for (int i = 0; i < 8; i++) {
            ops[i] = (i % 2 == 0) ? new AddOp() : new SubOp();
        }

        int[] a = new int[]{1, 2, 3, 4, 5, 6, 7, 8};
        int[] b = new int[]{1, 2, 3, 4, 5, 6, 7, 8};
        int[] c = new int[8];

        TaskSchedule s0 = new TaskSchedule("s0")
                .task("t0", InterfaceExample::run, ops, a, b, c)
                .streamOut(c);
        s0.warmup();
        s0.execute();

        System.out.println("c = " + Arrays.toString(c));

    }

}
