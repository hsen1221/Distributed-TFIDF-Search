import com.sun.net.httpserver.*;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class WebServer {
    // Logger for tracking server activity and errors
    private static final Logger logger = Logger.getLogger(WebServer.class.getName());

    // Endpoint constants
    private static final String TASK_ENDPOINT = "/task";
    private static final String STATUS_ENDPOINT = "/status";

    // Header constants
    private static final String HEADER_TEST = "X-Test";
    private static final String HEADER_DEBUG = "X-Debug";

    private final int port;
    private HttpServer server;

    public WebServer(int port) {
        this.port = port;
    }

    public static void main(String[] args) {
        int serverPort = 8080;
        if (args.length == 1) {
            try {
                serverPort = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                logger.warning("Invalid port provided, defaulting to 8080.");
            }
        }

        WebServer webServer = new WebServer(serverPort);
        webServer.startServer();
    }

    public void startServer() {
        try {
            // Create HTTP server binding to the specified port
            this.server = HttpServer.create(new InetSocketAddress(port), 0);
        } catch (IOException e) {
            logger.severe("Could not create web server: " + e.getMessage());
            throw new RuntimeException(e);
        }

        // Define contexts (routes)
        HttpContext statusContext = server.createContext(STATUS_ENDPOINT);
        HttpContext taskContext = server.createContext(TASK_ENDPOINT);

        // Map routes to handler methods
        statusContext.setHandler(this::handleStatusCheckRequest);
        taskContext.setHandler(this::handleTaskRequest);

        // Create a thread pool to handle multiple requests concurrently
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();

        logger.info("Server is listening on port " + port);
    }

    /**
     * Handles the multiplication task request.
     * Expects a comma-separated list of numbers in the request body.
     */
    private void handleTaskRequest(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            exchange.close();
            return;
        }

        Headers headers = exchange.getRequestHeaders();

        // Check for Test Mode header
        if (headers.containsKey(HEADER_TEST) && headers.get(HEADER_TEST).get(0).equalsIgnoreCase("true")) {
            String dummyResp = "123\n";
            sendResponse(dummyResp.getBytes(), exchange, 200);
            return;
        }

        // Check for Debug Mode header
        boolean isDebugMode = headers.containsKey(HEADER_DEBUG) &&
                headers.get(HEADER_DEBUG).get(0).equalsIgnoreCase("true");

        long startTime = System.nanoTime();

        try {
            // Read and parse request body
            byte[] requestBytes = exchange.getRequestBody().readAllBytes();

            // Calculate result (Business Logic)
            byte[] responseBytes = calculateResponse(requestBytes);

            long finishTime = System.nanoTime();

            // Add timing info to headers if Debug Mode is on
            if (isDebugMode) {
                String debugMessage = String.format("Operation took %d ns", finishTime - startTime);
                exchange.getResponseHeaders().put("X-Debug-Info", Collections.singletonList(debugMessage));
            }

            // Send successful response
            sendResponse(responseBytes, exchange, 200);

        } catch (NumberFormatException e) {
            // Handle invalid number format (e.g., user sent text instead of numbers)
            String errorMsg = "Invalid input: Please send comma-separated numbers.\n";
            sendResponse(errorMsg.getBytes(), exchange, 400); // 400 Bad Request
        } catch (Exception e) {
            // Handle unexpected server errors
            logger.severe("Internal Error: " + e.getMessage());
            sendResponse("Internal Server Error\n".getBytes(), exchange, 500);
        }
    }

    /**
     * core logic: Multiplies a list of numbers.
     */
    private byte[] calculateResponse(byte[] requestBytes) {
        String bodyString = new String(requestBytes).trim(); // Trim to remove accidental newlines

        if (bodyString.isEmpty()) {
            return "Result is empty\n".getBytes();
        }

        String[] stringNumbers = bodyString.split(",");
        BigInteger result = BigInteger.ONE;

        for (String number : stringNumbers) {
            BigInteger bigInteger = new BigInteger(number.trim());
            result = result.multiply(bigInteger);
        }

        return String.format("Result of the multiplication is %s\n", result).getBytes();
    }

    /**
     * Simple health check endpoint.
     */
    private void handleStatusCheckRequest(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            exchange.close();
            return;
        }
        String responseMessage = "Server is alive\n";
        sendResponse(responseMessage.getBytes(), exchange, 200);
    }

    /**
     * Helper method to send HTTP responses.
     */
    private void sendResponse(byte[] responseBytes, HttpExchange exchange, int statusCode) throws IOException {
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(responseBytes);
            outputStream.flush();
        } // Try-with-resources automatically closes the stream
        exchange.close();
    }
}