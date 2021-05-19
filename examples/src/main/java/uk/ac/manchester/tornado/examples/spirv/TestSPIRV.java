package uk.ac.manchester.tornado.examples.spirv;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;

import java.util.Arrays;

/**
 * Test used for generating OpenCL kernel. Note, the lookupBuffer address kernel
 * is pre-compiled.
 * 
 * How to run?
 * 
 * <code>
 *    tornado --debug -Dtornado.recover.bailout=False -Ds0.t0.device=0:0 --printKernel uk.ac.manchester.tornado.examples.spirv.TestSPIRV 4
 * </code>
 */
public class TestSPIRV {

    public static void copyTest(int[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = 50;
        }
    }

    public static void addValue(int[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] += 50;
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

    public static void vectorMul(int[] a, int[] b, int[] c) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = b[i] * c[i];
        }
    }

    public static void vectorSub(int[] a, int[] b, int[] c) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = b[i] - c[i];
        }
    }

    public static void vectorDiv(int[] a, int[] b, int[] c) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = b[i] / c[i];
        }
    }

    public static void vectorSquare(int[] a, int[] b) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = b[i] * b[i];
        }
    }

    public static void saxpy(int[] a, int[] b, int[] c, int alpha) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = alpha * b[i] + c[i];
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

    public static void vectorMul() {

        final int numElements = 256;
        int[] a = new int[numElements];
        int[] b = new int[numElements];
        int[] c = new int[numElements];

        Arrays.fill(b, 100);
        Arrays.fill(c, 5);

        new TaskSchedule("s0") //
                .task("t0", TestSPIRV::vectorMul, a, b, c) //
                .streamOut(a) //
                .execute(); //

        System.out.println("a: " + Arrays.toString(a));

        boolean correct = true;
        for (int i = 0; i < a.length; i++) {
            if (a[i] != (b[i] * c[i])) {
                correct = false;
                break;
            }
        }

        if (correct) {
            System.out.println("Result is CORRECT");
        }
    }

    public static void vectorSub() {

        final int numElements = 256;
        int[] a = new int[numElements];
        int[] b = new int[numElements];
        int[] c = new int[numElements];

        Arrays.fill(b, 100);
        Arrays.fill(c, 75);

        new TaskSchedule("s0") //
                .task("t0", TestSPIRV::vectorSub, a, b, c) //
                .streamOut(a) //
                .execute(); //

        System.out.println("a: " + Arrays.toString(a));

        boolean correct = true;
        for (int i = 0; i < a.length; i++) {
            if (a[i] != (b[i] - c[i])) {
                correct = false;
                break;
            }
        }

        if (correct) {
            System.out.println("Result is CORRECT");
        }
    }

    public static void vectorDiv() {

        final int numElements = 256;
        int[] a = new int[numElements];
        int[] b = new int[numElements];
        int[] c = new int[numElements];

        Arrays.fill(b, 512);
        Arrays.fill(c, 2);

        new TaskSchedule("s0") //
                .task("t0", TestSPIRV::vectorDiv, a, b, c) //
                .streamOut(a) //
                .execute(); //

        System.out.println("a: " + Arrays.toString(a));

        boolean correct = true;
        for (int i = 0; i < a.length; i++) {
            if (a[i] != (b[i] / c[i])) {
                correct = false;
                break;
            }
        }

        if (correct) {
            System.out.println("Result is CORRECT");
        }
    }

    public static void square() {

        final int numElements = 32;
        int[] a = new int[numElements];
        int[] b = new int[numElements];
        int[] c = new int[numElements];

        for (int i = 0; i < a.length; i++) {
            b[i] = i;
        }

        new TaskSchedule("s0") //
                .task("t0", TestSPIRV::vectorSquare, a, b) //
                .streamOut(a) //
                .execute(); //

        System.out.println("a: " + Arrays.toString(a));

        boolean correct = true;
        for (int i = 0; i < a.length; i++) {
            if (a[i] != (b[i] * b[i])) {
                correct = false;
                break;
            }
        }

        if (correct) {
            System.out.println("Result is CORRECT");
        }
    }

    public static void saxpy() {

        final int numElements = 512;
        int[] a = new int[numElements];
        int[] b = new int[numElements];
        int[] c = new int[numElements];

        for (int i = 0; i < a.length; i++) {
            b[i] = i;
            c[i] = i;
        }

        new TaskSchedule("s0") //
                .task("t0", TestSPIRV::saxpy, c, a, b, 2) //
                .streamOut(c) //
                .execute(); //

        System.out.println("c: " + Arrays.toString(c));

        boolean correct = true;
        for (int i = 0; i < a.length; i++) {
            if (c[i] != ((2 * a[i]) + b[i])) {
                correct = false;
                break;
            }
        }

        if (correct) {
            System.out.println("Result is CORRECT");
        }
    }

    public static void multipleRuns() {

        final int numElements = 512;
        int[] a = new int[numElements];

        TaskSchedule ts = new TaskSchedule("s0") //
                .streamIn(a) //
                .task("t0", TestSPIRV::addValue, a) //
                .streamOut(a); //

        for (int i = 0; i < 10; i++) {
            ts.execute();
        }

        System.out.println("a: " + Arrays.toString(a));

        boolean correct = true;
        for (int i = 0; i < a.length; i++) {
            if (a[i] != (10 * 50)) {
                correct = false;
                break;
            }
        }

        if (correct) {
            System.out.println("Result is CORRECT");
        }
    }

    private static void testFloatCopy(float[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = 50.0f;
        }
    }

    private static void testFloatsCopy() {
        final int numElements = 256;
        float[] a = new float[numElements];

        new TaskSchedule("s0") //
                .task("t0", TestSPIRV::testFloatCopy, a) //
                .streamOut(a) //
                .execute(); //

        System.out.println("a: " + Arrays.toString(a));

        if (a[0] == 50.0f) {
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
            case 5:
                vectorMul();
                break;
            case 6:
                vectorSub();
                break;
            case 7:
                vectorDiv();
                break;
            case 8:
                square();
                break;
            case 9:
                saxpy();
                break;
            case 10:
                multipleRuns();
                break;
            case 11:
                testFloatsCopy();
                break;
            default:
                testSimple00();
                break;
        }
    }

}
