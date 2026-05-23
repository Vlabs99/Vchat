package com.vchat.app.notifications;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;
import com.vchat.app.utils.RelationshipStateHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class NotificationActionReceiver extends BroadcastReceiver {
    private static final String REL_CLEANUP_TAG = "VCHAT_REL_CLEANUP";

    public static final String ACTION_ACCEPT_REQUEST = "com.vchat.app.ACTION_ACCEPT_REQUEST";
    public static final String ACTION_DECLINE_REQUEST = "com.vchat.app.ACTION_DECLINE_REQUEST";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;

        String requestId = intent.getStringExtra("requestId");
        String senderId = intent.getStringExtra("senderId");

        if (TextUtils.isEmpty(requestId) || TextUtils.isEmpty(senderId)) return;

        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) return;

        String receiverId = auth.getCurrentUser().getUid();
        String action = intent.getAction();

        if (ACTION_ACCEPT_REQUEST.equals(action)) {
            acceptRequest(context, requestId, senderId, receiverId);
        } else if (ACTION_DECLINE_REQUEST.equals(action)) {
            declineRequest(context, requestId);
        }
    }

    private void declineRequest(Context context, String requestId) {
        Log.d("VCHAT_TRACE_LOG", "NotificationActionReceiver declineRequest() -> RequestId: " + requestId);
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("chat_requests")
                .document(requestId)
                .get()
                .addOnSuccessListener(doc -> {
                    String senderId = doc != null ? doc.getString("senderId") : "";
                    String receiverId = doc != null ? doc.getString("receiverId") : "";
                    if (!TextUtils.isEmpty(senderId) && !TextUtils.isEmpty(receiverId)) {
                        Map<String, Object> a = new HashMap<>();
                        a.put("state", RelationshipStateHelper.STATE_REMOVED);
                        a.put("blockedBy", "");
                        a.put("peerUid", receiverId);
                        a.put("initiatedBy", receiverId);
                        a.put("updatedAt", FieldValue.serverTimestamp());
                        Map<String, Object> b = new HashMap<>();
                        b.put("state", RelationshipStateHelper.STATE_REMOVED);
                        b.put("blockedBy", "");
                        b.put("peerUid", senderId);
                        b.put("initiatedBy", receiverId);
                        b.put("updatedAt", FieldValue.serverTimestamp());
                        Log.d(REL_CLEANUP_TAG, "source=NotificationActionReceiver writePayload state=" + RelationshipStateHelper.STATE_REMOVED
                                + " normalizedState=" + RelationshipStateHelper.CANON_REMOVED + " uidA=" + senderId + " uidB=" + receiverId + " blockedBy=");
                        db.collection("users").document(senderId).collection("relationships").document(receiverId).set(a);
                        db.collection("users").document(receiverId).collection("relationships").document(senderId).set(b);
                        Log.d(REL_CLEANUP_TAG, "source=NotificationActionReceiver mirroredUpdates pathA=users/" + senderId + "/relationships/" + receiverId
                                + " pathB=users/" + receiverId + "/relationships/" + senderId + " finalUiState=NOT FRIENDS");
                    }
                });
        db
                .collection("chat_requests")
                .document(requestId)
                .update(
                        "status", "rejected",
                        "updatedAt", FieldValue.serverTimestamp()
                )
                .addOnSuccessListener(unused ->
                        Toast.makeText(context, "Request declined", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Log.e("NotifActionReceiver", "Decline failed", e));
    }

    private void acceptRequest(Context context, String requestId, String senderId, String receiverId) {
        Log.d("VCHAT_TRACE_LOG", "NotificationActionReceiver acceptRequest() -> RequestId: " + requestId + ", SenderId: " + senderId + ", ReceiverId: " + receiverId);
        String chatId = generateChatId(senderId, receiverId);
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        Map<String, Object> chatData = new HashMap<>();
        Map<String, Boolean> participants = new HashMap<>();
        participants.put(senderId, true);
        participants.put(receiverId, true);

        chatData.put("chatId", chatId);
        chatData.put("isGroup", false);
        chatData.put("participants", participants);
        chatData.put("createdAt", System.currentTimeMillis());
        chatData.put("lastMessage", "");
        chatData.put("lastMessageTimestamp", 0L);

        db.collection("chat_requests").document(requestId).get().addOnSuccessListener(latest -> {
            String latestStatus = latest != null ? latest.getString("status") : "";
            if (!"pending".equals(latestStatus)) {
                return;
            }

            db.collection("chats")
                    .document(chatId)
                    .set(chatData, com.google.firebase.firestore.SetOptions.merge())
                    .addOnSuccessListener(unused -> db.collection("chat_requests")
                            .document(requestId)
                            .update(
                                     "status", "accepted",
                                     "chatId", chatId,
                                     "updatedAt", FieldValue.serverTimestamp()
                            )
                            .addOnSuccessListener(v -> {
                                updateRelationshipOnAccept(senderId, receiverId);
                                clearPendingRequestsBetween(senderId, receiverId, requestId);
                                createInAppNotificationForSender(senderId, receiverId, chatId);
                                Toast.makeText(context, "Request accepted", Toast.LENGTH_SHORT).show();
                            })
                            .addOnFailureListener(e ->
                                    Log.e("NotifActionReceiver", "Request accept update failed", e)))
                    .addOnFailureListener(e ->
                            Log.e("NotifActionReceiver", "Chat creation failed", e));
        });
    }

    private void createInAppNotificationForSender(String senderId, String accepterId, String chatId) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        Map<String, Object> notif = new HashMap<>();
        notif.put("notificationId", UUID.randomUUID().toString());
        notif.put("type", "request_accepted");
        notif.put("title", "Request accepted");
        notif.put("body", "Your chat request was accepted");
        notif.put("fromUserId", accepterId);
        notif.put("chatId", chatId);
        notif.put("isRead", false);
        notif.put("createdAt", FieldValue.serverTimestamp());

        db.collection("users")
                .document(senderId)
                .collection("notifications")
                .add(notif)
                .addOnFailureListener(e ->
                        Log.e("NotifActionReceiver", "In-app sender notification failed", e));
    }

    private void updateRelationshipOnAccept(String senderId, String receiverId) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        Map<String, Object> senderRel = new HashMap<>();
        senderRel.put("state", RelationshipStateHelper.STATE_FRIEND);
        senderRel.put("blockedBy", "");
        senderRel.put("peerUid", receiverId);
        senderRel.put("initiatedBy", senderId);
        senderRel.put("updatedAt", FieldValue.serverTimestamp());

        Map<String, Object> receiverRel = new HashMap<>();
        receiverRel.put("state", RelationshipStateHelper.STATE_FRIEND);
        receiverRel.put("blockedBy", "");
        receiverRel.put("peerUid", senderId);
        receiverRel.put("initiatedBy", senderId);
        receiverRel.put("updatedAt", FieldValue.serverTimestamp());

        Log.d(REL_CLEANUP_TAG, "source=NotificationActionReceiver writePayload state=" + RelationshipStateHelper.STATE_FRIEND
                + " normalizedState=" + RelationshipStateHelper.CANON_FRIEND + " uidA=" + senderId + " uidB=" + receiverId + " blockedBy=");
        com.google.android.gms.tasks.Task<Void> taskA = db.collection("users").document(senderId).collection("relationships").document(receiverId).set(senderRel)
                .addOnSuccessListener(u -> Log.d(REL_CLEANUP_TAG, "source=NotificationActionReceiver taskA success path=users/" + senderId + "/relationships/" + receiverId))
                .addOnFailureListener(e -> Log.e(REL_CLEANUP_TAG, "source=NotificationActionReceiver taskA failure path=users/" + senderId + "/relationships/" + receiverId, e));
        com.google.android.gms.tasks.Task<Void> taskB = db.collection("users").document(receiverId).collection("relationships").document(senderId).set(receiverRel)
                .addOnSuccessListener(u -> Log.d(REL_CLEANUP_TAG, "source=NotificationActionReceiver taskB success path=users/" + receiverId + "/relationships/" + senderId))
                .addOnFailureListener(e -> Log.e(REL_CLEANUP_TAG, "source=NotificationActionReceiver taskB failure path=users/" + receiverId + "/relationships/" + senderId, e));
        Log.d(REL_CLEANUP_TAG, "source=NotificationActionReceiver mirroredUpdates pathA=users/" + senderId + "/relationships/" + receiverId
                + " pathB=users/" + receiverId + "/relationships/" + senderId + " finalUiState=FRIEND");
    }

    private void clearPendingRequestsBetween(String uidA, String uidB, String activeRequestId) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        com.google.android.gms.tasks.Task<com.google.firebase.firestore.QuerySnapshot> task1 = db.collection("chat_requests")
                .whereEqualTo("status", "pending")
                .whereEqualTo("senderId", uidA)
                .whereEqualTo("receiverId", uidB)
                .get();

        com.google.android.gms.tasks.Task<com.google.firebase.firestore.QuerySnapshot> task2 = db.collection("chat_requests")
                .whereEqualTo("status", "pending")
                .whereEqualTo("senderId", uidB)
                .whereEqualTo("receiverId", uidA)
                .get();

        com.google.android.gms.tasks.Tasks.whenAllSuccess(task1, task2)
                .addOnSuccessListener(results -> {
                    WriteBatch batch = db.batch();
                    boolean hasUpdates = false;
                    for (Object res : results) {
                        com.google.firebase.firestore.QuerySnapshot snapshots = (com.google.firebase.firestore.QuerySnapshot) res;
                        for (com.google.firebase.firestore.QueryDocumentSnapshot doc : snapshots) {
                            if (doc.getId().equals(activeRequestId)) continue;
                            batch.update(doc.getReference(), "status", "cancelled", "updatedAt", FieldValue.serverTimestamp());
                            hasUpdates = true;
                        }
                    }
                    if (hasUpdates) {
                        batch.commit().addOnFailureListener(e -> Log.e("NotifActionReceiver", "Failed clearing pending requests", e));
                    }
                })
                .addOnFailureListener(e -> Log.e("NotifActionReceiver", "Querying pending requests failed", e));
    }

    @NonNull
    private String generateChatId(@NonNull String uid1, @NonNull String uid2) {
        return (uid1.compareTo(uid2) < 0) ? uid1 + "_" + uid2 : uid2 + "_" + uid1;
    }
}
