/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018, APT Group, School of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.benchmarks.rodinia.kmean;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class DataLoader {
	
	public static class KmeansData {
		private final float[]			data;
		private final int					numPoints;
		private final int					numFeatures;
		
		public KmeansData(float[] data, int numPoints, int numFeatures){
			this.data = data;
			this.numPoints = numPoints;
			this.numFeatures = numFeatures;
		}

		public float[] getData() {
			return data;
		}

		public int getNumPoints() {
			return numPoints;
		}

		public int getNumFeatures() {
			return numFeatures;
		}
	}

	public static KmeansData loadData(String file) {
		KmeansData result = null;
		BufferedReader br = null;
		String line = "";
		final String splitBy = " ";

		try {
			int i = 0;
			br = new BufferedReader(new FileReader(file));

			br.mark(64 * 1024 * 1024);

			int numPoints = 0;
			while ((line = br.readLine()) != null) {
				numPoints++;
			}

			int numFeatures = -1;
			float[] data = null;
			br.reset();

			int index = 0;
			while ((line = br.readLine()) != null) {
				final String[] values = line.split(splitBy);
				if(numFeatures == -1){
					numFeatures = values.length -1;
					data = new float[numFeatures * numPoints];
				}

				for (int j = 1; j < values.length; j++,index++)
					data[index] = Float.valueOf(values[j]);
			}
			
			result = new KmeansData(data, numPoints, numFeatures);

		} catch (final IOException e) {
			e.printStackTrace();
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (final IOException e) {
					e.printStackTrace();
				}
			}
		}
		
		return result;
	}
	
	
}
