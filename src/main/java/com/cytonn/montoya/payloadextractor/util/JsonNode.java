package com.cytonn.montoya.payloadextractor.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * A small, dependency-free JSON DOM: parse, pretty/compact serialize, path-based flatten (dotted
 * object keys / bracketed array indices, e.g. {@code nested.roles[0].id}), and immutable
 * path-based edits (replace a leaf value, remove a key/element, or reorder a key within its
 * parent object) - every edit returns a brand new tree, the original is never mutated. Object key
 * order is preserved on parse and by every edit (backed by {@link LinkedHashMap}), which is what
 * makes real "drag to reorder a JSON field" possible: the Workbench can move a key within its
 * parent's key order and re-serialize with that new order intact.
 */
public final class JsonNode
{
    public enum Type
    {OBJECT, ARRAY, STRING, NUMBER, BOOLEAN, NULL}

    public static final JsonNode NULL_NODE = new JsonNode(Type.NULL, null, null, null, null, false);

    private final Type type;
    private final LinkedHashMap<String, JsonNode> object;
    private final List<JsonNode> array;
    private final String stringValue;
    private final String numberLiteral;
    private final boolean boolValue;

    private JsonNode(Type type, LinkedHashMap<String, JsonNode> object, List<JsonNode> array,
                      String stringValue, String numberLiteral, boolean boolValue)
    {
        this.type = type;
        this.object = object;
        this.array = array;
        this.stringValue = stringValue;
        this.numberLiteral = numberLiteral;
        this.boolValue = boolValue;
    }

    static JsonNode ofObject(LinkedHashMap<String, JsonNode> map)
    {
        return new JsonNode(Type.OBJECT, map, null, null, null, false);
    }

    static JsonNode ofArray(List<JsonNode> list)
    {
        return new JsonNode(Type.ARRAY, null, list, null, null, false);
    }

    public static JsonNode ofString(String s)
    {
        return new JsonNode(Type.STRING, null, null, s, null, false);
    }

    static JsonNode ofNumber(String literal)
    {
        return new JsonNode(Type.NUMBER, null, null, null, literal, false);
    }

    static JsonNode ofBoolean(boolean b)
    {
        return new JsonNode(Type.BOOLEAN, null, null, null, null, b);
    }

    // ---------------------------------------------------------------- parsing

    public static JsonNode parse(String json)
    {
        Parser p = new Parser(json);
        p.skipWhitespace();
        JsonNode result = p.parseValue();
        p.skipWhitespace();
        if (!p.atEnd())
        {
            throw new IllegalArgumentException("Trailing content after JSON value at position " + p.pos);
        }
        return result;
    }

    private static final class Parser
    {
        final String s;
        int pos;

        Parser(String s)
        {
            this.s = s;
            this.pos = 0;
        }

        boolean atEnd()
        {
            return pos >= s.length();
        }

        char peek()
        {
            return s.charAt(pos);
        }

        void skipWhitespace()
        {
            while (!atEnd() && Character.isWhitespace(peek()))
            {
                pos++;
            }
        }

        JsonNode parseValue()
        {
            skipWhitespace();
            if (atEnd())
            {
                throw new IllegalArgumentException("Unexpected end of JSON input");
            }
            char c = peek();
            switch (c)
            {
                case '{':
                    return parseObject();
                case '[':
                    return parseArray();
                case '"':
                    return JsonNode.ofString(parseStringLiteral());
                case 't':
                    expect("true");
                    return JsonNode.ofBoolean(true);
                case 'f':
                    expect("false");
                    return JsonNode.ofBoolean(false);
                case 'n':
                    expect("null");
                    return JsonNode.NULL_NODE;
                default:
                    return parseNumber();
            }
        }

