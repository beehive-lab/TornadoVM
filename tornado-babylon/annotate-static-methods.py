"""Annotates every static method declaration with a body in the given source tree with @Reflect.

Used by reflect-all-sdk.sh to give every TornadoVM unit-test kernel a code model. A line-based rewrite:
a declaration line starts with modifiers including 'static', names a method, and is not a field. Methods
whose body has a lambda or a method reference are left alone: in a @Reflect method those become
reflectable too, which changes the task lambdas TornadoVM inspects (and kernels have none).
"""
import pathlib
import re
import sys

DECL = re.compile(r'^(\s*)((?:@\w+(?:\([^)]*\))?\s+)*)((?:(?:public|private|protected|final|synchronized|strictfp)\s+)*static\s+(?:(?:final|synchronized|strictfp)\s+)*'
                  r'(?:<[^>]+>\s+)?[\w.<>\[\], ?]+\s+\w+\s*\()')
STRINGS = re.compile(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'')


def body(lines, start):
    """The lines of the method declared at lines[start], up to its closing brace."""
    depth, seen, out = 0, False, []
    for line in lines[start:]:
        code = STRINGS.sub('""', line.split('//')[0])
        out.append(code)
        depth += code.count('{') - code.count('}')
        seen = seen or '{' in code
        if seen and depth <= 0:
            break
    return '\n'.join(out)


annotated = skipped = 0
for path in pathlib.Path(sys.argv[1]).rglob('*.java'):
    lines = path.read_text().split('\n')
    for i, line in enumerate(lines):
        m = DECL.match(line)
        if not m or ' native ' in line or ' abstract ' in line or ' class ' in line or '=' in line.split('(')[0] or line.rstrip().endswith(';'):
            continue
        text = body(lines, i)
        if '->' in text or '::' in text:
            skipped += 1
            continue
        lines[i] = m.group(1) + m.group(2) + '@jdk.incubator.code.Reflect ' + line[len(m.group(1)) + len(m.group(2)):]
        annotated += 1
    path.write_text('\n'.join(lines))
print(f"annotated {annotated} static methods, left {skipped} with lambdas or method references", file=sys.stderr)
