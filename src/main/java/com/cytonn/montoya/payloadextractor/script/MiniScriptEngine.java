package com.cytonn.montoya.payloadextractor.script;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A tiny, dependency-free scripting language for the Custom Script payload generator, used as a
 * guaranteed-to-work fallback when no JSR-223 JavaScript engine is present on the JVM (the JDK has
 * shipped none since Nashorn's removal, and Burp's own bundled JRE is no exception) - see
 * {@link ScriptEngineManager}.
 *
 * <p>A script is a semicolon/newline-separated sequence of {@code name = expression} assignments
 * and bare expressions; the last statement's value is the generated result. Built-in variables
 * {@code index}, {@code count}, {@code collectionName} are pre-bound each run. Supported
 * operators: {@code + - * / % == != < > <= >=} (numeric {@code +} auto-promotes to string
 * concatenation if either side is a string). Built-in functions: {@code pad(value,length)},
 * {@code upper(s)}, {@code lower(s)}, {@code len(s)}, {@code substr(s,start,end)},
 * {@code randInt(min,max)}, {@code randStr(length)}, {@code concat(a,b,...)},
 * {@code replace(s,from,to)}, {@code now()}.
 */
public final class MiniScriptEngine {

    private MiniScriptEngine() {
    }

    public static Program compile(String source) {
        return new Program(new Parser(new Lexer(source == null ? "" : source).tokenize()).parseProgram());
    }

    public static final class Program {
        private final List<Node> statements;

        private Program(List<Node> statements) {
            this.statements = statements;
        }

        public String run(Map<String, Object> bindings, Random random) {
            Env env = new Env(bindings, random);
            Object last = "";
            for (Node n : statements) {
                last = n.eval(env);
            }
            return stringify(last);
        }
    }

    // ---------------------------------------------------------------- lexer

    private enum TokType { NUMBER, STRING, IDENT, OP, LPAREN, RPAREN, COMMA, ASSIGN, SEMI, EOF }

    private static final class Tok {
        final TokType type;
        final String text;

        Tok(TokType type, String text) {
            this.type = type;
            this.text = text;
        }
    }

    private static final class Lexer {
        final String s;
        int pos = 0;

        Lexer(String s) {
            this.s = s;
        }

        List<Tok> tokenize() {
            List<Tok> out = new ArrayList<>();
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (Character.isWhitespace(c)) {
                    pos++;
                } else if (c == ';' || c == '\n') {
                    out.add(new Tok(TokType.SEMI, ";"));
                    pos++;
                } else if (c == '(') {
                    out.add(new Tok(TokType.LPAREN, "("));
                    pos++;
                } else if (c == ')') {
                    out.add(new Tok(TokType.RPAREN, ")"));
                    pos++;
                } else if (c == ',') {
                    out.add(new Tok(TokType.COMMA, ","));
                    pos++;
                } else if (c == '"' || c == '\'') {
                    out.add(readString(c));
                } else if (Character.isDigit(c)) {
                    out.add(readNumber());
                } else if (Character.isLetter(c) || c == '_') {
                    out.add(readIdent());
                } else if ("=!<>".indexOf(c) >= 0) {
                    if (pos + 1 < s.length() && s.charAt(pos + 1) == '=') {
                        out.add(new Tok(TokType.OP, "" + c + '='));
                        pos += 2;
                    } else if (c == '=') {
                        out.add(new Tok(TokType.ASSIGN, "="));
                        pos++;
                    } else {
                        out.add(new Tok(TokType.OP, String.valueOf(c)));
                        pos++;
                    }
                } else if ("+-*/%".indexOf(c) >= 0) {
                    out.add(new Tok(TokType.OP, String.valueOf(c)));
                    pos++;
                } else {
                    pos++; // skip unrecognized character
                }
            }
            out.add(new Tok(TokType.EOF, ""));
            return out;
        }

        Tok readString(char quote) {
            pos++;
            StringBuilder sb = new StringBuilder();
            while (pos < s.length() && s.charAt(pos) != quote) {
                char c = s.charAt(pos);
                if (c == '\\' && pos + 1 < s.length()) {
                    char esc = s.charAt(pos + 1);
                    sb.append(esc == 'n' ? '\n' : esc == 't' ? '\t' : esc);
                    pos += 2;
                } else {
                    sb.append(c);
                    pos++;
                }
            }
            if (pos < s.length()) {
                pos++; // closing quote
            }
            return new Tok(TokType.STRING, sb.toString());
        }