        JsonNode parseObject()
        {
            pos++; // {
            LinkedHashMap<String, JsonNode> map = new LinkedHashMap<>();
            skipWhitespace();
            if (!atEnd() && peek() == '}')
            {
                pos++;
                return JsonNode.ofObject(map);
            }
            while (true)
            {
                skipWhitespace();
                String key = parseStringLiteral();
                skipWhitespace();
                if (atEnd() || peek() != ':')
                {
                    throw new IllegalArgumentException("Expected ':' at position " + pos);
                }
                pos++;
                JsonNode value = parseValue();
                map.put(key, value);
                skipWhitespace();
                if (atEnd())
                {
                    throw new IllegalArgumentException("Unterminated object");
                }
                char c = peek();
                if (c == ',')
                {
                    pos++;
                    continue;
                }
                if (c == '}')
                {
                    pos++;
                    break;
                }
                throw new IllegalArgumentException("Expected ',' or '}' at position " + pos);
            }
            return JsonNode.ofObject(map);
        }

        JsonNode parseArray()
        {
            pos++; // [
            List<JsonNode> list = new ArrayList<>();
            skipWhitespace();
            if (!atEnd() && peek() == ']')
            {
                pos++;
                return JsonNode.ofArray(list);
            }
            while (true)
            {
                JsonNode value = parseValue();
                list.add(value);
                skipWhitespace();
                if (atEnd())
                {
                    throw new IllegalArgumentException("Unterminated array");
                }
                char c = peek();
                if (c == ',')
                {
                    pos++;
                    continue;
                }
                if (c == ']')
                {
                    pos++;
                    break;
                }
                throw new IllegalArgumentException("Expected ',' or ']' at position " + pos);
            }
            return JsonNode.ofArray(list);
        }

