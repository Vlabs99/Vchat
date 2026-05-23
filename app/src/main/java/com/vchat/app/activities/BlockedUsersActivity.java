package com.vchat.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.vchat.app.R;
import com.vchat.app.adapters.BlockedUsersAdapter;
import com.vchat.app.models.UserModel;
import com.vchat.app.utils.RelationshipStateHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BlockedUsersActivity extends AppCompatActivity {
    private static final String REL_CLEANUP_TAG = "VCHAT_REL_CLEANUP";
    private RecyclerView rvBlockedUsers;
    private TextView tvEmpty;
    private ProgressBar progress;
    private BlockedUsersAdapter adapter;
    private final List<UserModel> blockedUsers = new ArrayList<>();
    private FirebaseUser currentUser;
    private FirebaseFirestore db;
    private ListenerRegistration blockedListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blocked_users);

        Toolbar toolbar = findViewById(R.id.toolbar_blocked_users);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());

        rvBlockedUsers = findViewById(R.id.rv_blocked_users);
        tvEmpty = findViewById(R.id.tv_blocked_empty);
        progress = findViewById(R.id.progress_blocked);

        rvBlockedUsers.setLayoutManager(new LinearLayoutManager(this));
        rvBlockedUsers.setHasFixedSize(true);
        adapter = new BlockedUsersAdapter(this::showBlockedUserActions);
        rvBlockedUsers.setAdapter(adapter);

        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        db = FirebaseFirestore.getInstance();
        if (currentUser == null) {
            finish();
            return;
        }
        listenBlockedUsers();
    }

    private void listenBlockedUsers() {
        progress.setVisibility(View.VISIBLE);
        blockedListener = db.collection("users")
                .document(currentUser.getUid())
                .collection("relationships")
                .whereEqualTo("state", RelationshipStateHelper.STATE_BLOCKED)
                .whereEqualTo("blockedBy", currentUser.getUid())
                .addSnapshotListener((value, error) -> {
                    progress.setVisibility(View.GONE);
                    if (value == null || error != null) {
                        tvEmpty.setText("Could not load blocked users");
                        tvEmpty.setVisibility(View.VISIBLE);
                        adapter.submit(new ArrayList<>());
                        return;
                    }
                    blockedUsers.clear();
                    if (value.isEmpty()) {
                        tvEmpty.setText("No blocked users");
                        tvEmpty.setVisibility(View.VISIBLE);
                        adapter.submit(new ArrayList<>());
                        return;
                    }
                    final int[] pending = {value.size()};
                    value.getDocuments().forEach(doc -> {
                        String peerUid = doc.getString("peerUid");
                        if (TextUtils.isEmpty(peerUid)) {
                            if (--pending[0] == 0) finishBlockedLoad();
                            return;
                        }
                        db.collection("users").document(peerUid).get()
                                .addOnSuccessListener(userDoc -> {
                                    UserModel model = userDoc.toObject(UserModel.class);
                                    if (model != null) blockedUsers.add(model);
                                    if (--pending[0] == 0) finishBlockedLoad();
                                })
                                .addOnFailureListener(e -> {
                                    if (--pending[0] == 0) finishBlockedLoad();
                                });
                    });
                });
    }

    private void finishBlockedLoad() {
        adapter.submit(blockedUsers);
        tvEmpty.setVisibility(blockedUsers.isEmpty() ? View.VISIBLE : View.GONE);
        rvBlockedUsers.setVisibility(blockedUsers.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void showBlockedUserActions(UserModel user) {
        if (user == null || TextUtils.isEmpty(user.getUid())) return;
        String displayName = TextUtils.isEmpty(user.getUsername()) ? "User" : user.getUsername();
        new AlertDialog.Builder(this)
                .setTitle(displayName)
                .setItems(new CharSequence[]{"Unblock user", "View profile", "Open chat history"}, (dialog, which) -> {
                    if (which == 0) {
                        unblockUser(user.getUid());
                    } else if (which == 1) {
                        Intent infoIntent = new Intent(this, FriendInfoActivity.class);
                        infoIntent.putExtra("otherUserId", user.getUid());
                        infoIntent.putExtra("otherUserName", displayName);
                        startActivity(infoIntent);
                    } else if (which == 2) {
                        String myUid = currentUser.getUid();
                        String chatId = myUid.compareTo(user.getUid()) < 0 ? myUid + "_" + user.getUid() : user.getUid() + "_" + myUid;
                        Intent chatIntent = new Intent(this, ChatActivity.class);
                        chatIntent.putExtra("chatId", chatId);
                        chatIntent.putExtra("isGroup", false);
                        chatIntent.putExtra("otherUserId", user.getUid());
                        chatIntent.putExtra("otherUserName", displayName);
                        startActivity(chatIntent);
                    }
                })
                .show();
    }

    private void unblockUser(String otherUid) {
        if (TextUtils.isEmpty(otherUid) || currentUser == null) return;
        Map<String, Object> relA = new HashMap<>();
        relA.put("state", RelationshipStateHelper.STATE_REMOVED);
        relA.put("blockedBy", "");
        relA.put("peerUid", otherUid);
        relA.put("initiatedBy", currentUser.getUid());
        relA.put("updatedAt", FieldValue.serverTimestamp());

        Map<String, Object> relB = new HashMap<>();
        relB.put("state", RelationshipStateHelper.STATE_REMOVED);
        relB.put("blockedBy", "");
        relB.put("peerUid", currentUser.getUid());
        relB.put("initiatedBy", currentUser.getUid());
        relB.put("updatedAt", FieldValue.serverTimestamp());

        Log.d(REL_CLEANUP_TAG, "source=BlockedUsersActivity writePayload state=" + RelationshipStateHelper.STATE_REMOVED
                + " normalizedState=" + RelationshipStateHelper.CANON_REMOVED + " uidA=" + currentUser.getUid() + " uidB=" + otherUid + " blockedBy=");
        com.google.android.gms.tasks.Task<Void> taskA = db.collection("users").document(currentUser.getUid()).collection("relationships").document(otherUid).set(relA)
                .addOnSuccessListener(u -> Log.d(REL_CLEANUP_TAG, "source=BlockedUsersActivity unblock taskA success path=users/" + currentUser.getUid() + "/relationships/" + otherUid))
                .addOnFailureListener(e -> Log.e(REL_CLEANUP_TAG, "source=BlockedUsersActivity unblock taskA failure path=users/" + currentUser.getUid() + "/relationships/" + otherUid, e));
        com.google.android.gms.tasks.Task<Void> taskB = db.collection("users").document(otherUid).collection("relationships").document(currentUser.getUid()).set(relB)
                .addOnSuccessListener(unused -> {
                    Log.d(REL_CLEANUP_TAG, "source=BlockedUsersActivity unblock taskB success path=users/" + otherUid + "/relationships/" + currentUser.getUid());
                    Toast.makeText(this, "User unblocked", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> Log.e(REL_CLEANUP_TAG, "source=BlockedUsersActivity unblock taskB failure path=users/" + otherUid + "/relationships/" + currentUser.getUid(), e));
        Log.d(REL_CLEANUP_TAG, "source=BlockedUsersActivity mirroredUpdates pathA=users/" + currentUser.getUid() + "/relationships/" + otherUid
                + " pathB=users/" + otherUid + "/relationships/" + currentUser.getUid() + " finalUiState=NOT FRIENDS");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (blockedListener != null) {
            blockedListener.remove();
            blockedListener = null;
        }
    }
}
