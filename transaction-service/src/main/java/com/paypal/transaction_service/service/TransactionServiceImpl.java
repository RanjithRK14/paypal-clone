package com.paypal.transaction_service.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paypal.transaction_service.kafka.KafkaEventProducer;
import com.paypal.transaction_service.model.Transaction;
import com.paypal.transaction_service.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository repository;
    private final ObjectMapper objectMapper;
    private final KafkaEventProducer kafkaEventProducer;
    private final RestTemplate restTemplate;

    @Override
    public Transaction createTransaction(Transaction request) {

        System.out.println("🚀 Entered createTransaction()");

        Long senderId = request.getSenderId();
        Long receiverId = request.getReceiverId();
        Double amount = request.getAmount();

        request.setStatus("PENDING");
        request.setTimestamp(LocalDateTime.now());

        Transaction savedTransaction = repository.save(request);

        String walletServiceUrl = "http://localhost:8083/api/v1/wallets";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-Id", senderId.toString());

        String holdReference = null;
        boolean captured = false;

        try {

            // Step 1: HOLD sender amount
            String holdJson = String.format(
                    "{\"userId\": %d, \"currency\": \"INR\", \"amount\": %.2f}",
                    senderId, amount);

            HttpEntity<String> holdEntity = new HttpEntity<>(holdJson, headers);

            ResponseEntity<String> holdResponse =
                    restTemplate.postForEntity(walletServiceUrl + "/hold", holdEntity, String.class);

            JsonNode holdNode;

            try {
                holdNode = objectMapper.readTree(holdResponse.getBody());
            } catch (Exception e) {
                throw new RuntimeException("Failed to parse hold response", e);
            }

            holdReference = holdNode.get("holdReference").asText();

            System.out.println("🛑 Hold placed: " + holdReference);

            // Step 2: Check receiver wallet exists
            HttpEntity<?> receiverEntity = new HttpEntity<>(headers);

            ResponseEntity<String> receiverCheck =
                    restTemplate.exchange(
                            walletServiceUrl + "/" + receiverId,
                            HttpMethod.GET,
                            receiverEntity,
                            String.class
                    );

            if (!receiverCheck.getStatusCode().is2xxSuccessful()) {

                tryReleaseHold(walletServiceUrl, holdReference, headers);

                savedTransaction.setStatus("FAILED");
                return repository.save(savedTransaction);
            }

            // Step 3: CAPTURE (debit sender)
            String captureJson = String.format("{\"holdReference\":\"%s\"}", holdReference);

            HttpEntity<String> captureEntity = new HttpEntity<>(captureJson, headers);

            ResponseEntity<String> captureResponse =
                    restTemplate.postForEntity(walletServiceUrl + "/capture", captureEntity, String.class);

            if (!captureResponse.getStatusCode().is2xxSuccessful()) {

                tryReleaseHold(walletServiceUrl, holdReference, headers);

                savedTransaction.setStatus("FAILED");
                return repository.save(savedTransaction);
            }

            captured = true;

            System.out.println("💸 Sender debited");

            // Step 4: Credit receiver
            String creditJson = String.format(
                    "{\"userId\": %d, \"currency\": \"INR\", \"amount\": %.2f}",
                    receiverId, amount);

            HttpEntity<String> creditEntity = new HttpEntity<>(creditJson, headers);

            try {

                ResponseEntity<String> creditResponse =
                        restTemplate.postForEntity(walletServiceUrl + "/credit", creditEntity, String.class);

                if (!creditResponse.getStatusCode().is2xxSuccessful()) {
                    throw new RuntimeException("Receiver credit failed");
                }

                System.out.println("💰 Receiver credited");

            } catch (Exception ex) {

                System.out.println("❌ Credit failed → refund sender");

                String refundJson = String.format(
                        "{\"userId\": %d, \"currency\": \"INR\", \"amount\": %.2f}",
                        senderId, amount);

                HttpEntity<String> refundEntity = new HttpEntity<>(refundJson, headers);

                restTemplate.postForEntity(walletServiceUrl + "/credit", refundEntity, String.class);

                savedTransaction.setStatus("FAILED");
                return repository.save(savedTransaction);
            }

            savedTransaction.setStatus("SUCCESS");
            savedTransaction = repository.save(savedTransaction);

        } catch (HttpClientErrorException ex) {

            System.out.println("❌ Wallet error: " + ex.getResponseBodyAsString());

            if (holdReference != null && !captured) {
                tryReleaseHold(walletServiceUrl, holdReference, headers);
            }

            savedTransaction.setStatus("FAILED");
            savedTransaction = repository.save(savedTransaction);
        }

        try {

            kafkaEventProducer.sendTransactionEvent(
                    String.valueOf(savedTransaction.getId()),
                    savedTransaction
            );

        } catch (Exception e) {
            System.out.println("Kafka send failed");
        }

        return savedTransaction;
    }

    private void tryReleaseHold(String walletServiceUrl, String holdReference, HttpHeaders headers) {

        try {

            HttpEntity<?> entity = new HttpEntity<>(headers);

            restTemplate.postForEntity(
                    walletServiceUrl + "/release/" + holdReference,
                    entity,
                    String.class
            );

        } catch (Exception ignored) {
        }
    }

    @Override
    public Transaction getTransactionById(Long id) {
        return repository.findById(id).orElse(null);
    }

    public List<Transaction> getTransactionsByUser(Long userId) {
        return repository.findBySenderIdOrReceiverId(userId, userId);
    }
}