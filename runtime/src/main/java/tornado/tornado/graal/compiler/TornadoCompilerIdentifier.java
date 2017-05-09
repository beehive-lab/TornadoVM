/* 
 * Copyright 2012 James Clarkson.
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
 */
package tornado.graal.compiler;

import com.oracle.graal.compiler.common.CompilationIdentifier;

public class TornadoCompilerIdentifier implements CompilationIdentifier {

    private final int id;
    private final String name;

    public TornadoCompilerIdentifier(String name, int id) {
        this.name = name;
        this.id = id;
    }

    @Override
    public String toString(Verbosity verbosity) {
        return name + "-" + id;
    }

}
