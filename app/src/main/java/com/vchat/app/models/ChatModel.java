package com.vchat.app.models;

import com.google.firebase.Timestamp;
import java.util.HashMap;
import java.util.Map;

public class ChatModel {
    private String chatId;
    private String chatName;
    private boolean isGroup;
    private Map<String, Boolean> participants;
    private Map<String, Boolean> admins;
    private String lastMessage;

    // Keep flexible for backward compatibility (Long) and future Timestamp migration
    private Object lastMessageTimestamp;
    private Object createdAt;
    private String groupDescription;
    private String groupRules;
    private Map<String, Boolean> groupSettings;
    private Map<String, Boolean> pinnedBy;
    private Map<String, Boolean> archivedBy;
    private Map<String, Boolean> mutedBy;
    private Map<String, Boolean> unreadBy;
    private Map<String, Boolean> deletedFor;
    private Map<String, Boolean> personalPinnedBy;
    private Map<String, Boolean> personalArchivedBy;
    private Map<String, Boolean> personalMutedBy;
    private Map<String, Boolean> personalUnreadBy;
    private Map<String, Boolean> personalDeletedFor;
    private Map<String, Boolean> groupPinnedBy;
    private Map<String, Boolean> groupArchivedBy;
    private Map<String, Boolean> groupMutedBy;
    private Map<String, Boolean> groupUnreadBy;
    private Map<String, Boolean> groupDeletedFor;

    public ChatModel() {}

    public ChatModel(String chatId, Map<String, Boolean> participants, String lastMessage, long lastMessageTimestamp, long createdAt) {
        this.chatId = chatId;
        this.participants = participants;
        this.lastMessage = lastMessage;
        this.lastMessageTimestamp = lastMessageTimestamp;
        this.createdAt = createdAt;
    }

    public String getChatId() { return chatId; }
    public void setChatId(String chatId) { this.chatId = chatId; }

    public String getChatName() { return chatName == null ? "" : chatName; }
    public void setChatName(String chatName) { this.chatName = chatName; }

    public boolean isGroup() { return isGroup; }
    public void setGroup(boolean group) { isGroup = group; }

    public Map<String, Boolean> getParticipants() { return participants; }
    public void setParticipants(Map<String, Boolean> participants) { this.participants = participants; }

    public Map<String, Boolean> getAdmins() { return admins; }
    public void setAdmins(Map<String, Boolean> admins) { this.admins = admins; }

    public String getLastMessage() { return lastMessage == null ? "" : lastMessage; }
    public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }

    public long getLastMessageTimestamp() {
        return parseTimeValue(lastMessageTimestamp);
    }

    public void setLastMessageTimestamp(Object lastMessageTimestamp) {
        this.lastMessageTimestamp = lastMessageTimestamp;
    }

    public long getCreatedAt() {
        return parseTimeValue(createdAt);
    }

    public void setCreatedAt(Object createdAt) {
        this.createdAt = createdAt;
    }

    private long parseTimeValue(Object value) {
        if (value == null) return 0L;

        if (value instanceof Long) {
            return (Long) value;
        }

        if (value instanceof Number) {
            return ((Number) value).longValue();
        }

        if (value instanceof Timestamp) {
            return ((Timestamp) value).toDate().getTime();
        }

        return 0L;
    }

    public String getGroupDescription() { return groupDescription == null ? "" : groupDescription; }
    public void setGroupDescription(String groupDescription) { this.groupDescription = groupDescription; }

    public String getGroupRules() { return groupRules == null ? "" : groupRules; }
    public void setGroupRules(String groupRules) { this.groupRules = groupRules; }

    public Map<String, Boolean> getGroupSettings() {
        return groupSettings == null ? new HashMap<>() : groupSettings;
    }

    public void setGroupSettings(Map<String, Boolean> groupSettings) { this.groupSettings = groupSettings; }

    public Map<String, Boolean> getPinnedBy() { return pinnedBy == null ? new HashMap<>() : pinnedBy; }
    public Map<String, Boolean> getArchivedBy() { return archivedBy == null ? new HashMap<>() : archivedBy; }
    public Map<String, Boolean> getMutedBy() { return mutedBy == null ? new HashMap<>() : mutedBy; }
    public Map<String, Boolean> getUnreadBy() { return unreadBy == null ? new HashMap<>() : unreadBy; }
    public Map<String, Boolean> getDeletedFor() { return deletedFor == null ? new HashMap<>() : deletedFor; }
    public Map<String, Boolean> getPersonalPinnedBy() { return personalPinnedBy == null ? new HashMap<>() : personalPinnedBy; }
    public Map<String, Boolean> getPersonalArchivedBy() { return personalArchivedBy == null ? new HashMap<>() : personalArchivedBy; }
    public Map<String, Boolean> getPersonalMutedBy() { return personalMutedBy == null ? new HashMap<>() : personalMutedBy; }
    public Map<String, Boolean> getPersonalUnreadBy() { return personalUnreadBy == null ? new HashMap<>() : personalUnreadBy; }
    public Map<String, Boolean> getPersonalDeletedFor() { return personalDeletedFor == null ? new HashMap<>() : personalDeletedFor; }
    public Map<String, Boolean> getGroupPinnedBy() { return groupPinnedBy == null ? new HashMap<>() : groupPinnedBy; }
    public Map<String, Boolean> getGroupArchivedBy() { return groupArchivedBy == null ? new HashMap<>() : groupArchivedBy; }
    public Map<String, Boolean> getGroupMutedBy() { return groupMutedBy == null ? new HashMap<>() : groupMutedBy; }
    public Map<String, Boolean> getGroupUnreadBy() { return groupUnreadBy == null ? new HashMap<>() : groupUnreadBy; }
    public Map<String, Boolean> getGroupDeletedFor() { return groupDeletedFor == null ? new HashMap<>() : groupDeletedFor; }
}
