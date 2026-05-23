package com.vchat.app.models;

import java.util.HashMap;
import java.util.Map;

public class GroupModel {
    private String chatId;
    private String chatName;
    private Map<String, Boolean> participants;
    private Map<String, Boolean> admins;
    private boolean isGroup;
    private String createdBy;

    public GroupModel() {}

    public String getChatId() { return chatId == null ? "" : chatId; }
    public void setChatId(String chatId) { this.chatId = chatId; }

    public String getChatName() { return chatName == null ? "" : chatName; }
    public void setChatName(String chatName) { this.chatName = chatName; }

    public Map<String, Boolean> getParticipants() { return participants == null ? new HashMap<>() : participants; }
    public void setParticipants(Map<String, Boolean> participants) { this.participants = participants; }

    public Map<String, Boolean> getAdmins() { return admins == null ? new HashMap<>() : admins; }
    public void setAdmins(Map<String, Boolean> admins) { this.admins = admins; }

    public boolean isGroup() { return isGroup; }
    public void setGroup(boolean group) { isGroup = group; }

    public String getCreatedBy() { return createdBy == null ? "" : createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
