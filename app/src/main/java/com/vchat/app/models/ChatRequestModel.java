package com.vchat.app.models;

import com.google.firebase.Timestamp;

public class ChatRequestModel {
    private String requestId;
    private String senderId;
    private String receiverId;
    private String status; // "pending", "accepted", "rejected"

    // Backward + forward compatible time field
    private Object timestamp;

    // Added for accepted flow deep-link support
    private String chatId;

    public ChatRequestModel() {}

    public ChatRequestModel(String requestId, String senderId, String receiverId, String status, long timestamp) {
        this.requestId = requestId;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.status = status;
        this.timestamp = timestamp;
    }

    public String getRequestId() { return requestId == null ? "" : requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getSenderId() { return senderId == null ? "" : senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getReceiverId() { return receiverId == null ? "" : receiverId; }
    public void setReceiverId(String receiverId) { this.receiverId = receiverId; }

    public String getStatus() { return status == null ? "pending" : status; }
    public void setStatus(String status) { this.status = status; }

    public long getTimestamp() {
        if (timestamp == null) return 0L;
        if (timestamp instanceof Long) return (Long) timestamp;
        if (timestamp instanceof Number) return ((Number) timestamp).longValue();
        if (timestamp instanceof Timestamp) return ((Timestamp) timestamp).toDate().getTime();
        return 0L;
    }

    public Object getTimestampRaw() { return timestamp; }
    public void setTimestamp(Object timestamp) { this.timestamp = timestamp; }

    public String getChatId() { return chatId == null ? "" : chatId; }
    public void setChatId(String chatId) { this.chatId = chatId; }
}
