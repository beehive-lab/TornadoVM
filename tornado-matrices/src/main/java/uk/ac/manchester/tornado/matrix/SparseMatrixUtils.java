/*
 * Copyright (c) 2013-2023, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.matrix;

import static java.lang.Float.parseFloat;
import static java.lang.Integer.parseInt;
import static java.lang.String.format;
import static java.lang.System.err;
import static java.lang.System.out;
import static java.util.Collections.sort;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;

public class SparseMatrixUtils {

    private static final boolean VERBOSE = false;

    public static CSRMatrix<DoubleArray> loadMatrixD(final String path) {
        boolean pattern = false;
        boolean symmetric = false;

        final CSRMatrix<DoubleArray> mat = new CSRMatrix<>();

        Random rand = null;
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {

            String line = br.readLine();

            String[] opts = line.split(" ");
            // id object format field symmetry

            if (!opts[1].equalsIgnoreCase("matrix")) {
                System.out.printf("Matrix file doesnot contain matrix\n");
                br.close();
                return null;
            }

            if (!opts[2].equalsIgnoreCase("coordinate")) {
                System.out.printf("Matrix representation is dense\n");
                br.close();
                return null;
            }

            if (opts[3].equalsIgnoreCase("pattern")) {
                pattern = true;
                rand = new Random();
                // System.out.printf("Matrix pattern unimplemented\n");
                // return;
            }

            if (opts[4].equalsIgnoreCase("symmetric")) {
                symmetric = true;
            }

            while ((line = br.readLine()) != null) {
                if (!line.startsWith("%")) {
                    break;
                }
            }

            opts = line.split(" ");

            final int nRows = Integer.parseInt(opts[0]);
            final int nCols = Integer.parseInt(opts[1]);
            int nElements = Integer.parseInt(opts[2]);

            class Coordinate implements Comparable<Coordinate> {

                int x;
                int y;
                float val;

                Coordinate(final int a, final int b, final float c) {
                    x = a;
                    y = b;
                    val = c;
                }

                Coordinate(final String[] opts) {
                    x = parseInt(opts[0]) - 1;
                    y = parseInt(opts[1]) - 1;
                    val = parseFloat(opts[2]);
                }

                @Override
                public int compareTo(final Coordinate c) {
                    int val = 0;
                    if (x != c.x) {
                        val = x - c.x;
                    } else {
                        val = y - c.y;
                    }

                    return val;
                }

                @Override
                public String toString() {
                    return String.format("[ %d, %d, %10.2f ]", x, y, val);
                }
            }

            if (VERBOSE) {
                System.out.printf("Matrix: rows=%d, cols=%d, elements=%d\n", nRows, nCols, nElements);
            }

            List<Coordinate> coords;
            if (symmetric) {
                coords = new ArrayList<>(2 * nElements);
            } else {
                coords = new ArrayList<>(nElements);
            }

            int index = 0;
            while ((line = br.readLine()) != null) {
                opts = line.split(" ");
                if (pattern) {
                    final Coordinate c = new Coordinate(parseInt(opts[0]) - 1, parseInt(opts[1]) - 1, rand.nextFloat() * 256.0f);
                    coords.add(c);
                } else {
                    coords.add(new Coordinate(opts));
                }

                index++;

                if (symmetric) {
                    final Coordinate last = coords.get(index - 1);
                    if (last.x != last.y) {
                        coords.add(new Coordinate(last.y, last.x, last.val));
                        index++;
                    }
                }
            }

            sort(coords);
            nElements = index;
            mat.n = nElements;
            mat.size = nRows;
            mat.vals = new DoubleArray(nElements);
            mat.cols = new IntArray(nElements);
            mat.rows = new IntArray(nRows + 1);
            mat.rows.set(0, 0);
            mat.rows.set(nRows, nElements);
            int r = 0;
            for (int i = 0; i < nElements; i++) {

                final Coordinate c = coords.get(i);

                while (c.x != r) {
                    mat.rows.set(++r, i);
                }
                mat.vals.set(i, c.val);
                mat.cols.set(i, c.y);
            }
            coords.clear();
        } catch (final FileNotFoundException e) {
            System.out.printf("Unable to open matrix %s\n", path);
        } catch (final IOException e) {
            e.printStackTrace();
        }

        return mat;

    }

    public static CSRMatrix<FloatArray> loadMatrixF(InputStream inStream) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(inStream))) {
            return loadMatrixF(br);
        } catch (IOException e) {
            err.printf("unable to read matrix from input steam: %s\n", e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    public static CSRMatrix<FloatArray> loadMatrixF(final String path) {
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            return loadMatrixF(br);
        } catch (IOException e) {
            err.printf("unable to read matrix from file: %s (%s)\n", path, e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    private static CSRMatrix<FloatArray> loadMatrixF(BufferedReader br) throws IOException {
        boolean pattern = false;
        boolean symmetric = false;

        final CSRMatrix<FloatArray> mat = new CSRMatrix<>();

        Random rand = null;

        String line = br.readLine();

        String[] opts = line.split(" ");
        // id object format field symmetry

        if (!opts[1].equalsIgnoreCase("matrix")) {
            out.printf("Matrix file doesnot contain matrix\n");
            br.close();
            return null;
        }

        if (!opts[2].equalsIgnoreCase("coordinate")) {
            out.printf("Matrix representation is dense\n");
            br.close();
            return null;
        }

        if (opts[3].equalsIgnoreCase("pattern")) {
            pattern = true;
            rand = new Random();
            rand.setSeed(7);
        }

        if (opts[4].equalsIgnoreCase("symmetric")) {
            symmetric = true;
        }

        while ((line = br.readLine()) != null) {
            if (!line.startsWith("%")) {
                break;
            }
        }

        opts = line.split(" ");

        final int nRows = parseInt(opts[0]);
        final int nCols = parseInt(opts[1]);
        int nElements = parseInt(opts[2]);

        class Coordinate implements Comparable<Coordinate> {

            int x;
            int y;
            float val;

            Coordinate(final int a, final int b, final float c) {
                x = a;
                y = b;
                val = c;
            }

            Coordinate(final String[] opts) {
                x = parseInt(opts[0]) - 1;
                y = parseInt(opts[1]) - 1;
                val = parseFloat(opts[2]);
            }

            @Override
            public int compareTo(final Coordinate c) {
                int val;
                if (x != c.x) {
                    val = x - c.x;
                } else {
                    val = y - c.y;
                }

                return val;
            }

            @Override
            public String toString() {
                return format("[ %d, %d, %10.2f ]", x, y, val);
            }
        }

        if (VERBOSE) {
            out.printf("Matrix: rows=%d, cols=%d, elements=%d\n", nRows, nCols, nElements);
        }

        List<Coordinate> coords;
        if (symmetric) {
            coords = new ArrayList<>(2 * nElements);
        } else {
            coords = new ArrayList<>(nElements);
        }

        int index = 0;
        while ((line = br.readLine()) != null) {
            opts = line.split(" ");
            if (pattern) {
                final Coordinate c = new Coordinate(parseInt(opts[0]) - 1, parseInt(opts[1]) - 1, rand.nextFloat() * 256.0f);
                coords.add(c);
            } else {
                coords.add(new Coordinate(opts));
            }

            index++;

            if (symmetric) {
                final Coordinate last = coords.get(index - 1);
                if (last.x != last.y) {
                    coords.add(new Coordinate(last.y, last.x, last.val));
                    index++;
                }
            }
        }

        br.close();

        sort(coords);

        nElements = index;

        mat.n = nElements;
        mat.size = nRows;
        mat.vals = new FloatArray(nElements);
        mat.cols = new IntArray(nElements);
        mat.rows = new IntArray(nRows + 1);

        mat.rows.set(0, 0);
        mat.rows.set(nRows, nElements);

        int r = 0;

        for (int i = 0; i < nElements; i++) {

            final Coordinate c = coords.get(i);

            while (c.x != r) {
                mat.rows.set(++r, i);
            }
            mat.vals.set(i, c.val);
            mat.cols.set(i, c.y);
        }

        coords.clear();
        return mat;
    }

    public static class CSRMatrix<T> {

        public int n;
        public int size;
        public T vals;
        public IntArray rows;
        public IntArray cols;
    }
}
