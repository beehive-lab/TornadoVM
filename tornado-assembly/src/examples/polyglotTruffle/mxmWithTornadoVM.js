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

console.log("Hello TornadoVM from JavaScript!")

for (var i = 0; i < 5; i++) {
    var myclass = Java.type('uk.ac.manchester.tornado.examples.polyglot.MyCompute')
    var start = new Date().getTime() / 1000;
    myclass.compute()
    var end = new Date().getTime() / 1000;
    console.log("Total Time (s): " + (end - start))
}