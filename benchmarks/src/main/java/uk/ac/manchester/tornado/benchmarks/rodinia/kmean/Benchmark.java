/*
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
 * The University of Manchester.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *    http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

    private static final String dataFile = "/Users/jamesclarkson/opt/rodinia_3.0/data/kmeans/kdd_cup";

    float[][] newClusters;

    int[] newClustersSize;

}
