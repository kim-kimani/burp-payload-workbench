package com.cytonn.montoya.payloadextractor.testutil;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.Function;

/** Proxy-based fake for Montoya's {@code Http}, used to drive {@code ReplayEngine} end-to-end without a live Burp instance. */
public final class FakeHttp implements InvocationHandler {

    private final Function<HttpRequest, Integer> statusForRequest;

    private FakeHttp(Function<HttpRequest, Integer> statusForRequest) {
        this.statusForRequest = statusForRequest;
    }

    public static Http create(Function<HttpRequest, Integer> statusForRequest) {
        FakeHttp handler = new FakeHttp(statusForRequest);
        return (Http) Proxy.newProxyInstance(FakeHttp.class.getClassLoader(), new Class<?>[]{Http.class}, handler);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        if (method.getName().equals("sendRequest") && args.length >= 1 && args[0] instanceof HttpRequest) {
            HttpRequest req = (HttpRequest) args[0];
            int status = statusForRequest.apply(req);
            HttpResponse fakeResponse = (HttpResponse) Proxy.newProxyInstance(FakeHttp.class.getClassLoader(),
                    new Class<?>[]{HttpResponse.class}, (p, m, a) -> {
                        switch (m.getName()) {
                            case "statusCode": return (short) status;
                            case "equals": return p == a[0];
                            case "hashCode": return System.identityHashCode(p);
                            case "toString": return "FakeResponse{" + status + "}";
                            default: throw new UnsupportedOperationException(m.getName());
                        }
                    });
            return (HttpRequestResponse) Proxy.newProxyInstance(FakeHttp.class.getClassLoader(),
                    new Class<?>[]{HttpRequestResponse.class}, (p, m, a) -> {
                        switch (m.getName()) {
                            case "hasResponse": return true;
                            case "response": return fakeResponse;
                            case "request": return req;
                            case "equals": return p == a[0];
                            case "hashCode": return System.identityHashCode(p);
                            case "toString": return "FakeRR{}";
                            default: throw new UnsupportedOperationException(m.getName());
                        }
                    });
        }
        throw new UnsupportedOperationException("Not implemented in FakeHttp: " + method.getName());
    }
}
