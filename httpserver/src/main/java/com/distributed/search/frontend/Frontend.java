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
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

public class Frontend implements Watcher {
    private static final String ZOOKEEPER_ADDRESS = "localhost:2181";
    private static final int SESSION_TIMEOUT = 3000;
    private static final String ELECTION_NAMESPACE = "/election";
    private static final String DOCUMENTS_DIRECTORY = "./documents"; // Shared folder path

    private final int port;
    private ZooKeeper zooKeeper;
    private final HttpClient httpClient;
    private final CountDownLatch connectedSignal = new CountDownLatch(1);

    public Frontend(int port) {
        this.port = port;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public void start() throws Exception {
        connectToZookeeper();

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // API 1: Search
        server.createContext("/search", this::handleSearchRequest);

        // API 2: Serve Document Content (NEW!)
        server.createContext("/document", this::handleDocumentRequest);

        // API 3: Status Check
        server.createContext("/status", this::handleStatusRequest);

        // API 4: Serve UI
        server.createContext("/", this::handleStaticResource);

        server.setExecutor(Executors.newFixedThreadPool(10));
        server.start();

        System.out.println("Frontend Server started on port " + port);
        System.out.println("Open your browser at: http://localhost:" + port);
    }

    private void connectToZookeeper() throws IOException, InterruptedException {
        this.zooKeeper = new ZooKeeper(ZOOKEEPER_ADDRESS, SESSION_TIMEOUT, this);
        connectedSignal.await();
    }

    @Override
    public void process(WatchedEvent event) {
        if (event.getState() == Event.KeeperState.SyncConnected) {
            connectedSignal.countDown();
        }
    }

    // --- NEW METHOD: Handles requests like /document?name=file1.txt ---
    private void handleDocumentRequest(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            exchange.close();
            return;
        }

        String query = exchange.getRequestURI().getQuery();
        String fileName = null;

        // Parse "name=file1.txt"
        if (query != null && query.contains("name=")) {
            String[] parts = query.split("=");
            if (parts.length > 1) {
                fileName = URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
            }
        }

        if (fileName == null || fileName.isEmpty()) {
            sendResponse(exchange, 400, "Missing file name");
            return;
        }

        // Security check: prevent users from accessing files outside ./documents
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            sendResponse(exchange, 403, "Access Denied");
            return;
        }

        try {
            Path filePath = Paths.get(DOCUMENTS_DIRECTORY, fileName);
            if (!Files.exists(filePath)) {
                sendResponse(exchange, 404, "File not found");
                return;
            }

            byte[] fileContent = Files.readAllBytes(filePath);
            exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=UTF-8");
            sendResponse(exchange, 200, fileContent);

        } catch (IOException e) {
            e.printStackTrace();
            sendResponse(exchange, 500, "Error reading file");
        }
    }

    // --- Serve index.html ---
    private void handleStaticResource(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            exchange.close();
            return;
        }
        try {
            byte[] response = Files.readAllBytes(Paths.get("index.html"));
            exchange.getResponseHeaders().add("Content-Type", "text/html");
            sendResponse(exchange, 200, response);
        } catch (IOException e) {
            sendResponse(exchange, 404, "Error loading UI: index.html not found.");
        }
    }

    // --- Handle Search ---
    private void handleSearchRequest(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            exchange.close();
            return;
        }
        try {
            String query = new String(exchange.getRequestBody().readAllBytes()).trim();
            String leaderAddress = getLeaderAddress();

            if (leaderAddress == null) {
                sendResponse(exchange, 503, "No Leader found.");
                return;
            }

            String leaderUrl = "http://" + leaderAddress + "/search";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(leaderUrl))
                    .POST(HttpRequest.BodyPublishers.ofString(query))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            sendResponse(exchange, response.statusCode(), response.body());

        } catch (Exception e) {
            e.printStackTrace();
            sendResponse(exchange, 500, "Internal Error: " + e.getMessage());
        }
    }

    private void handleStatusRequest(HttpExchange exchange) throws IOException {
        String leader = getLeaderAddress();
        String status = "Frontend is running. Current Leader: " + (leader != null ? leader : "None");
        sendResponse(exchange, 200, status);
    }

    private String getLeaderAddress() {
        try {
            if (zooKeeper.exists(ELECTION_NAMESPACE, false) == null) return null;
            List<String> children = zooKeeper.getChildren(ELECTION_NAMESPACE, false);
            if (children.isEmpty()) return null;
            Collections.sort(children);
            String leaderZnode = children.get(0);
            //ip port
            byte[] data = zooKeeper.getData(ELECTION_NAMESPACE + "/" + leaderZnode, false, null);
            return new String(data);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        sendResponse(exchange, statusCode, response.getBytes());
    }

    private void sendResponse(HttpExchange exchange, int statusCode, byte[] response) throws IOException {
        exchange.sendResponseHeaders(statusCode, response.length);
        OutputStream os = exchange.getResponseBody();
        os.write(response);
        os.close();
    }

    public static void main(String[] args) {
        int port = 9000;
        if (args.length > 0) port = Integer.parseInt(args[0]);
        try {
            new Frontend(port).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}