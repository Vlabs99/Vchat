package com.vchat.app.models;

import com.google.firebase.Timestamp;

public class UserModel {
    private String uid;
    private String username;
    private String email;
    private String profileImage;
    private String bio;
    private boolean isOnline;
    private Object lastSeen; // supports both old long and new Firestore Timestamp
    private String typingTo;
    private String friendshipState;
    private String stateLabel;

    public UserModel() {}

    public UserModel(String uid, String username, String email) {
        this.uid = uid;
        this.username = username;
        this.email = email;
        this.profileImage = "";
        this.bio = "Hey there! I am using VChat.";
        this.isOnline = true;
        this.lastSeen = Timestamp.now();
        this.typingTo = "";
    }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getProfileImage() { return profileImage; }
    public void setProfileImage(String profileImage) { this.profileImage = profileImage; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public boolean isOnline() { return isOnline; }
    public void setOnline(boolean online) { isOnline = online; }

    // Keep this method name same so existing UI code continues to work
    public long getLastSeen() {
        if (lastSeen == null) return 0L;

        if (lastSeen instanceof Timestamp) {
            return ((Timestamp) lastSeen).toDate().getTime();
        }

        if (lastSeen instanceof Long) {
            return (Long) lastSeen;
        }

        if (lastSeen instanceof Number) {
            return ((Number) lastSeen).longValue();
        }

        return 0L;
    }

    public Object getLastSeenRaw() { return lastSeen; }

    public void setLastSeen(Object lastSeen) { this.lastSeen = lastSeen; }

    public String getTypingTo() { return typingTo == null ? "" : typingTo; }
    public void setTypingTo(String typingTo) { this.typingTo = typingTo; }

    public String getFriendshipState() { return friendshipState == null ? "" : friendshipState; }
    public void setFriendshipState(String friendshipState) { this.friendshipState = friendshipState; }

    public String getStateLabel() { return stateLabel == null ? "" : stateLabel; }
    public void setStateLabel(String stateLabel) { this.stateLabel = stateLabel; }
}
