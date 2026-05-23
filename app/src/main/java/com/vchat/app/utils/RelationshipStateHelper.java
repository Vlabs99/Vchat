package com.vchat.app.utils;

import android.text.TextUtils;
import android.util.Log;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.vchat.app.models.UserModel;

public final class RelationshipStateHelper {
    private static final String REL_CLEANUP_TAG = "VCHAT_REL_CLEANUP";
    public static final String CANON_FRIEND = "friend";
    public static final String CANON_PENDING = "pending";
    public static final String CANON_REMOVED = "removed";
    public static final String CANON_BLOCKED = "blocked";
    public static final String CANON_BLOCKED_BY_USER = "blocked_by_user";
    public static final String CANON_UNKNOWN = "unknown";

    public static final String STATE_FRIEND = "friend";
    public static final String STATE_FRIENDS = "friends";
    public static final String STATE_PENDING = "pending";
    public static final String STATE_REMOVED = "removed";
    public static final String STATE_BLOCKED = "blocked";

    public static final String VIEW_FRIENDS = "friends";
    public static final String VIEW_PENDING_OUT = "pending_outgoing";
    public static final String VIEW_PENDING_IN = "pending_incoming";
    public static final String VIEW_REMOVED = "removed";
    public static final String VIEW_BLOCKED_BY_ME = "blocked_by_me";
    public static final String VIEW_BLOCKED_ME = "blocked_me";

    private RelationshipStateHelper() {}

    public static String normalizeState(String rawState) {
        if (rawState == null) return CANON_UNKNOWN;
        String value = rawState.trim().toLowerCase();
        if (TextUtils.isEmpty(value) || "unavailable".equals(value) || "unknown".equals(value)) return CANON_UNKNOWN;
        if (STATE_FRIEND.equals(value) || STATE_FRIENDS.equals(value)) return CANON_FRIEND;
        if (STATE_PENDING.equals(value)) return CANON_PENDING;
        if (STATE_REMOVED.equals(value)) return CANON_REMOVED;
        if (STATE_BLOCKED.equals(value) || "blocked_by_me".equals(value)) return CANON_BLOCKED;
        if ("blocked_by_user".equals(value) || "blocked_me".equals(value)) return CANON_BLOCKED_BY_USER;
        return CANON_UNKNOWN;
    }

    public static String resolveCanonicalState(String rawState, String blockedBy, String myUid) {
        String normalized = normalizeState(rawState);
        if (CANON_BLOCKED.equals(normalized)) {
            if (!TextUtils.isEmpty(myUid) && TextUtils.equals(myUid, blockedBy)) {
                return CANON_BLOCKED;
            }
            return CANON_BLOCKED_BY_USER;
        }
        return normalized;
    }

    public static void bindRelationship(UserModel user, FirebaseUser currentUser, DocumentSnapshot snapshot) {
        if (user == null || currentUser == null) return;
        String state = snapshot != null ? snapshot.getString("state") : "";
        String blockedBy = snapshot != null ? snapshot.getString("blockedBy") : "";
        String initiatedBy = snapshot != null ? snapshot.getString("initiatedBy") : "";
        String myUid = currentUser.getUid();
        String canonicalState = resolveCanonicalState(state, blockedBy, myUid);
        applyCanonicalToUser(user, canonicalState, myUid, initiatedBy);
        Log.d(REL_CLEANUP_TAG, "source=RelationshipStateHelper rawDbState=" + state + " normalizedState=" + canonicalState
                + " finalUiState=" + user.getStateLabel() + " peerUid=" + user.getUid());
    }

    public static void applyCanonicalToUser(UserModel user, String canonicalState, String myUid, String initiatedBy) {
        if (user == null) return;
        if (CANON_FRIEND.equals(canonicalState)) {
            user.setFriendshipState(VIEW_FRIENDS);
            user.setStateLabel("FRIEND");
            return;
        }
        if (CANON_PENDING.equals(canonicalState)) {
            if (TextUtils.equals(myUid, initiatedBy)) {
                user.setFriendshipState(VIEW_PENDING_OUT);
                user.setStateLabel("REQUEST PENDING");
            } else {
                user.setFriendshipState(VIEW_PENDING_IN);
                user.setStateLabel("REQUEST PENDING");
            }
            return;
        }
        if (CANON_REMOVED.equals(canonicalState)) {
            user.setFriendshipState(VIEW_REMOVED);
            user.setStateLabel("NOT FRIENDS");
            return;
        }
        if (CANON_BLOCKED.equals(canonicalState)) {
            user.setFriendshipState(VIEW_BLOCKED_BY_ME);
            user.setStateLabel("BLOCKED");
            return;
        }
        if (CANON_BLOCKED_BY_USER.equals(canonicalState)) {
            user.setFriendshipState(VIEW_BLOCKED_ME);
            user.setStateLabel("BLOCKED YOU");
            return;
        }
        user.setFriendshipState(VIEW_REMOVED);
        user.setStateLabel("NOT FRIENDS");
    }
}
