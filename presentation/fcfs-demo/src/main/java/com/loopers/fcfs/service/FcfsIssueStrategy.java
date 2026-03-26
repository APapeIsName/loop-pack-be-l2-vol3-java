package com.loopers.fcfs.service;

public interface FcfsIssueStrategy {
    FcfsIssueResult issue(Long couponId, Long memberId);
}
