/*
 * Copyright (c) 2013-2020, 2023, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package uk.ac.manchester.tornado.unittests.slam.utils;

import static java.lang.Math.abs;
import static uk.ac.manchester.tornado.api.math.TornadoMath.clamp;
import static uk.ac.manchester.tornado.api.math.TornadoMath.exp;
import static uk.ac.manchester.tornado.api.types.utils.FloatOps.sq;

import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.images.ImageByte3;
import uk.ac.manchester.tornado.api.types.images.ImageFloat;
import uk.ac.manchester.tornado.api.types.vectors.Byte3;

public class ImagingOps {

    public static void resizeImage6(ImageFloat dest, ImageFloat src, int scaleFactor, float eDelta, int radius) {

        for (@Parallel int y = 0; y < dest.Y(); y++) {
            for (@Parallel int x = 0; x < dest.X(); x++) {
                // co-ords of center pixel
                final int cx = clamp(scaleFactor * x, 0, src.X() - 1);
                final int cy = clamp(scaleFactor * y, 0, src.Y() - 1);

                float sum = 0f;
                float t = 0f;
                final float center = src.get(cx, cy);

                // calculate new pixel value from values of surrounding pixels
                for (int yy = -radius + 1; yy <= radius; yy++) {
                    for (int xx = -radius + 1; xx <= radius; xx++) {

                        // co-ords of supporting pixel
                        // co-ords of supporting pixel
                        final int px = clamp(cx + xx, 0, src.X() - 1);
                        final int py = clamp(cy + yy, 0, src.Y() - 1);

                        final float current = src.get(px, py);

                        if (abs(current - center) < eDelta) {
                            sum += 1f;
                            t += current;
                        }
                    }
                }
                final float value = (sum > 0f) ? t / sum : 0f;
                dest.set(x, y, value);
            }
        }
    }

    public static void mm2metersKernel(ImageFloat dest, ImageFloat src, int scaleFactor) {
        for (@Parallel int y = 0; y < dest.Y(); y++) {
            for (@Parallel int x = 0; x < dest.X(); x++) {
                // co-ords of center pixel
                final int sx = scaleFactor * x;
                final int sy = scaleFactor * y;
                final float value = src.get(sx, sy) * 1e-3f;
                dest.set(x, y, value);
            }
        }
    }

    public static void resizeImage(ImageFloat dest, ImageFloat src, int scaleFactor) {
        for (@Parallel int y = 0; y < dest.Y(); y++) {
            for (@Parallel int x = 0; x < dest.X(); x++) {
                // co-ords of center pixel
                int cx = clamp(scaleFactor * x, 0, src.X() - 1);
                int cy = clamp(scaleFactor * y, 0, src.Y() - 1);
                float center = src.get(cx, cy);
                dest.set(x, y, center);
            }
        }
    }

    public static void resizeImage(ImageByte3 dest, ImageByte3 src, int scaleFactor) {
        for (@Parallel int y = 0; y < dest.Y(); y++) {
            for (@Parallel int x = 0; x < dest.X(); x++) {
                // co-ords of center pixel
                int cx = clamp(scaleFactor * x, 0, src.X() - 1);
                int cy = clamp(scaleFactor * y, 0, src.Y() - 1);
                final Byte3 center = src.get(cx, cy);
                dest.set(x, y, center);
            }
        }
    }

    public static void bilateralFilter(ImageFloat dest, ImageFloat src, FloatArray gaussian, float eDelta, int radius) {
        final float eDSquared2 = eDelta * eDelta * 2f;
        // for every point
        for (@Parallel int y = 0; y < src.Y(); y++) {
            for (@Parallel int x = 0; x < src.X(); x++) {
                final float center = src.get(x, y);
                if (center > 0f) {
                    float sum = 0f;
                    float t = 0f;
                    for (int yy = -radius; yy <= radius; yy++) {
                        for (int xx = -radius; xx <= radius; xx++) {
                            final int px = clamp(x + xx, 0, src.X() - 1);
                            final int py = clamp(y + yy, 0, src.Y() - 1);
                            final float currentPixel = src.get(px, py);
                            if (currentPixel > 0f) {
                                final float mod = sq(currentPixel - center);
                                // TODO find out gaussian size
                                final float factor = (gaussian.get(xx + radius) * gaussian.get(yy + radius) * exp(-mod / eDSquared2));
                                t += factor * currentPixel;
                                sum += factor;
                            }
                        }
                    }
                    dest.set(x, y, t / sum);
                } else {
                    dest.set(x, y, 0f);
                }
            }
        }
    }
}
