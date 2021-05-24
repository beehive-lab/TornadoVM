package uk.ac.manchester.tornado.examples.spirv;

import java.util.Arrays;

import uk.ac.manchester.tornado.api.TaskSchedule;

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

    public static void testSimple00() {
        final int numElements = 256;
        int[] a = new int[numElements];

        new TaskSchedule("s0") //
                .task("t0", TestKernels::copyTestZero, a) //
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
                .task("t0", TestKernels::copyTest, a) //
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
                .task("t0", TestKernels::copyTest2, a, b) //
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
                .task("t0", TestKernels::compute, a, b) //
                .streamOut(a) //
                .execute(); //

        System.out.println("a: " + Arrays.toString(a));

        boolean correct = true;
        for (int i = 0; i < a.length; i++) {
            if (a[i] != (b[i] + 50)) {
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
                .task("t0", TestKernels::vectorAddCompute, a, b, c) //
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
                .task("t0", TestKernels::vectorMul, a, b, c) //
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
                .task("t0", TestKernels::vectorSub, a, b, c) //
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
                .task("t0", TestKernels::vectorDiv, a, b, c) //
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
                .task("t0", TestKernels::vectorSquare, a, b) //
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
                .task("t0", TestKernels::saxpy, c, a, b, 2) //
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
                .task("t0", TestKernels::addValue, a) //
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

    private static void testFloatsCopy() {
        final int numElements = 256;
        float[] a = new float[numElements];

        new TaskSchedule("s0") //
                .task("t0", TestKernels::testFloatCopy, a) //
                .streamOut(a) //
                .execute(); //

        System.out.println("a: " + Arrays.toString(a));

        if (a[0] == 50.0f) {
            System.out.println("Result is CORRECT");
        }
    }

    public static void testVectorFloatAdd() {

        final int numElements = 256;
        float[] a = new float[numElements];
        float[] b = new float[numElements];
        float[] c = new float[numElements];

        Arrays.fill(b, 100);
        Arrays.fill(c, 200);

        new TaskSchedule("s0") //
                .task("t0", TestKernels::vectorAddFloatCompute, a, b, c) //
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

    public static void testDoublesAdd() {

        final int numElements = 256;
        double[] a = new double[numElements];
        double[] b = new double[numElements];
        double[] c = new double[numElements];

        Arrays.fill(b, 100);
        Arrays.fill(c, 200);

        new TaskSchedule("s0") //
                .task("t0", TestKernels::vectorAddDoubleCompute, a, b, c) //
                .streamOut(a) //
                .execute(); //

        System.out.println("a: " + Arrays.toString(a));

        boolean correct = true;
        for (int i = 0; i < a.length; i++) {
            if ((Math.abs(a[i] - (b[i] + c[i])) > 0.1)) {
                correct = false;
                break;
            }
        }

        if (correct) {
            System.out.println("Result is CORRECT");
        }
    }

    public static void testVectorFloatSub() {

        final int numElements = 256;
        float[] a = new float[numElements];
        float[] b = new float[numElements];
        float[] c = new float[numElements];

        Arrays.fill(b, 200);
        Arrays.fill(c, 100);

        new TaskSchedule("s0") //
                .task("t0", TestKernels::vectorSubFloatCompute, a, b, c) //
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

    public static void testVectorFloatMul() {

        final int numElements = 256;
        float[] a = new float[numElements];
        float[] b = new float[numElements];
        float[] c = new float[numElements];

        Arrays.fill(b, 100.0f);
        Arrays.fill(c, 5.0f);

        new TaskSchedule("s0") //
                .task("t0", TestKernels::vectorMulFloatCompute, a, b, c) //
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

    public static void testVectorFloatDiv() {

        final int numElements = 256;
        float[] a = new float[numElements];
        float[] b = new float[numElements];
        float[] c = new float[numElements];

        Arrays.fill(b, 100.0f);
        Arrays.fill(c, 5.0f);

        new TaskSchedule("s0") //
                .task("t0", TestKernels::vectorDivFloatCompute, a, b, c) //
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

    private static void testDoublesCopy() {
        final int numElements = 256;
        double[] a = new double[numElements];

        new TaskSchedule("s0") //
                .task("t0", TestKernels::testDoublesCopy, a) //
                .streamOut(a) //
                .execute(); //

        System.out.println("a: " + Arrays.toString(a));

        if (a[0] == 50.0f) {
            System.out.println("Result is CORRECT");
        }
    }

    public static void testDoublesSub() {

        final int numElements = 256;
        double[] a = new double[numElements];
        double[] b = new double[numElements];
        double[] c = new double[numElements];

        Arrays.fill(b, 2.2);
        Arrays.fill(c, 3.5);

        new TaskSchedule("s0") //
                .task("t0", TestKernels::vectorSubDoubleCompute, a, b, c) //
                .streamOut(a) //
                .execute(); //

        System.out.println("a: " + Arrays.toString(a));

        boolean correct = true;
        for (int i = 0; i < a.length; i++) {
            if ((Math.abs(a[i] - (b[i] - c[i])) > 0.1)) {
                correct = false;
                break;
            }
        }

        if (correct) {
            System.out.println("Result is CORRECT");
        }
    }

    public static void testDoublesMul() {

        final int numElements = 256;
        double[] a = new double[numElements];
        double[] b = new double[numElements];
        double[] c = new double[numElements];

        Arrays.fill(b, 2.2);
        Arrays.fill(c, 3.5);

        new TaskSchedule("s0") //
                .task("t0", TestKernels::vectorMulDoubleCompute, a, b, c) //
                .streamOut(a) //
                .execute(); //

        System.out.println("a: " + Arrays.toString(a));

        boolean correct = true;
        for (int i = 0; i < a.length; i++) {
            if ((Math.abs(a[i] - (b[i] * c[i])) > 0.1)) {
                correct = false;
                break;
            }
        }

        if (correct) {
            System.out.println("Result is CORRECT");
        }
    }

    public static void testDoublesDiv() {

        final int numElements = 256;
        double[] a = new double[numElements];
        double[] b = new double[numElements];
        double[] c = new double[numElements];

        Arrays.fill(b, 10.2);
        Arrays.fill(c, 2.0);

        new TaskSchedule("s0") //
                .task("t0", TestKernels::vectorDivDoubleCompute, a, b, c) //
                .streamOut(a) //
                .execute(); //

        System.out.println("a: " + Arrays.toString(a));

        boolean correct = true;
        for (int i = 0; i < a.length; i++) {
            if ((Math.abs(a[i] - (b[i] / c[i])) > 0.1)) {
                correct = false;
                break;
            }
        }

        if (correct) {
            System.out.println("Result is CORRECT");
        }
    }

    private static void testLongsCopy() {
        final int numElements = 256;
        long[] a = new long[numElements];

        new TaskSchedule("s0") //
                .task("t0", TestKernels::testLongsCopy, a) //
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

    private static void testLongsAdd() {
        final int numElements = 256;
        long[] a = new long[numElements];
        long[] b = new long[numElements];
        long[] c = new long[numElements];

        Arrays.fill(b, Integer.MAX_VALUE);
        Arrays.fill(c, 1);
        new TaskSchedule("s0") //
                .task("t0", TestKernels::vectorSumLongCompute, a, b, c) //
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

    private static void testShortAdd() {
        final int numElements = 256;
        short[] a = new short[numElements];
        short[] b = new short[numElements];
        short[] c = new short[numElements];

        Arrays.fill(b, (short) 1);
        Arrays.fill(c, (short) 3);
        new TaskSchedule("s0") //
                .task("t0", TestKernels::vectorSumShortCompute, a, b, c) //
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

        System.out.println("Test short add");
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
            case 12:
                testVectorFloatAdd();
                break;
            case 13:
                testVectorFloatSub();
                break;
            case 14:
                testVectorFloatMul();
                break;
            case 15:
                testVectorFloatDiv();
                break;
            case 17:
                testDoublesCopy();
                break;
            case 18:
                testDoublesAdd();
                break;
            case 19:
                testDoublesSub();
                break;
            case 20:
                testDoublesMul();
                break;
            case 21:
                testDoublesDiv();
                break;
            case 22:
                testLongsCopy();
                break;
            case 23:
                testLongsAdd();
                break;
            case 24:
                testShortAdd();
                break;
            default:
                testSimple00();
                break;
        }
    }

}
