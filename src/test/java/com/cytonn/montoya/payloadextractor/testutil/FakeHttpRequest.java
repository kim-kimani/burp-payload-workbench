package com.cytonn.montoya.payloadextractor.testutil;

import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.requests.HttpRequest;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A minimal JDK dynamic-proxy stand-in for Montoya's {@code HttpRequest}, just enough to exercise
 * {@code RequestModifier}'s JSON body / cookie / header reconciliation paths and {@code ReplayEngine}
 * outside a live Burp instance (Montoya's own static factories require a live extension context
 * and throw when called standalone - see {@code ScriptEngineManagerTest}/README for details).
 * Any interface method not explicitly implemented here throws {@link UnsupportedOperationException}.
 */
public final class FakeHttpRequest implements InvocationHandler {

    private final String body;
    private final Map<String, String> headers;

    private FakeHttpRequest(String body, Map<String, String> headers) {
        this.body = body;
        this.headers = headers;
    }

    public static HttpRequest create(String body, Map<String, String> headers) {
        FakeHttpRequest handler = new FakeHttpRequest(body, new LinkedHashMap<>(headers));
        return (HttpRequest) Proxy.newProxyInstance(
                FakeHttpRequest.class.getClassLoader(),
                new Class<?>[]{HttpRequest.class},
                handler);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        String name = method.getName();
        switch (name) {
            case "bodyToString":
                return body;
            case "withBody":
                return create((String) args[0], headers);
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
            case "hasHeader":
                if (args.length == 1 && args[0] instanceof String) {
                    String hname = (String) args[0];
                    for (String k : headers.keySet()) {
                        if (k.equalsIgnoreCase(hname)) return true;
                    }
                    return false;
                }
                return false;
            case "withUpdatedHeader": {
                String hname = (String) args[0];
                String hvalue = (String) args[1];
                Map<String, String> copy = new LinkedHashMap<>(headers);
                String actualKey = hname;
                for (String k : headers.keySet()) {
                    if (k.equalsIgnoreCase(hname)) { actualKey = k; break; }
                }
                copy.put(actualKey, hvalue);
                return create(body, copy);
            }
            case "withAddedHeader": {
                Map<String, String> copy = new LinkedHashMap<>(headers);
                copy.put((String) args[0], (String) args[1]);
                return create(body, copy);
            }
            case "withRemovedHeader": {
                Map<String, String> copy = new LinkedHashMap<>(headers);
                String hname = (String) args[0];
                copy.keySet().removeIf(k -> k.equalsIgnoreCase(hname));
                return create(body, copy);
            }
            case "parameters":
                return List.of();
            case "hasParameter":
                return false;
            case "parameter":
                return null;
            case "equals":
                return proxy == args[0];
            case "hashCode":
                return System.identityHashCode(proxy);
            case "toString":
                return "FakeHttpRequest{body=" + body + ", headers=" + headers + "}";
            default:
                throw new UnsupportedOperationException("Not implemented in FakeHttpRequest: " + name);
        }
    }

    private static HttpHeader fakeHeader(String name, String value) {
        return (HttpHeader) Proxy.newProxyInstance(
                FakeHttpRequest.class.getClassLoader(),
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
