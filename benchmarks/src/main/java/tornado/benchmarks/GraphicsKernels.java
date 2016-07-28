package tornado.benchmarks;

import tornado.api.Parallel;
import tornado.api.Read;
import tornado.api.ReadWrite;
import tornado.api.Write;
import static tornado.collections.graphics.GraphicsMath.rotate;
import tornado.collections.types.Float3;
import static tornado.collections.types.Float3.add;
import tornado.collections.types.Float4;
import tornado.collections.types.ImageFloat;
import tornado.collections.types.ImageFloat3;
import tornado.collections.types.Matrix4x4Float;
import tornado.collections.types.VectorFloat3;
import tornado.collections.types.VectorFloat4;

public final class GraphicsKernels {

    public static void rotateVector(@ReadWrite VectorFloat3 output,
            @Read Matrix4x4Float m, @Read VectorFloat3 input) {

        for (@Parallel
        int i = 0; i < output.getLength(); i++) {
            final Float3 y = new Float3();
            final Float3 x = input.get(i);

            rotate(y, m, x);

            output.set(i, y);
        }
    }

    public static void dotVector(@Read VectorFloat3 A, @Read VectorFloat3 B,
            @ReadWrite float[] c) {

        for (@Parallel
        int i = 0; i < c.length; i++) {
            final Float3 a = A.get(i);
            final Float3 b = B.get(i);

            c[i] = Float3.dot(a, b);
        }
    }

    public static void addVector(@Read VectorFloat4 a, @Read VectorFloat4 b,
            @Write VectorFloat4 c) {

        for (@Parallel
        int i = 0; i < c.getLength(); i++) {
            c.set(i, Float4.add(a.get(i), b.get(i)));
        }
    }

    public static void rotateImage(@ReadWrite ImageFloat3 output,
            @Read Matrix4x4Float m, @Read ImageFloat3 input) {

        for (@Parallel
        int i = 0; i < output.Y(); i++) {
            for (@Parallel
            int j = 0; j < output.X(); j++) {
                final Float3 x = input.get(j, i);

                final Float3 y = rotate(m, x);

                output.set(j, i, y);
            }
        }
    }

    public static void dotImage(@Read ImageFloat3 A, @Read ImageFloat3 B,
            @ReadWrite ImageFloat C) {

        for (@Parallel
        int i = 0; i < C.Y(); i++) {
            for (@Parallel
            int j = 0; j < C.X(); j++) {
                final Float3 a = A.get(j, i);
                final Float3 b = B.get(j, i);

                C.set(j, i, Float3.dot(a, b));
            }
        }
    }

    public static void addImage(@Read ImageFloat3 a, @Read ImageFloat3 b,
            @ReadWrite ImageFloat3 c) {

        for (@Parallel
        int i = 0; i < c.Y(); i++) {
            for (@Parallel
            int j = 0; j < c.X(); j++) {
                c.set(j, i, add(a.get(j, i), b.get(j, i)));
            }
        }
    }

    public static void convolveImageArray(@Read final float[] input,
            @Read final float[] filter, @Write final float[] output,
            @Read final int iW, @Read final int iH, @Read final int fW,
            @Read final int fH) {
        int u, v;

        final int filterX2 = fW / 2;
        final int filterY2 = fH / 2;

        for (@Parallel
        int y = 0; y < iH; y++) {
            for (@Parallel
            int x = 0; x < iW; x++) {
                float sum = 0.0f;
                for (v = 0; v < fH; v++) {
                    for (u = 0; u < fW; u++) {

                        if ((((y - filterY2) + v) >= 0) && ((y + v) < iH)) {
                            if ((((x - filterX2) + u) >= 0) && ((x + u) < iW)) {
                                sum += filter[(v * fW) + u]
                                        * input[(((y - filterY2) + v) * iW)
                                                + ((x - filterX2) + u)];
                            }
                        }
                    }
                }

                // int outIndex = y * outputImageWidth + x;
                output[(y * iW) + x] = sum;
            }
        }
    }

    public static void convolveImage(@Read final ImageFloat input,
            @Read final ImageFloat filter, @Write final ImageFloat output) {
        int u, v;

        final int filterX2 = filter.X() / 2;
        final int filterY2 = filter.Y() / 2;

        for (@Parallel
        int y = 0; y < output.Y(); y++) {
            for (@Parallel
            int x = 0; x < output.X(); x++) {
                float sum = 0.0f;
                for (v = 0; v < filter.Y(); v++) {
                    for (u = 0; u < filter.X(); u++) {

                        if ((((y - filterY2) + v) >= 0)
                                && ((y + v) < output.Y())) {
                            if ((((x - filterX2) + u) >= 0)
                                    && ((x + u) < output.X())) {
                                sum += filter.get(u, v)
                                        * input.get(x - filterX2 + u, y
                                                - filterY2 + v);
                                // if(x == 140 && y == 98)
                                // System.out.printf("[%d, %d]: sum=%f, filter=%f, input=%f\n",x,y,sum,filter.get(u,v),input.get(
                                // x - filterX2 +u,y - filterY2 + v));
                            }
                        }
                    }
                }
                // if(x == 140 && y == 98)
                // System.out.printf("[%d, %d]: result=%f\n",x,y,sum);
                output.set(x, y, sum);
            }
        }
    }

}
