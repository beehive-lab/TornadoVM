package tornado.collections.math;

import tornado.api.Parallel;
import tornado.api.Read;
import tornado.api.Write;


public class SimpleMath {

    public static void vectorAdd(@Read final float[] a, @Read final float[] b,
             @Write final float[] c) {
        for (@Parallel
        int i = 0; i < a.length; i++)
            c[i] = a[i] + b[i];
    }

    public static void vectorAdd(@Read final int[] a, @Read final int[] b,
            @Write final int[] c) {
        for (@Parallel
        int i = 0; i < a.length; i++)
            c[i] = a[i] + b[i];
    }

    public static void vectorMultiply( @Read final float[] a,
             @Read final float[] b, @Write final float[] c) {
        for (@Parallel
        int i = 0; i < a.length; i++)
            c[i] = a[i] * b[i];
    }
}
