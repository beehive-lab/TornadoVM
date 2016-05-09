package tornado.collections.graphics;


import tornado.collections.math.TornadoMath;
import tornado.collections.types.Byte3;
import tornado.collections.types.FloatOps;
import tornado.collections.types.ImageByte3;
import tornado.collections.types.ImageFloat;

public class ImagingOps {

    public static final void resizeImage6( ImageFloat dest,
             ImageFloat src,  int scaleFactor,  float eDelta,
             int radius) {

        for (
        int y = 0; y < dest.Y(); y++)
            for (
            int x = 0; x < dest.X(); x++) {

                // co-ords of center pixel
                final int cx = TornadoMath.clamp(scaleFactor * x, 0,
                        src.X() - 1);
                final int cy = TornadoMath.clamp(scaleFactor * y, 0,
                        src.Y() - 1);

                float sum = 0f;
                float t = 0f;
                final float center = src.get(cx, cy);

                // calculate new pixel value from values of surrounding pixels
                for (int yy = -radius + 1; yy <= radius; yy++)
                    for (int xx = -radius + 1; xx <= radius; xx++) {

                        // co-ords of supporting pixel
                        // co-ords of supporting pixel
                        int px = TornadoMath.clamp(cx + xx, 0, src.X() - 1);
                        int py = TornadoMath.clamp(cy + yy, 0, src.Y() - 1);

                        float current = src.get(px, py);

                        if (Math.abs(current - center) < eDelta) {
                            sum += 1f;
                            t += current;
                        }
                    }

                dest.set(x, y, t / sum);
            }
    }

    public static final void mm2metersKernel( ImageFloat dest,
             ImageFloat src,  int scaleFactor) {
        for (
        int y = 0; y < dest.Y(); y++)
            for (
            int x = 0; x < dest.X(); x++) {

                // co-ords of center pixel
                final int sx = scaleFactor * x;
                final int sy = scaleFactor * y;

                final float value = src.get(sx, sy) * 1e-3f;

                dest.set(x, y, value);
            }
    }

    public static final void resizeImage( ImageFloat dest,
             ImageFloat src, int scaleFactor) {

        for (
        int y = 0; y < dest.Y(); y++)
            for (
            int x = 0; x < dest.X(); x++) {

                // co-ords of center pixel
                int cx = TornadoMath.clamp(scaleFactor * x, 0, src.X() - 1);
                int cy = TornadoMath.clamp(scaleFactor * y, 0, src.Y() - 1);

                float center = src.get(cx, cy);

                dest.set(x, y, center);
            }
    }

    public static final void resizeImage( ImageByte3 dest,
             ImageByte3 src, int scaleFactor) {

        for (
        int y = 0; y < dest.Y(); y++)
            for (
            int x = 0; x < dest.X(); x++) {

                // co-ords of center pixel
                int cx = TornadoMath.clamp(scaleFactor * x, 0, src.X() - 1);
                int cy = TornadoMath.clamp(scaleFactor * y, 0, src.Y() - 1);

                final Byte3 center = src.get(cx, cy);

                dest.set(x, y, center);
            }
    }

    public static final void bilateralFilter( ImageFloat dest,
             ImageFloat src,  float[] gaussian,  float eDelta,
             int radius) {

        final float e_d_squared_2 = eDelta * eDelta * 2f;

        // for every point
        for (
        int y = 0; y < src.Y(); y++)
            for (
            int x = 0; x < src.X(); x++) {

                final float center = src.get(x, y);

                if (center > 0f) {

                    float sum = 0f;
                    float t = 0f;

                    for (int yy = -radius; yy <= radius; yy++)
                        for (int xx = -radius; xx <= radius; xx++) {
                            final int px = TornadoMath.clamp(x + xx, 0,
                                    src.X() - 1);
                            final int py = TornadoMath.clamp(y + yy, 0,
                                    src.Y() - 1);
                            final float currentPixel = src.get(px, py);

                            if (currentPixel > 0f) {
                                final float mod = FloatOps.sq(currentPixel
                                        - center);

                                // TODO find out gaussian size
                                final float factor = (gaussian[xx + radius]
                                        * gaussian[yy + radius] * TornadoMath
                                        .exp(-mod / e_d_squared_2));

                                t += factor * currentPixel;

                                sum += factor;
                            }
                        }

                    dest.set(x, y, t / sum);
                } else {
                    dest.set(x, y, 0f);
                }
            }

    }

}
