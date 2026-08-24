package com.cytonn.montoya.payloadextractor.testutil;

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.responses.HttpResponse;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A minimal JDK dynamic-proxy stand-in for Montoya's {@code HttpResponse}, just enough to exercise
 * {@code ResponseDiff} outside a live Burp instance - status code, headers, body, and body-length
 * (used for response-size). Any interface method not explicitly implemented here throws
 * {@link UnsupportedOperationException}, same convention as {@code FakeHttpRequest}.
 */
public final class FakeHttpResponse implements InvocationHandler {

    private final int statusCode;
    private final String body;
    private final Map<String, String> headers;

    private FakeHttpResponse(int statusCode, String body, Map<String, String> headers) {
        this.statusCode = statusCode;
        this.body = body == null ? "" : body;
        this.headers = headers;
    }

    public static HttpResponse create(int statusCode, String body, Map<String, String> headers) {
        FakeHttpResponse handler = new FakeHttpResponse(statusCode, body, headers);
        return (HttpResponse) Proxy.newProxyInstance(
                FakeHttpResponse.class.getClassLoader(),
                new Class<?>[]{HttpResponse.class},
                handler);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        switch (method.getName()) {
            case "statusCode":
                return (short) statusCode;
            case "bodyToString":
                return body;
            case "toByteArray":
                return fakeByteArray(body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
            case "headers": {
                List<HttpHeader> list = new ArrayList<>();
                for (Map.Entry<String, String> e : headers.entrySet()) {
                    list.add(fakeHeader(e.getKey(), e.getValue()));
                }
                return list;
            }
            case "header": {
                String hname = (String) args[0];
                for (Map.Entry<String, String> e : headers.entrySet()) {
                    if (e.getKey().equalsIgnoreCase(hname)) {
                        return fakeHeader(e.getKey(), e.getValue());
                    }
                }
                return null;
            }
            case "equals":
                return proxy == args[0];
            case "hashCode":
                return System.identityHashCode(proxy);
            case "toString":
                return "FakeHttpResponse{" + statusCode + ", body=" + body + ", headers=" + headers + "}";
            default:
                throw new UnsupportedOperationException("Not implemented in FakeHttpResponse: " + method.getName());
        }
    }

    private static ByteArray fakeByteArray(int length) {
        return (ByteArray) Proxy.newProxyInstance(
                FakeHttpResponse.class.getClassLoader(),
                new Class<?>[]{ByteArray.class},
                (p, m, a) -> {
                    switch (m.getName()) {
                        case "length": return length;
                        case "equals": return p == a[0];
                        case "hashCode": return System.identityHashCode(p);
                        default: throw new UnsupportedOperationException(m.getName());
                    }
                });
    }

    private static HttpHeader fakeHeader(String name, String value) {
        return (HttpHeader) Proxy.newProxyInstance(
                FakeHttpResponse.class.getClassLoader(),
                new Class<?>[]{HttpHeader.class},
                (p, m, a) -> {
                    switch (m.getName()) {
                        case "name": return name;
                        case "value": return value;
                        case "toString": return name + ": " + value;
                        case "equals": return p == a[0];
                        case "hashCode": return System.identityHashCode(p);
                        default: throw new UnsupportedOperationException(m.getName());
                    }
                });
    }
}
