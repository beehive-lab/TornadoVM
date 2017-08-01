/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package tornado.graal;

import org.graalvm.compiler.replacements.SnippetCounter;
import org.graalvm.compiler.replacements.SnippetCounter.Group;

/**
 *
 * @author James Clarkson
 */
public class DummySnippetFactory implements SnippetCounter.Group.Factory {

    @Override
    public Group createSnippetCounterGroup(String name) {
        return new SnippetCounter.Group(name);
    }

}
