package com.vchat.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;
import com.vchat.app.R;
import com.vchat.app.adapters.NotificationsAdapter;
import com.vchat.app.models.NotificationModel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NotificationsActivity extends AppCompatActivity {

    private RecyclerView rvNotifications;
    private TextView tvNoNotifications;

    private NotificationsAdapter adapter;
    private final Map<String, NotificationModel> notificationsByDocId = new HashMap<>();
    private final List<NotificationsAdapter.NotificationRow> rows = new ArrayList<>();

    private FirebaseFirestore db;
    private FirebaseUser currentUser;
    private ListenerRegistration notificationsListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        Toolbar toolbar = findViewById(R.id.toolbar_notifications);
        rvNotifications = findViewById(R.id.rv_notifications);
        tvNoNotifications = findViewById(R.id.tv_no_notifications);

        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Notifications");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        rvNotifications.setLayoutManager(new LinearLayoutManager(this));
        rvNotifications.setHasFixedSize(true);

        adapter = new NotificationsAdapter(new NotificationsAdapter.OnNotificationClickListener() {
            @Override
            public void onNotificationClick(String docId, NotificationModel model) {
                if (TextUtils.isEmpty(docId) || model == null) return;
                markRead(docId);
                handleNotificationClick(model);
            }

            @Override
            public void onMarkAllReadClick() {
                markAllAsRead();
            }
        });
        rvNotifications.setAdapter(adapter);

        db = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser != null) {
            listenNotifications();
        } else {
            finish();
        }
    }

    private void listenNotifications() {
        if (notificationsListener != null) notificationsListener.remove();
        notificationsListener = db.collection("users")
                .document(currentUser.getUid())
                .collection("notifications")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null) return;

                    for (DocumentChange change : snapshots.getDocumentChanges()) {
                        String docId = change.getDocument().getId();
                        if (TextUtils.isEmpty(docId)) continue;

                        if (change.getType() == DocumentChange.Type.REMOVED) {
                            notificationsByDocId.remove(docId);
                            continue;
                        }

                        NotificationModel model = change.getDocument().toObject(NotificationModel.class);
                        if (model == null) continue;
                        notificationsByDocId.put(docId, model);
                    }
                    rebuildRows();
                });
    }

    private void rebuildRows() {
        rows.clear();
        List<Map.Entry<String, NotificationModel>> sorted = new ArrayList<>(notificationsByDocId.entrySet());
        sorted.sort((a, b) -> Long.compare(
                b.getValue() == null ? 0L : b.getValue().getCreatedAtMillis(),
                a.getValue() == null ? 0L : a.getValue().getCreatedAtMillis()
        ));

        for (Map.Entry<String, NotificationModel> entry : sorted) {
            NotificationModel model = entry.getValue();
            if (model == null) continue;
            rows.add(new NotificationsAdapter.NotificationRow(entry.getKey(), model));
        }

        adapter.submit(rows);
        boolean hasData = !rows.isEmpty();
        rvNotifications.setVisibility(hasData ? View.VISIBLE : View.GONE);
        tvNoNotifications.setVisibility(hasData ? View.GONE : View.VISIBLE);
    }

    private void markRead(String docId) {
        if (TextUtils.isEmpty(docId) || currentUser == null) return;
        NotificationModel current = notificationsByDocId.get(docId);
        if (current != null && current.isRead()) return;
        db.collection("users").document(currentUser.getUid()).collection("notifications").document(docId)
                .update("isRead", true, "readAt", FieldValue.serverTimestamp());
    }

    private void markAllAsRead() {
        if (currentUser == null) return;
        if (notificationsByDocId.isEmpty()) {
            Toast.makeText(this, "No notifications", Toast.LENGTH_SHORT).show();
            return;
        }

        WriteBatch batch = db.batch();
        int unreadCount = 0;
        for (Map.Entry<String, NotificationModel> entry : notificationsByDocId.entrySet()) {
            NotificationModel model = entry.getValue();
            if (model == null || model.isRead()) continue;
            unreadCount++;
            batch.update(db.collection("users").document(currentUser.getUid()).collection("notifications").document(entry.getKey()),
                    "isRead", true,
                    "readAt", FieldValue.serverTimestamp());
        }

        if (unreadCount == 0) {
            Toast.makeText(this, "All already read", Toast.LENGTH_SHORT).show();
            return;
        }

        int finalUnreadCount = unreadCount;
        batch.commit()
                .addOnSuccessListener(unused -> Toast.makeText(this, "Marked " + finalUnreadCount + " as read", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to mark all read", Toast.LENGTH_SHORT).show());
    }

    private void handleNotificationClick(NotificationModel model) {
        String type = model.getType();
        if ("chat_request".equals(type)) {
            startActivity(new Intent(this, PendingRequestsActivity.class));
            return;
        }

        if (!model.getChatId().isEmpty()) {
            if (model.isGroup()) {
                Intent intent = new Intent(this, ChatActivity.class);
                intent.putExtra("chatId", model.getChatId());
                intent.putExtra("isGroup", true);
                intent.putExtra("groupName", model.getGroupName());
                startActivity(intent);
                return;
            }

            db.collection("users").document(model.getFromUserId()).get().addOnSuccessListener(snapshot -> {
                String otherName = "Chat";
                if (snapshot.exists()) {
                    String username = snapshot.getString("username");
                    if (username != null && !username.trim().isEmpty()) otherName = username;
                }

                Intent intent = new Intent(this, ChatActivity.class);
                intent.putExtra("chatId", model.getChatId());
                intent.putExtra("otherUserId", model.getFromUserId());
                intent.putExtra("otherUserName", otherName);
                startActivity(intent);
            });
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (notificationsListener != null) {
            notificationsListener.remove();
            notificationsListener = null;
        }
        notificationsByDocId.clear();
        rows.clear();
    }
}
