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


import uk.ac.manchester.tornado.benchmarks.rodinia.kmean.DataLoader.KmeansData;

public class Benchmark {


	public static void main(final String[] args) {

		

		System.out.printf("Data file         : %s\n", dataFile);
		KmeansData data = DataLoader.loadData(dataFile);
		
		System.out.printf("Number of objects : %d\n", data.getNumPoints());
		System.out.printf("Number of features: %d\n", data.getNumFeatures());
		
		final Kmean km = new Kmean(data, 5, 5);
		km.run();

		km.printCentres();
		km.printSizes();

	}

	private static final String				dataFile		= "/Users/jamesclarkson/opt/rodinia_3.0/data/kmeans/kdd_cup";
//	private final float[][]		clusters;
//	private final int[]			clusterSizes;
//
//	private final int[]			membership;


	float[][]					newClusters;

	int[]						newClustersSize;
	private final float			threshold		= 0.001f;
	private final int			clustersMax		= 5;
	private final int			clustersMin		= 5;

	private final int			numberOfLoops	= 1;
	private final boolean		useRMSE			= true;
	private float				rmse;

	private float				delta;

	private float				deltaAtomic;

//	private final Random		rand;

//	public Benchmark() {
//		importData();
//
//		clusters = new float[clustersMax][numFeatures];
//		clusterSizes = new int[clustersMax];
//		newClusters = new float[clustersMax][numFeatures];
//		newClustersSize = new int[clustersMax];
//		membership = new int[numPoints];
//
//		rand = new Random();
//		// rand.setSeed(7);
//
//		findNearestCluster = Task.create(Benchmark.class, "findNearestCluster",
//				new Dims(numPoints), new Dims(128));
//
//		taskGraph = new NewTaskGraph() {
//
//			@Override
//			public void create() {
//
//				executeTaskOn(findNearestCluster, deviceContext);
//			}
//
//		};
//
//		taskGraph.printCallChain();
//
//	}
//
//	private float calculateRMSE(final int numClusters) {
//		float sum = 0.0f;
//		final int[] membership = new int[numPoints];
//		findNearestCluster(numClusters, membership);
//
//		for (int i = 0; i < numPoints; i++) {
//			final int cluster = membership[i];
//			sum += euclidDist(data[i], clusters[cluster]);
//		}
//
//		return (float) Math.sqrt(sum / numPoints);
//	}
//
//	private final float euclidDist(final float[] a, final float[] b) {
//		float value = 0f;
//		for (int i = 0; i < a.length; i++) {
//			final float dist = a[i] - b[i];
//			value += dist * dist;
//		}
//		return value;
//	}
//
//	
//	private void findNearestCluster(final int numClusters,
//			final int[] membership) {
//		for (int i = 0; i < numPoints; i++) {
//			int index = -1;
//			float minDist = Float.MAX_VALUE;
//
//			for (int j = 0; j < numClusters; j++) {
//				float dist = 0f;
//
//				final float diff = euclidDist(data[i], clusters[j]);
//				dist += diff * diff;
//
//				if (dist < minDist) {
//					minDist = dist;
//					index = j;
//				}
//			}
//
//			if (membership[i] != index) {
//				deltaAtomic = 1.0f;
//			}
//
//			membership[i] = index;
//		}
//	}
//
//	public String getDataFile() {
//		return dataFile;
//	}
//
//	public int getNumFeatures() {
//		return numFeatures;
//	}
//
//	public int getNumPoints() {
//		return numPoints;
//	}
//
//	
//
//	private void kmeans(int numClusters) {
//		if (numClusters > numPoints) numClusters = numPoints;
//
//		for (int i = 0; i < numClusters; i++) {
//			final int n = rand.nextInt(numPoints);
//			for (int j = 0; j < numFeatures; j++) {
//				clusters[i][j] = data[n][j];
//				newClusters[i][j] = 0f;
//			}
//			clusterSizes[i] = 0;
//			newClustersSize[i] = 0;
//		}
//
//		for (int i = 0; i < numPoints; i++)
//			membership[i] = -1;
//
//		int loop = 0;
//		do {
//			delta = 0f;
//			deltaAtomic = 0f;
//			findNearestCluster.setParameters(this, numClusters, membership);
//
//			try {
//				taskGraph.execute();
//			} catch (final JaccRuntimeException e) {
//				e.printStackTrace();
//				System.out.println("Unable to execute: " + e.getMessage());
//				break;
//			}
//
//			// findNearestCluster(numClusters,membership);
//			delta = deltaAtomic;
//
//			updateClusters(numClusters);
//
//			for (int i = 0; i < numClusters; i++) {
//				for (int j = 0; j < numFeatures; j++) {
//					if (newClustersSize[i] > 0)
//						clusters[i][j] = newClusters[i][j] / newClustersSize[i];
//					newClusters[i][j] = 0f;
//				}
//				clusterSizes[i] = newClustersSize[i];
//				newClustersSize[i] = 0;
//
//			}
//
//			// assume delta should be percentage of points
//			// changing membership this iteration...?
//			delta /= numPoints;
//			// System.out.printf("loop: id=%d, delta=%f\n",loop,delta);
//			loop++;
//		} while ((delta > threshold) && (loop < 500));
//
//		System.out.printf("Iterated %d times\n", loop);
//	}
//
//	public void printCentres() {
//		final StringBuilder sb = new StringBuilder();
//		for (int i = 0; i < clusters.length; i++) {
//			sb.append(String.format("cluster: id=%d, ", i));
//			for (int j = 0; j < numFeatures; j++) {
//				sb.append(String.format("%6.2f ", clusters[i][j]));
//			}
//			sb.append(String.format("\n"));
//		}
//
//		System.out.println(sb.toString());
//	}
//
//	public void printMembership() {
//
//	}
//
//	public void printSizes() {
//		final StringBuilder sb = new StringBuilder();
//
//		for (int i = 0; i < clusters.length; i++) {
//			sb.append(String.format("cluster: id=%d, points=%d\n", i,
//					clusterSizes[i]));
//		}
//
//		System.out.println(sb.toString());
//	}
//
//	public void run() {
//		float minRMSERef = Float.MAX_VALUE;
//
//		if (numPoints < clustersMin) {
//			System.out
//					.printf("Error: min_nclusters(%d) > npoints (%s) -- cannot proceed\n",
//							clustersMin, numPoints);
//			return;
//		}
//
//		for (int nclusters = clustersMin; nclusters <= clustersMax; nclusters++) {
//			if (nclusters > numPoints) break;
//
//			final long start = System.nanoTime();
//			for (int i = 0; i < numberOfLoops; i++) {
//				kmeans(nclusters);
//
//				if (useRMSE) {
//					rmse = calculateRMSE(nclusters);
//					if (rmse < minRMSERef) {
//						minRMSERef = rmse;
//					}
//				}
//			}
//			final long stop = System.nanoTime();
//			final double elapsed = (stop - start) * 1e-9;
//			System.out
//					.printf("Kmeans: iterations=%d, clusters=%d, time=%.4f, rsme=%.4f\n",
//							numberOfLoops, nclusters, elapsed, rmse);
//
//		}
//	}
//
//	public void setDataFile(final String dataFile) {
//		this.dataFile = dataFile;
//	}
//
//	private void updateClusters(final int numClusters) {
//		for (int i = 0; i < numPoints; i++) {
//
//			final int clusterId = membership[i];
//
//			newClustersSize[clusterId]++;
//
//			for (int j = 0; j < numFeatures; j++) {
//				newClusters[clusterId][j] += data[i][j];
//			}
//
//		}
//	}
}
