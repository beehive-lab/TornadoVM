/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package tornado.benchmarks.stencil;

import tornado.api.Parallel;

/**
 *
 * @author James Clarkson
 */
public class Stencil {

    public static final void stencil3d(int n, int sz, float[] a0, float[] a1, float fac) {
        for (@Parallel int i = 1; i < n + 1; i++) {
            for (@Parallel int j = 1; j < n + 1; j++) {
                for (int k = 1; k < n + 1; k++) {
                    a1[i * sz * sz + j * sz + k] = (a0[i * sz * sz + (j - 1) * sz + k] + a0[i * sz * sz + (j + 1) * sz + k]
                            + a0[(i - 1) * sz * sz + j * sz + k] + a0[(i + 1) * sz * sz + j * sz + k]
                            + a0[(i - 1) * sz * sz + (j - 1) * sz + k] + a0[(i - 1) * sz * sz + (j + 1) * sz + k]
                            + a0[(i + 1) * sz * sz + (j - 1) * sz + k] + a0[(i + 1) * sz * sz + (j + 1) * sz + k]
                            + a0[i * sz * sz + (j - 1) * sz + (k - 1)] + a0[i * sz * sz + (j + 1) * sz + (k - 1)]
                            + a0[(i - 1) * sz * sz + j * sz + (k - 1)] + a0[(i + 1) * sz * sz + j * sz + (k - 1)]
                            + a0[(i - 1) * sz * sz + (j - 1) * sz + (k - 1)] + a0[(i - 1) * sz * sz + (j + 1) * sz + (k - 1)]
                            + a0[(i + 1) * sz * sz + (j - 1) * sz + (k - 1)] + a0[(i + 1) * sz * sz + (j + 1) * sz + (k - 1)]
                            + a0[i * sz * sz + (j - 1) * sz + (k + 1)] + a0[i * sz * sz + (j + 1) * sz + (k + 1)]
                            + a0[(i - 1) * sz * sz + j * sz + (k + 1)] + a0[(i + 1) * sz * sz + j * sz + (k + 1)]
                            + a0[(i - 1) * sz * sz + (j - 1) * sz + (k + 1)] + a0[(i - 1) * sz * sz + (j + 1) * sz + (k + 1)]
                            + a0[(i + 1) * sz * sz + (j - 1) * sz + (k + 1)] + a0[(i + 1) * sz * sz + (j + 1) * sz + (k + 1)]
                            + a0[i * sz * sz + j * sz + (k - 1)] + a0[i * sz * sz + j * sz + (k + 1)]) * fac;
                }
            }
        }
    }

    public static final void copy(int sz, float[] a0, float[] a1) {
        for (@Parallel int i = 0; i < a0.length; i++) {
            a1[i] = a0[i];
        }
    }
}
