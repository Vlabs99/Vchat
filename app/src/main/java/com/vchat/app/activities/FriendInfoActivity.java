package com.vchat.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.WriteBatch;
import com.vchat.app.R;
import com.vchat.app.adapters.SharedGroupsAdapter;
import com.vchat.app.models.ChatModel;
import com.vchat.app.models.UserModel;
import com.vchat.app.utils.RelationshipStateHelper;
import com.vchat.app.utils.TimeUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FriendInfoActivity extends AppCompatActivity {
    private static final String TAG = "FriendInfoActivity";
    private static final String REL_SYNC_TAG = "VCHAT_REL_SYNC";
    private static final String REL_CLEANUP_TAG = "VCHAT_REL_CLEANUP";
    private static final String RULES_TAG = "VCHAT_GROUP_RULES";
    private String otherUserId;
    private String otherUserName;
    private TextView tvName;
    private TextView tvDisplayName;
    private TextView tvEmail;
    private TextView tvPresence;
    private TextView tvState;
    private TextView tvCount;
    private TextView tvEmpty;
    private RecyclerView rvSharedGroups;
    private Button btnManage;

    private SharedGroupsAdapter groupsAdapter;
    private final Map<String, ChatModel> sharedGroupsById = new HashMap<>();

    private FirebaseUser currentUser;
    private FirebaseFirestore db;
    private String relationState = "";
    private String blockedBy = "";
    private String initiatedBy = "";

    private ListenerRegistration profileListener;
    private ListenerRegistration relationshipListener;
    private ListenerRegistration sharedGroupsListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_friend_info);
        otherUserId = getIntent().getStringExtra("otherUserId");
        otherUserName = getIntent().getStringExtra("otherUserName");

        Toolbar tb = findViewById(R.id.toolbar_friend_info);
        setSupportActionBar(tb);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        tb.setNavigationOnClickListener(v -> finish());

        tvName = findViewById(R.id.tv_friend_name);
        tvDisplayName = findViewById(R.id.tv_friend_display_name);
        tvEmail = findViewById(R.id.tv_friend_email);
        tvPresence = findViewById(R.id.tv_friend_presence);
        tvState = findViewById(R.id.tv_friend_state);
        tvCount = findViewById(R.id.tv_shared_groups_count);
        tvEmpty = findViewById(R.id.tv_shared_groups_empty);
        btnManage = findViewById(R.id.btn_manage_friendship);
        rvSharedGroups = findViewById(R.id.rv_shared_groups);
        rvSharedGroups.setLayoutManager(new LinearLayoutManager(this));
        rvSharedGroups.setHasFixedSize(true);

        groupsAdapter = new SharedGroupsAdapter(this::openGroup);
        rvSharedGroups.setAdapter(groupsAdapter);
        btnManage.setOnClickListener(v -> showActionsSheet());

        db = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null || TextUtils.isEmpty(otherUserId)) {
            finish();
            return;
        }
        groupsAdapter.setCurrentUid(currentUser.getUid());

        tvName.setText(fallbackName(otherUserName));
        tvDisplayName.setText("No display name");
        tvEmail.setText("Email unavailable");
        tvPresence.setText("Last seen unavailable");
        tvState.setText("Loading...");
        tvEmpty.setVisibility(View.VISIBLE);
        tvEmpty.setText("Loading shared groups...");

        listenProfile();
        listenRelationship();
        listenSharedGroups();
    }

    private void listenProfile() {
        if (profileListener != null) profileListener.remove();
        profileListener = db.collection("users").document(otherUserId).addSnapshotListener((s, e) -> {
            if (e != null || s == null || !s.exists()) {
                tvName.setText(fallbackName(otherUserName));
                tvDisplayName.setText("No display name");
                tvEmail.setText("Email unavailable");
                tvPresence.setText("Last seen unavailable");
                return;
            }
            UserModel u = s.toObject(UserModel.class);
            if (u == null) return;

            tvName.setText(fallbackName(u.getUsername()));
            tvDisplayName.setText(resolveDisplayName(u));
            tvEmail.setText(TextUtils.isEmpty(u.getEmail()) ? "Email unavailable" : u.getEmail().trim());
            tvPresence.setText(u.isOnline() ? "Online" : TimeUtils.getLastSeenFormatted(u.getLastSeen()));
        });
    }

    private void listenRelationship() {
        if (relationshipListener != null) relationshipListener.remove();
        relationshipListener = db.collection("users").document(currentUser.getUid()).collection("relationships").document(otherUserId)
                .addSnapshotListener((s, e) -> {
                    if (e != null) return;
                    String rawState = s != null ? safe(s.getString("state")) : "";
                    blockedBy = s != null ? safe(s.getString("blockedBy")) : "";
                    initiatedBy = s != null ? safe(s.getString("initiatedBy")) : "";
                    relationState = RelationshipStateHelper.resolveCanonicalState(rawState, blockedBy, currentUser.getUid());
                    if (!TextUtils.isEmpty(blockedBy)) {
                        relationState = TextUtils.equals(currentUser.getUid(), blockedBy)
                                ? RelationshipStateHelper.CANON_BLOCKED
                                : RelationshipStateHelper.CANON_BLOCKED_BY_USER;
                    }
                    Log.d(REL_SYNC_TAG, "source=FriendInfoActivity rawState=" + rawState + " normalizedState=" + relationState
                            + " blockedBy=" + blockedBy + " peerUid=" + otherUserId);
                    updateStateUi();
                });
    }

    private void listenSharedGroups() {
        if (sharedGroupsListener != null) sharedGroupsListener.remove();
        String uid = currentUser == null ? "" : currentUser.getUid();
        Log.d(RULES_TAG, "source=FriendInfoActivity listenerStart path=chats filters=[isGroup==true,participants." + uid + "==true] currentUserId=" + uid);
        sharedGroupsListener = db.collection("chats")
                .whereEqualTo("isGroup", true)
                .whereEqualTo("participants." + currentUser.getUid(), true)
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null) {
                        Log.d(RULES_TAG, "source=FriendInfoActivity listenerFailure path=chats currentUserId=" + uid
                                + " exception=" + Log.getStackTraceString(error));
                        tvEmpty.setVisibility(View.VISIBLE);
                        tvEmpty.setText("Could not load shared groups");
                        if (sharedGroupsById.isEmpty()) {
                            tvCount.setText("Shared groups (0)");
                        }
                        return;
                    }
                    Log.d(RULES_TAG, "source=FriendInfoActivity listenerSuccess path=chats currentUserId=" + uid + " docs=" + value.size());

                    for (DocumentChange change : value.getDocumentChanges()) {
                        String id = change.getDocument().getId();
                        if (TextUtils.isEmpty(id)) continue;
                        if (change.getType() == DocumentChange.Type.REMOVED) {
                            sharedGroupsById.remove(id);
                            continue;
                        }

                        Boolean peerIn = change.getDocument().getBoolean("participants." + otherUserId);
                        if (!Boolean.TRUE.equals(peerIn)) {
                            sharedGroupsById.remove(id);
                            continue;
                        }

                        ChatModel model = change.getDocument().toObject(ChatModel.class);
                        if (model == null) continue;
                        model.setChatId(id);
                        sharedGroupsById.put(id, model);
                    }
                    renderSharedGroups();
                });
    }

    private void renderSharedGroups() {
        List<ChatModel> groups = new ArrayList<>(sharedGroupsById.values());
        groups.sort(Comparator.comparingLong(ChatModel::getLastMessageTimestamp).reversed());
        groupsAdapter.submit(groups);
        tvCount.setText("Shared groups (" + groups.size() + ")");
        boolean hasData = !groups.isEmpty();
        rvSharedGroups.setVisibility(hasData ? View.VISIBLE : View.GONE);
        tvEmpty.setVisibility(hasData ? View.GONE : View.VISIBLE);
        if (!hasData) tvEmpty.setText("No shared groups with this contact yet");
    }

    private void updateStateUi() {
        boolean iBlockedUser = RelationshipStateHelper.CANON_BLOCKED.equals(relationState);
        boolean blockedByOther = RelationshipStateHelper.CANON_BLOCKED_BY_USER.equals(relationState);
        Log.d(REL_SYNC_TAG, "source=FriendInfoActivity finalUIMapping state=" + relationState
                + " iBlockedUser=" + iBlockedUser + " blockedByOther=" + blockedByOther);

        if (RelationshipStateHelper.CANON_FRIEND.equals(relationState)) {
            tvState.setText("FRIEND");
            tvState.setBackgroundResource(R.drawable.bg_chip_friend);
            btnManage.setEnabled(true);
            btnManage.setText("Manage friendship");
        } else if (RelationshipStateHelper.CANON_PENDING.equals(relationState)) {
            tvState.setText("REQUEST PENDING");
            tvState.setBackgroundResource(R.drawable.bg_chip_pending);
            btnManage.setEnabled(false);
            btnManage.setText("Request pending");
        } else if (RelationshipStateHelper.CANON_REMOVED.equals(relationState)) {
            tvState.setText("NOT FRIENDS");
            tvState.setBackgroundResource(R.drawable.bg_chip_removed);
            btnManage.setEnabled(false);
            btnManage.setText("Not friends");
        } else if (iBlockedUser) {
            tvState.setText("You blocked this user");
            tvState.setBackgroundResource(R.drawable.bg_chip_blocked);
            btnManage.setEnabled(true);
            btnManage.setText("Manage friendship");
        } else if (blockedByOther) {
            tvState.setText("You were blocked by this user");
            tvState.setBackgroundResource(R.drawable.bg_chip_blocked);
            btnManage.setEnabled(false);
            btnManage.setText("Blocked");
        } else {
            tvState.setText("NOT FRIENDS");
            tvState.setBackgroundResource(R.drawable.bg_chip_removed);
            btnManage.setEnabled(false);
            btnManage.setText("Not friends");
        }
        Log.d(REL_CLEANUP_TAG, "source=FriendInfoActivity finalUiState=" + tvState.getText() + " normalizedState=" + relationState + " peerUid=" + otherUserId);
    }

    private void openGroup(ChatModel group) {
        if (group == null || TextUtils.isEmpty(group.getChatId())) return;
        Intent i = new Intent(this, ChatActivity.class);
        i.putExtra("chatId", group.getChatId());
        i.putExtra("isGroup", true);
        i.putExtra("groupName", group.getChatName());
        startActivity(i);
    }

    private void showActionsSheet() {
        if (btnManage == null || !btnManage.isEnabled()) return;
        BottomSheetDialog d = new BottomSheetDialog(this);
        View v = getLayoutInflater().inflate(R.layout.bottomsheet_friend_actions, null, false);
        d.setContentView(v);
        View rowRemove = v.findViewById(R.id.row_remove_friend);
        View rowBlock = v.findViewById(R.id.row_block_user);
        View rowUnblock = v.findViewById(R.id.row_unblock_user);

        boolean iBlockedUser = RelationshipStateHelper.CANON_BLOCKED.equals(relationState);
        boolean blockedByOther = RelationshipStateHelper.CANON_BLOCKED_BY_USER.equals(relationState);
        boolean pendingState = RelationshipStateHelper.CANON_PENDING.equals(relationState);
        boolean removedState = RelationshipStateHelper.CANON_REMOVED.equals(relationState);

        rowUnblock.setVisibility(iBlockedUser ? View.VISIBLE : View.GONE);
        rowBlock.setVisibility((blockedByOther || iBlockedUser || pendingState || removedState) ? View.GONE : View.VISIBLE);
        rowRemove.setVisibility((blockedByOther || iBlockedUser || pendingState || removedState) ? View.GONE : View.VISIBLE);

        rowRemove.setOnClickListener(vv -> { d.dismiss(); confirmUpdate("removed", "", "Remove friend?"); });
        rowBlock.setOnClickListener(vv -> { d.dismiss(); confirmUpdate("blocked", currentUser.getUid(), "Block this user?"); });
        rowUnblock.setOnClickListener(vv -> { d.dismiss(); confirmUpdate(RelationshipStateHelper.STATE_REMOVED, "", "Unblock user?"); });
        v.findViewById(R.id.row_report_user).setOnClickListener(vv -> { d.dismiss(); Toast.makeText(this, "Report placeholder", Toast.LENGTH_SHORT).show(); });
        v.findViewById(R.id.row_mute_user).setOnClickListener(vv -> { d.dismiss(); Toast.makeText(this, "Mute placeholder", Toast.LENGTH_SHORT).show(); });
        d.show();
    }

    private void confirmUpdate(String state, String blockedByUser, String prompt) {
        new AlertDialog.Builder(this).setTitle("Confirm").setMessage(prompt)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Continue", (dialog, which) -> updateRelationship(state, blockedByUser))
                .show();
    }

    private void updateRelationship(String state, String blockedByUser) {
        String normalized = RelationshipStateHelper.normalizeState(state);
        String stateToWrite = RelationshipStateHelper.CANON_FRIEND.equals(normalized) ? RelationshipStateHelper.STATE_FRIEND : normalized;
        Log.d(TAG, "updateRelationship state=" + stateToWrite + " otherUserId=" + otherUserId + " blockedBy=" + blockedByUser);
        Map<String, Object> a = new HashMap<>();
        a.put("state", stateToWrite);
        a.put("blockedBy", blockedByUser);
        a.put("peerUid", otherUserId);
        a.put("initiatedBy", currentUser.getUid());
        a.put("updatedAt", FieldValue.serverTimestamp());
        com.google.android.gms.tasks.Task<Void> taskA =
                db.collection("users").document(currentUser.getUid()).collection("relationships").document(otherUserId).set(a)
                .addOnSuccessListener(u -> Log.d(REL_CLEANUP_TAG, "source=FriendInfoActivity taskA success path=users/" + currentUser.getUid() + "/relationships/" + otherUserId))
                .addOnFailureListener(e -> Log.e(REL_CLEANUP_TAG, "source=FriendInfoActivity taskA failure path=users/" + currentUser.getUid() + "/relationships/" + otherUserId, e));

        Map<String, Object> b = new HashMap<>();
        b.put("state", stateToWrite);
        b.put("blockedBy", blockedByUser);
        b.put("peerUid", currentUser.getUid());
        b.put("initiatedBy", currentUser.getUid());
        b.put("updatedAt", FieldValue.serverTimestamp());
        com.google.android.gms.tasks.Task<Void> taskB =
                db.collection("users").document(otherUserId).collection("relationships").document(currentUser.getUid()).set(b)
                .addOnSuccessListener(u -> Log.d(REL_CLEANUP_TAG, "source=FriendInfoActivity taskB success path=users/" + otherUserId + "/relationships/" + currentUser.getUid()))
                .addOnFailureListener(e -> Log.e(REL_CLEANUP_TAG, "source=FriendInfoActivity taskB failure path=users/" + otherUserId + "/relationships/" + currentUser.getUid(), e));
        Log.d(REL_CLEANUP_TAG, "source=FriendInfoActivity writePayload state=" + stateToWrite + " normalizedState=" + normalized
                + " uidA=" + currentUser.getUid() + " uidB=" + otherUserId + " blockedBy=" + blockedByUser);
        com.google.android.gms.tasks.Tasks.whenAll(taskA, taskB)
                .addOnSuccessListener(u -> {
                    Log.d(REL_CLEANUP_TAG, "source=FriendInfoActivity mirroredUpdates pathA=users/" + currentUser.getUid() + "/relationships/" + otherUserId
                            + " pathB=users/" + otherUserId + "/relationships/" + currentUser.getUid());
                    if (RelationshipStateHelper.STATE_REMOVED.equals(stateToWrite) || RelationshipStateHelper.STATE_BLOCKED.equals(stateToWrite)) {
                        clearPendingRequestsBetween(currentUser.getUid(), otherUserId);
                    }
                    Toast.makeText(this, "Updated", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> Log.e(TAG, "updateRelationship failed", e));
    }

    private void clearPendingRequestsBetween(String uidA, String uidB) {
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
                            batch.update(doc.getReference(), "status", "cancelled", "updatedAt", FieldValue.serverTimestamp());
                            hasUpdates = true;
                        }
                    }
                    if (hasUpdates) {
                        batch.commit().addOnFailureListener(e -> Log.e(TAG, "Failed clearing pending requests", e));
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Querying pending requests failed", e));
    }

    private String fallbackName(String username) {
        if (!TextUtils.isEmpty(username) && !TextUtils.isEmpty(username.trim())) return username.trim();
        if (!TextUtils.isEmpty(otherUserName) && !TextUtils.isEmpty(otherUserName.trim())) return otherUserName.trim();
        return "User";
    }

    private String resolveDisplayName(UserModel user) {
        if (user == null) return "No display name";
        String bio = user.getBio();
        if (!TextUtils.isEmpty(bio)) {
            String cleaned = bio.trim();
            if (!cleaned.isEmpty() && !"Hey there! I am using VChat.".equalsIgnoreCase(cleaned)) return cleaned;
        }
        return "No display name";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (profileListener != null) profileListener.remove();
        if (relationshipListener != null) relationshipListener.remove();
        if (sharedGroupsListener != null) sharedGroupsListener.remove();
    }
}
