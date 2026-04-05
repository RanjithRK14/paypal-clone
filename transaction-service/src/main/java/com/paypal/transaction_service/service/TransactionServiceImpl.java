package com.paypal.transaction_service.service;

import org.springframework.beans.factory.annotation.Value;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paypal.transaction_service.kafka.KafkaEventProducer;
import com.paypal.transaction_service.model.Transaction;
import com.paypal.transaction_service.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
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

    @Value("${WALLET_SERVICE_URL:http://localhost:8083}")
    private String walletServiceUrl;

    private String getWalletUrl() {
        return walletServiceUrl + "/api/v1/wallets";
    }

    @Override
    public Transaction createTransaction(Transaction request) {

        System.out.println("🚀 createTransaction() senderId=" + request.getSenderId()
                + " receiverId=" + request.getReceiverId()
                + " amount=" + request.getAmount());

        Long senderId   = request.getSenderId();
        Long receiverId = request.getReceiverId();
        Long amount     = request.getAmount();

        request.setStatus("PENDING");
        request.setTimestamp(LocalDateTime.now());

        Transaction saved = repository.save(request);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-Id", senderId.toString());

        String holdReference = null;
        boolean captured = false;

        try {
            // Step 1: HOLD sender amount
            String holdJson = String.format(
                    "{\"userId\": %d, \"currency\": \"INR\", \"amount\": %d}",
                    senderId, amount);

            ResponseEntity<String> holdResp = restTemplate.postForEntity(
                    getWalletUrl() + "/hold", new HttpEntity<>(holdJson, headers), String.class);

            // Null-safe holdReference extraction — prevents NullPointerException
            JsonNode holdNode = objectMapper.readTree(holdResp.getBody());
            if (holdNode == null || !holdNode.has("holdReference") || holdNode.get("holdReference").isNull()) {
                System.err.println("❌ Hold response missing holdReference field: " + holdResp.getBody());
                saved.setStatus("FAILED");
                return repository.save(saved);
            }
            holdReference = holdNode.get("holdReference").asText();
            System.out.println("🛑 Hold placed: " + holdReference);

            // Step 2: Verify receiver wallet exists
            ResponseEntity<String> receiverResp = restTemplate.exchange(
                    getWalletUrl() + "/" + receiverId,
                    HttpMethod.GET, new HttpEntity<>(headers), String.class);

            if (!receiverResp.getStatusCode().is2xxSuccessful()) {
                tryReleaseHold(holdReference, headers);
                saved.setStatus("FAILED");
                return repository.save(saved);
            }

            // Step 3: CAPTURE (debit sender balance)
            String captureJson = String.format("{\"holdReference\":\"%s\"}", holdReference);
            ResponseEntity<String> captureResp = restTemplate.postForEntity(
                    getWalletUrl() + "/capture", new HttpEntity<>(captureJson, headers), String.class);

            if (!captureResp.getStatusCode().is2xxSuccessful()) {
                tryReleaseHold(holdReference, headers);
                saved.setStatus("FAILED");
                return repository.save(saved);
            }

            captured = true;
            System.out.println("💸 Sender debited via capture");

            // Step 4: CREDIT receiver
            String creditJson = String.format(
                    "{\"userId\": %d, \"currency\": \"INR\", \"amount\": %d}",
                    receiverId, amount);

            try {
                ResponseEntity<String> creditResp = restTemplate.postForEntity(
                        getWalletUrl() + "/credit", new HttpEntity<>(creditJson, headers), String.class);

                if (!creditResp.getStatusCode().is2xxSuccessful()) {
                    throw new RuntimeException("Receiver credit failed");
                }
                System.out.println("💰 Receiver credited");

            } catch (Exception ex) {
                System.out.println("❌ Credit failed → refunding sender");
                String refundJson = String.format(
                        "{\"userId\": %d, \"currency\": \"INR\", \"amount\": %d}",
                        senderId, amount);
                try {
                    restTemplate.postForEntity(
                            getWalletUrl() + "/credit", new HttpEntity<>(refundJson, headers), String.class);
                } catch (Exception refundEx) {
                    System.err.println("⚠️ Refund also failed: " + refundEx.getMessage());
                }
                saved.setStatus("FAILED");
                return repository.save(saved);
            }

            saved.setStatus("SUCCESS");
            saved = repository.save(saved);

        } catch (ResourceAccessException ex) {
            // Timeout or connection refused — wallet-service sleeping or unreachable
            System.err.println("❌ Wallet service unreachable (timeout/connection refused): " + ex.getMessage());
            if (holdReference != null && !captured) {
                tryReleaseHold(holdReference, headers);
            }
            saved.setStatus("FAILED");
            saved = repository.save(saved);

        } catch (HttpClientErrorException ex) {
            System.err.println("❌ Wallet HTTP error: " + ex.getStatusCode()
                    + " — " + ex.getResponseBodyAsString());
            if (holdReference != null && !captured) {
                tryReleaseHold(holdReference, headers);
            }
            saved.setStatus("FAILED");
            saved = repository.save(saved);

        } catch (Exception ex) {
            System.err.println("❌ Unexpected error in createTransaction: " + ex.getMessage());
            ex.printStackTrace();
            if (holdReference != null && !captured) {
                tryReleaseHold(holdReference, headers);
            }
            saved.setStatus("FAILED");
            saved = repository.save(saved);
        }

        // Kafka — non-blocking, failure does not affect response
        try {
            kafkaEventProducer.sendTransactionEvent(String.valueOf(saved.getId()), saved);
        } catch (Exception e) {
            System.out.println("⚠️ Kafka send failed (non-critical): " + e.getMessage());
        }

        return saved;
    }

    private void tryReleaseHold(String holdReference, HttpHeaders headers) {
        try {
            restTemplate.postForEntity(
                    getWalletUrl() + "/release/" + holdReference,
                    new HttpEntity<>(headers), String.class);
            System.out.println("🔓 Hold released: " + holdReference);
        } catch (Exception e) {
            System.err.println("⚠️ Failed to release hold: " + e.getMessage());
        }
    }

    @Override
    public Transaction getTransactionById(Long id) {
        return repository.findById(id).orElse(null);
    }

    @Override
    public List<Transaction> getTransactionsByUser(Long userId) {
        return repository.findBySenderIdOrReceiverId(userId, userId);
    }
}
