package com.vchat.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.vchat.app.R;
import com.vchat.app.models.MessageModel;
import com.vchat.app.utils.TimeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class StarredMessagesActivity extends AppCompatActivity {

    private RecyclerView rvStarred;
    private EditText etSearch;
    private TextView tvEmpty;
    private final List<StarredItem> allItems = new ArrayList<>();
    private final List<StarredItem> items = new ArrayList<>();
    private StarredAdapter adapter;
    private FirebaseFirestore db;
    private String uid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_starred_messages);

        Toolbar toolbar = findViewById(R.id.toolbar_starred);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());

        rvStarred = findViewById(R.id.rv_starred);
        etSearch = findViewById(R.id.et_starred_search);
        tvEmpty = findViewById(R.id.tv_starred_empty);

        rvStarred.setLayoutManager(new LinearLayoutManager(this));
        adapter = new StarredAdapter(items, this::openSourceChat);
        rvStarred.setAdapter(adapter);

        db = FirebaseFirestore.getInstance();
        uid = FirebaseAuth.getInstance().getUid();
        loadStarred();

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filter(s == null ? "" : s.toString());
            }
        });
    }

    private void loadStarred() {
        if (TextUtils.isEmpty(uid)) return;
        db.collectionGroup("messages")
                .whereEqualTo("starredBy." + uid, true)
                .get()
                .addOnSuccessListener(snap -> {
                    allItems.clear();
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        MessageModel message = doc.toObject(MessageModel.class);
                        if (message == null) continue;
                        DocumentSnapshot parent = null;
                        String chatId = "";
                        if (doc.getReference().getParent() != null && doc.getReference().getParent().getParent() != null) {
                            chatId = doc.getReference().getParent().getParent().getId();
                        }
                        allItems.add(new StarredItem(chatId, message));
                    }
                    filter(etSearch.getText() == null ? "" : etSearch.getText().toString());
                });
    }

    private void filter(String queryRaw) {
        String q = queryRaw == null ? "" : queryRaw.trim().toLowerCase();
        items.clear();
        for (StarredItem item : allItems) {
            String text = item.message.getMessageText();
            if (TextUtils.isEmpty(q) || (!TextUtils.isEmpty(text) && text.toLowerCase().contains(q))) {
                items.add(item);
            }
        }
        adapter.notifyDataSetChanged();
        tvEmpty.setVisibility(items.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);
    }

    private void openSourceChat(StarredItem item) {
        if (item == null || TextUtils.isEmpty(item.chatId)) return;
        db.collection("chats").document(item.chatId).get().addOnSuccessListener(chatDoc -> {
            boolean isGroup = Boolean.TRUE.equals(chatDoc.getBoolean("isGroup"));
            Intent intent = new Intent(this, ChatActivity.class);
            intent.putExtra("chatId", item.chatId);
            intent.putExtra("isGroup", isGroup);
            intent.putExtra("groupName", chatDoc.getString("chatName"));
            intent.putExtra("highlightMessageId", item.message.getMessageId());
            if (!isGroup) {
                Map<String, Boolean> participants = (Map<String, Boolean>) chatDoc.get("participants");
                String otherUid = "";
                if (participants != null) {
                    for (String p : participants.keySet()) {
                        if (!TextUtils.equals(uid, p)) {
                            otherUid = p;
                            break;
                        }
                    }
                }
                intent.putExtra("otherUserId", otherUid);
                intent.putExtra("otherUserName", otherUid);
            }
            startActivity(intent);
        });
    }

    private interface OnItemClick {
        void onClick(StarredItem item);
    }

    private static class StarredItem {
        final String chatId;
        final MessageModel message;

        StarredItem(String chatId, MessageModel message) {
            this.chatId = chatId;
            this.message = message;
        }
    }

    private static class StarredAdapter extends RecyclerView.Adapter<StarredAdapter.Holder> {
        private final List<StarredItem> data;
        private final OnItemClick onItemClick;

        StarredAdapter(List<StarredItem> data, OnItemClick onItemClick) {
            this.data = data;
            this.onItemClick = onItemClick;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            android.view.View view = android.view.LayoutInflater.from(parent.getContext()).inflate(R.layout.item_starred_message, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            StarredItem item = data.get(position);
            holder.tvChat.setText("Chat: " + item.chatId);
            holder.tvText.setText(TextUtils.isEmpty(item.message.getMessageText()) ? "(empty)" : item.message.getMessageText());
            holder.tvTime.setText(TimeUtils.getFormattedTime(item.message.getTimestamp()));
            holder.itemView.setOnClickListener(v -> onItemClick.onClick(item));
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        static class Holder extends RecyclerView.ViewHolder {
            final TextView tvChat;
            final TextView tvText;
            final TextView tvTime;

            Holder(@NonNull android.view.View itemView) {
                super(itemView);
                tvChat = itemView.findViewById(R.id.tv_starred_chat);
                tvText = itemView.findViewById(R.id.tv_starred_text);
                tvTime = itemView.findViewById(R.id.tv_starred_time);
            }
        }
    }
}
