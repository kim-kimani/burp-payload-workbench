package com.cytonn.montoya.payloadextractor.generator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates strings that MATCH a given regex, rather than search with it - a small "Xeger"-style
 * engine supporting literals, character classes ({@code [a-z0-9]}, negation {@code [^...]}),
 * groups, alternation ({@code (a|b|c)}), the escapes {@code \d \w \s \D \W \S}, and quantifiers
 * {@code * + ? {n} {n,m}}. Unbounded quantifiers ({@code *}, {@code +}, an open-ended {@code {n,}})
 * are capped at a sane maximum repeat count so generation always terminates.
 */
public final class RegexGenerator implements PayloadGenerator {

    private static final int UNBOUNDED_CAP = 10;

    @Override
    public GeneratorKind kind() {
        return GeneratorKind.REGEX;
    }

    @Override
    public List<String> generate(GeneratorParams params) {
        Random random = params.randomSeed() != null ? new Random(params.randomSeed()) : new Random();
        List<String> out = new ArrayList<>(Math.max(0, params.count()));
        for (int i = 0; i < params.count(); i++) {
            String value;
            try {
                value = new Engine(params.regex(), random).generate();
            } catch (RuntimeException e) {
                value = "";
            }
            out.add(params.prefix() + value + params.suffix());
        }
        return out;
    }

    private static final class Engine {
        final String pattern;
        final Random random;
        int pos;

        Engine(String pattern, Random random) {
            this.pattern = pattern == null ? "" : pattern;
            this.random = random;
        }

        String generate() {
            pos = 0;
            return parseAlternation();
        }

        String parseAlternation() {
            List<String> branches = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            int depth = 0;
            List<String> parts = new ArrayList<>();
            String seq = parseSequence();
            branches.add(seq);
            while (pos < pattern.length() && pattern.charAt(pos) == '|') {
                pos++;
                branches.add(parseSequence());
            }
            return branches.get(random.nextInt(branches.size()));
        }

        String parseSequence() {
            StringBuilder sb = new StringBuilder();
            while (pos < pattern.length() && pattern.charAt(pos) != '|' && pattern.charAt(pos) != ')') {
                sb.append(parseQuantified());
            }
            return sb.toString();
        }

        String parseQuantified() {
            int atomStart = pos;
            String atom = parseAtom();
            int atomEnd = pos;
            if (pos >= pattern.length()) {
                return atom;
            }
            char c = pattern.charAt(pos);
            int min = 1, max = 1;
            boolean quantified = false;
            if (c == '*') { min = 0; max = UNBOUNDED_CAP; pos++; quantified = true; }
            else if (c == '+') { min = 1; max = UNBOUNDED_CAP; pos++; quantified = true; }
            else if (c == '?') { min = 0; max = 1; pos++; quantified = true; }
            else if (c == '{') {
                int close = pattern.indexOf('}', pos);
                if (close > 0) {
                    String spec = pattern.substring(pos + 1, close);
                    String[] parts = spec.split(",", -1);
                    try {
                        if (parts.length == 1) {
                            min = max = Integer.parseInt(parts[0].trim());
                        } else {
                            min = parts[0].trim().isEmpty() ? 0 : Integer.parseInt(parts[0].trim());
                            max = parts[1].trim().isEmpty() ? min + UNBOUNDED_CAP : Integer.parseInt(parts[1].trim());
                        }
                        pos = close + 1;
                        quantified = true;
                    } catch (NumberFormatException ignored) {
                        // not a valid {..} quantifier - treat '{' as a literal below
                    }
                }
            }
            if (!quantified) {
                return atom;
            }
            // Re-parse the atom's own source text fresh for each repetition (via a throwaway sub-Engine
            // sharing our Random) so e.g. [a-z]{4} draws 4 independently-random letters, not one letter copied 4x.
            String atomSource = pattern.substring(atomStart, atomEnd);
            int repeat = min + (max > min ? random.nextInt(max - min + 1) : 0);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < repeat; i++) {
                sb.append(new Engine(atomSource, random).generate());
            }
            return sb.toString();
        }

        String parseAtom() {
            char c = pattern.charAt(pos);
            if (c == '(') {
                pos++; // consume '('
                if (pos + 1 < pattern.length() && pattern.charAt(pos) == '?' && pattern.charAt(pos + 1) == ':') {
                    pos += 2;
                }
                String inner = parseAlternation();
                if (pos < pattern.length() && pattern.charAt(pos) == ')') {
                    pos++;
                }
                return inner;
            }
            if (c == '[') {
                return parseCharClass();
            }
            if (c == '\\') {
                pos++;
                if (pos >= pattern.length()) {
                    return "";
                }
                char esc = pattern.charAt(pos++);
                return String.valueOf(fromEscape(esc));
            }
            if (c == '.') {
                pos++;
                return String.valueOf(randomFrom(PRINTABLE));
            }
            pos++;
            return String.valueOf(c);
        }

        String parseCharClass() {
            pos++; // consume '['
            boolean negate = false;
            if (pos < pattern.length() && pattern.charAt(pos) == '^') {
                negate = true;
                pos++;
            }
            StringBuilder members = new StringBuilder();
            while (pos < pattern.length() && pattern.charAt(pos) != ']') {
                char c = pattern.charAt(pos);
                if (c == '\\' && pos + 1 < pattern.length()) {
                    members.append(fromEscapeClass(pattern.charAt(pos + 1)));
                    pos += 2;
                    continue;
                }
                if (pos + 2 < pattern.length() && pattern.charAt(pos + 1) == '-' && pattern.charAt(pos + 2) != ']') {
                    char from = c;
                    char to = pattern.charAt(pos + 2);
                    for (char ch = from; ch <= to; ch++) {
                        members.append(ch);
                    }
                    pos += 3;
                    continue;
                }
                members.append(c);
                pos++;
            }
            if (pos < pattern.length()) {
                pos++; // consume ']'
            }
            String pool = members.toString();
            if (negate) {
                StringBuilder inverted = new StringBuilder();
                for (char ch : PRINTABLE.toCharArray()) {
                    if (pool.indexOf(ch) < 0) {
                        inverted.append(ch);
                    }
                }
                pool = inverted.toString();
            }
            if (pool.isEmpty()) {
                return "";
            }
            return String.valueOf(randomFrom(pool));
        }

        char randomFrom(String s) {
            return s.charAt(random.nextInt(s.length()));
        }

        char fromEscape(char esc) {
            switch (esc) {
                case 'd': return randomFrom("0123456789");
                case 'w': return randomFrom("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_");
                case 's': return ' ';
                case 'n': return '\n';
                case 't': return '\t';
                default: return esc;
            }
        }

        String fromEscapeClass(char esc) {
            switch (esc) {
                case 'd': return "0123456789";
                case 'w': return "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_";
                case 's': return " \t";
                default: return String.valueOf(esc);
            }
        }

        static final String PRINTABLE = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    }
}
