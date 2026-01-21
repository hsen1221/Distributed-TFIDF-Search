package com.distributed.search;

import com.distributed.search.cluster.LeaderElection;
import com.distributed.search.cluster.OnElectionCallback;
import com.distributed.search.cluster.ServiceRegistry;
import com.distributed.search.model.DocumentScore;
import com.distributed.search.model.TFRequest;
import com.distributed.search.model.TFResponse;
import com.distributed.search.model.TFServiceGrpc;
import com.distributed.search.service.TFServiceImpl;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * The main node class that acts as the entry point for the application.
 * It connects to Zookeeper to determine its role (Leader or Worker).
 * - Leader: Exposes HTTP API, distributes tasks to Workers via gRPC, aggregates results.
 * - Worker: Exposes gRPC Service, calculates TF for assigned documents.
 */
public class SearchNode implements OnElectionCallback, Watcher {
    // Configuration constants
    private static final String ZOOKEEPER_ADDRESS = "localhost:2181";
    private static final int SESSION_TIMEOUT = 3000;
    private static final String DOCUMENTS_DIRECTORY = "./documents";
    private static final int GRPC_PORT_OFFSET = 1000; // gRPC port = HTTP port + 1000

    private final int serverPort;
    private ZooKeeper zooKeeper;
    private ServiceRegistry serviceRegistry;
    private LeaderElection leaderElection;
    private HttpServer httpServer;
    private Server grpcServer;

    // Latch used to block startup until Zookeeper connection is fully established
    private final CountDownLatch connectedSignal = new CountDownLatch(1);

    public SearchNode(int port) {
        this.serverPort = port;
    }

    /**
     * Starts the node, connects to the cluster, and initiates role selection.
     */
    public void start() throws Exception {
        // 1. Connect to Zookeeper
        connectToZookeeper();

        // 2. Init components
        this.serviceRegistry = new ServiceRegistry(zooKeeper);
        this.leaderElection = new LeaderElection(zooKeeper, this);

        // 3. Start gRPC
        int grpcPort = serverPort + GRPC_PORT_OFFSET;
        this.grpcServer = ServerBuilder.forPort(grpcPort)
                .addService(new TFServiceImpl())
                .build()
                .start();
        System.out.println("gRPC Server started on port " + grpcPort);

        // 4. Start HTTP
        this.httpServer = HttpServer.create(new InetSocketAddress(serverPort), 0);
        this.httpServer.createContext("/search", this::handleSearchRequest);
        this.httpServer.setExecutor(Executors.newFixedThreadPool(10));
        this.httpServer.start();
        System.out.println("HTTP Server started on port " + serverPort);

        // 5. Volunteer for Leadership
        // Pass the HTTP address so Frontend can find us
        String currentAddress = "localhost:" + serverPort;
        leaderElection.volunteerForLeadership(currentAddress);

        leaderElection.reelectLeader();
    }

    /**
     * Establishes connection to Zookeeper and waits for the SyncConnected event.
     */
    private void connectToZookeeper() throws IOException, InterruptedException {
        this.zooKeeper = new ZooKeeper(ZOOKEEPER_ADDRESS, SESSION_TIMEOUT, this);
        // Block current thread until process() counts down the latch
        connectedSignal.await();
        System.out.println("Successfully connected to Zookeeper");
    }

    /**
     * Callback for Zookeeper events.
     */
    @Override
    public void process(WatchedEvent event) {
        if (event.getState() == Event.KeeperState.SyncConnected) {
            connectedSignal.countDown(); // Signal that connection is ready
        }
    }

    /**
     * Triggered when this node wins the election.
     */
    @Override
    public void onElectedToBeLeader() {
        System.out.println("I am the LEADER now.");
        if (serviceRegistry != null) {
            // Leaders do not register themselves as workers
            serviceRegistry.unregisterFromCluster();
            // Leaders subscribe to updates to know about available workers
            serviceRegistry.registerForUpdates();
        }
    }

