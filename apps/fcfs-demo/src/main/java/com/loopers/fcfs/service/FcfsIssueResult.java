package com.loopers.fcfs.service;

public record FcfsIssueResult(boolean success, String message, int issuedCount) {

    public static FcfsIssueResult success(String message, int issuedCount) {
        return new FcfsIssueResult(true, message, issuedCount);
    }

    public static FcfsIssueResult failure(String message, int issuedCount) {
        return new FcfsIssueResult(false, message, issuedCount);
    }
}
