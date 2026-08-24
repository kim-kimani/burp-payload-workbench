package com.cytonn.montoya.payloadextractor.detector;

import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.cytonn.montoya.payloadextractor.parser.HttpMessageParser;
import com.cytonn.montoya.payloadextractor.parser.ParsedField;

import java.util.List;

/**
 * Runs structural extraction ({@link HttpMessageParser}) then layers naming and categorization on
 * top: every field gets a friendly {@link ParsedField#setName(String) name} and a best-guess
 * {@link PayloadCategory} via {@link InterestingKeyMatcher}. Nothing is dropped here - "interesting
 * vs. not" is left to the UI as a filter, since the analyst may still want to see (and act on)
 * generic fields.
 */
public final class PayloadDetector {

    private PayloadDetector() {
    }

    public static List<ParsedField> detectRequest(HttpRequest request) {
        List<ParsedField> fields = HttpMessageParser.parseRequest(request);
        annotate(fields);
        return fields;
    }

    public static List<ParsedField> detectResponse(HttpResponse response) {
        List<ParsedField> fields = HttpMessageParser.parseResponse(response);
        annotate(fields);
        return fields;
    }

    private static void annotate(List<ParsedField> fields) {
        for (ParsedField f : fields) {
            PayloadCategory category = InterestingKeyMatcher.categorize(f.path());
            f.setCategory(category.name());
            f.setName(NameNormalizer.displayName(f.path()) + "  [" + f.location().displayName() + "]");
        }
    }

    /** Convenience filter: only the fields whose category isn't GENERIC. */
    public static List<ParsedField> onlyInteresting(List<ParsedField> fields) {
        return fields.stream()
                .filter(f -> !"GENERIC".equals(f.category()))
                .toList();
    }
}
