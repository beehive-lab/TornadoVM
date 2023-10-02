/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * GNU Classpath is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 * 
 * GNU Classpath is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with GNU Classpath; see the file COPYING.  If not, write to the
 * Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 *
 * Linking this library statically or dynamically with other modules is
 * making a combined work based on this library.  Thus, the terms and
 * conditions of the GNU General Public License cover the whole
 * combination.
 * 
 * As a special exception, the copyright holders of this library give you
 * permission to link this library with independent modules to produce an
 * executable, regardless of the license terms of these independent
 * modules, and to copy and distribute the resulting executable under
 * terms of your choice, provided that you also meet, for each linked
 * independent module, the terms and conditions of the license of that
 * module.  An independent module is a module which is not derived from
 * or based on this library.  If you modify this library, you may extend
 * this exception to your version of the library, but you are not
 * obligated to do so.  If you do not wish to do so, delete this
 * exception statement from your version.
 *
 */
package uk.ac.manchester.tornado.api.collections.graphics;

import static java.lang.Math.abs;
import static uk.ac.manchester.tornado.api.collections.math.TornadoMath.clamp;
import static uk.ac.manchester.tornado.api.collections.math.TornadoMath.exp;
import static uk.ac.manchester.tornado.api.collections.types.FloatOps.sq;

import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.collections.types.Byte3;
import uk.ac.manchester.tornado.api.collections.types.ImageByte3;
import uk.ac.manchester.tornado.api.collections.types.ImageFloat;

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

    public static void bilateralFilter(ImageFloat dest, ImageFloat src, float[] gaussian, float eDelta, int radius) {
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
                                final float factor = (gaussian[xx + radius] * gaussian[yy + radius] * exp(-mod / eDSquared2));
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
