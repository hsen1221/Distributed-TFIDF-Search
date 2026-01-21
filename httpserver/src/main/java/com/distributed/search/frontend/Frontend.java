package com.distributed.search.frontend;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

/**
 * The Frontend Server acts as a Gateway / Load Balancer.
 * It exposes a stable API to clients and dynamically forwards requests
 * to the current Leader of the distributed cluster.
 */
public class Frontend implements Watcher {
    // Configuration constants
    private static final String ZOOKEEPER_ADDRESS = "localhost:2181";
    private static final int SESSION_TIMEOUT = 3000;
    private static final String ELECTION_NAMESPACE = "/election";

    private final int port;
    private ZooKeeper zooKeeper;
    private final HttpClient httpClient;

    // Latch to block startup until the Zookeeper connection is fully established
    private final CountDownLatch connectedSignal = new CountDownLatch(1);

    public Frontend(int port) {
        this.port = port;
        // Configure HttpClient with a timeout to avoid hanging if the Leader is slow
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public void start() throws Exception {
        // 1. Connect to Zookeeper
        connectToZookeeper();

        // 2. Start HTTP Server
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // Endpoint 1: API for Search (POST) - Forwards to Cluster Leader
        server.createContext("/search", this::handleSearchRequest);

        // Endpoint 2: Status Check - Checks if Frontend is alive
        server.createContext("/status", this::handleStatusRequest);

        // Endpoint 3: Serve the HTML UI (Root URL) - Returns index.html
        server.createContext("/", this::handleStaticResource);

        // Use a thread pool to handle multiple concurrent users
        server.setExecutor(Executors.newFixedThreadPool(10));
        server.start();

        System.out.println("Frontend Server started on port " + port);
        System.out.println("Open your browser at: http://localhost:" + port);
    }

    /**
     * Connects to Zookeeper and blocks until the connection is ready.
     */
    private void connectToZookeeper() throws IOException, InterruptedException {
        this.zooKeeper = new ZooKeeper(ZOOKEEPER_ADDRESS, SESSION_TIMEOUT, this);
        connectedSignal.await(); // Wait for SyncConnected event
        System.out.println("Connected to Zookeeper.");
    }

    /**
     * Zookeeper Watcher callback.
     */
    @Override
    public void process(WatchedEvent event) {
        if (event.getState() == Event.KeeperState.SyncConnected) {
            connectedSignal.countDown(); // Release the latch
        }
    }

    /**
     * Serves the 'index.html' file when the user visits the root URL (http://localhost:9000/).
     */
    private void handleStaticResource(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            exchange.close();
            return;
        }

        try {
            // Read index.html from the project root directory
            byte[] response = Files.readAllBytes(Paths.get("index.html"));

            // Set content type so the browser knows it's HTML
            exchange.getResponseHeaders().add("Content-Type", "text/html");
            sendResponse(exchange, 200, response);
        } catch (IOException e) {
            String error = "Error loading UI: index.html not found in project root.";
            sendResponse(exchange, 404, error);
        }
    }

    /**
     * Handles search requests from users (e.g., browsers, curl).
     * It queries Zookeeper for the Leader's address and forwards the request.
     */
    private void handleSearchRequest(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            exchange.close();
            return;
        }

        try {
            // 1. Read the user's query
            String query = new String(exchange.getRequestBody().readAllBytes()).trim();
            System.out.println("Received query: " + query);

            if (query.isEmpty()) {
                sendResponse(exchange, 400, "Query cannot be empty");
                return;
            }

            // 2. Find the current Leader from Zookeeper
            String leaderAddress = getLeaderAddress();
            if (leaderAddress == null) {
                sendResponse(exchange, 503, "Service Unavailable: No Cluster Leader found.");
                return;
            }

            // 3. Forward the request to the Leader
            // leaderAddress is stored as "host:port" (e.g., localhost:8081)
            String leaderUrl = "http://" + leaderAddress + "/search";
            System.out.println("Forwarding to Leader at: " + leaderUrl);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(leaderUrl))
                    .POST(HttpRequest.BodyPublishers.ofString(query))
                    .build();

            // Send request to Leader synchronously
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            // 4. Send the Leader's response back to the user
            sendResponse(exchange, response.statusCode(), response.body());

        } catch (Exception e) {
            e.printStackTrace();
            sendResponse(exchange, 500, "Internal Gateway Error: " + e.getMessage());
        }
    }

    /**
     * Simple health check endpoint.
     */
    private void handleStatusRequest(HttpExchange exchange) throws IOException {
        String leader = getLeaderAddress();
        String status = "Frontend is running. Current Leader: " + (leader != null ? leader : "None");
        sendResponse(exchange, 200, status);
    }

    /**
     * Queries Zookeeper to find the node with the smallest sequence number in /election.
     * The node with the smallest number is always the Leader.
     */
    private String getLeaderAddress() {
        try {
            if (zooKeeper.exists(ELECTION_NAMESPACE, false) == null) {
                return null;
            }

            List<String> children = zooKeeper.getChildren(ELECTION_NAMESPACE, false);
            if (children.isEmpty()) {
                return null;
            }

            // Sort to find the smallest node (The Leader)
            Collections.sort(children);
            String leaderZnode = children.get(0);

            // Fetch the data (HTTP Address) from the Znode
            byte[] data = zooKeeper.getData(ELECTION_NAMESPACE + "/" + leaderZnode, false, null);
            return new String(data);

        } catch (KeeperException | InterruptedException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Helper to send String responses.
     */
    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        sendResponse(exchange, statusCode, response.getBytes());
    }

    /**
     * Helper to send Byte Array responses (used for Files/HTML).
     */
    private void sendResponse(HttpExchange exchange, int statusCode, byte[] response) throws IOException {
        exchange.sendResponseHeaders(statusCode, response.length);
        OutputStream os = exchange.getResponseBody();
        os.write(response);
        os.close();
    }

    public static void main(String[] args) {
        int port = 9000;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }

        try {
            Frontend frontend = new Frontend(port);
            frontend.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}