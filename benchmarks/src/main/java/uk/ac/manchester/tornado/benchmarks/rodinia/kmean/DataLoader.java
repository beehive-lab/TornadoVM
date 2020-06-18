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

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class DataLoader {

    public static class KmeansData {
        private final float[] data;
        private final int numPoints;
        private final int numFeatures;

        public KmeansData(float[] data, int numPoints, int numFeatures) {
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
                if (numFeatures == -1) {
                    numFeatures = values.length - 1;
                    data = new float[numFeatures * numPoints];
                }

                for (int j = 1; j < values.length; j++, index++) {
                    data[index] = Float.valueOf(values[j]);
                }
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