    /**
     * Triggered when this node becomes a Worker.
     */
    @Override
    public void onWorker() {
        System.out.println("I am a WORKER.");
        try {
            // Register this node's address in Zookeeper so the Leader can find it.
            // We register the "Host:GRPCPort" string so the leader knows where to send gRPC requests.
            String currentAddress = "localhost:" + (serverPort + GRPC_PORT_OFFSET);
            if (serviceRegistry != null) {
                serviceRegistry.registerToCluster(currentAddress);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Handles HTTP POST requests for search. This logic runs ONLY on the Leader node.
     */
    private void handleSearchRequest(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            exchange.close();
            return;
        }

        try {
            // 1. Parse the query
            String query = new String(exchange.getRequestBody().readAllBytes()).trim();
            System.out.println("Received search query: " + query);

            if (query.isEmpty()) {
                sendResponse(exchange, 400, "Query cannot be empty");
                return;
            }

            // 2. Get active workers from ServiceRegistry
            List<String> workers = serviceRegistry.getAllServiceAddresses();
            if (workers.isEmpty()) {
                sendResponse(exchange, 503, "No workers available in the cluster");
                return;
            }

            // 3. Get all document filenames
            File dir = new File(DOCUMENTS_DIRECTORY);
            String[] fileNames = dir.list((d, name) -> name.endsWith(".txt"));
            if (fileNames == null || fileNames.length == 0) {
                sendResponse(exchange, 404, "No documents found in " + DOCUMENTS_DIRECTORY);
                return;
            }
            List<String> allFiles = Arrays.asList(fileNames);

            // 4. Distribute files among workers (Round Robin strategy)
            Map<String, List<String>> tasks = new HashMap<>();
            for (int i = 0; i < allFiles.size(); i++) {
                String worker = workers.get(i % workers.size());
                tasks.computeIfAbsent(worker, k -> new ArrayList<>()).add(allFiles.get(i));
            }

            // 5. Send tasks to workers via gRPC and aggregate results
            // Note: We use a List to collect ALL individual term scores from workers.
            List<DocumentScore> allTermScores = new ArrayList<>();

            for (Map.Entry<String, List<String>> entry : tasks.entrySet()) {
                String workerAddress = entry.getKey();
                List<String> filesForWorker = entry.getValue();

                // Extract host and port
                String[] parts = workerAddress.split(":");
                String host = parts[0];
                int port = Integer.parseInt(parts[1]);

                // Establish gRPC connection
                ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
                        .usePlaintext()
                        .build();

                TFServiceGrpc.TFServiceBlockingStub stub = TFServiceGrpc.newBlockingStub(channel);

                TFRequest request = TFRequest.newBuilder()
                        .setSearchQuery(query)
                        .addAllFilePaths(filesForWorker)
                        .build();

                try {
                    TFResponse response = stub.calculateTF(request);
                    // Add all individual term scores to our list
                    allTermScores.addAll(response.getDocumentScoresList());
                } catch (Exception e) {
                    System.err.println("Worker " + workerAddress + " failed: " + e.getMessage());
                } finally {
                    channel.shutdown();
                }
            }

            // --- NEW SCORING LOGIC WITH FAILURE CASE FIX ---

            // 6. Calculate IDF for each term
            // FIX: If user searches "car car", we get 2 results for "car" from the same file.
            // We must use a SET to count unique documents per term to avoid IDF errors.
            Map<String, Set<String>> termToDocs = new HashMap<>();

            for (DocumentScore score : allTermScores) {
                termToDocs.computeIfAbsent(score.getTerm(), k -> new HashSet<>()).add(score.getDocumentName());
            }

            Map<String, Double> idfMap = new HashMap<>();
            for (Map.Entry<String, Set<String>> entry : termToDocs.entrySet()) {
                String term = entry.getKey();
                int docsWithTerm = entry.getValue().size(); // Correct unique count

                // IDF = log(Total Docs / Docs with Term)
                double idf = Math.log((double) allFiles.size() / docsWithTerm);
                idfMap.put(term, idf);
            }

            // 7. Calculate Total Score per Document
            // Score = Sum(TF * IDF) for each term in the doc
            Map<String, Double> finalDocScores = new HashMap<>();

            for (DocumentScore score : allTermScores) {
                double tf = score.getTfScore();
                double idf = idfMap.getOrDefault(score.getTerm(), 0.0);
                double termScore = tf * idf;

                // Add to the document's total score
                finalDocScores.put(
                        score.getDocumentName(),
                        finalDocScores.getOrDefault(score.getDocumentName(), 0.0) + termScore
                );
            }

            // 8. Sort results (Descending order by score)
            List<Map.Entry<String, Double>> sortedResults = finalDocScores.entrySet().stream()
                    .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                    .collect(Collectors.toList());

            // 9. Construct and send response
            StringBuilder resultBuilder = new StringBuilder("Results for '" + query + "':\n");
            for (Map.Entry<String, Double> entry : sortedResults) {
                resultBuilder.append(entry.getKey())
                        .append(" : ")
                        .append(String.format("%.4f", entry.getValue()))
                        .append("\n");
            }

            sendResponse(exchange, 200, resultBuilder.toString());

        } catch (Exception e) {
            e.printStackTrace();
            sendResponse(exchange, 500, "Internal Server Error: " + e.getMessage());
        }
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.sendResponseHeaders(statusCode, response.getBytes().length);
        OutputStream os = exchange.getResponseBody();
        os.write(response.getBytes());
        os.close();
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java SearchNode <port>");
            return;
        }

        try {
            int port = Integer.parseInt(args[0]);

            // Ensure documents directory exists before starting
            File docDir = new File(DOCUMENTS_DIRECTORY);
            if (!docDir.exists()) {
                docDir.mkdirs();
            }

            SearchNode node = new SearchNode(port);
            node.start();

            // Keep main thread alive to allow servers to run
            Thread.currentThread().join();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}