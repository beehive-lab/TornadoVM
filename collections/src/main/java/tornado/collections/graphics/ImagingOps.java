package tornado.collections.graphics;


import tornado.api.Parallel;
import tornado.api.Read;
import tornado.api.Write;
import tornado.collections.math.TornadoMath;
import tornado.collections.types.Byte3;
import tornado.collections.types.FloatOps;
import tornado.collections.types.ImageByte3;
import tornado.collections.types.ImageFloat;

public class ImagingOps {

    public static final void resizeImage6(@Write ImageFloat dest,
             @Read ImageFloat src, @Read  int scaleFactor,@Read  float eDelta,
            @Read  int radius) {

        for (@Parallel
        int y = 0; y < dest.Y(); y++)
            for (@Parallel
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
                        final int px = TornadoMath.clamp(cx + xx, 0, src.X() - 1);
                        final int py = TornadoMath.clamp(cy + yy, 0, src.Y() - 1);

                        final float current = src.get(px, py);

                        if (Math.abs(current - center) < eDelta) {
                            sum += 1f;
                            t += current;
                        }
                    }

                final float value = (sum > 0f) ? t / sum : 0f;
                dest.set(x, y, value);
            }
    }

    public static final void mm2metersKernel(@Write ImageFloat dest,
            @Read ImageFloat src, @Read int scaleFactor) {
        for (@Parallel
        int y = 0; y < dest.Y(); y++)
            for (@Parallel
            int x = 0; x < dest.X(); x++) {

                // co-ords of center pixel
                final int sx = scaleFactor * x;
                final int sy = scaleFactor * y;

                final float value = src.get(sx, sy) * 1e-3f;

                dest.set(x, y, value);
            }
    }

    public static final void resizeImage(@Write ImageFloat dest,
            @Read ImageFloat src, @Read int scaleFactor) {

        for (@Parallel
        int y = 0; y < dest.Y(); y++)
            for (@Parallel
            int x = 0; x < dest.X(); x++) {

                // co-ords of center pixel
                int cx = TornadoMath.clamp(scaleFactor * x, 0, src.X() - 1);
                int cy = TornadoMath.clamp(scaleFactor * y, 0, src.Y() - 1);

                float center = src.get(cx, cy);

                dest.set(x, y, center);
            }
    }

    public static final void resizeImage(@Write ImageByte3 dest,
             @Read ImageByte3 src, @Read int scaleFactor) {

        for (@Parallel
        int y = 0; y < dest.Y(); y++)
            for (@Parallel
            int x = 0; x < dest.X(); x++) {

                // co-ords of center pixel
                int cx = TornadoMath.clamp(scaleFactor * x, 0, src.X() - 1);
                int cy = TornadoMath.clamp(scaleFactor * y, 0, src.Y() - 1);

                final Byte3 center = src.get(cx, cy);

                dest.set(x, y, center);
            }
    }

    public static final void bilateralFilter( @Write ImageFloat dest,
             @Read ImageFloat src, @Read float[] gaussian, @Read float eDelta,
            @Read int radius) {

        final float e_d_squared_2 = eDelta * eDelta * 2f;

        // for every point
        for (@Parallel
        int y = 0; y < src.Y(); y++)
            for (@Parallel
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
