package tornado.benchmarks;

import tornado.api.Parallel;
import tornado.collections.types.*;

import static tornado.collections.graphics.GraphicsMath.rotate;
import static tornado.collections.types.Float3.add;

public final class GraphicsKernels {

    public static void rotateVector(VectorFloat3 output,
            Matrix4x4Float m, VectorFloat3 input) {

        for (@Parallel int i = 0; i < output.getLength(); i++) {
            final Float3 x = input.get(i);
            final Float3 y = rotate(m, x);
            output.set(i, y);
        }
    }

    public static void dotVector(VectorFloat3 A, VectorFloat3 B,
            float[] c) {

        for (@Parallel int i = 0; i < c.length; i++) {
            final Float3 a = A.get(i);
            final Float3 b = B.get(i);

            c[i] = Float3.dot(a, b);
        }
    }

    public static void addVector(VectorFloat4 a, VectorFloat4 b,
            VectorFloat4 c) {

        for (@Parallel int i = 0; i < c.getLength(); i++) {
            c.set(i, Float4.add(a.get(i), b.get(i)));
        }
    }

    public static void rotateImage(ImageFloat3 output,
            Matrix4x4Float m, ImageFloat3 input) {

        for (@Parallel int i = 0; i < output.Y(); i++) {
            for (@Parallel int j = 0; j < output.X(); j++) {
                final Float3 x = input.get(j, i);

                final Float3 y = rotate(m, x);

                output.set(j, i, y);
            }
        }
    }

    public static void dotImage(ImageFloat3 A, ImageFloat3 B,
            ImageFloat C) {

        for (@Parallel int i = 0; i < C.Y(); i++) {
            for (@Parallel int j = 0; j < C.X(); j++) {
                final Float3 a = A.get(j, i);
                final Float3 b = B.get(j, i);

                C.set(j, i, Float3.dot(a, b));
            }
        }
    }

    public static void addImage(ImageFloat3 a, ImageFloat3 b,
            ImageFloat3 c) {

        for (@Parallel int i = 0; i < c.Y(); i++) {
            for (@Parallel int j = 0; j < c.X(); j++) {
                c.set(j, i, add(a.get(j, i), b.get(j, i)));
            }
        }
    }

    public static void convolveImageArray(final float[] input,
            final float[] filter, final float[] output,
            final int iW, final int iH, final int fW,
            final int fH) {
        int u, v;

        final int filterX2 = fW / 2;
        final int filterY2 = fH / 2;

        for (@Parallel int y = 0; y < iH; y++) {
            for (@Parallel int x = 0; x < iW; x++) {
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

    public static void convolveImage(final ImageFloat input,
            final ImageFloat filter, final ImageFloat output) {
        int u, v;

        final int filterX2 = filter.X() / 2;
        final int filterY2 = filter.Y() / 2;

        for (@Parallel int y = 0; y < output.Y(); y++) {
            for (@Parallel int x = 0; x < output.X(); x++) {
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
                            }
                        }
                    }
                }
                output.set(x, y, sum);
            }
        }
    }

}
