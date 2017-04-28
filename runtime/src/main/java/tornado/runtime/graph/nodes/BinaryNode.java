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
package tornado.runtime.graph.nodes;

import static tornado.common.exceptions.TornadoInternalError.shouldNotReachHere;

public abstract class BinaryNode extends AbstractNode {

    protected AbstractNode x;
    protected AbstractNode y;

    public AbstractNode getX() {
        return x;
    }

    public AbstractNode getY() {
        return y;
    }

    public void replaceAtInputs(AbstractNode toReplace, AbstractNode replacement) {
        if (x == toReplace) {
            x = replacement;
        } else if (y == toReplace) {
            y = replacement;
        } else {
            shouldNotReachHere();
        }
    }

}
