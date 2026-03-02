package com.paypal.reward_service.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paypal.reward_service.model.Reward;
import com.paypal.reward_service.model.Transaction;
import com.paypal.reward_service.repository.RewardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class RewardConsumer {

    private final RewardRepository rewardRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "txn-initiated",
            groupId = "reward-group"
    )
    public void consumerTransaction(byte[] message) {

        try {

            // ✅ Convert byte[] → Transaction
            Transaction transaction =
                    objectMapper.readValue(message, Transaction.class);

            System.out.println("📥 Transaction received: " + transaction);

            // ✅ Check duplicate reward
            if (rewardRepository.existsByTransactionId(transaction.getId())) {

                System.out.println(
                        "⚠️ Reward already exists for transaction: "
                                + transaction.getId()
                );
                return;
            }

            // ✅ Create reward
            Reward reward = new Reward();
            reward.setUserId(transaction.getSenderId());
            reward.setPoints(
                    transaction.getAmount() * 100
            );
            reward.setSentAt(
                    LocalDateTime.now()
            );
            reward.setTransactionId(
                    transaction.getId()
            );

            // ✅ Save reward
            rewardRepository.save(reward);
            System.out.println("✅ Reward saved: " + reward);

        }

        catch (Exception e) {

            System.err.println(
                    "❌ Failed to process transaction: "
                            + e.getMessage()
            );
            e.printStackTrace();

        }

    }

}
