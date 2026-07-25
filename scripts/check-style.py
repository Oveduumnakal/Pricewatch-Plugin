#!/usr/bin/env python3
"""Style checker for the plugin's coding standards.

Enforces the mechanically checkable subset of the project's style rules over
every Java source file:

  1. No line wider than 120 columns (tabs count as 4).
  2. No inlined control statements (`if (x) foo();` on one line).
  3. No blank line directly after `{` or directly before `}`.
  4. No one-line method/lambda bodies containing a statement (`{ return x; }`).
  5. Brace uniformity across if/else chains (all branches braced or none).
  6. Brace uniformity across nesting (a braceless outer control statement may
     not wrap a braced inner one).
  7. Allman lambdas: a multi-statement lambda body's `{` goes on the next line.
  8. No wildcard imports.
  9. Statement-level method chains of three or more links must be wrapped.
 10. No inline `//` comments (Javadoc instead), except a note that is the sole
     content of an intentionally-empty `catch` block.
 11. Every class/interface/enum declaration (including nested) carries Javadoc.
 12. No plain `/* ... */` block comments — the only permitted block comments
     are `/**` Javadoc and the license header opening on line 1.
 13. No access-bypass reflection (RuneLite plugin guideline): `setAccessible`,
     `sun.misc.Unsafe`/`jdk.internal.*`, reflective `getDeclaredMethod`/
     `getDeclaredConstructor` lookups, or `Class.forName`. Gson `TypeToken`
     generics (`java.lang.reflect.Type`/`ParameterizedType`) and the schema
     snapshot test's structural `getDeclaredFields()` inspection are deliberately
     exempt — they read type/field shape, they don't defeat access modifiers.

Exits non-zero listing every violation, or prints a summary and exits zero.
"""

import re
import sys
import glob

TAB = 4
MAX_WIDTH = 120
CONTROL = r'(?:if|for|while|switch|synchronized)'

# Reflection primitives that bypass access control or the constructor contract.
# Structural inspection (getDeclaredFields/Modifier) and TypeToken generics are
# intentionally absent — see rule 13 in the module docstring.
REFLECTION_BANS = [
    (r'\bimport\s+sun\.misc\.', 'sun.misc'),
    (r'\bsun\.misc\.Unsafe\b', 'sun.misc.Unsafe'),
    (r'\bimport\s+jdk\.internal\.', 'jdk.internal'),
    (r'\.setAccessible\s*\(', 'setAccessible'),
    (r'\bClass\.forName\s*\(', 'Class.forName'),
    (r'\.getDeclaredMethod\s*\(', 'getDeclaredMethod'),
    (r'\.getDeclaredConstructor\s*\(', 'getDeclaredConstructor'),
]

violations = []


def report(path, lineno, rule, text):
    violations.append(f"{path}:{lineno}: [{rule}] {text.strip()[:100]}")


def strip_strings(line):
    """Blank out string/char literal contents so their symbols don't confuse checks."""
    out = []
    quote = None
    i = 0
    while i < len(line):
        c = line[i]
        if quote:
            if c == '\\':
                out.append('..')
                i += 2
                continue
            if c == quote:
                quote = None
                out.append(c)
            else:
                out.append('.')
        else:
            if c in '"\'':
                quote = c
            out.append(c)
        i += 1
    return ''.join(out)


def indent_of(line):
    return len(line) - len(line.lstrip('\t'))


def width_of(line):
    tabs = indent_of(line)
    return tabs * TAB + (len(line) - tabs)


def in_block_comment_mask(lines):
    """Per-line flag for lines lying inside /* ... */ block comments."""
    mask = []
    inside = False
    for line in lines:
        started_inside = inside
        stripped = strip_strings(line)
        if inside and '*/' in stripped:
            inside = False
        elif not inside and '/*' in stripped and '*/' not in stripped.split('/*', 1)[1]:
            inside = True
        mask.append(started_inside or line.lstrip('\t ').startswith('*'))
    return mask


def header_end(lines, i):
    """Index of the line where the control header starting at i balances its parens."""
    bal = 0
    j = i
    while j < len(lines):
        s = strip_strings(lines[j])
        bal += s.count('(') - s.count(')')
        if bal <= 0:
            return j
        j += 1
    return j


def next_code_line(lines, j):
    k = j + 1
    while k < len(lines) and lines[k].strip() == '':
        k += 1
    return k


def allowed_empty_catch_note(lines, i):
    """Whether the // comment on 0-based line i is the sole content of an empty catch block."""
    bal = 0
    j = i - 1
    while j >= 0:
        s = strip_strings(lines[j]).split('//')[0]
        bal += s.count('}') - s.count('{')
        if bal < 0:
            break

        j -= 1

    if j < 0:
        return False

    header = strip_strings(lines[j]).split('//')[0].strip()
    if header == '{' and j > 0:
        header = strip_strings(lines[j - 1]).split('//')[0].strip()

    if not header.startswith('catch'):
        return False

    k = j + 1
    while k < len(lines):
        s = strip_strings(lines[k]).split('//')[0].strip()
        if s.startswith('}'):
            return True

        content = lines[k].strip()
        if content and not content.startswith('//'):
            return False

        k += 1

    return False


