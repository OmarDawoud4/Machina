package com.machina;

import com.google.gson.Gson;

public class Node {

    private record Status(int nodeId, int port) {}

    private static final Gson gson = new Gson();

    final int id;
    final int port;

    Node(int id, int port) {
        this.id = id;
        this.port = port;
    }

    String statusJson() {
        return gson.toJson(new Status(id, port));
    }
}