package com.vchat.app.data;

import android.util.Log;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.vchat.app.models.UserModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GroupRepository {
    private static final String TAG_GROUP_CREATE = "VCHAT_GROUP_CREATE";

    public interface MembersResolvedCallback {
        void onSuccess(List<String> memberUids);
        void onError(String message);
    }

    public interface FriendsCallback {
        void onSuccess(List<UserModel> users);
        void onError(String message);
    }

    private final FirebaseFirestore db;

    public GroupRepository() {
        this.db = FirebaseFirestore.getInstance();
    }

    public ListenerRegistration listenMyGroups(@NonNull String uid,
                                               @NonNull com.google.firebase.firestore.EventListener<com.google.firebase.firestore.QuerySnapshot> listener) {
        return db.collection("chats")
                .whereEqualTo("isGroup", true)
                .whereEqualTo("participants." + uid, true)
                .addSnapshotListener(listener);
    }

    public void resolveMembersByEmails(@NonNull List<String> emails, @NonNull MembersResolvedCallback callback) {
        Log.d(TAG_GROUP_CREATE, "resolveMembersByEmails start emails=" + emails);
        if (emails.isEmpty()) {
            Log.d(TAG_GROUP_CREATE, "resolveMembersByEmails no emails; returning empty");
            callback.onSuccess(new ArrayList<>());
            return;
        }

        List<Task<com.google.firebase.firestore.QuerySnapshot>> tasks = new ArrayList<>();
        for (String email : emails) {
            if (TextUtils.isEmpty(email)) continue;
            tasks.add(db.collection("users").whereEqualTo("email", email.trim().toLowerCase()).limit(1).get());
        }

        if (tasks.isEmpty()) {
            Log.e(TAG_GROUP_CREATE, "resolveMembersByEmails no valid emails after filtering");
            callback.onError("No valid emails provided");
            return;
        }

        Tasks.whenAllComplete(tasks).addOnSuccessListener(done -> {
            List<String> uids = new ArrayList<>();
            for (Task<com.google.firebase.firestore.QuerySnapshot> t : tasks) {
                if (!t.isSuccessful() || t.getResult() == null || t.getResult().isEmpty()) continue;
                DocumentSnapshot doc = t.getResult().getDocuments().get(0);
                String uid = doc.getString("uid");
                if (!TextUtils.isEmpty(uid) && !uids.contains(uid)) uids.add(uid);
            }
            Log.d(TAG_GROUP_CREATE, "resolveMembersByEmails success resolvedUids=" + uids);
            callback.onSuccess(uids);
        }).addOnFailureListener(e -> {
            Log.e(TAG_GROUP_CREATE, "resolveMembersByEmails failure: " + Log.getStackTraceString(e));
            callback.onError("Failed to resolve users");
        });
    }

    public Task<Void> createGroup(@NonNull String ownerUid,
                                  @NonNull String groupName,
                                  @NonNull List<String> memberUids) {
        String chatId = UUID.randomUUID().toString();
        Map<String, Boolean> participants = new HashMap<>();
        participants.put(ownerUid, true);
        for (String uid : memberUids) {
            if (!TextUtils.isEmpty(uid)) participants.put(uid, true);
        }

        Map<String, Boolean> admins = new HashMap<>();
        admins.put(ownerUid, true);

        Map<String, Object> group = new HashMap<>();
        group.put("chatId", chatId);
        group.put("chatName", groupName);
        group.put("isGroup", true);
        group.put("participants", participants);
        group.put("admins", admins);
        group.put("createdBy", ownerUid);
        group.put("createdAt", FieldValue.serverTimestamp());
        group.put("lastMessage", "");
        group.put("lastMessageTimestamp", 0L);
        group.put("pinnedMessageId", "");
        group.put("pinnedMessageText", "");
        group.put("groupDescription", "");
        group.put("groupRules", "");
        Map<String, Boolean> settings = new HashMap<>();
        settings.put("onlyAdminsCanEditGroupInfo", true);
        group.put("groupSettings", settings);

        Log.d(TAG_GROUP_CREATE, "createGroup currentUserId=" + ownerUid);
        Log.d(TAG_GROUP_CREATE, "createGroup selectedMembers=" + memberUids);
        Log.d(TAG_GROUP_CREATE, "createGroup generatedGroupId=" + chatId);
        Log.d(TAG_GROUP_CREATE, "createGroup participantMap=" + participants);
        Log.d(TAG_GROUP_CREATE, "createGroup payload=" + group);
        Log.d(TAG_GROUP_CREATE, "createGroup firestoreWriteStart path=chats/" + chatId);

        return db.collection("chats").document(chatId).set(group)
                .addOnSuccessListener(unused ->
                        Log.d(TAG_GROUP_CREATE, "createGroup firestoreWriteSuccess path=chats/" + chatId))
                .addOnFailureListener(e ->
                        Log.e(TAG_GROUP_CREATE, "createGroup firestoreWriteFailure path=chats/" + chatId
                                + " exception=" + Log.getStackTraceString(e)));
    }

    public Task<Void> renameGroup(@NonNull String chatId, @NonNull String name) {
        return db.collection("chats").document(chatId).update("chatName", name);
    }

    public Task<Void> addMember(@NonNull String chatId, @NonNull String uid) {
        return db.collection("chats").document(chatId).update("participants." + uid, true);
    }

    public Task<Void> removeMember(@NonNull String chatId, @NonNull String uid) {
        return db.collection("chats").document(chatId).update("participants." + uid, FieldValue.delete(), "admins." + uid, FieldValue.delete());
    }

    public Task<Void> setAdmin(@NonNull String chatId, @NonNull String uid, boolean isAdmin) {
        if (isAdmin) {
            return db.collection("chats").document(chatId).update("admins." + uid, true);
        }
        return db.collection("chats").document(chatId).update("admins." + uid, FieldValue.delete());
    }

    public Task<Void> updateGroupMeta(@NonNull String chatId, @NonNull String name, @NonNull String description, @NonNull String rules) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("chatName", name);
        updates.put("groupDescription", description);
        updates.put("groupRules", rules);
        return db.collection("chats").document(chatId).update(updates);
    }

    public Task<Void> setAdminOnlyMessaging(@NonNull String chatId, boolean enabled) {
        return db.collection("chats").document(chatId).update("groupSettings.onlyAdminsCanMessage", enabled);
    }

    public Task<Void> setGroupMutedForUser(@NonNull String chatId, @NonNull String uid, boolean muted) {
        if (muted) {
            return db.collection("chats").document(chatId).update("mutedBy." + uid, true);
        }
        return db.collection("chats").document(chatId).update("mutedBy." + uid, FieldValue.delete());
    }

    public Task<Void> leaveGroup(@NonNull String chatId, @NonNull String uid) {
        return removeMember(chatId, uid);
    }

    public Task<Void> sendGroupSystemMessage(String chatId, String senderId, String messageText) {
        String messageId = UUID.randomUUID().toString();
        long timestamp = System.currentTimeMillis();

        Map<String, Object> message = new HashMap<>();
        message.put("messageId", messageId);
        message.put("senderId", TextUtils.isEmpty(senderId) ? "system" : senderId);
        message.put("messageText", messageText);
        message.put("timestamp", timestamp);
        message.put("status", "sent");
        message.put("messageType", "system");

        return db.collection("chats").document(chatId).collection("messages").document(messageId).set(message)
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) return Tasks.forException(task.getException());
                    
                    return db.collection("chats").document(chatId).get().continueWithTask(chatTask -> {
                        if (!chatTask.isSuccessful() || chatTask.getResult() == null) {
                            return Tasks.forResult(null);
                        }
                        DocumentSnapshot chatSnap = chatTask.getResult();
                        Map<String, Boolean> participants = (Map<String, Boolean>) chatSnap.get("participants");
                        
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("lastMessage", messageText);
                        updates.put("lastMessageTimestamp", timestamp);
                        
                        if (participants != null) {
                            for (String uid : participants.keySet()) {
                                updates.put("personalDeletedFor." + uid, FieldValue.delete());
                                updates.put("deletedFor." + uid, FieldValue.delete());
                                updates.put("groupDeletedFor." + uid, FieldValue.delete());
                            }
                        }
                        return db.collection("chats").document(chatId).update(updates);
                    });
                });
    }

    public void fetchConnectedFriends(@NonNull String uid, @NonNull FriendsCallback callback) {
        Log.d(TAG_GROUP_CREATE, "fetchConnectedFriends start currentUserId=" + uid);
        Task<com.google.firebase.firestore.QuerySnapshot> sentTask = db.collection("chat_requests")
                .whereEqualTo("status", "accepted")
                .whereEqualTo("senderId", uid)
                .get();
        Task<com.google.firebase.firestore.QuerySnapshot> receivedTask = db.collection("chat_requests")
                .whereEqualTo("status", "accepted")
                .whereEqualTo("receiverId", uid)
                .get();

        Tasks.whenAllComplete(sentTask, receivedTask)
                .addOnSuccessListener(done -> {
                    List<String> friendUids = new ArrayList<>();

                    if (sentTask.isSuccessful() && sentTask.getResult() != null) {
                        for (DocumentSnapshot doc : sentTask.getResult().getDocuments()) {
                            String receiver = doc.getString("receiverId");
                            if (!TextUtils.isEmpty(receiver) && !friendUids.contains(receiver)) friendUids.add(receiver);
                        }
                    }

                    if (receivedTask.isSuccessful() && receivedTask.getResult() != null) {
                        for (DocumentSnapshot doc : receivedTask.getResult().getDocuments()) {
                            String sender = doc.getString("senderId");
                            if (!TextUtils.isEmpty(sender) && !friendUids.contains(sender)) friendUids.add(sender);
                        }
                    }

                    Log.d(TAG_GROUP_CREATE, "fetchConnectedFriends resolvedFriendUids=" + friendUids);
                    if (friendUids.isEmpty()) {
                        callback.onSuccess(new ArrayList<>());
                        return;
                    }

                    List<Task<DocumentSnapshot>> tasks = new ArrayList<>();
                    for (String fuid : friendUids) {
                        tasks.add(db.collection("users").document(fuid).get());
                    }
                    Tasks.whenAllComplete(tasks).addOnSuccessListener(fetchDone -> {
                        List<UserModel> users = new ArrayList<>();
                        for (Task<DocumentSnapshot> task : tasks) {
                            if (!task.isSuccessful() || task.getResult() == null || !task.getResult().exists()) continue;
                            UserModel user = task.getResult().toObject(UserModel.class);
                            if (user != null) users.add(user);
                        }
                        Log.d(TAG_GROUP_CREATE, "fetchConnectedFriends success usersCount=" + users.size());
                        callback.onSuccess(users);
                    }).addOnFailureListener(e -> {
                        Log.e(TAG_GROUP_CREATE, "fetchConnectedFriends usersFetchFailure: " + Log.getStackTraceString(e));
                        callback.onError("Failed to fetch friends");
                    });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG_GROUP_CREATE, "fetchConnectedFriends queryFailure: " + Log.getStackTraceString(e));
                    callback.onError("Failed to fetch connections");
                });
    }
}
