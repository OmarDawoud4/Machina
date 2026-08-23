package com.machina;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

public class Node {

    private record Status(int nodeId, int port , String role, Map<Integer,String>peers) {}

    private static final Gson gson = new Gson();

    private static final Map<Integer, Integer> CLUSTER  = new TreeMap<>(
            Map.of(
                    1,7001,
                    2,7002,
                    3,7003
            )
    );
    private static final long DEAD_AFTER_MS =1500 ;
    private static final HttpClient http = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofMillis(200))
                        .build();
    final int id;
    final int port;

    private final Map<Integer, Long> lastSeen = new ConcurrentHashMap<>();

    void tick() {
        for (int peerId : CLUSTER.keySet()) {
            if (peerId == id) {
                continue;
            }
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + CLUSTER.get(peerId) + "/status"))
                        .timeout(Duration.ofMillis(300))
                        .GET()
                        .build();
                HttpResponse<String> response =
                        http.send(request, HttpResponse.BodyHandlers.ofString());
                JsonObject body = gson.fromJson(response.body(), JsonObject.class);
                lastSeen.put(body.get("nodeId").getAsInt(), System.currentTimeMillis());
                System.out.println("heartbeat ok: node " + peerId);
            } catch (Exception e) {
                System.out.println("heartbeat failed: node " + peerId);
            }
        }
    }
    Node(int id, int port) {
        this.id = id;
        this.port = port;
    }

    String statusJson() {

        Map<Integer, String> peers = new LinkedHashMap<>();
        for (int peerId : CLUSTER.keySet()) {
            if (peerId == id) {
                continue;
            }
            Long seen = lastSeen.get(peerId);
            boolean alive = seen != null && System.currentTimeMillis() - seen < DEAD_AFTER_MS;
            peers.put(peerId, alive ? "ALIVE" : "DEAD");

        }

        return gson.toJson(new Status(id, port,"follower", peers));
    }
}