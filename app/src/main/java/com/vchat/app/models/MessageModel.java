package com.vchat.app.models;

import com.google.firebase.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MessageModel {
    private String messageId;
    private String senderId;
    private String messageText;
    private Object timestamp;
    private String status;      // sent, delivered, seen
    private String messageType; // text, image, contact, poll
    private String mediaUrl;    // image url
    private String mediaName;   // optional filename/title

    // Poll fields
    private String pollQuestion;
    private List<String> pollOptions;
    private Map<String, Integer> pollVotes; // uid -> selected option index
    private Map<String, String> reactions; // uid -> emoji
    private Map<String, Boolean> starredBy; // uid -> true
    private Map<String, Boolean> deletedFor; // uid -> true
    private Map<String, Boolean> deliveredTo; // uid -> true
    private Map<String, Boolean> seenBy; // uid -> true
    private String replyToMessageId;
    private String replyPreview;

    public MessageModel() {}

    public MessageModel(String messageId, String senderId, String messageText, long timestamp, String status, String messageType) {
        this.messageId = messageId;
        this.senderId = senderId;
        this.messageText = messageText;
        this.timestamp = timestamp;
        this.status = status;
        this.messageType = messageType;
    }

    public String getMessageId() { return messageId == null ? "" : messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }

    public String getSenderId() { return senderId == null ? "" : senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getMessageText() { return messageText == null ? "" : messageText; }
    public void setMessageText(String messageText) { this.messageText = messageText; }

    public long getTimestamp() {
        if (timestamp == null) return 0L;
        if (timestamp instanceof Long) return (Long) timestamp;
        if (timestamp instanceof Number) return ((Number) timestamp).longValue();
        if (timestamp instanceof Timestamp) return ((Timestamp) timestamp).toDate().getTime();
        return 0L;
    }

    public Object getTimestampRaw() { return timestamp; }
    public void setTimestamp(Object timestamp) { this.timestamp = timestamp; }

    public String getStatus() { return status == null ? "sent" : status; }
    public void setStatus(String status) { this.status = status; }

    public String getMessageType() { return messageType == null ? "text" : messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }

    public String getMediaUrl() { return mediaUrl == null ? "" : mediaUrl; }
    public void setMediaUrl(String mediaUrl) { this.mediaUrl = mediaUrl; }

    public String getMediaName() { return mediaName == null ? "" : mediaName; }
    public void setMediaName(String mediaName) { this.mediaName = mediaName; }

    public String getPollQuestion() { return pollQuestion == null ? "" : pollQuestion; }
    public void setPollQuestion(String pollQuestion) { this.pollQuestion = pollQuestion; }

    public List<String> getPollOptions() {
        return pollOptions == null ? new ArrayList<>() : pollOptions;
    }
    public void setPollOptions(List<String> pollOptions) { this.pollOptions = pollOptions; }

    public Map<String, Integer> getPollVotes() {
        return pollVotes == null ? new HashMap<>() : pollVotes;
    }
    public void setPollVotes(Map<String, Integer> pollVotes) { this.pollVotes = pollVotes; }

    public int getPollVoteCountForOption(int optionIndex) {
        int count = 0;
        for (Integer selected : getPollVotes().values()) {
            if (selected != null && selected == optionIndex) count++;
        }
        return count;
    }

    public Map<String, String> getReactions() {
        return reactions == null ? new HashMap<>() : reactions;
    }

    public void setReactions(Map<String, String> reactions) { this.reactions = reactions; }

    public Map<String, Boolean> getStarredBy() {
        return starredBy == null ? new HashMap<>() : starredBy;
    }

    public void setStarredBy(Map<String, Boolean> starredBy) { this.starredBy = starredBy; }

    public Map<String, Boolean> getDeletedFor() {
        return deletedFor == null ? new HashMap<>() : deletedFor;
    }

    public void setDeletedFor(Map<String, Boolean> deletedFor) { this.deletedFor = deletedFor; }

    public Map<String, Boolean> getDeliveredTo() {
        return deliveredTo == null ? new HashMap<>() : deliveredTo;
    }

    public void setDeliveredTo(Map<String, Boolean> deliveredTo) { this.deliveredTo = deliveredTo; }

    public Map<String, Boolean> getSeenBy() {
        return seenBy == null ? new HashMap<>() : seenBy;
    }

    public void setSeenBy(Map<String, Boolean> seenBy) { this.seenBy = seenBy; }

    public String getReplyToMessageId() { return replyToMessageId == null ? "" : replyToMessageId; }
    public void setReplyToMessageId(String replyToMessageId) { this.replyToMessageId = replyToMessageId; }

    public String getReplyPreview() { return replyPreview == null ? "" : replyPreview; }
    public void setReplyPreview(String replyPreview) { this.replyPreview = replyPreview; }
}