def javadoc_precedes(lines, i):
    """Whether a /** ... */ block sits directly above 0-based declaration line i, skipping annotations."""
    rev_bal = 0
    j = i - 1
    while j >= 0:
        s = strip_strings(lines[j]).strip()
        if s.endswith('*/'):
            return True

        rev_bal += s.count(')') - s.count('(')
        if rev_bal > 0 or (s.startswith('@') and rev_bal == 0):
            if s.startswith('@'):
                rev_bal = 0

            j -= 1
            continue

        return False

    return False


def check_file(path):
    lines = open(path).read().split('\n')
    comment = in_block_comment_mask(lines)

    for i, raw in enumerate(lines, 1):
        if width_of(raw) > MAX_WIDTH:
            report(path, i, 'width>120', raw)

    prev = ''
    for i, raw in enumerate(lines, 1):
        if raw.strip() == '' and prev.strip().endswith('{') and prev.strip() != '':
            report(path, i, 'blank-after-brace', prev)

        if re.match(r'^[\t ]*\}[,;)]*$', raw) and prev.strip() == '' and i > 1:
            report(path, i, 'blank-before-brace', raw)

        prev = raw

    for i, raw in enumerate(lines, 1):
        if comment[i - 1]:
            continue

        line = strip_strings(raw)
        code = line.split('//')[0]

        if re.match(r'^import .*\*;$', code.strip()):
            report(path, i, 'wildcard-import', raw)

        for pattern, name in REFLECTION_BANS:
            if re.search(pattern, code):
                report(path, i, 'reflection', f"{name}: {raw}")
                break

        if '//' in line and not allowed_empty_catch_note(lines, i - 1):
            report(path, i, 'inline-comment', raw)

        if re.search(r'/\*(?!\*)', line.split('//')[0]) and i > 1:
            report(path, i, 'block-comment', raw)

        stripped = code.strip()

        if (re.match(r'^(?:(?:public|protected|private|static|final|abstract)\s+)*(?:class|interface|enum)\s+[A-Z]', stripped)
                and not javadoc_precedes(lines, i - 1)):
            report(path, i, 'missing-class-javadoc', raw)

        if re.match(r'^' + CONTROL + r'\s*\(', stripped):
            bal = stripped.count('(') - stripped.count(')')
            if bal == 0:
                after = re.sub(r'^' + CONTROL + r'\s*', '', stripped)
                depth = 0
                rest = ''
                for idx, c in enumerate(after):
                    if c == '(':
                        depth += 1
                    elif c == ')':
                        depth -= 1
                        if depth == 0:
                            rest = after[idx + 1:].strip()
                            break
                if rest and rest not in ('{',) and not rest.startswith('//'):
                    report(path, i, 'inline-control', raw)

        if re.search(r'\)\s*\{[^{}]*;[^{}]*\}', code) or re.search(r'->\s*\{[^{}]*;[^{}]*\}', code):
            report(path, i, 'one-line-body', raw)

        if re.search(r'->\s*\{\s*$', code):
            report(path, i, 'inline-lambda-brace', raw)

        if (stripped.endswith(';') and not re.match(r'^(if|for|while|else|case|default|do|switch|import|package)\b', stripped)
                and '->' not in code and '||' not in code and '&&' not in code and '+' not in code):
            hops = len(re.findall(r'\)\.[A-Za-z_]', code))
            first_hop = code.find(').')
            base_is_call = first_hop >= 0 and re.search(r'\bnew\s', code[:first_hop])
            links = hops if base_is_call else hops + 1
            if hops >= 2 and links >= 3:
                report(path, i, 'unwrapped-chain', raw)

    # if/else chain and nesting uniformity
    chains = []
    for i in range(len(lines)):
        if comment[i]:
            continue

        s = lines[i].strip()
        kind = None
        if re.match(r'^if\s*\(', s):
            kind = 'if'
        elif s == 'else' or re.match(r'^else if\s*\(', s):
            kind = 'else'

        if not kind:
            continue

        ind = indent_of(lines[i])
        j = header_end(lines, i) if '(' in s else i
        k = next_code_line(lines, j)
        braced = k < len(lines) and lines[k].strip().startswith('{')

        if kind == 'if':
            chains.append((i + 1, ind, [braced]))
        elif chains and indent_of(lines[chains[-1][0] - 1]) == ind:
            chains[-1][2].append(braced)

        if not braced and k < len(lines):
            body = lines[k].strip()
            if re.match(r'^(?:if|for|while|switch|try|synchronized)\b', body) and indent_of(lines[k]) == ind + 1:
                j2 = header_end(lines, k) if '(' in body else k
                k2 = next_code_line(lines, j2)
                if k2 < len(lines) and lines[k2].strip().startswith('{'):
                    report(path, i + 1, 'braceless-outer-braced-inner', lines[i])

    for start, _, flags in chains:
        if len(flags) > 1 and len(set(flags)) > 1:
            report(path, start, 'mixed-chain-braces', lines[start - 1])


def main():
    files = sorted(glob.glob('src/main/java/**/*.java', recursive=True)
            + glob.glob('src/test/java/**/*.java', recursive=True))
    for path in files:
        check_file(path)

    if violations:
        print(f"Style check FAILED: {len(violations)} violation(s)")
        for v in violations:
            print("  " + v)

        sys.exit(1)

    print(f"Style check passed ({len(files)} files).")


if __name__ == '__main__':
    main()
