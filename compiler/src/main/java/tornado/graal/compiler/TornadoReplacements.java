package tornado.graal.compiler;

import com.oracle.graal.api.replacements.SnippetReflectionProvider;
import com.oracle.graal.bytecode.BytecodeProvider;
import com.oracle.graal.phases.util.Providers;
import com.oracle.graal.replacements.ReplacementsImpl;
import jdk.vm.ci.code.TargetDescription;

public class TornadoReplacements extends ReplacementsImpl {

    public TornadoReplacements(Providers providers, SnippetReflectionProvider snippetReflection, BytecodeProvider bytecodeProvider, TargetDescription target) {
        super(providers, snippetReflection, bytecodeProvider, target);
    }

}
