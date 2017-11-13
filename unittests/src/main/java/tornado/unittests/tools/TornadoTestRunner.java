/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2017 APT Group, School of Computer Science, 
 * The University of Manchester
 *
 * This work is partially supported by EPSRC grants:
 * Anyscale EP/L000725/1 and PAMELA EP/K008730/1.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Authors: Juan Fumero
 *
 */
package tornado.unittests.tools;

public class TornadoTestRunner {

    public static void main(String[] args) throws ClassNotFoundException {

        boolean verbose = TornadoHelper.getProperty("tornado.unittests.verbose");

        String[] classAndMethod = args[0].split("#");
        if (!verbose) {
            if (classAndMethod.length > 1) {
                TornadoHelper.runTestClassAndMethod(classAndMethod[0], classAndMethod[1]);
            } else {
                TornadoHelper.runTestClass(classAndMethod[0]);
            }
        } else {
            if (classAndMethod.length > 1) {
                TornadoHelper.runTestVerbose(classAndMethod[0], classAndMethod[1]);
            } else {
                TornadoHelper.runTestVerbose(classAndMethod[0], null);
            }
        }
    }
}
