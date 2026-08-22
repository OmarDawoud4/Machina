package com.machina;
public class Main {

    public static void main(String[] args) throws Exception {
        int id =-1 ;
        int port = -1 ;
        for ( int i = 0; i < args.length; i++ ) {
            switch (args[i]) {
                case "--id" -> id = Integer.parseInt(args[++i]);
                case "--port" -> port = Integer.parseInt(args[++i]);
                default -> System.err.println("ignoring arg: " + args[i]);
            }
        }
        if (id == -1 || port == -1) {
            System.err.println("usage: java -jar machina-1.0.jar --id N --port P");
            System.exit(1);
        }
        Node node = new Node(id, port);
        new HttpApi(node).start();
        System.out.println("node " + id + " listening on " + port);
    }
}
