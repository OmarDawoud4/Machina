package com.machina;

import com.sun.net.httpserver.HttpServer;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
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
        server.createContext("/requestvote", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8);
            respond(exchange, 200, node.handleRequestVote(body));
        });
        server.createContext("/heartbeat", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8);
            node.handleHeartbeat(body);
            respond(exchange, 200, "{}");
        });

        server.start();
    }

    private static void respond(HttpExchange exchange, int code, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}