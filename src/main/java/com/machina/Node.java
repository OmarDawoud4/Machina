package com.machina;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class Node {

    private record Status(int nodeId, int port , String role, int currentTerm,
                          int leaderId, int logSize, int commitIndex, int lastApplied) {}

    private record VoteRequest (int term , int candidateId){}
    private record Heartbeat (int term , int leaderId , List<LogEntry> entries, int commitIndex){}
    private record LogEntry (int term , int index , String key , String value ){}
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
//    private static final long DEAD_AFTER_MS =1500 ;
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

//    private final Map<Integer, Long> lastSeen = new ConcurrentHashMap<>();

    private final List<LogEntry> log = new ArrayList<>();
    private final Map<String, String> state = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> matchIndex = new ConcurrentHashMap<>();
    private int commitIndex = 0;
    private int lastApplied = 0;

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
        return gson.toJson(new Status(id, port, role.name().toLowerCase(),
                currentTerm, leaderId, log.size(), commitIndex, lastApplied));
    }

    synchronized String handleGet(String key) {
        JsonObject res = new JsonObject();
        res.addProperty("key", key);
        String value = state.get(key);
        if (value == null) {
            res.addProperty("error", "not found");
        } else {
            res.addProperty("value", value);
        }
        return res.toString();
    }


    private void sendHeartbeats() {
        String json = gson.toJson(new Heartbeat(currentTerm, id, log, commitIndex));
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
                HttpResponse<String> response =
                        http.send(request, HttpResponse.BodyHandlers.ofString());
                JsonObject ack = gson.fromJson(response.body(), JsonObject.class);
                matchIndex.put(peerId, ack.get("matchIndex").getAsInt());
            } catch (Exception e) {
                System.out.println("heartbeat: node " + peerId + " unreachable");
            }
        }
        advanceCommitIndex();
    }

    private void advanceCommitIndex() {
        for (int n = log.size(); n > commitIndex; n--) {
            int copies = 1;
            for (int peerId : CLUSTER.keySet()) {
                if (peerId == id) {
                    continue;
                }
                Integer m = matchIndex.get(peerId);
                if (m != null && m >= n) {
                    copies++;
                }
            }
            if (copies >= MAJORITY) {
                commitIndex = n;
                System.out.println("commit: majority holds through index " + n);
                applyCommitted();
                return;
            }
        }
    }

    private synchronized void applyCommitted() {
        while (lastApplied < commitIndex && lastApplied < log.size()) {
            LogEntry e = log.get(lastApplied);
            state.put(e.key(), e.value());
            lastApplied++;
            System.out.println("apply: " + e.key() + "=" + e.value()
                    + "  (applied " + lastApplied + "/" + commitIndex + ")");
        }
    }

    synchronized String handleHeartbeat(String requestBody) {
        Heartbeat hb = gson.fromJson(requestBody, Heartbeat.class);
        if (hb.term() >= currentTerm) {
            currentTerm = hb.term();
            role = Role.FOLLOWER;
            leaderId = hb.leaderId();
            lastActivityMs = System.currentTimeMillis();

            log.clear();
            if (hb.entries() != null ){
                log.addAll(hb.entries());
            }

            commitIndex = Math.min(hb.commitIndex(), log.size());
            applyCommitted();
        }
        JsonObject res = new JsonObject();
        res.addProperty("matchIndex", log.size());
        return res.toString();
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
    synchronized String handleSet (String requestBody) {
        JsonObject req = gson.fromJson(requestBody, JsonObject.class);
        String key = req.get("key").getAsString();
        String value = req.get("value").getAsString();
        if (role != Role.LEADER) {
            JsonObject res = new JsonObject();
            res.addProperty("error","not leader");
            res.addProperty("leaderId", leaderId);
            return res.toString();
        }
        log.add(new LogEntry(currentTerm, log.size()+1, key , value ));
        System.out.println("log : appended "+ key + "= " + value+"at index "+ log.size());

        JsonObject res = new JsonObject();
        res.addProperty("logged" , true );
        res.addProperty("index", log.size());
        return res.toString();
    }




}