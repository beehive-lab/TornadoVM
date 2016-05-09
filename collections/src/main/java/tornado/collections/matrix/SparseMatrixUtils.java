package tornado.collections.matrix;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;


public class SparseMatrixUtils {

	private static final boolean VERBOSE = false;
	
	public static class CSRMatrix<T> {
		public int		n;
		public int		size;
		public T		vals;
		public int[]	rows;
		public int[]	cols;
	}

	public static CSRMatrix<double[]> loadMatrixD(final String path) {
		boolean pattern = false;
		boolean symmetric = false;

		final CSRMatrix<double[]> mat = new CSRMatrix<double[]>();

		Random rand = null;
		try {
			final BufferedReader br = new BufferedReader(new FileReader(path));

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
				if (!line.startsWith("%")) break;
			}

			opts = line.split(" ");

			final int nRows = Integer.parseInt(opts[0]);
			final int nCols = Integer.parseInt(opts[1]);
			int nElements = Integer.parseInt(opts[2]);

			class Coordinate implements Comparable<Coordinate> {
				int		x;
				int		y;
				float	val;

				public Coordinate(final int a, final int b, final float c) {
					x = a;
					y = b;
					val = c;
				}

				public Coordinate(final String[] opts) {
					x = Integer.parseInt(opts[0]) - 1;
					y = Integer.parseInt(opts[1]) - 1;
					val = Float.parseFloat(opts[2]);
				}

				@Override
				public int compareTo(final Coordinate c) {
					int val = 0;
					if (x != c.x)
						val = x - c.x;
					else
						val = y - c.y;

					return val;
				}

				@Override
				public String toString() {
					return String.format("[ %d, %d, %10.2f ]", x, y, val);
				}
			}

			if (VERBOSE)
				System.out.printf("Matrix: rows=%d, cols=%d, elements=%d\n",
						nRows, nCols, nElements);

			List<Coordinate> coords;
			if (symmetric)
				coords = new ArrayList<Coordinate>(2 * nElements);
			else
				coords = new ArrayList<Coordinate>(nElements);

			int index = 0;
			while ((line = br.readLine()) != null) {
				opts = line.split(" ");
				if (pattern) {
					final Coordinate c = new Coordinate(
							Integer.parseInt(opts[0]) - 1,
							Integer.parseInt(opts[1]) - 1,
							rand.nextFloat() * 256.0f);
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

			Collections.sort(coords);

			nElements = index;

			mat.n = nElements;
			mat.size = nRows;
			mat.vals = new double[nElements];
			mat.cols = new int[nElements];
			mat.rows = new int[nRows + 1];

			mat.rows[0] = 0;
			mat.rows[nRows] = nElements;

			int r = 0;

			for (int i = 0; i < nElements; i++) {

				final Coordinate c = coords.get(i);

				while (c.x != r) {
					mat.rows[++r] = i;
				}
				mat.vals[i] = c.val;
				mat.cols[i] = c.y;
			}

			coords.clear();
		} catch (final FileNotFoundException e) {
			System.out.printf("Unable to open matrix %s\n", path);
		} catch (final IOException e) {
			e.printStackTrace();
		}

		return mat;

	}
	
	public static CSRMatrix<float[]> loadMatrixF(final String path) {
		boolean pattern = false;
		boolean symmetric = false;

		final CSRMatrix<float[]> mat = new CSRMatrix<float[]>();

		Random rand = null;
		try {
			final BufferedReader br = new BufferedReader(new FileReader(path));

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
				rand.setSeed(7);
				// System.out.printf("Matrix pattern unimplemented\n");
				// return;
			}

			if (opts[4].equalsIgnoreCase("symmetric")) {
				symmetric = true;
			}

			while ((line = br.readLine()) != null) {
				if (!line.startsWith("%")) break;
			}

			opts = line.split(" ");

			final int nRows = Integer.parseInt(opts[0]);
			final int nCols = Integer.parseInt(opts[1]);
			int nElements = Integer.parseInt(opts[2]);

			class Coordinate implements Comparable<Coordinate> {
				int		x;
				int		y;
				float	val;

				public Coordinate(final int a, final int b, final float c) {
					x = a;
					y = b;
					val = c;
				}

				public Coordinate(final String[] opts) {
					x = Integer.parseInt(opts[0]) - 1;
					y = Integer.parseInt(opts[1]) - 1;
					val = Float.parseFloat(opts[2]);
				}

				@Override
				public int compareTo(final Coordinate c) {
					int val = 0;
					if (x != c.x)
						val = x - c.x;
					else
						val = y - c.y;

					return val;
				}

				@Override
				public String toString() {
					return String.format("[ %d, %d, %10.2f ]", x, y, val);
				}
			}

			if (VERBOSE)
				System.out.printf("Matrix: rows=%d, cols=%d, elements=%d\n",
						nRows, nCols, nElements);

			List<Coordinate> coords;
			if (symmetric)
				coords = new ArrayList<Coordinate>(2 * nElements);
			else
				coords = new ArrayList<Coordinate>(nElements);

			int index = 0;
			while ((line = br.readLine()) != null) {
				opts = line.split(" ");
				if (pattern) {
					final Coordinate c = new Coordinate(
							Integer.parseInt(opts[0]) - 1,
							Integer.parseInt(opts[1]) - 1,
							rand.nextFloat() * 256.0f);
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

			Collections.sort(coords);

			nElements = index;

			mat.n = nElements;
			mat.size = nRows;
			mat.vals = new float[nElements];
			mat.cols = new int[nElements];
			mat.rows = new int[nRows + 1];

			mat.rows[0] = 0;
			mat.rows[nRows] = nElements;

			int r = 0;

			for (int i = 0; i < nElements; i++) {

				final Coordinate c = coords.get(i);

				while (c.x != r) {
					mat.rows[++r] = i;
				}
				mat.vals[i] = c.val;
				mat.cols[i] = c.y;
			}

			coords.clear();
		} catch (final FileNotFoundException e) {
			System.out.printf("Unable to open matrix %s\n", path);
		} catch (final IOException e) {
			e.printStackTrace();
		}

		return mat;

	}

}