        Tok readNumber() {
            int start = pos;
            while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) {
                pos++;
            }
            return new Tok(TokType.NUMBER, s.substring(start, pos));
        }

        Tok readIdent() {
            int start = pos;
            while (pos < s.length() && (Character.isLetterOrDigit(s.charAt(pos)) || s.charAt(pos) == '_')) {
                pos++;
            }
            return new Tok(TokType.IDENT, s.substring(start, pos));
        }
    }

    // ---------------------------------------------------------------- parser (recursive descent)

    private static final class Parser {
        final List<Tok> toks;
        int i = 0;

        Parser(List<Tok> toks) {
            this.toks = toks;
        }

        Tok peek() { return toks.get(i); }
        Tok advance() { return toks.get(i++); }
        boolean check(TokType t) { return peek().type == t; }

        List<Node> parseProgram() {
            List<Node> statements = new ArrayList<>();
            skipSemis();
            while (!check(TokType.EOF)) {
                statements.add(parseStatement());
                skipSemis();
            }
            if (statements.isEmpty()) {
                statements.add(new Literal(""));
            }
            return statements;
        }

        void skipSemis() {
            while (check(TokType.SEMI)) advance();
        }

        Node parseStatement() {
            if (check(TokType.IDENT) && i + 1 < toks.size() && toks.get(i + 1).type == TokType.ASSIGN) {
                String name = advance().text;
                advance(); // '='
                Node value = parseExpression();
                return new Assign(name, value);
            }
            return parseExpression();
        }

        Node parseExpression() { return parseComparison(); }

        Node parseComparison() {
            Node left = parseAdditive();
            while (check(TokType.OP) && isComparisonOp(peek().text)) {
                String op = advance().text;
                Node right = parseAdditive();
                left = new BinOp(op, left, right);
            }
            return left;
        }

        boolean isComparisonOp(String op) {
            return op.equals("==") || op.equals("!=") || op.equals("<") || op.equals(">") || op.equals("<=") || op.equals(">=");
        }

        Node parseAdditive() {
            Node left = parseTerm();
            while (check(TokType.OP) && (peek().text.equals("+") || peek().text.equals("-"))) {
                String op = advance().text;
                Node right = parseTerm();
                left = new BinOp(op, left, right);
            }
            return left;
        }

        Node parseTerm() {
            Node left = parseUnary();
            while (check(TokType.OP) && (peek().text.equals("*") || peek().text.equals("/") || peek().text.equals("%"))) {
                String op = advance().text;
                Node right = parseUnary();
                left = new BinOp(op, left, right);
            }
            return left;
        }

        Node parseUnary() {
            if (check(TokType.OP) && peek().text.equals("-")) {
                advance();
                return new Negate(parseUnary());
            }
            return parsePrimary();
        }

        Node parsePrimary() {
            Tok t = peek();
            if (t.type == TokType.NUMBER) {
                advance();
                return new Literal(t.text.contains(".") ? Double.parseDouble(t.text) : (double) Long.parseLong(t.text));
            }
            if (t.type == TokType.STRING) {
                advance();
                return new Literal(t.text);
            }
            if (t.type == TokType.LPAREN) {
                advance();
                Node inner = parseExpression();
                if (check(TokType.RPAREN)) advance();
                return inner;
            }
            if (t.type == TokType.IDENT) {
                advance();
                if (check(TokType.LPAREN)) {
                    advance();
                    List<Node> args = new ArrayList<>();
                    if (!check(TokType.RPAREN)) {
                        args.add(parseExpression());
                        while (check(TokType.COMMA)) {
                            advance();
                            args.add(parseExpression());
                        }
                    }
                    if (check(TokType.RPAREN)) advance();
                    return new Call(t.text, args);
                }
                return new VarRef(t.text);
            }
            advance();
            return new Literal("");
        }
    }

    // ---------------------------------------------------------------- AST + evaluator

    private static final class Env {
        final Map<String, Object> vars;
        final Random random;

        Env(Map<String, Object> vars, Random random) {
            this.vars = vars;
            this.random = random;
        }
    }

    private interface Node {
        Object eval(Env env);
    }

    private static final class Literal implements Node {
        final Object value;
        Literal(Object value) { this.value = value; }
        public Object eval(Env env) { return value; }
    }

    private static final class VarRef implements Node {
        final String name;
        VarRef(String name) { this.name = name; }
        public Object eval(Env env) { return env.vars.getOrDefault(name, ""); }
    }

    private static final class Assign implements Node {
        final String name;
        final Node value;
        Assign(String name, Node value) { this.name = name; this.value = value; }
        public Object eval(Env env) {
            Object v = value.eval(env);
            env.vars.put(name, v);
            return v;
        }
    }

    private static final class Negate implements Node {
        final Node inner;
        Negate(Node inner) { this.inner = inner; }
        public Object eval(Env env) { return -toNumber(inner.eval(env)); }
    }

    private static final class BinOp implements Node {
        final String op;
        final Node left, right;
        BinOp(String op, Node left, Node right) { this.op = op; this.left = left; this.right = right; }

        public Object eval(Env env) {
            Object l = left.eval(env);
            Object r = right.eval(env);
            switch (op) {
                case "+":
                    if (l instanceof String || r instanceof String) {
                        return stringify(l) + stringify(r);
                    }
                    return toNumber(l) + toNumber(r);
                case "-": return toNumber(l) - toNumber(r);
                case "*": return toNumber(l) * toNumber(r);
                case "/": return toNumber(r) == 0 ? 0.0 : toNumber(l) / toNumber(r);
                case "%": return toNumber(r) == 0 ? 0.0 : toNumber(l) % toNumber(r);
                case "==": return stringify(l).equals(stringify(r));
                case "!=": return !stringify(l).equals(stringify(r));
                case "<": return toNumber(l) < toNumber(r);
                case ">": return toNumber(l) > toNumber(r);
                case "<=": return toNumber(l) <= toNumber(r);
                case ">=": return toNumber(l) >= toNumber(r);
                default: return "";
            }
        }
    }

    private static final class Call implements Node {
        final String name;
        final List<Node> args;
        Call(String name, List<Node> args) { this.name = name; this.args = args; }

        public Object eval(Env env) {
            List<Object> vals = new ArrayList<>();
            for (Node a : args) vals.add(a.eval(env));
            switch (name) {
                case "pad": {
                    String s = stringify(vals.get(0));
                    int len = (int) toNumber(vals.get(1));
                    StringBuilder sb = new StringBuilder(s);
                    while (sb.length() < len) sb.insert(0, '0');
                    return sb.toString();
                }
                case "upper": return stringify(vals.get(0)).toUpperCase();
                case "lower": return stringify(vals.get(0)).toLowerCase();
                case "len": return (double) stringify(vals.get(0)).length();
                case "substr": {
                    String s = stringify(vals.get(0));
                    int start = Math.max(0, Math.min(s.length(), (int) toNumber(vals.get(1))));
                    int end = vals.size() > 2 ? Math.max(start, Math.min(s.length(), (int) toNumber(vals.get(2)))) : s.length();
                    return s.substring(start, end);
                }
                case "randInt": {
                    long min = (long) toNumber(vals.get(0));
                    long max = (long) toNumber(vals.get(1));
                    if (max < min) { long tmp = min; min = max; max = tmp; }
                    return (double) (min + (long) (env.random.nextDouble() * (max - min + 1)));
                }
                case "randStr": {
                    int len = vals.isEmpty() ? 8 : (int) toNumber(vals.get(0));
                    String charset = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < len; i++) sb.append(charset.charAt(env.random.nextInt(charset.length())));
                    return sb.toString();
                }
                case "concat": {
                    StringBuilder sb = new StringBuilder();
                    for (Object v : vals) sb.append(stringify(v));
                    return sb.toString();
                }
                case "replace": {
                    String s = stringify(vals.get(0));
                    String from = stringify(vals.get(1));
                    String to = stringify(vals.get(2));
                    return s.replace(from, to);
                }
                case "now":
                    return (double) System.currentTimeMillis();
                default:
                    return "";
            }
        }
    }

    private static double toNumber(Object o) {
        if (o instanceof Double) return (Double) o;
        if (o instanceof Number) return ((Number) o).doubleValue();
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private static final Pattern INTEGRAL = Pattern.compile("-?\\d+\\.0");

    private static String stringify(Object o) {
        if (o == null) return "";
        if (o instanceof Double) {
            String s = String.valueOf(o);
            Matcher m = INTEGRAL.matcher(s);
            return m.matches() ? s.substring(0, s.length() - 2) : s;
        }
        return String.valueOf(o);
    }
}
