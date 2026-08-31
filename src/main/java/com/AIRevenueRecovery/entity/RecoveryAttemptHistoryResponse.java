package com.AIRevenueRecovery.entity;

public class RecoveryAttemptHistoryResponse {

    private Long id;
    private Integer attemptNumber;
    private String action;
    private String result;
    private FailureReason failureReason;
    private String aiRecommendation;
    private Double aiConfidence;
    public RecoveryAttemptHistoryResponse() {
    }
    public RecoveryAttemptHistoryResponse(
            Long id,
            Integer attemptNumber,
            String action,
            String result,
            FailureReason failureReason,
            String aiRecommendation,
            Double aiConfidence) {
        this.id = id;
        this.attemptNumber = attemptNumber;
        this.action = action;
        this.result = result;
        this.failureReason = failureReason;
        this.aiRecommendation = aiRecommendation;
        this.aiConfidence = aiConfidence;
    }
    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public Integer getAttemptNumber() {
        return attemptNumber;
    }
    public void setAttemptNumber(Integer attemptNumber) {
        this.attemptNumber = attemptNumber;
    }
    public String getAction() {
        return action;
    }
    public void setAction(String action) {
        this.action = action;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public FailureReason getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(
            FailureReason failureReason) {

        this.failureReason = failureReason;
    }

    public String getAiRecommendation() {
        return aiRecommendation;
    }

    public void setAiRecommendation(
            String aiRecommendation) {

        this.aiRecommendation =
                aiRecommendation;
    }

    public Double getAiConfidence() {
        return aiConfidence;
    }

    public void setAiConfidence(
            Double aiConfidence) {

        this.aiConfidence =
                aiConfidence;
    }
}