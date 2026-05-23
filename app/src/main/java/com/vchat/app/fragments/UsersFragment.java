package com.vchat.app.fragments;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.vchat.app.R;
import com.vchat.app.adapters.UsersAdapter;
import com.vchat.app.models.ChatRequestModel;
import com.vchat.app.models.UserModel;
import com.vchat.app.utils.RelationshipStateHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class UsersFragment extends Fragment {
    private static final String REL_CLEANUP_TAG = "VCHAT_REL_CLEANUP";

    private EditText etSearchEmail;
    private ImageButton btnSearch;
    private RecyclerView usersRecyclerView;

    private UsersAdapter usersAdapter;
    private List<UserModel> userList;

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private final Map<String, ListenerRegistration> relationshipListeners = new HashMap<>();

    public UsersFragment() {}

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_users, container, false);

        etSearchEmail = view.findViewById(R.id.et_search_email);
        btnSearch = view.findViewById(R.id.btn_search);
        usersRecyclerView = view.findViewById(R.id.users_recycler_view);

        usersRecyclerView.setHasFixedSize(true);
        usersRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        userList = new ArrayList<>();
        usersAdapter = new UsersAdapter(userList, this::sendChatRequest);
        usersRecyclerView.setAdapter(usersAdapter);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        btnSearch.setOnClickListener(v -> searchUserByEmail());

        return view;
    }

    private void searchUserByEmail() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(getContext(), "Please login again", Toast.LENGTH_SHORT).show();
            return;
        }

        String email = etSearchEmail.getText().toString().trim().toLowerCase(Locale.ROOT);
        if (TextUtils.isEmpty(email)) {
            Toast.makeText(getContext(), "Please enter an email", Toast.LENGTH_SHORT).show();
            return;
        }

        String myEmail = currentUser.getEmail() == null ? "" : currentUser.getEmail().trim().toLowerCase(Locale.ROOT);
        if (email.equals(myEmail)) {
            Toast.makeText(getContext(), "You cannot search for your own email", Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("users")
                .whereEqualTo("email", email)
                .get()
                .addOnCompleteListener(task -> {
                    if (!isAdded()) return;

                    if (task.isSuccessful()) {
                        clearRelationshipListeners();
                        userList.clear();

                        for (QueryDocumentSnapshot document : task.getResult()) {
                            UserModel user = document.toObject(UserModel.class);
                            if (user.getUid() != null && user.getUid().equals(currentUser.getUid())) continue;
                            userList.add(user);
                            enrichRelationshipState(user, currentUser.getUid());
                        }

                        usersAdapter.notifyDataSetChanged();
                        if (userList.isEmpty()) {
                            Toast.makeText(getContext(), "No user found with this email", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Log.e("UsersFragment", "Error searching user", task.getException());
                        Toast.makeText(getContext(), "Error searching user", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void sendChatRequest(UserModel receiver) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null || receiver == null || TextUtils.isEmpty(receiver.getUid())) return;

        if (RelationshipStateHelper.VIEW_BLOCKED_BY_ME.equals(receiver.getFriendshipState())) {
            unblockUser(receiver);
            return;
        }
        if (RelationshipStateHelper.VIEW_BLOCKED_ME.equals(receiver.getFriendshipState())) {
            Toast.makeText(getContext(), "You are blocked", Toast.LENGTH_SHORT).show();
            return;
        }
        if (RelationshipStateHelper.VIEW_FRIENDS.equals(receiver.getFriendshipState())) {
            Toast.makeText(getContext(), "Already friends", Toast.LENGTH_SHORT).show();
            return;
        }
        if (RelationshipStateHelper.VIEW_PENDING_OUT.equals(receiver.getFriendshipState())) {
            Toast.makeText(getContext(), "Request already pending", Toast.LENGTH_SHORT).show();
            return;
        }

        String senderId = currentUser.getUid();
        String receiverId = receiver.getUid();

        if (senderId.equals(receiverId)) {
            Toast.makeText(getContext(), "Invalid request", Toast.LENGTH_SHORT).show();
            return;
        }

        createNewRequest(senderId, receiverId, currentUser.getDisplayName(), currentUser.getEmail(), receiver.getUsername());
    }

    private void createNewRequest(String senderId, String receiverId, String senderDisplayName, String senderEmail, String receiverName) {
        String requestId = UUID.randomUUID().toString();

        ChatRequestModel request = new ChatRequestModel(requestId, senderId, receiverId, "pending", System.currentTimeMillis());

        db.collection("chat_requests")
                .document(requestId)
                .set(request)
                .addOnSuccessListener(aVoid -> {
                    setPendingRelationshipState(senderId, receiverId);
                    createInAppNotificationForReceiver(requestId, senderId, receiverId, senderDisplayName, senderEmail);
                    if (!isAdded()) return;
                    Toast.makeText(getContext(), "Chat request sent!", Toast.LENGTH_SHORT).show();
                    userList.clear();
                    usersAdapter.notifyDataSetChanged();
                    etSearchEmail.setText("");
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    Log.e("UsersFragment", "Error sending request", e);
                    Toast.makeText(getContext(), "Failed to send request", Toast.LENGTH_SHORT).show();
                });
    }

    private void createInAppNotificationForReceiver(String requestId, String senderId, String receiverId, String senderDisplayName, String senderEmail) {
        String senderName = !TextUtils.isEmpty(senderDisplayName) ? senderDisplayName : (!TextUtils.isEmpty(senderEmail) ? senderEmail : "Someone");

        Map<String, Object> notif = new HashMap<>();
        notif.put("notificationId", UUID.randomUUID().toString());
        notif.put("type", "chat_request");
        notif.put("title", "New chat request");
        notif.put("body", senderName + " sent you a chat request");
        notif.put("requestId", requestId);
        notif.put("fromUserId", senderId);
        notif.put("isRead", false);
        notif.put("createdAt", FieldValue.serverTimestamp());

        db.collection("users")
                .document(receiverId)
                .collection("notifications")
                .add(notif)
                .addOnFailureListener(e -> Log.e("UsersFragment", "Failed to create in-app notification", e));
    }

    private void enrichRelationshipState(UserModel user, String myUid) {
        if (user == null || TextUtils.isEmpty(user.getUid()) || TextUtils.isEmpty(myUid)) return;
        String otherUid = user.getUid();
        ListenerRegistration existing = relationshipListeners.remove(otherUid);
        if (existing != null) existing.remove();
        ListenerRegistration registration = db.collection("users").document(myUid).collection("relationships").document(otherUid)
                .addSnapshotListener((snapshot, error) -> {
                    if (!isAdded() || error != null) return;
                    RelationshipStateHelper.bindRelationship(user, auth.getCurrentUser(), snapshot);
                    usersAdapter.notifyDataSetChanged();
                });
        relationshipListeners.put(otherUid, registration);
    }

    private void clearRelationshipListeners() {
        for (ListenerRegistration registration : relationshipListeners.values()) {
            if (registration != null) registration.remove();
        }
        relationshipListeners.clear();
    }

    private void setPendingRelationshipState(String senderId, String receiverId) {
        Map<String, Object> outgoing = new HashMap<>();
        outgoing.put("state", RelationshipStateHelper.STATE_PENDING);
        outgoing.put("blockedBy", "");
        outgoing.put("peerUid", receiverId);
        outgoing.put("initiatedBy", senderId);
        outgoing.put("updatedAt", FieldValue.serverTimestamp());

        Map<String, Object> incoming = new HashMap<>(outgoing);
        incoming.put("peerUid", senderId);

        Log.d(REL_CLEANUP_TAG, "source=UsersFragment writePayload state=" + RelationshipStateHelper.STATE_PENDING
                + " normalizedState=" + RelationshipStateHelper.CANON_PENDING + " uidA=" + senderId + " uidB=" + receiverId);
        com.google.android.gms.tasks.Task<Void> taskA = db.collection("users").document(senderId).collection("relationships").document(receiverId).set(outgoing)
                .addOnSuccessListener(u -> Log.d(REL_CLEANUP_TAG, "source=UsersFragment taskA success path=users/" + senderId + "/relationships/" + receiverId))
                .addOnFailureListener(e -> Log.e(REL_CLEANUP_TAG, "source=UsersFragment taskA failure path=users/" + senderId + "/relationships/" + receiverId, e));
        com.google.android.gms.tasks.Task<Void> taskB = db.collection("users").document(receiverId).collection("relationships").document(senderId).set(incoming)
                .addOnSuccessListener(u -> Log.d(REL_CLEANUP_TAG, "source=UsersFragment taskB success path=users/" + receiverId + "/relationships/" + senderId))
                .addOnFailureListener(e -> Log.e(REL_CLEANUP_TAG, "source=UsersFragment taskB failure path=users/" + receiverId + "/relationships/" + senderId, e));
        Log.d(REL_CLEANUP_TAG, "source=UsersFragment mirroredUpdates pathA=users/" + senderId + "/relationships/" + receiverId
                + " pathB=users/" + receiverId + "/relationships/" + senderId + " finalUiState=REQUEST PENDING");
    }

    private void unblockUser(UserModel receiver) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null || receiver == null || TextUtils.isEmpty(receiver.getUid())) return;

        String myUid = currentUser.getUid();
        String otherUid = receiver.getUid();

        Map<String, Object> relA = new HashMap<>();
        relA.put("state", RelationshipStateHelper.STATE_REMOVED);
        relA.put("blockedBy", "");
        relA.put("peerUid", otherUid);
        relA.put("initiatedBy", myUid);
        relA.put("updatedAt", FieldValue.serverTimestamp());

        Map<String, Object> relB = new HashMap<>();
        relB.put("state", RelationshipStateHelper.STATE_REMOVED);
        relB.put("blockedBy", "");
        relB.put("peerUid", myUid);
        relB.put("initiatedBy", myUid);
        relB.put("updatedAt", FieldValue.serverTimestamp());

        com.google.android.gms.tasks.Task<Void> taskA = db.collection("users").document(myUid).collection("relationships").document(otherUid).set(relA)
                .addOnSuccessListener(u -> Log.d(REL_CLEANUP_TAG, "source=UsersFragment unblock taskA success path=users/" + myUid + "/relationships/" + otherUid))
                .addOnFailureListener(e -> Log.e(REL_CLEANUP_TAG, "source=UsersFragment unblock taskA failure path=users/" + myUid + "/relationships/" + otherUid, e));
        com.google.android.gms.tasks.Task<Void> taskB = db.collection("users").document(otherUid).collection("relationships").document(myUid).set(relB)
                .addOnSuccessListener(unused -> {
                    Log.d(REL_CLEANUP_TAG, "source=UsersFragment unblock taskB success path=users/" + otherUid + "/relationships/" + myUid);
                    Toast.makeText(getContext(), "User unblocked", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> Log.e(REL_CLEANUP_TAG, "source=UsersFragment unblock taskB failure path=users/" + otherUid + "/relationships/" + myUid, e));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        clearRelationshipListeners();
    }
}
