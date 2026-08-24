package com.cytonn.montoya.payloadextractor.db;

import com.cytonn.montoya.payloadextractor.detector.NameNormalizer;
import com.cytonn.montoya.payloadextractor.util.JsonNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The persistent payload collection store: a set of {@link PayloadCollection}s keyed by
 * normalized field name, so values captured under different raw key spellings ("authToken" vs
 * "auth_token") land in the same bucket and can be replayed against either.
 */
public final class PayloadDatabase {

    private final Map<String, PayloadCollection> collectionsByNormalizedName = new LinkedHashMap<>();

    public synchronized PayloadCollection findOrCreate(String rawKeyOrPath, String category) {
        String normal = NameNormalizer.normalForm(rawKeyOrPath);
        if (normal.isEmpty()) {
            normal = rawKeyOrPath == null ? "unnamed" : rawKeyOrPath;
        }
        PayloadCollection collection = collectionsByNormalizedName.get(normal);
        if (collection == null) {
            collection = new PayloadCollection(null, normal, category == null ? "GENERIC" : category);
            collectionsByNormalizedName.put(normal, collection);
        }
        return collection;
    }

    public synchronized Optional<PayloadCollection> find(String normalizedName) {
        return Optional.ofNullable(collectionsByNormalizedName.get(normalizedName));
    }

    public synchronized Optional<PayloadCollection> findById(String id) {
        return collectionsByNormalizedName.values().stream().filter(c -> c.id().equals(id)).findFirst();
    }

    public synchronized PayloadValue remember(String rawKeyOrPath, String category, String value, PayloadSource source, long capturedAtEpochMillis, String originHost) {
        PayloadCollection collection = findOrCreate(rawKeyOrPath, category);
        PayloadValue pv = PayloadValue.of(value, source, capturedAtEpochMillis, originHost);
        collection.add(pv);
        return pv;
    }

    public synchronized List<PayloadCollection> allCollections() {
        List<PayloadCollection> list = new ArrayList<>(collectionsByNormalizedName.values());
        list.sort(Comparator.comparing(PayloadCollection::normalizedName, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    public synchronized boolean removeCollection(String id) {
        return collectionsByNormalizedName.values().removeIf(c -> c.id().equals(id));
    }

    /** Merges every value from {@code sourceId} into {@code targetId} (dedupe-aware) and deletes the source collection. */
    public synchronized boolean mergeCollections(String sourceId, String targetId) {
        Optional<PayloadCollection> source = findById(sourceId);
        Optional<PayloadCollection> target = findById(targetId);
        if (source.isEmpty() || target.isEmpty() || sourceId.equals(targetId)) {
            return false;
        }
        for (PayloadValue v : source.get().values()) {
            target.get().add(new PayloadValue(null, v.value(), v.source(), v.capturedAtEpochMillis(), v.originHost(), v.notes(), v.isFavorite()));
        }
        removeCollection(sourceId);
        return true;
    }

    /** Renames a collection's normalized name and re-keys it in the lookup map. */
    public synchronized boolean renameCollection(String id, String newNormalizedName) {
        if (newNormalizedName == null || newNormalizedName.isBlank()) {
            return false;
        }
        Optional<PayloadCollection> collection = findById(id);
        if (collection.isEmpty()) {
            return false;
        }
        collectionsByNormalizedName.values().remove(collection.get());
        collection.get().setNormalizedName(newNormalizedName);
        collectionsByNormalizedName.put(newNormalizedName, collection.get());
        return true;
    }

    public synchronized void clear() {
        collectionsByNormalizedName.clear();
    }

    public synchronized int collectionCount() {
        return collectionsByNormalizedName.size();
    }

    public synchronized int valueCount() {
        return collectionsByNormalizedName.values().stream().mapToInt(c -> c.values().size()).sum();
    }

    // ---------------------------------------------------------------- serialization

    public synchronized String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"collections\":[");
        boolean firstCollection = true;
        for (PayloadCollection c : allCollections()) {
            if (!firstCollection) sb.append(',');
            firstCollection = false;
            sb.append("{\"id\":").append(q(c.id()))
              .append(",\"normalizedName\":").append(q(c.normalizedName()))
              .append(",\"category\":").append(q(c.category()))
              .append(",\"values\":[");
            boolean firstValue = true;
            for (PayloadValue v : c.values()) {
                if (!firstValue) sb.append(',');
                firstValue = false;
                sb.append("{\"id\":").append(q(v.id()))
                  .append(",\"value\":").append(q(v.value()))
                  .append(",\"source\":").append(q(v.source().name()))
                  .append(",\"capturedAt\":").append(v.capturedAtEpochMillis())
                  .append(",\"originHost\":").append(q(v.originHost()))
                  .append(",\"notes\":").append(q(v.notes()))
                  .append(",\"favorite\":").append(v.isFavorite())
                  .append('}');
            }
            sb.append("]}");
        }
        sb.append("]}");
        return sb.toString();
    }

    public static PayloadDatabase fromJson(String json) {
        PayloadDatabase db = new PayloadDatabase();
        if (json == null || json.isBlank()) {
            return db;
        }
        JsonNode root = JsonNode.parse(json);
        JsonNode collections = root.get("collections");
        if (collections == null || !collections.isArray()) {
            return db;
        }
        for (int i = 0; i < collections.size(); i++) {
            JsonNode c = collections.get(i);
            String id = str(c.get("id"));
            String normalizedName = str(c.get("normalizedName"));
            String category = str(c.get("category"));
            PayloadCollection collection = new PayloadCollection(id, normalizedName, category);
            JsonNode values = c.get("values");
            if (values != null && values.isArray()) {
                for (int j = 0; j < values.size(); j++) {
                    JsonNode v = values.get(j);
                    PayloadSource source;
                    try {
                        source = PayloadSource.valueOf(str(v.get("source")));
                    } catch (Exception e) {
                        source = PayloadSource.MANUAL;
                    }
                    long capturedAt = 0L;
                    JsonNode capturedNode = v.get("capturedAt");
                    if (capturedNode != null) {
                        try {
                            capturedAt = Long.parseLong(capturedNode.asString());
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    boolean favorite = false;
                    JsonNode favoriteNode = v.get("favorite");
                    if (favoriteNode != null) {
                        favorite = Boolean.parseBoolean(favoriteNode.asString());
                    }
                    PayloadValue pv = new PayloadValue(str(v.get("id")), str(v.get("value")), source, capturedAt, str(v.get("originHost")), str(v.get("notes")), favorite);
                    collection.add(pv);
                }
            }
            db.collectionsByNormalizedName.put(normalizedName, collection);
        }
        return db;
    }

    private static String str(JsonNode node) {
        return node == null || node.isNull() ? null : node.asString();
    }

    private static String q(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.append('"').toString();
    }
}
