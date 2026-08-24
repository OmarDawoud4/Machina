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
import java.util.concurrent.ThreadLocalRandom;

public class Node {

    private record Status(int nodeId, int port , String role, int currentTerm,
                          int leaderId, Map<Integer,String>peers) {}

    private record VoteRequest (int term , int candidateId){}
    private record Heartbeat (int term , int leaderId){}
    private static final Gson gson = new Gson();
    private long lastActivityMs = System.currentTimeMillis();
    private long electionTimeoutMs = freshTimeout();

    private static final Map<Integer, Integer> CLUSTER  = new TreeMap<>(
            Map.of(
                    1,7001,
                    2,7002,
                    3,7003
            )
    );

    private static final int MAJORITY = 2 ;
    private static final long DEAD_AFTER_MS =1500 ;
    private static final HttpClient http = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofMillis(200))
                        .build();

    enum Role { FOLLOWER, CANDIDATE, LEADER }

    private Role role = Role.FOLLOWER;
    private int currentTerm = 0;
    private int votedFor = -1;
    private int leaderId = -1;
    final int id;
    final int port;

    private final Map<Integer, Long> lastSeen = new ConcurrentHashMap<>();


    synchronized void tick() {
        if ((role == Role.FOLLOWER || role == Role.CANDIDATE)
                && System.currentTimeMillis() - lastActivityMs > electionTimeoutMs) {
            startElection();
            return;
        }
        if (role == Role.LEADER) {
            sendHeartbeats();
        }
    }
    private static long freshTimeout() {
        return ThreadLocalRandom.current().nextLong(1500, 3000);
    }
        private void startElection() {
            role = Role.CANDIDATE ;
            currentTerm ++ ;
            votedFor = id ;
            leaderId = -1 ;
            lastActivityMs = System.currentTimeMillis();
            electionTimeoutMs = freshTimeout();
            int electionTerm = currentTerm;

            System.out.println("election: node " + id + " declares candidacy, term "+ currentTerm);
            String json = gson.toJson(new VoteRequest(electionTerm, id));
            int votes = 1 ;

            for (int peerId : CLUSTER.keySet()) {
                if (peerId == id) {continue;
                }
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:" + CLUSTER.get(peerId)
                                    + "/requestvote"))
                            .timeout(Duration.ofMillis(500))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(json))
                            .build();
                    HttpResponse<String> response =
                            http.send(request, HttpResponse.BodyHandlers.ofString());
                    JsonObject body = gson.fromJson(response.body(), JsonObject.class);
                    if (body.get("voteGranted").getAsBoolean()) {
                        votes++;
                        System.out.println("election: node " + peerId + " voted for me");
                    }
                } catch (Exception e) {
                    System.out.println("election: node " + peerId + " unreachable");
                }
            }
            if (votes >= MAJORITY && role == Role.CANDIDATE && currentTerm == electionTerm) {
                role = Role.LEADER;
                leaderId = id;
                System.out.println("node " + id + " IS THE LEADER, term " + currentTerm);
            }}


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


        return gson.toJson(new Status(id, port, role.name().toLowerCase(),
                currentTerm, leaderId, peers));    }


    private void sendHeartbeats() {
        String json = gson.toJson(new Heartbeat(currentTerm, id));
        for (int peerId : CLUSTER.keySet()) {
            if (peerId == id) {
                continue;
            }
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + CLUSTER.get(peerId)
                                + "/heartbeat"))
                        .timeout(Duration.ofMillis(300))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();
                http.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) {
                System.out.println("heartbeat: node " + peerId + " unreachable");
            }
        }
    }

    synchronized void handleHeartbeat(String requestBody) {
        Heartbeat hb = gson.fromJson(requestBody, Heartbeat.class);
        if (hb.term() >= currentTerm) {
            currentTerm = hb.term();
            role = Role.FOLLOWER;
            leaderId = hb.leaderId();
            lastActivityMs = System.currentTimeMillis();
        }
    }

    synchronized String handleRequestVote(String requestBody) {

        VoteRequest req = gson.fromJson(requestBody, VoteRequest.class);
        boolean grant ;
        if (req.term() < currentTerm) {
            grant = false;
        } else {
            if (req.term()>currentTerm) {
                currentTerm = req.term();
                role = Role.FOLLOWER;
                votedFor = - 1;
            }
            grant = votedFor == -1 || votedFor == req.candidateId();

            if(grant) {
                votedFor = req.candidateId();
                lastActivityMs = System.currentTimeMillis();
            }
        }
        JsonObject response = new JsonObject();
        response.addProperty("voteGranted" , grant );
        return response.toString();
    }




}