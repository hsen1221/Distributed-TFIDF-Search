package com.distributed.search.service;

import com.distributed.search.model.DocumentScore;
import com.distributed.search.model.TFRequest;
import com.distributed.search.model.TFResponse;
import com.distributed.search.model.TFServiceGrpc;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TFServiceImpl extends TFServiceGrpc.TFServiceImplBase {

    // The shared directory containing the document files
    private static final String DOCUMENTS_DIRECTORY = "./documents";

    @Override
    public void calculateTF(TFRequest request, StreamObserver<TFResponse> responseObserver) {

        // 1. Parse the search query: Normalize to lowercase and split into terms
        String searchQuery = request.getSearchQuery().toLowerCase();
        List<String> searchTerms = Arrays.asList(searchQuery.split("\\s+"));
        List<String> filePaths = request.getFilePathsList();

        TFResponse.Builder responseBuilder = TFResponse.newBuilder();

        // 2. Process each assigned file
        for (String fileName : filePaths) {
            try {
                Path path = Paths.get(DOCUMENTS_DIRECTORY, fileName);

                // Read content using Java 11's readString (more efficient)
                String content = Files.readString(path).toLowerCase();

                // Tokenize content into words
                String[] words = content.split("\\s+");
                double totalWords = words.length;

                // Avoid division by zero for empty files
                if (totalWords == 0) {
                    continue;
                }

                // Optimization: Create a Frequency Map of the document words.
                // This reduces complexity from O(N*M) to O(N), where N=doc length, M=query length.
                Map<String, Long> wordCounts = Arrays.stream(words)
                        .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

                double cumulativeTF = 0.0;

                // Calculate TF for the search terms
                // Formula: TF = (Term Count in Doc / Total Words in Doc)
                for (String term : searchTerms) {
                    long termCount = wordCounts.getOrDefault(term, 0L);
                    cumulativeTF += (double) termCount / totalWords;
                }

                // Only return results if the document contains at least one of the terms
                if (cumulativeTF > 0) {
                    responseBuilder.addDocumentScores(
                            DocumentScore.newBuilder()
                                    .setDocumentName(fileName)
                                    .setTfScore(cumulativeTF)
                                    .build()
                    );
                }

            } catch (IOException e) {
                System.err.println("Error reading file: " + fileName + " -> " + e.getMessage());
            }
        }

        // 3. Send the response back to the Leader
        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }
}