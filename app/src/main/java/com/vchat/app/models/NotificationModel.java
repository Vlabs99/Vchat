package com.vchat.app.models;

import com.google.firebase.Timestamp;

public class NotificationModel {
    private String notificationId;
    private String type;        // chat_request, request_accepted
    private String title;
    private String body;
    private String fromUserId;
    private String requestId;
    private String chatId;
    private boolean isGroup;
    private String groupName;
    private boolean isRead;
    private Object createdAt;   // Timestamp or long

    public NotificationModel() {}

    public String getNotificationId() { return notificationId == null ? "" : notificationId; }
    public void setNotificationId(String notificationId) { this.notificationId = notificationId; }

    public String getType() { return type == null ? "" : type; }
    public void setType(String type) { this.type = type; }

    public String getTitle() { return title == null ? "" : title; }
    public void setTitle(String title) { this.title = title; }

    public String getBody() { return body == null ? "" : body; }
    public void setBody(String body) { this.body = body; }

    public String getFromUserId() { return fromUserId == null ? "" : fromUserId; }
    public void setFromUserId(String fromUserId) { this.fromUserId = fromUserId; }

    public String getRequestId() { return requestId == null ? "" : requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getChatId() { return chatId == null ? "" : chatId; }
    public void setChatId(String chatId) { this.chatId = chatId; }

    public boolean isGroup() { return isGroup; }
    public void setGroup(boolean group) { isGroup = group; }

    public String getGroupName() { return groupName == null ? "" : groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }

    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }

    public long getCreatedAtMillis() {
        if (createdAt == null) return 0L;
        if (createdAt instanceof Long) return (Long) createdAt;
        if (createdAt instanceof Number) return ((Number) createdAt).longValue();
        if (createdAt instanceof Timestamp) return ((Timestamp) createdAt).toDate().getTime();
        return 0L;
    }

    public Object getCreatedAtRaw() { return createdAt; }
    public void setCreatedAt(Object createdAt) { this.createdAt = createdAt; }
}