        String parseStringLiteral()
        {
            if (atEnd() || peek() != '"')
            {
                throw new IllegalArgumentException("Expected string at position " + pos);
            }
            pos++;
            StringBuilder sb = new StringBuilder();
            while (true)
            {
                if (atEnd())
                {
                    throw new IllegalArgumentException("Unterminated string");
                }
                char c = s.charAt(pos++);
                if (c == '"')
                {
                    break;
                }
                if (c == '\\')
                {
                    if (atEnd())
                    {
                        throw new IllegalArgumentException("Unterminated escape");
                    }
                    char esc = s.charAt(pos++);
                    switch (esc)
                    {
                        case '"':
                            sb.append('"');
                            break;
                        case '\\':
                            sb.append('\\');
                            break;
                        case '/':
                            sb.append('/');
                            break;
                        case 'n':
                            sb.append('\n');
                            break;
                        case 'r':
                            sb.append('\r');
                            break;
                        case 't':
                            sb.append('\t');
                            break;
                        case 'b':
                            sb.append('\b');
                            break;
                        case 'f':
                            sb.append('\f');
                            break;
                        case 'u':
                            if (pos + 4 > s.length())
                            {
                                throw new IllegalArgumentException("Invalid \\u escape at position " + pos);
                            }
                            sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                            pos += 4;
                            break;
                        default:
                            throw new IllegalArgumentException("Invalid escape \\" + esc + " at position " + pos);
                    }
                }
                else
                {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        JsonNode parseNumber()
        {
            int start = pos;
            if (!atEnd() && peek() == '-')
            {
                pos++;
            }
            while (!atEnd() && Character.isDigit(peek()))
            {
                pos++;
            }
            if (!atEnd() && peek() == '.')
            {
                pos++;
                while (!atEnd() && Character.isDigit(peek()))
                {
                    pos++;
                }
            }
            if (!atEnd() && (peek() == 'e' || peek() == 'E'))
            {
                pos++;
                if (!atEnd() && (peek() == '+' || peek() == '-'))
                {
                    pos++;
                }
                while (!atEnd() && Character.isDigit(peek()))
                {
                    pos++;
                }
            }
            if (pos == start)
            {
                throw new IllegalArgumentException("Invalid number at position " + pos);
            }
            return JsonNode.ofNumber(s.substring(start, pos));
        }

        void expect(String literal)
        {
            if (pos + literal.length() > s.length() || !s.startsWith(literal, pos))
            {
                throw new IllegalArgumentException("Expected '" + literal + "' at position " + pos);
            }
            pos += literal.length();
        }
    }

    // ---------------------------------------------------------------- serialization

    public String toCompactJson()
    {
        StringBuilder sb = new StringBuilder();
        writeCompact(sb);
        return sb.toString();
    }

    public String toJson()
    {
        StringBuilder sb = new StringBuilder();
        writePretty(sb, 0);
        return sb.toString();
    }

    @Override
    public String toString()
    {
        return toCompactJson();
    }

    private void writeCompact(StringBuilder sb)
    {
        switch (type)
        {
            case OBJECT:
                sb.append('{');
                boolean firstEntry = true;
                for (Map.Entry<String, JsonNode> e : object.entrySet())
                {
                    if (!firstEntry)
                    {
                        sb.append(',');
                    }
                    firstEntry = false;
                    writeString(sb, e.getKey());
                    sb.append(':');
                    e.getValue().writeCompact(sb);
                }
                sb.append('}');
                break;
            case ARRAY:
                sb.append('[');
                for (int i = 0; i < array.size(); i++)
                {
                    if (i > 0)
                    {
                        sb.append(',');
                    }
                    array.get(i).writeCompact(sb);
                }
                sb.append(']');
                break;
            case STRING:
                writeString(sb, stringValue);
                break;
            case NUMBER:
                sb.append(numberLiteral);
                break;
            case BOOLEAN:
                sb.append(boolValue);
                break;
            case NULL:
                sb.append("null");
                break;
        }
    }

    private void writePretty(StringBuilder sb, int depth)
    {
        String indent = "  ".repeat(depth + 1);
        String closeIndent = "  ".repeat(depth);
        switch (type)
        {
            case OBJECT:
                if (object.isEmpty())
                {
                    sb.append("{}");
                    break;
                }
                sb.append("{\n");
                int i = 0;
                for (Map.Entry<String, JsonNode> e : object.entrySet())
                {
                    sb.append(indent);
                    writeString(sb, e.getKey());
                    sb.append(": ");
                    e.getValue().writePretty(sb, depth + 1);
                    if (++i < object.size())
                    {
                        sb.append(',');
                    }
                    sb.append('\n');
                }
                sb.append(closeIndent).append('}');
                break;
            case ARRAY:
                if (array.isEmpty())
                {
                    sb.append("[]");
                    break;
                }
                sb.append("[\n");
                for (int j = 0; j < array.size(); j++)
                {
                    sb.append(indent);
                    array.get(j).writePretty(sb, depth + 1);
                    if (j < array.size() - 1)
                    {
                        sb.append(',');
                    }
                    sb.append('\n');
                }
                sb.append(closeIndent).append(']');
                break;
            default:
                writeCompact(sb);
        }
    }

    private static void writeString(StringBuilder sb, String s)
    {
        sb.append('"');
        for (int i = 0; i < s.length(); i++)
        {
            char c = s.charAt(i);
            switch (c)
            {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                default:
                    if (c < 0x20)
                    {
                        sb.append(String.format("\\u%04x", (int) c));
                    }
                    else
                    {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    // ---------------------------------------------------------------- accessors

    public boolean isObject()
    {
        return type == Type.OBJECT;
    }

    public boolean isArray()
    {
        return type == Type.ARRAY;
    }

    public boolean isNull()
    {
        return type == Type.NULL;
    }

    public Type type()
    {
        return type;
    }

    public JsonNode asObject()
    {
        if (!isObject())
        {
            throw new IllegalStateException("Not a JSON object: " + type);
        }
        return this;
    }

    public JsonNode asArray()
    {
        if (!isArray())
        {
            throw new IllegalStateException("Not a JSON array: " + type);
        }
        return this;
    }

    public JsonNode get(String key)
    {
        if (!isObject())
        {
            throw new IllegalStateException("Not a JSON object: " + type);
        }
        return object.get(key);
    }

    public JsonNode get(int index)
    {
        if (!isArray())
        {
            throw new IllegalStateException("Not a JSON array: " + type);
        }
        return array.get(index);
    }

    public Set<String> keys()
    {
        return isObject() ? object.keySet() : Collections.emptySet();
    }

    public int size()
    {
        if (isObject())
        {
            return object.size();
        }
        if (isArray())
        {
            return array.size();
        }
        return 0;
    }

    public String asString()
    {
        switch (type)
        {
            case STRING:
                return stringValue;
            case NUMBER:
                return numberLiteral;
            case BOOLEAN:
                return String.valueOf(boolValue);
            case NULL:
                return null;
            default:
                throw new IllegalStateException("Cannot convert a " + type + " directly to a string");
        }
    }

    /** Flattens every scalar leaf under this node into {@code path -> node} pairs. */
    public void flatten(String prefix, BiConsumer<String, JsonNode> visitor)
    {
        switch (type)
        {
            case OBJECT:
                for (Map.Entry<String, JsonNode> e : object.entrySet())
                {
                    String childPath = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
                    e.getValue().flatten(childPath, visitor);
                }
                break;
            case ARRAY:
                for (int i = 0; i < array.size(); i++)
                {
                    array.get(i).flatten(prefix + "[" + i + "]", visitor);
                }
                break;
            default:
                visitor.accept(prefix, this);
        }
    }

    /** The key order of the object found at {@code path} ({@code ""} = this node itself), or an empty list if that isn't an object. */
    public List<String> objectKeysAt(String path)
    {
        JsonNode node = navigate(path);
        return node != null && node.isObject() ? new ArrayList<>(node.object.keySet()) : List.of();
    }

    private JsonNode navigate(String path)
    {
        if (path == null || path.isEmpty())
        {
            return this;
        }
        JsonNode cur = this;
        for (Segment seg : parsePath(path))
        {
            if (cur == null)
            {
                return null;
            }
            if (!seg.isIndex())
            {
                cur = cur.isObject() ? cur.object.get(seg.key) : null;
            }
            else
            {
                cur = (cur.isArray() && seg.index >= 0 && seg.index < cur.array.size()) ? cur.array.get(seg.index) : null;
            }
        }
        return cur;
    }

    // ---------------------------------------------------------------- immutable edits

    /**
     * Returns a new tree with the leaf at {@code path} replaced by {@code newValue}. The
     * replacement keeps the original leaf's JSON type when {@code newValue} still parses as that
     * type (a number stays a number, true/false stays a boolean); otherwise it becomes a string.
     * Throws {@link IllegalArgumentException} if {@code path} doesn't exist.
     */
    public JsonNode withReplacedPath(String path, String newValue)
    {
        List<Segment> segs = parsePath(path);
        if (segs.isEmpty())
        {
            throw new IllegalArgumentException("Empty path");
        }
        return replaceRec(this, segs, 0, newValue);
    }

    private static JsonNode replaceRec(JsonNode node, List<Segment> segs, int i, String newValue)
    {
        Segment seg = segs.get(i);
        boolean last = i == segs.size() - 1;
        if (!seg.isIndex())
        {
            requireObjectKey(node, seg.key);
            LinkedHashMap<String, JsonNode> copy = new LinkedHashMap<>(node.object);
            copy.put(seg.key, last ? inferLeaf(newValue, node.object.get(seg.key)) : replaceRec(node.object.get(seg.key), segs, i + 1, newValue));
            return JsonNode.ofObject(copy);
        }
        requireArrayIndex(node, seg.index);
        List<JsonNode> copy = new ArrayList<>(node.array);
        copy.set(seg.index, last ? inferLeaf(newValue, node.array.get(seg.index)) : replaceRec(node.array.get(seg.index), segs, i + 1, newValue));
        return JsonNode.ofArray(copy);
    }

    /** Returns a new tree with the key/element at {@code path} removed entirely from its parent. */
    public JsonNode withRemovedPath(String path)
    {
        List<Segment> segs = parsePath(path);
        if (segs.isEmpty())
        {
            throw new IllegalArgumentException("Empty path");
        }
        return removeRec(this, segs, 0);
    }

    private static JsonNode removeRec(JsonNode node, List<Segment> segs, int i)
    {
        Segment seg = segs.get(i);
        boolean last = i == segs.size() - 1;
        if (!seg.isIndex())
        {
            requireObjectKey(node, seg.key);
            LinkedHashMap<String, JsonNode> copy = new LinkedHashMap<>(node.object);
            if (last)
            {
                copy.remove(seg.key);
            }
            else
            {
                copy.put(seg.key, removeRec(node.object.get(seg.key), segs, i + 1));
            }
            return JsonNode.ofObject(copy);
        }
        requireArrayIndex(node, seg.index);
        List<JsonNode> copy = new ArrayList<>(node.array);
        if (last)
        {
            copy.remove(seg.index);
        }
        else
        {
            copy.set(seg.index, removeRec(node.array.get(seg.index), segs, i + 1));
        }
        return JsonNode.ofArray(copy);
    }

    /**
     * Returns a new tree with the object key at {@code path} moved to {@code newIndex} within its
     * parent object's key order (clamped into range). Only meaningful when the path's last segment
     * is an object key - a path ending in an array index is returned unchanged, since an array
     * element's position already *is* its identity in our path scheme.
     */
    public JsonNode withReorderedKey(String path, int newIndex)
    {
        List<Segment> segs = parsePath(path);
        if (segs.isEmpty())
        {
            throw new IllegalArgumentException("Empty path");
        }
        return reorderRec(this, segs, 0, newIndex);
    }

    private static JsonNode reorderRec(JsonNode node, List<Segment> segs, int i, int newIndex)
    {
        Segment seg = segs.get(i);
        boolean last = i == segs.size() - 1;
        if (!seg.isIndex())
        {
            requireObjectKey(node, seg.key);
            if (last)
            {
                List<String> keys = new ArrayList<>(node.object.keySet());
                keys.remove(seg.key);
                int clamped = Math.max(0, Math.min(newIndex, keys.size()));
                keys.add(clamped, seg.key);
                LinkedHashMap<String, JsonNode> copy = new LinkedHashMap<>();
                for (String k : keys)
                {
                    copy.put(k, node.object.get(k));
                }
                return JsonNode.ofObject(copy);
            }
            LinkedHashMap<String, JsonNode> copy = new LinkedHashMap<>(node.object);
            copy.put(seg.key, reorderRec(node.object.get(seg.key), segs, i + 1, newIndex));
            return JsonNode.ofObject(copy);
        }
        requireArrayIndex(node, seg.index);
        if (last)
        {
            return node; // array element position is its identity - nothing to reorder
        }
        List<JsonNode> copy = new ArrayList<>(node.array);
        copy.set(seg.index, reorderRec(node.array.get(seg.index), segs, i + 1, newIndex));
        return JsonNode.ofArray(copy);
    }

    /**
     * Returns a new tree with a brand new key inserted into the object at {@code parentPath}
     * ({@code ""} = the root object). If the key already exists there, its value is overwritten
     * in place (order unchanged) rather than duplicated. Otherwise the key is inserted at
     * {@code index} within the parent's key order (clamped into range; a negative or too-large
     * index appends at the end). Throws {@link IllegalArgumentException} if {@code parentPath}
     * doesn't resolve to an object.
     */
    public JsonNode withAddedKey(String parentPath, String key, String value, int index)
    {
        List<Segment> segs = (parentPath == null || parentPath.isEmpty()) ? List.of() : parsePath(parentPath);
        return addRec(this, segs, 0, key, value, index);
    }

    private static JsonNode addRec(JsonNode node, List<Segment> segs, int i, String key, String value, int index)
    {
        if (i == segs.size())
        {
            if (!node.isObject())
            {
                throw new IllegalArgumentException("Parent path does not resolve to a JSON object");
            }
            LinkedHashMap<String, JsonNode> copy = new LinkedHashMap<>(node.object);
            if (copy.containsKey(key))
            {
                copy.put(key, JsonNode.ofString(value));
                return JsonNode.ofObject(copy);
            }
            List<String> keys = new ArrayList<>(copy.keySet());
            int clamped = (index < 0 || index > keys.size()) ? keys.size() : index;
            keys.add(clamped, key);
            LinkedHashMap<String, JsonNode> ordered = new LinkedHashMap<>();
            for (String k : keys)
            {
                ordered.put(k, k.equals(key) && !node.object.containsKey(k) ? JsonNode.ofString(value) : copy.get(k));
            }
            return JsonNode.ofObject(ordered);
        }
        Segment seg = segs.get(i);
        if (!seg.isIndex())
        {
            requireObjectKey(node, seg.key);
            LinkedHashMap<String, JsonNode> copy = new LinkedHashMap<>(node.object);
            copy.put(seg.key, addRec(node.object.get(seg.key), segs, i + 1, key, value, index));
            return JsonNode.ofObject(copy);
        }
        requireArrayIndex(node, seg.index);
        List<JsonNode> copy = new ArrayList<>(node.array);
        copy.set(seg.index, addRec(node.array.get(seg.index), segs, i + 1, key, value, index));
        return JsonNode.ofArray(copy);
    }

    private static void requireObjectKey(JsonNode node, String key)
    {
        if (!node.isObject() || !node.object.containsKey(key))
        {
            throw new IllegalArgumentException("JSON path not found: " + key);
        }
    }

    private static void requireArrayIndex(JsonNode node, int index)
    {
        if (!node.isArray() || index < 0 || index >= node.array.size())
        {
            throw new IllegalArgumentException("JSON array index not found: " + index);
        }
    }

    private static JsonNode inferLeaf(String newValue, JsonNode oldLeaf)
    {
        if (oldLeaf != null)
        {
            if (oldLeaf.type == Type.NUMBER && isNumeric(newValue))
            {
                return JsonNode.ofNumber(newValue);
            }
            if (oldLeaf.type == Type.BOOLEAN && ("true".equalsIgnoreCase(newValue) || "false".equalsIgnoreCase(newValue)))
            {
                return JsonNode.ofBoolean(Boolean.parseBoolean(newValue));
            }
        }
        return JsonNode.ofString(newValue);
    }

    private static boolean isNumeric(String s)
    {
        return s != null && s.matches("-?\\d+(\\.\\d+)?([eE][-+]?\\d+)?");
    }

    // ---------------------------------------------------------------- path syntax helpers

    /** The path of {@code path}'s parent (everything but the last segment), or {@code ""} for a top-level key. */
    public static String parentPathOf(String path)
    {
        List<Segment> segs = parsePath(path);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < segs.size() - 1; i++)
        {
            Segment seg = segs.get(i);
            if (!seg.isIndex())
            {
                if (sb.length() > 0)
                {
                    sb.append('.');
                }
                sb.append(seg.key);
            }
            else
            {
                sb.append('[').append(seg.index).append(']');
            }
        }
        return sb.toString();
    }

    /** The final object key named by {@code path}, or {@code null} if it ends in an array index instead. */
    public static String lastKeyOf(String path)
    {
        List<Segment> segs = parsePath(path);
        Segment last = segs.get(segs.size() - 1);
        return last.isIndex() ? null : last.key;
    }

    private static final class Segment
    {
        final String key;
        final int index;

        Segment(String key)
        {
            this.key = key;
            this.index = -1;
        }

        Segment(int index)
        {
            this.key = null;
            this.index = index;
        }

        boolean isIndex()
        {
            return key == null;
        }
    }

    private static List<Segment> parsePath(String path)
    {
        List<Segment> segs = new ArrayList<>();
        int i = 0;
        int n = path.length();
        StringBuilder cur = new StringBuilder();
        while (i < n)
        {
            char c = path.charAt(i);
            if (c == '.')
            {
                if (cur.length() > 0)
                {
                    segs.add(new Segment(cur.toString()));
                    cur.setLength(0);
                }
                i++;
            }
            else if (c == '[')
            {
                if (cur.length() > 0)
                {
                    segs.add(new Segment(cur.toString()));
                    cur.setLength(0);
                }
                int close = path.indexOf(']', i);
                if (close < 0)
                {
                    throw new IllegalArgumentException("Unterminated '[' in path: " + path);
                }
                segs.add(new Segment(Integer.parseInt(path.substring(i + 1, close))));
                i = close + 1;
            }
            else
            {
                cur.append(c);
                i++;
            }
        }
        if (cur.length() > 0)
        {
            segs.add(new Segment(cur.toString()));
        }
        if (segs.isEmpty())
        {
            throw new IllegalArgumentException("Empty JSON path");
        }
        return segs;
    }
}
