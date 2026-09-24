/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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
/**
 * TornadoVM unit tests written in Kotlin. Open so that JUnit can instantiate the test classes.
 */
open module tornado.kotlin.unittests {
    requires junit;
    requires tornado.kotlin.api;
    requires tornado.unittests;

    exports uk.ac.manchester.tornado.kotlin.unittests.api;
    exports uk.ac.manchester.tornado.kotlin.unittests.compiler;
}
