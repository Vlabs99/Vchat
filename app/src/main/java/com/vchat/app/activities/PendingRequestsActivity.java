package com.vchat.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;
import com.vchat.app.R;
import com.vchat.app.adapters.PendingRequestsAdapter;
import com.vchat.app.models.ChatRequestModel;
import com.vchat.app.models.UserModel;
import com.vchat.app.utils.RelationshipStateHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PendingRequestsActivity extends AppCompatActivity {
    private static final String REL_CLEANUP_TAG = "VCHAT_REL_CLEANUP";

    private RecyclerView rvPendingRequests;
    private TextView tvNoRequests;
    private PendingRequestsAdapter adapter;
    private final List<ChatRequestModel> requestList = new ArrayList<>();
    private final List<UserModel> senderList = new ArrayList<>();

    private FirebaseFirestore db;
    private FirebaseUser currentUser;
    private ListenerRegistration requestsListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pending_requests);
        Toolbar toolbar = findViewById(R.id.toolbar_pending_requests);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());

        rvPendingRequests = findViewById(R.id.rv_pending_requests);
        tvNoRequests = findViewById(R.id.tv_no_requests);

        rvPendingRequests.setHasFixedSize(true);
        rvPendingRequests.setLayoutManager(new LinearLayoutManager(this));

        adapter = new PendingRequestsAdapter(requestList, senderList, new PendingRequestsAdapter.OnRequestActionClickListener() {
            @Override
            public void onAcceptClick(ChatRequestModel request) { acceptRequest(request); }
            @Override
            public void onRejectClick(ChatRequestModel request) { rejectRequest(request); }
        });
        rvPendingRequests.setAdapter(adapter);

        db = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser != null) loadPendingRequests(); else finish();
    }

    private void loadPendingRequests() {
        Log.d("VCHAT_TRACE_LOG", "PendingRequestsActivity loadPendingRequests() started");
        requestsListener = db.collection("chat_requests")
                .whereEqualTo("receiverId", currentUser.getUid())
                .whereEqualTo("status", "pending")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.d("VCHAT_TRACE_LOG", "PendingRequestsActivity requestsListener error: " + error.getMessage());
                        Log.e("PendingRequests", "Listen failed.", error);
                        return;
                    }
                    boolean isFromCache = value != null && value.getMetadata() != null && value.getMetadata().isFromCache();
                    Log.d("VCHAT_TRACE_LOG", "PendingRequestsActivity requestsListener update -> Source Method Name: loadPendingRequests"
                        + ", Snapshot size: " + (value != null ? value.size() : 0)
                        + ", Is from cache: " + isFromCache
                        + ", Incoming state: pending"
                        + ", Normalized state: pending"
                        + ", Current cached state: N/A"
                        + ", Whether FRIEND already exists: N/A"
                        + ", Whether pending listener still active: " + (requestsListener != null));

                    requestList.clear();
                    senderList.clear();

                    if (value != null && !value.isEmpty()) {
                        tvNoRequests.setVisibility(View.GONE);
                        rvPendingRequests.setVisibility(View.VISIBLE);

                        for (QueryDocumentSnapshot doc : value) {
                            ChatRequestModel request = doc.toObject(ChatRequestModel.class);
                            if (request.getRequestId().isEmpty()) request.setRequestId(doc.getId());
                            requestList.add(request);
                            senderList.add(new UserModel());

                            int index = requestList.size() - 1;
                            db.collection("users").document(request.getSenderId()).get()
                                    .addOnSuccessListener(documentSnapshot -> {
                                        UserModel sender = documentSnapshot.toObject(UserModel.class);
                                        if (sender != null && index < senderList.size()) {
                                            senderList.set(index, sender);
                                            adapter.notifyItemChanged(index);
                                        }
                                    });
                        }

                        adapter.notifyDataSetChanged();
                    } else {
                        tvNoRequests.setVisibility(View.VISIBLE);
                        rvPendingRequests.setVisibility(View.GONE);
                        adapter.notifyDataSetChanged();
                    }
                });
    }

    private void acceptRequest(ChatRequestModel request) {
        String senderId = request.getSenderId();
        String receiverId = request.getReceiverId();
        String requestId = request.getRequestId();
        Log.d("VCHAT_TRACE_LOG", "PendingRequestsActivity acceptRequest() -> RequestId: " + requestId + ", SenderId: " + senderId + ", ReceiverId: " + receiverId);
        if (senderId.isEmpty() || receiverId.isEmpty() || requestId.isEmpty()) return;

        String chatId = generateChatId(senderId, receiverId);
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
                Toast.makeText(this, "Request is no longer pending", Toast.LENGTH_SHORT).show();
                return;
            }

            db.collection("chats").document(chatId).set(chatData, com.google.firebase.firestore.SetOptions.merge())
                    .addOnSuccessListener(unused -> db.collection("chat_requests").document(requestId)
                            .update("status", "accepted", "chatId", chatId, "updatedAt", FieldValue.serverTimestamp())
                            .addOnSuccessListener(v -> {
                                updateRelationship(senderId, receiverId, RelationshipStateHelper.STATE_FRIEND, "");
                                clearPendingRequestsBetween(senderId, receiverId, requestId);
                                createInAppNotificationForSender(senderId, receiverId, chatId);
                                Toast.makeText(this, "Request accepted", Toast.LENGTH_SHORT).show();
                                Intent intent = new Intent(this, ChatActivity.class);
                                intent.putExtra("chatId", chatId);
                                intent.putExtra("isGroup", false);
                                intent.putExtra("otherUserId", senderId);
                                intent.putExtra("otherUserName", findSenderNameForRequest(requestId));
                                startActivity(intent);
                                finish();
                            }));
        });
    }

    private void rejectRequest(ChatRequestModel request) {
        String requestId = request.getRequestId();
        String senderId = request.getSenderId();
        String receiverId = request.getReceiverId();
        Log.d("VCHAT_TRACE_LOG", "PendingRequestsActivity rejectRequest() -> RequestId: " + requestId + ", SenderId: " + senderId + ", ReceiverId: " + receiverId);
        if (requestId.isEmpty()) return;
        db.collection("chat_requests").document(requestId)
                .update("status", "rejected", "updatedAt", FieldValue.serverTimestamp())
                .addOnSuccessListener(unused -> {
                    if (!senderId.isEmpty() && !receiverId.isEmpty()) {
                        updateRelationship(senderId, receiverId, RelationshipStateHelper.STATE_REMOVED, "");
                    }
                    Toast.makeText(this, "Request rejected", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to reject request", Toast.LENGTH_SHORT).show());
    }

    private void updateRelationship(String uidA, String uidB, String state, String blockedBy) {
        String normalized = RelationshipStateHelper.normalizeState(state);
        String stateToWrite = RelationshipStateHelper.CANON_FRIEND.equals(normalized) ? RelationshipStateHelper.STATE_FRIEND : normalized;
        Map<String, Object> relA = new HashMap<>();
        relA.put("state", stateToWrite);
        relA.put("blockedBy", blockedBy);
        relA.put("updatedAt", FieldValue.serverTimestamp());
        relA.put("peerUid", uidB);
        relA.put("initiatedBy", uidA);

        Map<String, Object> relB = new HashMap<>();
        relB.put("state", stateToWrite);
        relB.put("blockedBy", blockedBy);
        relB.put("updatedAt", FieldValue.serverTimestamp());
        relB.put("peerUid", uidA);
        relB.put("initiatedBy", uidA);

        Log.d(REL_CLEANUP_TAG, "source=PendingRequestsActivity writePayload state=" + stateToWrite
                + " normalizedState=" + normalized + " uidA=" + uidA + " uidB=" + uidB + " blockedBy=" + blockedBy);
        com.google.android.gms.tasks.Task<Void> taskA = db.collection("users").document(uidA).collection("relationships").document(uidB).set(relA)
                .addOnSuccessListener(u -> Log.d(REL_CLEANUP_TAG, "source=PendingRequestsActivity taskA success path=users/" + uidA + "/relationships/" + uidB))
                .addOnFailureListener(e -> Log.e(REL_CLEANUP_TAG, "source=PendingRequestsActivity taskA failure path=users/" + uidA + "/relationships/" + uidB, e));
        com.google.android.gms.tasks.Task<Void> taskB = db.collection("users").document(uidB).collection("relationships").document(uidA).set(relB)
                .addOnSuccessListener(u -> Log.d(REL_CLEANUP_TAG, "source=PendingRequestsActivity taskB success path=users/" + uidB + "/relationships/" + uidA))
                .addOnFailureListener(e -> Log.e(REL_CLEANUP_TAG, "source=PendingRequestsActivity taskB failure path=users/" + uidB + "/relationships/" + uidA, e));
        Log.d(REL_CLEANUP_TAG, "source=PendingRequestsActivity mirroredUpdates pathA=users/" + uidA + "/relationships/" + uidB
                + " pathB=users/" + uidB + "/relationships/" + uidA + " finalUiState=FRIEND");
    }

    private void createInAppNotificationForSender(String senderId, String accepterId, String chatId) {
        Map<String, Object> notif = new HashMap<>();
        notif.put("notificationId", UUID.randomUUID().toString());
        notif.put("type", "request_accepted");
        notif.put("title", "Request accepted");
        notif.put("body", "Your chat request was accepted");
        notif.put("fromUserId", accepterId);
        notif.put("chatId", chatId);
        notif.put("isRead", false);
        notif.put("createdAt", FieldValue.serverTimestamp());
        db.collection("users").document(senderId).collection("notifications").add(notif);
    }

    private String findSenderNameForRequest(String requestId) {
        for (int i = 0; i < requestList.size(); i++) {
            if (requestId.equals(requestList.get(i).getRequestId())) {
                UserModel sender = senderList.get(i);
                if (sender != null && !sender.getUsername().isEmpty()) return sender.getUsername();
            }
        }
        return "Chat";
    }

    private String generateChatId(String uid1, String uid2) {
        return uid1.compareTo(uid2) < 0 ? uid1 + "_" + uid2 : uid2 + "_" + uid1;
    }

    private void clearPendingRequestsBetween(String uidA, String uidB, String activeRequestId) {
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
                        batch.commit().addOnFailureListener(e -> Log.e("PendingRequests", "Failed clearing pending requests", e));
                    }
                })
                .addOnFailureListener(e -> Log.e("PendingRequests", "Querying pending requests failed", e));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (requestsListener != null) requestsListener.remove();
    }
}
