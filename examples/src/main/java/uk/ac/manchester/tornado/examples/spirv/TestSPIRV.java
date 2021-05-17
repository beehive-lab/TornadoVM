package uk.ac.manchester.tornado.examples.spirv;

import java.util.Arrays;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;

/**
 * Test used for generating OpenCL kernel. Note, the lookupBuffer address kernel
 * is pre-compiled.
 * 
 * How to run?
 * 
 * <code>
 *    tornado --debug -Dtornado.recover.bailout=False -Dtornado.fullDebug=False -Ds0.t0.device=0:0 --printKernel uk.ac.manchester.tornado.examples.spirv.TestSPIRV 4
 * </code>
 */
public class TestSPIRV {

    public static void copyTest(int[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = 50;
        }
    }

    public static void copyTest2(int[] a, int[] b) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = b[i];
        }
    }

    public static void compute(int[] a, int[] b) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = b[i] + 50;
        }
    }

    public static void vectorAddCompute(int[] a, int[] b, int[] c) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = b[i] + c[i];
        }
    }

    public static void copyTestZero(int[] a) {
        a[0] = 50;
    }

    public static void sum(int[] a, int[] b, int[] c) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = b[i] + c[i];
        }
    }

    public static void testSimple00() {
        final int numElements = 256;
        int[] a = new int[numElements];

        new TaskSchedule("s0") //
                .task("t0", TestSPIRV::copyTestZero, a) //
                .streamOut(a) //
                .execute(); //

        System.out.println("a: " + Arrays.toString(a));

        if (a[0] == 50) {
            System.out.println("Result is CORRECT");
        }
    }

    public static void testSimple01() {

        final int numElements = 256;
        int[] a = new int[numElements];

        new TaskSchedule("s0") //
                .task("t0", TestSPIRV::copyTest, a) //
                .streamOut(a) //
                .execute(); //

        System.out.println("a: " + Arrays.toString(a));

        boolean correct = true;
        for (int i = 0; i < a.length; i++) {
            if (a[i] != 50) {
                correct = false;
                break;
            }
        }

        if (correct) {
            System.out.println("Result is CORRECT");
        }
    }

    public static void testSimple02() {

        final int numElements = 256;
        int[] a = new int[numElements];
        int[] b = new int[numElements];

        Arrays.fill(b, 100);

        new TaskSchedule("s0") //
                .task("t0", TestSPIRV::copyTest2, a, b) //
                .streamOut(a) //
                .execute(); //

        System.out.println("a: " + Arrays.toString(a));

        boolean correct = true;
        for (int i = 0; i < a.length; i++) {
            if (a[i] != 100) {
                correct = false;
                break;
            }
        }

        if (correct) {
            System.out.println("Result is CORRECT");
        }

    }

    public static void testSimple03() {

        final int numElements = 256;
        int[] a = new int[numElements];
        int[] b = new int[numElements];

        Arrays.fill(b, 100);

        new TaskSchedule("s0") //
                .task("t0", TestSPIRV::compute, a, b) //
                .streamOut(a) //
                .execute(); //

        System.out.println("a: " + Arrays.toString(a));

        boolean correct = true;
        for (int i = 0; i < a.length; i++) {
            if (a[i] != 100) {
                correct = false;
                break;
            }
        }

        if (correct) {
            System.out.println("Result is CORRECT");
        }
    }

    public static void vectorAdd() {

        final int numElements = 256;
        int[] a = new int[numElements];
        int[] b = new int[numElements];
        int[] c = new int[numElements];

        Arrays.fill(b, 100);
        Arrays.fill(c, 200);

        new TaskSchedule("s0") //
                .task("t0", TestSPIRV::vectorAddCompute, a, b, c) //
                .streamOut(a) //
                .execute(); //

        System.out.println("a: " + Arrays.toString(a));

        boolean correct = true;
        for (int i = 0; i < a.length; i++) {
            if (a[i] != (b[i] + c[i])) {
                correct = false;
                break;
            }
        }

        if (correct) {
            System.out.println("Result is CORRECT");
        }

    }

    public static void main(String[] args) {

        int test = 0;
        if (args.length > 0) {
            try {
                test = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                test = 0;
            }
        }

        switch (test) {
            case 0:
                testSimple00();
                break;
            case 1:
                testSimple01();
                break;
            case 2:
                testSimple02();
                break;
            case 3:
                testSimple03();
                break;
            case 4:
                vectorAdd();
                break;
            default:
                testSimple00();
                break;
        }
    }
}
