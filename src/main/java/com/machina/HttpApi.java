package com.machina;

import com.sun.net.httpserver.HttpServer;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class HttpApi {

    private final Node node;

    HttpApi(Node node) {
        this.node = node;
    }

    void start() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(node.port), 0);
        server.createContext("/status", exchange -> {
            byte[] body = node.statusJson().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
    }
}