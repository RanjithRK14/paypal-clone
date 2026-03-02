package com.paypal.reward_service.service;

import com.paypal.reward_service.model.Reward;

import java.util.List;

public interface RewardService {

    Reward sendReward(Reward reward);

    List<Reward> getRewardsByUserId(Long userId);
}
