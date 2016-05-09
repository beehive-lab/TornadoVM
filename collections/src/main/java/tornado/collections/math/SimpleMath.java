package tornado.collections.math;


public class SimpleMath {

    public static void vectorAdd( final float[] a,  final float[] b,
             final float[] c) {
        for (
        int i = 0; i < a.length; i++)
            c[i] = a[i] + b[i];
    }

    public static void vectorAdd( final int[] a,  final int[] b,
             final int[] c) {
        for (
        int i = 0; i < a.length; i++)
            c[i] = a[i] + b[i];
    }

    public static void vectorMultiply( final float[] a,
             final float[] b,  final float[] c) {
        for (
        int i = 0; i < a.length; i++)
            c[i] = a[i] * b[i];
    }
}
