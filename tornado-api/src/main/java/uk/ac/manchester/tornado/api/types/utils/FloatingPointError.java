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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with GNU Classpath; see the file COPYING. If not, write to the
 * Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 *
 * Linking this library statically or dynamically with other modules is
 * making a combined work based on this library. Thus, the terms and
 * conditions of the GNU General Public License cover the whole
 * combination.
 *
 * As a special exception, the copyright holders of this library give you
 * permission to link this library with independent modules to produce an
 * executable, regardless of the license terms of these independent
 * modules, and to copy and distribute the resulting executable under
 * terms of your choice, provided that you also meet, for each linked
 * independent module, the terms and conditions of the license of that
 * module. An independent module is a module which is not derived from
 * or based on this library. If you modify this library, you may extend
 * this exception to your version of the library, but you are not
 * obligated to do so. If you do not wish to do so, delete this
 * exception statement from your version.
 *
 */
package uk.ac.manchester.tornado.api.types.utils;

import static java.lang.String.format;

public class FloatingPointError {
    private final float averageUlp;
    private final float minUlp;
    private final float maxUlp;
    private final float stdDevUlp;
    private final int errors;

    public FloatingPointError(float average, float min, float max, float stdDev, int errors) {
        this.averageUlp = average;
        this.minUlp = min;
        this.maxUlp = max;
        this.stdDevUlp = stdDev;
        this.errors = errors;
    }

    public FloatingPointError(float average, float min, float max, float stdDev) {
        this(average, min, max, stdDev, -1);
    }

    public String toString() {
        return format("errors=%d, mean ulp=%f, std. dev =%f, min ulp=%f, max ulp=%f", errors, averageUlp, stdDevUlp, minUlp, maxUlp);
    }

    public float getErrors() {
        return errors;
    }

    public float getAverageUlp() {
        return averageUlp;
    }

    public float getMinUlp() {
        return minUlp;
    }

    public float getMaxUlp() {
        return maxUlp;
    }

    public float getStdDevUlp() {
        return stdDevUlp;
    }
}
