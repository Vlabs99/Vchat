package com.vchat.app.activities;

import android.app.AlertDialog;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ArrayAdapter;


import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.appcompat.view.ActionMode;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;
import com.vchat.app.R;
import com.vchat.app.adapters.MessageAdapter;
import com.vchat.app.chat.ChatComposerController;
import com.vchat.app.chat.ChatMessagePaginator;
import com.vchat.app.chat.ChatSelectionController;
import com.vchat.app.models.MessageModel;
import com.vchat.app.utils.RelationshipStateHelper;
import com.vchat.app.chat.MessageSender;
import com.vchat.app.chat.MessageFactory;
import com.vchat.app.chat.ChatPermissionHelper;
import com.vchat.app.chat.GroupPermissionManager;
import com.vchat.app.chat.ReplyManager;
import com.vchat.app.chat.ForwardManager;

// ... existing imports ...
import com.vchat.app.chat.TypingManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import android.widget.ProgressBar;


public class ChatActivity extends AppCompatActivity implements MessageAdapter.OnMessageActionListener {
    private static final String DM_DEBUG_TAG = "VCHAT_DM_DEBUG";
    private static final String REL_DEBUG_TAG = "VCHAT_REL_DEBUG";
    private static final String REL_SYNC_TAG = "VCHAT_REL_SYNC";
    private static final String REL_CLEANUP_TAG = "VCHAT_REL_CLEANUP";
    private static final String CHAT_RESTRICT_TAG = "VCHAT_CHAT_RESTRICT";
    private static final String DELETE_DEBUG_TAG = "VCHAT_DELETE_DEBUG";

    private Toolbar toolbarChat;
    private RecyclerView rvMessages;
    private EditText etMessage;
    private EditText etChatSearch;
    private ImageButton btnSend;
    private ImageButton btnAttach;
    private ImageButton btnSticker;
    private FloatingActionButton fabScrollBottom;
    private ProgressBar progressPagination;
    private TextView tvUnreadDivider;
    private LinearLayout layoutReplyPreview;
    private LinearLayout layoutRestrictionBanner;
    private TextView tvReplyingTo;
    private TextView tvRestrictionTitle;
    private TextView tvRestrictionSubtitle;
    private ImageButton btnCancelReply;

    private LinearLayout layoutAttachOptions;
    private Button btnOptGallery, btnOptCamera, btnOptDocument, btnOptAudio, btnOptContact, btnOptPoll, btnOptEvent;

    // pinned banner
    private LinearLayout layoutPinnedBanner;
    private TextView tvPinnedText;
    private ImageButton btnUnpin;

    private String chatId;
    private String otherUserId;
    private String otherUserName;
    private boolean isGroup;
    private String groupName;

    private FirebaseUser currentUser;
    private FirebaseFirestore db;

    private MessageAdapter messageAdapter;
    private final List<MessageModel> messageList = new ArrayList<>();
    private final List<MessageModel> allMessages = new ArrayList<>();

    private ListenerRegistration messagesListener;
    private ListenerRegistration newMessagesListener;
    private ListenerRegistration userListener; // This will be managed by TypingManager
    private ListenerRegistration pinnedListener;
    private ListenerRegistration relationshipListener;
    private String resolvedRelationshipState = "";
    private String restrictionState = "";
    private boolean canSendDirectMessage = true;
    private ReplyManager replyManager;
    private ForwardManager forwardManager;


    private MessageSender messageSender;
    private TypingManager typingManager;
    private GroupPermissionManager groupPermissionManager;
    

    // Remove these fields, they are now in TypingManager
    // private final Handler typingHandler = new Handler(Looper.getMainLooper());
    // private Runnable typingStopRunnable;
    // private String lastTypingValue = "";
    // private static final long TYPING_STOP_DELAY_MS = 1200L;
    private final ChatComposerController composerController = new ChatComposerController();
    private final ChatSelectionController selectionController = new ChatSelectionController();
    private final ChatMessagePaginator paginatorController = new ChatMessagePaginator();
    private static final int PAGE_SIZE = 35;
    private boolean isLoadingOlder = false;
    private boolean hasMoreOlder = true;
    private DocumentSnapshot oldestVisibleDoc;
    private ActionMode selectionActionMode;
    private boolean isAdminOnlyMessaging = false;
    private String pendingHighlightMessageId = "";
    private int unreadCount = 0;

    private final ActivityResultLauncher<Intent> contactPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri contactUri = result.getData().getData();
                    if (contactUri != null) {
                        handlePickedContact(contactUri);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        chatId = getIntent().getStringExtra("chatId");
        otherUserId = getIntent().getStringExtra("otherUserId");
        otherUserName = getIntent().getStringExtra("otherUserName");
        isGroup = getIntent().getBooleanExtra("isGroup", false);
        groupName = getIntent().getStringExtra("groupName");
        pendingHighlightMessageId = getIntent().getStringExtra("highlightMessageId");

        toolbarChat = findViewById(R.id.toolbar_chat);
        rvMessages = findViewById(R.id.rv_messages);
        etMessage = findViewById(R.id.et_message);
        etChatSearch = findViewById(R.id.et_chat_search);
    btnSend = findViewById(R.id.btn_send);
    btnAttach = findViewById(R.id.btn_attach);
    btnSticker = findViewById(R.id.btn_sticker);
    
    // Initialize UI elements as hidden for non-friends
    btnSend.setVisibility(View.GONE);
    etMessage.setVisibility(View.GONE);
        fabScrollBottom = findViewById(R.id.fab_scroll_bottom);
        progressPagination = findViewById(R.id.progress_pagination);
        tvUnreadDivider = findViewById(R.id.tv_unread_divider);
        layoutReplyPreview = findViewById(R.id.layout_reply_preview);
        layoutRestrictionBanner = findViewById(R.id.layout_restriction_banner);
        tvReplyingTo = findViewById(R.id.tv_replying_to);
        tvRestrictionTitle = findViewById(R.id.tv_restriction_title);
        tvRestrictionSubtitle = findViewById(R.id.tv_restriction_subtitle);
        btnCancelReply = findViewById(R.id.btn_cancel_reply);

        layoutAttachOptions = findViewById(R.id.layout_attach_options);
        btnOptGallery = findViewById(R.id.btn_opt_gallery);
        btnOptCamera = findViewById(R.id.btn_opt_camera);
        btnOptDocument = findViewById(R.id.btn_opt_document);
        btnOptAudio = findViewById(R.id.btn_opt_audio);
        btnOptContact = findViewById(R.id.btn_opt_contact);
        btnOptPoll = findViewById(R.id.btn_opt_poll);
        btnOptEvent = findViewById(R.id.btn_opt_event);

        // pinned banner views (add in XML in next step if missing)
        layoutPinnedBanner = findViewById(R.id.layout_pinned_banner);
        tvPinnedText = findViewById(R.id.tv_pinned_text);
        btnUnpin = findViewById(R.id.btn_unpin);
        forwardManager = new ForwardManager(db);
        replyManager = new ReplyManager(
        composerController,
        layoutReplyPreview,
        tvReplyingTo,
        etMessage
);

        setSupportActionBar(toolbarChat);
        if (getSupportActionBar() != null) {
            String title = isGroup ? groupName : otherUserName;
            getSupportActionBar().setTitle(!TextUtils.isEmpty(title) ? title : "Chat");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbarChat.setNavigationOnClickListener(v -> finish());
        if (isGroup) {
            toolbarChat.setOnClickListener(v -> {
                Intent intent = new Intent(ChatActivity.this, GroupInfoActivity.class);
                intent.putExtra("chatId", chatId);
                startActivity(intent);
            });
        } else {
            toolbarChat.setOnClickListener(v -> {
                Intent intent = new Intent(ChatActivity.this, FriendInfoActivity.class);
                intent.putExtra("otherUserId", otherUserId);
                intent.putExtra("otherUserName", otherUserName);
                startActivity(intent);
            });
        }

        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        db = FirebaseFirestore.getInstance();
        messageSender = new MessageSender(db);
        groupPermissionManager =
        new GroupPermissionManager(db);

        // Initialize TypingManager
        // Initialize TypingManager
typingManager = new TypingManager(
        db,
        currentUser,
        chatId,
        otherUserId,
        etMessage,
        toolbarChat,
        isGroup,
        () -> ChatPermissionHelper.isDirectChatRestricted(
                isGroup,
                canSendDirectMessage,
                restrictionState
        )
);

        if (currentUser == null || TextUtils.isEmpty(chatId) || (!isGroup && TextUtils.isEmpty(otherUserId))) {
            showToast("Invalid Chat Session");
            finish();
            return;
        }
        if (!isGroup) {
            Log.d(REL_DEBUG_TAG, "onCreate direct chat init; before canSendDirectMessage=" + canSendDirectMessage
                    + ", restrictionState=" + restrictionState + ", resolvedRelationshipState=" + resolvedRelationshipState);
            canSendDirectMessage = false; // Initialize as false until relationship listener confirms friendship
            Log.d(REL_DEBUG_TAG, "onCreate direct chat init; assigned canSendDirectMessage=false to prevent UI showing prematurely");
        }

        messageAdapter = new MessageAdapter(messageList, currentUser.getUid(), chatId, isGroup, this);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        rvMessages.setLayoutManager(layoutManager);
        rvMessages.setAdapter(messageAdapter);
        rvMessages.setItemAnimator(new androidx.recyclerview.widget.DefaultItemAnimator());
        rvMessages.setHasFixedSize(true);
        rvMessages.setItemViewCacheSize(40);
        rvMessages.setDrawingCacheEnabled(false);
        rvMessages.setNestedScrollingEnabled(true);

        setupSwipeToReply();
        setupPaginationScroll();
        setupScrollToBottomButton();

        btnSend.setOnClickListener(v -> {
        if (canSendDirectMessage) {
            sendMessage();
        }
    });
        btnCancelReply.setOnClickListener(
        v -> replyManager.clearReplyState()
);

        btnAttach.setOnClickListener(v -> toggleAttachOptions());
        btnSticker.setOnClickListener(v ->
                showToast("Sticker support coming soon"));

        btnOptGallery.setOnClickListener(v -> {
            closeAttachOptions();
            showToast("Gallery support coming soon");
        });

        btnOptCamera.setOnClickListener(v -> {
            closeAttachOptions();
            showToast("Camera support coming soon");
        });

        btnOptDocument.setOnClickListener(v -> {
            closeAttachOptions();
            showToast("Document support coming soon");
        });

        btnOptAudio.setOnClickListener(v -> {
            closeAttachOptions();
            showToast("Audio support coming soon");
        });

        btnOptContact.setOnClickListener(v -> {
            closeAttachOptions();
            openContactPicker();
        });

        btnOptPoll.setOnClickListener(v -> {
            closeAttachOptions();
            showCreatePollDialog();
        });

        btnOptEvent.setOnClickListener(v -> {
            closeAttachOptions();
            showCreateEventDialog();
        });

        if (layoutPinnedBanner != null) {
            layoutPinnedBanner.setOnClickListener(v -> scrollToPinnedMessage());
        }
        if (btnUnpin != null) {
            btnUnpin.setOnClickListener(v -> unpinMessage());
        }

        loadInitialMessages();
        validateChatAccess();
        if (!isGroup) {
            typingManager.listenForOtherUserPresence(); // Use TypingManager
            listenRelationshipState();
        } else {
            String initialGroupName = TextUtils.isEmpty(groupName) ? "Group chat" : groupName.trim();
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(initialGroupName);
            } else {
                toolbarChat.setTitle(initialGroupName);
            }
            toolbarChat.setSubtitle("Loading members...");
        }
        listenGroupMessagingRules();
        listenPinnedMessage();
        listenForNewIncomingMessages();
        typingManager.setupTypingIndicator(); // Use TypingManager
        setupChatSearch();
    }

    private void toggleAttachOptions() {
        if (layoutAttachOptions.getVisibility() == android.view.View.VISIBLE) {
            layoutAttachOptions.setVisibility(android.view.View.GONE);
        } else {
            layoutAttachOptions.setVisibility(android.view.View.VISIBLE);
        }
    }

    private void closeAttachOptions() {
        layoutAttachOptions.setVisibility(android.view.View.GONE);
    }

    private boolean canSendMessages() {

    return ChatPermissionHelper.allowComposerMessaging(
            isGroup,
            canSendDirectMessage,
            restrictionState
    );
}

private boolean isChatRestricted() {

    return ChatPermissionHelper.isDirectChatRestricted(
            isGroup,
            canSendDirectMessage,
            restrictionState
    );
}

private void showToast(String text) {

    Toast.makeText(
            this,
            text,
            Toast.LENGTH_SHORT
    ).show();
}

    private void openContactPicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
        contactPickerLauncher.launch(intent);
    }

    private void listenPinnedMessage() {
        pinnedListener = db.collection("chats")
                .document(chatId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null || snapshot == null || !snapshot.exists()) return;

                    String pinnedMessageId = snapshot.getString("pinnedMessageId");
                    String pinnedText = snapshot.getString("pinnedMessageText");

                    if (!TextUtils.isEmpty(pinnedMessageId) && !TextUtils.isEmpty(pinnedText) && layoutPinnedBanner != null) {
                        layoutPinnedBanner.setVisibility(android.view.View.VISIBLE);
                        tvPinnedText.setText(pinnedText);
                    } else if (layoutPinnedBanner != null) {
                        layoutPinnedBanner.setVisibility(android.view.View.GONE);
                    }
                });
    }

    private void scrollToPinnedMessage() {
        if (messageList.isEmpty()) return;

        db.collection("chats").document(chatId).get().addOnSuccessListener(snapshot -> {
            String pinnedMessageId = snapshot.getString("pinnedMessageId");
            if (TextUtils.isEmpty(pinnedMessageId)) return;

            for (int i = 0; i < messageList.size(); i++) {
                if (pinnedMessageId.equals(messageList.get(i).getMessageId())) {
                    rvMessages.scrollToPosition(i);
                    return;
                }
            }
        });
    }

    private void unpinMessage() {
        db.collection("chats")
                .document(chatId)
                .update("pinnedMessageId", "", "pinnedMessageText", "")
                .addOnSuccessListener(unused ->
                        showToast("Message Unpinned"));
    }

    @Override
    public void onPinMessage(MessageModel message) {
        if (message == null) return;
        if (!allowInteractiveMessageAction("Pinning is unavailable in this chat state")) return;

        String preview;
        if ("poll".equals(message.getMessageType())) {
            preview = "📊 " + message.getPollQuestion();
        } else if ("event".equals(message.getMessageType())) {
            preview = "📅 Event";
        } else if ("contact".equals(message.getMessageType())) {
            preview = "📞 Contact";
        } else if ("image".equals(message.getMessageType())) {
            preview = "🖼 Image";
        } else {
            preview = message.getMessageText();
        }

        db.collection("chats")
                .document(chatId)
                .update("pinnedMessageId", message.getMessageId(), "pinnedMessageText", preview)
                .addOnSuccessListener(unused ->
                        showToast("Message pinned")
                );
    }

    @Override
    public void onForwardMessage(MessageModel message) {
        if (message == null) return;
        if (!allowInteractiveMessageAction("Forwarding is unavailable in this chat state")) return;
        showForwardChatPicker(message);
    }


    

    @Override
    public void onDeleteForMe(MessageModel message) {
        if (message == null || currentUser == null) return;
        String messageId = safeMessageId(message);
        String uid = currentUser.getUid();
        Log.d(DELETE_DEBUG_TAG, "selectedMessageId=" + messageId + " currentUserId=" + uid + " deleteType=delete_for_me");
        if (TextUtils.isEmpty(messageId)) {
            Log.e(DELETE_DEBUG_TAG, "delete_for_me aborted: empty messageId");
            showToast("Delete Failed");
            return;
        }

        int originalPos = findMessageIndexById(messageId);
        if (originalPos < 0) {
            showToast("Message not found");
            return;
        }

        // Only mark as deleted locally first
        message.setDeletedFor(Map.of(uid, true));
        messageAdapter.notifyItemChanged(originalPos);
        
        // Then attempt Firestore update
        String docPath = "chats/" + chatId + "/messages/" + messageId;
        Log.d(DELETE_DEBUG_TAG, "firestorePath=" + docPath + " op=update deletedFor." + uid + "=true");
        db.collection("chats").document(chatId)
                .collection("messages").document(messageId)
                .update("deletedFor." + uid, true)
                .addOnSuccessListener(unused -> {
                    Log.d(DELETE_DEBUG_TAG, "successCallback type=delete_for_me messageId=" + messageId);
                    // Only remove from lists after successful update
                    messageList.remove(originalPos);
                    removeFromAllMessages(messageId);
                    messageAdapter.notifyItemRemoved(originalPos);
                })
                .addOnFailureListener(e -> {
                    boolean permissionFailure = e != null && e.getMessage() != null
                            && e.getMessage().toLowerCase().contains("permission");
                    Log.e(DELETE_DEBUG_TAG, "failureCallback type=delete_for_me messageId=" + messageId
                            + " permissionFailure=" + permissionFailure, e);
                    // Revert local changes on failure
                    message.setDeletedFor(null);
                    messageAdapter.notifyItemChanged(originalPos);
                    showToast("Delete Failed");
                });
    }

    @Override
    public void onDeleteMessage(MessageModel message) {
        if (message == null || currentUser == null) return;
        String messageId = safeMessageId(message);
        String uid = currentUser.getUid();
        Log.d(DELETE_DEBUG_TAG, "selectedMessageId=" + messageId + " currentUserId=" + uid + " deleteType=delete_for_everyone");
        if (TextUtils.isEmpty(messageId)) {
            Log.e(DELETE_DEBUG_TAG, "delete_for_everyone aborted: empty messageId");
            showToast("Delete Failed");
            return;
        }
        if (!TextUtils.equals(uid, message.getSenderId())) {
            Log.e(DELETE_DEBUG_TAG, "permissionDenied type=delete_for_everyone senderId=" + message.getSenderId() + " currentUserId=" + uid);
            showToast("Only sender can delete for everyone");
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Delete message")
                .setMessage("Delete this message for everyone?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    String docPath = "chats/" + chatId + "/messages/" + messageId;
                    Log.d(DELETE_DEBUG_TAG, "firestorePath=" + docPath + " op=delete");
                    db.collection("chats").document(chatId)
                            .collection("messages").document(messageId)
                            .delete()
                            .addOnSuccessListener(unused -> {
                                Log.d(DELETE_DEBUG_TAG, "successCallback type=delete_for_everyone messageId=" + messageId);
                                showToast("Message deleted");
                            })
                            .addOnFailureListener(e -> {
                                boolean permissionFailure = e != null && e.getMessage() != null
                                        && e.getMessage().toLowerCase().contains("permission");
                                Log.e(DELETE_DEBUG_TAG, "failureCallback type=delete_for_everyone messageId=" + messageId
                                        + " permissionFailure=" + permissionFailure, e);
                                showToast("Delete Failed");
                            });
                })
                .show();
    }

    @Override
    public void onMessageInfo(MessageModel message) {
        if (message == null) return;
        if (!isGroup) {
            showToast("Message info is available in group chats");
            return;
        }
        showGroupMessageInfo(message);
    }

    @Override
    public void onCopyMessage(MessageModel message) {
        if (message == null || TextUtils.isEmpty(message.getMessageText())) return;
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText("message", message.getMessageText()));
        showToast("Copied");
    }

    @Override
    public void onReactMessage(MessageModel message, String emoji) {
        if (message == null || currentUser == null || TextUtils.isEmpty(emoji)) return;
        if (!allowInteractiveMessageAction("Reactions are unavailable in this chat state")) return;
        db.collection("chats")
                .document(chatId)
                .collection("messages")
                .document(message.getMessageId())
                .update("reactions." + currentUser.getUid(), emoji)
                .addOnFailureListener(e -> Log.e("ChatActivity", "Reaction failed", e));
    }

    @Override
    public void onStarMessage(MessageModel message) {
        if (message == null || currentUser == null) return;
        if (!allowInteractiveMessageAction("Starring is unavailable in this chat state")) return;
        String uid = currentUser.getUid();
        boolean isStarred = message.getStarredBy().containsKey(uid);
        Object value = isStarred ? com.google.firebase.firestore.FieldValue.delete() : true;
        db.collection("chats")
                .document(chatId)
                .collection("messages")
                .document(message.getMessageId())
                .update("starredBy." + uid, value)
                .addOnFailureListener(e -> Log.e("ChatActivity", "Star update failed", e));
    }

    @Override
    public void onOpenRepliedMessage(String messageId) {
        if (TextUtils.isEmpty(messageId)) return;
        for (int i = 0; i < messageList.size(); i++) {
            if (messageId.equals(messageList.get(i).getMessageId())) {
                rvMessages.scrollToPosition(i);
                showToast("Replied message located");
                return;
            }
        }
    }

    @Override
    public void onMessageLongPressed(MessageModel message, int position) {
        if (message == null) return;
        if (selectionActionMode == null) {
            selectionActionMode = startSupportActionMode(selectionModeCallback);
            messageAdapter.setSelectionMode(true);
        }
        messageAdapter.toggleSelection(message);
        syncSelectionTitle();
    }

    @Override
    public void onStartMessageMultiSelect(MessageModel message, int position) {
        onMessageLongPressed(message, position);
    }

    private final ActionMode.Callback selectionModeCallback = new ActionMode.Callback() {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            getMenuInflater().inflate(R.menu.message_selection_menu, menu);
            syncSelectionTitle();
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            int id = item.getItemId();
            if (id == R.id.action_select_copy) {
                copySelectedMessages();
                mode.finish();
                return true;
            } else if (id == R.id.action_select_star) {
                starSelectedMessages();
                mode.finish();
                return true;
            } else if (id == R.id.action_select_delete) {
                deleteSelectedMessages();
                mode.finish();
                return true;
            } else if (id == R.id.action_select_forward) {
                forwardSelectedMessages();
                mode.finish();
                return true;
            }
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            messageAdapter.setSelectionMode(false);
            selectionActionMode = null;
        }
    };

    private void syncSelectionTitle() {
        if (selectionActionMode == null) return;
        int count = messageAdapter.getSelectedCount();
        if (count <= 0) {
            selectionActionMode.finish();
            return;
        }
        selectionActionMode.setTitle(count + " selected");
    }

    private void showForwardChatPicker(MessageModel message) {
        db.collection("chats").get().addOnSuccessListener(chatDocs -> {
            List<String> titles = new ArrayList<>();
            List<String> ids = new ArrayList<>();
            List<Task<DocumentSnapshot>> nameTasks = new ArrayList<>();
            List<Integer> nameTaskTargetIndexes = new ArrayList<>();

            String myUid = currentUser.getUid();

            for (QueryDocumentSnapshot doc : chatDocs) {
                Map<String, Boolean> participants = (Map<String, Boolean>) doc.get("participants");
                if (participants == null || !participants.containsKey(myUid)) continue;

                String targetChatId = doc.getId();
                if (targetChatId.equals(chatId)) continue;

                String groupName = doc.getString("chatName");
                if (!TextUtils.isEmpty(groupName)) {
                    titles.add(groupName);
                } else {
                    titles.add("Chat: " + targetChatId);

                    String otherUid = null;
                    for (String uid : participants.keySet()) {
                        if (!myUid.equals(uid)) {
                            otherUid = uid;
                            break;
                        }
                    }
                    if (!TextUtils.isEmpty(otherUid)) {
                        nameTasks.add(db.collection("users").document(otherUid).get());
                        nameTaskTargetIndexes.add(titles.size() - 1);
                    }
                }
                ids.add(targetChatId);
            }

            if (ids.isEmpty()) {
                showToast("No Other chats to Forward");
                return;
            }

            if (nameTasks.isEmpty()) {
                showSearchableForwardDialog(message, titles, ids);
                return;
            }

            Tasks.whenAllComplete(nameTasks).addOnSuccessListener(done -> {
                for (int i = 0; i < nameTasks.size(); i++) {
                    int titleIndex = nameTaskTargetIndexes.get(i);
                    Task<DocumentSnapshot> t = nameTasks.get(i);
                    if (!t.isSuccessful() || t.getResult() == null || !t.getResult().exists()) continue;

                    DocumentSnapshot userDoc = t.getResult();
                    String username = userDoc.getString("username");
                    if (TextUtils.isEmpty(username)) {
                        username = userDoc.getString("name");
                    }
                    if (!TextUtils.isEmpty(username) && titleIndex >= 0 && titleIndex < titles.size()) {
                        titles.set(titleIndex, username);
                    }
                }
                showSearchableForwardDialog(message, titles, ids);
            }).addOnFailureListener(e -> showSearchableForwardDialog(message, titles, ids));
        });
    }

    private void copySelectedMessages() {
        List<MessageModel> selected = messageAdapter.getSelectedMessages();
        if (selected.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        sb.append(selectionController.joinMessageTexts(selected));
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("messages", sb.toString()));
            showToast("Copied selected messages");
        }
    }

    private void starSelectedMessages() {
        if (currentUser == null) return;
        if (!allowInteractiveMessageAction("Starring is unavailable in this chat state")) return;
        List<MessageModel> selected = messageAdapter.getSelectedMessages();
        if (selected.isEmpty()) return;
        WriteBatch batch = db.batch();
        for (MessageModel message : selected) {
            batch.update(
                    db.collection("chats").document(chatId).collection("messages").document(message.getMessageId()),
                    "starredBy." + currentUser.getUid(),
                    true
            );
        }
        batch.commit();
    }

    private void deleteSelectedMessages() {
        if (currentUser == null) return;
        List<MessageModel> selected = messageAdapter.getSelectedMessages();
        if (selected.isEmpty()) return;
        String uid = currentUser.getUid();
        Log.d(DELETE_DEBUG_TAG, "selectedBatchCount=" + selected.size() + " currentUserId=" + uid + " deleteType=delete_selected_for_everyone");
        WriteBatch batch = db.batch();
        int validDeletes = 0;
        for (MessageModel message : selected) {
            String messageId = safeMessageId(message);
            if (TextUtils.isEmpty(messageId)) {
                Log.e(DELETE_DEBUG_TAG, "skipBatchDelete reason=empty_message_id");
                continue;
            }
            if (!TextUtils.equals(uid, message.getSenderId())) {
                Log.e(DELETE_DEBUG_TAG, "skipBatchDelete reason=permission senderId=" + message.getSenderId() + " currentUserId=" + uid + " messageId=" + messageId);
                continue;
            }
            String docPath = "chats/" + chatId + "/messages/" + messageId;
            Log.d(DELETE_DEBUG_TAG, "firestorePath=" + docPath + " op=batch_delete");
            batch.delete(db.collection("chats").document(chatId).collection("messages").document(messageId));
            validDeletes++;
        }
        if (validDeletes == 0) {
            showToast("No sender-owned messages selected for delete");
            return;
        }
        final int deletedCount = validDeletes;
        batch.commit()
                .addOnSuccessListener(unused -> Log.d(DELETE_DEBUG_TAG, "successCallback type=delete_selected_for_everyone deletedCount=" + deletedCount))
                .addOnFailureListener(e -> {
                    boolean permissionFailure = e != null && e.getMessage() != null
                            && e.getMessage().toLowerCase().contains("permission");
                    Log.e(DELETE_DEBUG_TAG, "failureCallback type=delete_selected_for_everyone permissionFailure=" + permissionFailure, e);
                });
    }

    private void forwardSelectedMessages() {
        if (!allowInteractiveMessageAction("Forwarding is unavailable in this chat state")) return;
        List<MessageModel> selected = messageAdapter.getSelectedMessages();
        if (selected.isEmpty()) return;
        showForwardChatPicker(selected.get(0));
    }

    private void showSearchableForwardDialog(MessageModel message, List<String> titles, List<String> ids) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);

        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad, pad, pad / 2);

        EditText etSearch = new EditText(this);
        etSearch.setHint("Search chat");
        container.addView(etSearch);

        ListView listView = new ListView(this);
        container.addView(listView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (int) (320 * getResources().getDisplayMetrics().density)
        ));

        List<String> filteredTitles = new ArrayList<>(titles);
        List<String> filteredIds = new ArrayList<>(ids);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                filteredTitles
        );
        listView.setAdapter(adapter);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Forward to")
                .setView(container)
                .setNegativeButton("Cancel", null)
                .create();

        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= filteredIds.size()) return;
            forwardMessageToChat(message, filteredIds.get(position));
            dialog.dismiss();
        });

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s == null ? "" : s.toString().trim().toLowerCase();
                filteredTitles.clear();
                filteredIds.clear();

                for (int i = 0; i < titles.size(); i++) {
                    String title = titles.get(i);
                    if (TextUtils.isEmpty(query) || title.toLowerCase().contains(query)) {
                        filteredTitles.add(title);
                        filteredIds.add(ids.get(i));
                    }
                }
                adapter.notifyDataSetChanged();
            }

            @Override
            public void afterTextChanged(Editable s) { }
        });

        dialog.show();
    }

    private void forwardMessageToChat(MessageModel original, String targetChatId) {
        if (!allowInteractiveMessageAction("Forwarding is unavailable in this chat state")) return;
        if (currentUser == null || TextUtils.isEmpty(targetChatId)) return;

        db.collection("chats").document(targetChatId).get().addOnSuccessListener(chatDoc -> {
            if (chatDoc == null || !chatDoc.exists()) {
                showToast("Target chat not Found");
                return;
            }
            Boolean group = chatDoc.getBoolean("isGroup");
            if (!Boolean.TRUE.equals(group)) {
                Map<String, Boolean> participants = (Map<String, Boolean>) chatDoc.get("participants");
                String myUid = currentUser.getUid();
                String peerUid = "";
                if (participants != null) {
                    for (String uid : participants.keySet()) {
                        if (!TextUtils.equals(uid, myUid)) {
                            peerUid = uid;
                            break;
                        }
                    }
                }
                if (TextUtils.isEmpty(peerUid)) {
                    showToast("Invalid target chat");
                    return;
                }
                db.collection("users").document(myUid).collection("relationships").document(peerUid).get()
                        .addOnSuccessListener(rel -> {
                            String state = rel.getString("state");
                            if (!RelationshipStateHelper.CANON_FRIEND.equals(RelationshipStateHelper.normalizeState(state))) {
                                showToast("Cannot Forward Restricted Friend Chat");
                                return;
                            }
                            forwardManager.performForward(
                                    original,
                                    targetChatId,
                                    currentUser.getUid(),
                                    isGroup,
                                    new ForwardManager.ForwardCallback() {

                                        @Override
                                        public void onSuccess() {

                                            showToast("Forwarded");
                                        }

                                        @Override
                                        public void onFailure() {

                                            showToast("Forward failed");
                                        }
                                    }
                            );
                        })
                        .addOnFailureListener(e -> showToast("Cannot validate target chat"));
                return;
            }
            forwardManager.performForward(
                    original,
                    targetChatId,
                    currentUser.getUid(),
                    isGroup,
                    new ForwardManager.ForwardCallback() {

                        @Override
                        public void onSuccess() {

                            showToast("Forwarded");
                        }

                        @Override
                        public void onFailure() {

                            showToast("Forward failed");
                        }
                    }
            );
        }).addOnFailureListener(e -> showToast("Forward failed"));
    }

    

    @Override
public void onReplyMessage(MessageModel message) {

    if (!allowInteractiveMessageAction(
            "Reply is unavailable in this chat state"
    )) {
        return;
    }

    replyManager.startReply(message);
}


    private void showCreatePollDialog() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad, pad, 0);

        final EditText etQuestion = new EditText(this);
        etQuestion.setHint("Poll question");
        container.addView(etQuestion);

        final EditText etOption1 = new EditText(this);
        etOption1.setHint("Option 1");
        container.addView(etOption1);

        final EditText etOption2 = new EditText(this);
        etOption2.setHint("Option 2");
        container.addView(etOption2);

        final EditText etOption3 = new EditText(this);
        etOption3.setHint("Option 3 (optional)");
        container.addView(etOption3);

        final EditText etOption4 = new EditText(this);
        etOption4.setHint("Option 4 (optional)");
        container.addView(etOption4);

        new AlertDialog.Builder(this)
                .setTitle("Create Poll")
                .setView(container)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Send", (dialog, which) -> {
                    String question = etQuestion.getText().toString().trim();
                    String o1 = etOption1.getText().toString().trim();
                    String o2 = etOption2.getText().toString().trim();
                    String o3 = etOption3.getText().toString().trim();
                    String o4 = etOption4.getText().toString().trim();

                    if (TextUtils.isEmpty(question) || TextUtils.isEmpty(o1) || TextUtils.isEmpty(o2)) {
                        showToast("Question + 2 options required");
                        return;
                    }

                    sendPollMessage(question, o1, o2, o3, o4);
                })
                .show();
    }

    private void sendPollMessage(String question, String o1, String o2, String o3, String o4) {
        if (currentUser == null) return;

        String messageId = UUID.randomUUID().toString();
        long timestamp = System.currentTimeMillis();

        List<String> options = new ArrayList<>();
        options.add(o1);
        options.add(o2);
        if (!TextUtils.isEmpty(o3)) options.add(o3);
        if (!TextUtils.isEmpty(o4)) options.add(o4);

        MessageModel message = new MessageModel(
                messageId,
                currentUser.getUid(),
                "",
                timestamp,
                "sent",
                "poll"
        );
        message.setPollQuestion(question);
        message.setPollOptions(options);
        message.setPollVotes(new HashMap<>());
        MessageFactory.initializeGroupTracking(
        message,
        isGroup,
        currentUser.getUid()
        );

        db.collection("chats")
                .document(chatId)
                .collection("messages")
                .document(messageId)
                .set(message)
                .addOnSuccessListener(unused -> {
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("lastMessage", "📊 Poll");
                    updates.put("lastMessageTimestamp", timestamp);
                    db.collection("chats").document(chatId).update(updates);
                })
                .addOnFailureListener(e ->
                        showToast("Failed to send poll"));
    }

    private void showCreateEventDialog() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad, pad, 0);

        final EditText etTitle = new EditText(this);
        etTitle.setHint("Event title");
        container.addView(etTitle);

        final EditText etDate = new EditText(this);
        etDate.setHint("Date (e.g. 15 May 2026)");
        container.addView(etDate);

        final EditText etTime = new EditText(this);
        etTime.setHint("Time (e.g. 7:30 PM)");
        container.addView(etTime);

        final EditText etLocation = new EditText(this);
        etLocation.setHint("Location (optional)");
        container.addView(etLocation);

        final EditText etNote = new EditText(this);
        etNote.setHint("Note (optional)");
        container.addView(etNote);

        new AlertDialog.Builder(this)
                .setTitle("Create Event")
                .setView(container)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Send", (dialog, which) -> {
                    String title = etTitle.getText().toString().trim();
                    String date = etDate.getText().toString().trim();
                    String time = etTime.getText().toString().trim();
                    String location = etLocation.getText().toString().trim();
                    String note = etNote.getText().toString().trim();

                    if (TextUtils.isEmpty(title) || TextUtils.isEmpty(date) || TextUtils.isEmpty(time)) {
                        showToast("Title + Date + Time required");
                        return;
                    }

                    sendEventMessage(title, date, time, location, note);
                })
                .show();
    }

    private void sendEventMessage(String title, String date, String time, String location, String note) {
        if (currentUser == null) return;

        String messageId = UUID.randomUUID().toString();
        long timestamp = System.currentTimeMillis();

        StringBuilder eventText = new StringBuilder();
        eventText.append("📅 EVENT\n");
        eventText.append("Title: ").append(title).append("\n");
        eventText.append("Date: ").append(date).append("\n");
        eventText.append("Time: ").append(time);

        if (!TextUtils.isEmpty(location)) {
            eventText.append("\nLocation: ").append(location);
        }
        if (!TextUtils.isEmpty(note)) {
            eventText.append("\nNote: ").append(note);
        }

        MessageModel message = new MessageModel(
                messageId,
                currentUser.getUid(),
                eventText.toString(),
                timestamp,
                "sent",
                "event"
        );
        MessageFactory.initializeGroupTracking(
        message,
        isGroup,
        currentUser.getUid()
        );
        db.collection("chats")
                .document(chatId)
                .collection("messages")
                .document(messageId)
                .set(message)
                .addOnSuccessListener(unused -> {
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("lastMessage", "📅 Event");
                    updates.put("lastMessageTimestamp", timestamp);
                    db.collection("chats").document(chatId).update(updates);
                })
                .addOnFailureListener(e ->
                        showToast("Failed to send event"));
    }

    private void loadInitialMessages() {
        progressPagination.setVisibility(View.VISIBLE);
        db.collection("chats")
                .document(chatId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .limitToLast(PAGE_SIZE)
                .get()
                .addOnSuccessListener(snap -> {
                    progressPagination.setVisibility(View.GONE);
                    allMessages.clear();
                    messageList.clear();
                    for (QueryDocumentSnapshot doc : snap) {
                        MessageModel message = doc.toObject(MessageModel.class);
                        if (message != null && TextUtils.isEmpty(message.getMessageId())) {
                            message.setMessageId(doc.getId());
                        }
                        if (message != null && isMessageVisibleToCurrentUser(message)) {
                            allMessages.add(message);
                            messageList.add(message);
                        }
                    }
                    if (!snap.isEmpty()) {
                        oldestVisibleDoc = snap.getDocuments().get(0);
                        hasMoreOlder = snap.size() >= PAGE_SIZE;
                    } else {
                        hasMoreOlder = false;
                    }
                    messageAdapter.notifyDataSetChanged();
                    markVisibleMessagesSeen(snap.getDocumentChanges());
                    if (!messageList.isEmpty()) rvMessages.scrollToPosition(messageList.size() - 1);
                    attemptJumpToHighlightedMessage();
                })
                .addOnFailureListener(e -> {
                    progressPagination.setVisibility(View.GONE);
                    Log.e("ChatActivity", "Initial load failed", e);
                });
    }

    private void loadOlderMessages() {
        if (isLoadingOlder || !hasMoreOlder || oldestVisibleDoc == null) return;
        isLoadingOlder = true;
        progressPagination.setVisibility(View.VISIBLE);
        db.collection("chats")
                .document(chatId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .endBefore(oldestVisibleDoc)
                .limitToLast(PAGE_SIZE)
                .get()
                .addOnSuccessListener(snap -> {
                    progressPagination.setVisibility(View.GONE);
                    isLoadingOlder = false;
                    if (snap.isEmpty()) {
                        hasMoreOlder = false;
                        return;
                    }
                    List<MessageModel> older = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snap) {
                        MessageModel message = doc.toObject(MessageModel.class);
                        if (message != null && isMessageVisibleToCurrentUser(message) && !containsMessageId(message.getMessageId())) {
                            older.add(message);
                            allMessages.add(message);
                        }
                    }
                    if (!older.isEmpty()) {
                        messageList.addAll(0, older);
                        oldestVisibleDoc = snap.getDocuments().get(0);
                        messageAdapter.notifyItemRangeInserted(0, older.size());
                        ((LinearLayoutManager) rvMessages.getLayoutManager()).scrollToPositionWithOffset(older.size(), 0);
                    }
                    hasMoreOlder = snap.size() >= PAGE_SIZE;
                })
                .addOnFailureListener(e -> {
                    progressPagination.setVisibility(View.GONE);
                    isLoadingOlder = false;
                    Log.e("ChatActivity", "Pagination failed", e);
                });
    }

    private void listenForNewIncomingMessages() {
        newMessagesListener = db.collection("chats")
                .document(chatId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null) return;
                    for (DocumentChange dc : value.getDocumentChanges()) {
                        MessageModel message = dc.getDocument().toObject(MessageModel.class);
                        if (message == null) continue;
                        if (TextUtils.isEmpty(message.getMessageId())) {
                            message.setMessageId(dc.getDocument().getId());
                        }
                        String msgId = message.getMessageId();
                        boolean visible = isMessageVisibleToCurrentUser(message);
                        Log.d(DELETE_DEBUG_TAG, "listenerEvent type=" + dc.getType() + " messageId=" + msgId + " visible=" + visible);
                        if (dc.getType() == DocumentChange.Type.ADDED) {
                            if (!visible || containsMessageId(msgId)) continue;
                            allMessages.add(message);
                            messageList.add(message);
                            messageAdapter.notifyItemInserted(messageList.size() - 1);
                            maybeAutoScrollOnNewMessage();
                            updateUnreadUi(message);
                        } else if (dc.getType() == DocumentChange.Type.MODIFIED) {
                            int index = findMessageIndexById(msgId);
                            if (!visible) {
                                if (index >= 0) {
                                    messageList.remove(index);
                                    messageAdapter.notifyItemRemoved(index);
                                }
                                removeFromAllMessages(msgId);
                                continue;
                            }
                            if (index >= 0) {
                                messageList.set(index, message);
                                messageAdapter.notifyItemChanged(index);
                            } else if (!containsMessageId(msgId)) {
                                allMessages.add(message);
                                messageList.add(message);
                                messageAdapter.notifyItemInserted(messageList.size() - 1);
                            }
                            upsertAllMessage(message);
                        } else if (dc.getType() == DocumentChange.Type.REMOVED) {
                            int index = findMessageIndexById(msgId);
                            if (index >= 0) {
                                messageList.remove(index);
                                messageAdapter.notifyItemRemoved(index);
                                Log.d(DELETE_DEBUG_TAG, "adapterRemovalEvent type=listener_removed index=" + index + " messageId=" + msgId);
                            }
                            removeFromAllMessages(msgId);
                        }
                    }
                    markVisibleMessagesSeen(value.getDocumentChanges());
                    attemptJumpToHighlightedMessage();
                });
    }

    private boolean isMessageVisibleToCurrentUser(MessageModel message) {
        if (message == null || currentUser == null) return false;
        Map<String, Boolean> deletedFor = message.getDeletedFor();
        return deletedFor == null || !Boolean.TRUE.equals(deletedFor.get(currentUser.getUid()));
    }

    private int findMessageIndexById(String messageId) {
        if (TextUtils.isEmpty(messageId)) return -1;
        for (int i = 0; i < messageList.size(); i++) {
            if (messageId.equals(messageList.get(i).getMessageId())) return i;
        }
        return -1;
    }

    private void removeFromAllMessages(String messageId) {
        if (TextUtils.isEmpty(messageId)) return;
        for (int i = allMessages.size() - 1; i >= 0; i--) {
            if (messageId.equals(allMessages.get(i).getMessageId())) {
                allMessages.remove(i);
            }
        }
    }

    private void upsertAllMessage(MessageModel message) {
        if (message == null || TextUtils.isEmpty(message.getMessageId())) return;
        for (int i = 0; i < allMessages.size(); i++) {
            if (message.getMessageId().equals(allMessages.get(i).getMessageId())) {
                allMessages.set(i, message);
                return;
            }
        }
        allMessages.add(message);
    }

    private void setupChatSearch() {
        if (etChatSearch == null) return;

        etChatSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyMessageFilter(s == null ? "" : s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) { }
        });
    }

    private boolean containsMessageId(String messageId) {
        if (TextUtils.isEmpty(messageId)) return false;
        for (MessageModel message : messageList) {
            if (messageId.equals(message.getMessageId())) return true;
        }
        return false;
    }

    private String safeMessageId(MessageModel message) {
        if (message == null) return "";
        if (!TextUtils.isEmpty(message.getMessageId())) return message.getMessageId();
        return "";
    }

    private void markVisibleMessagesSeen(List<DocumentChange> changes) {
        if (changes == null || changes.isEmpty() || currentUser == null) return;
        WriteBatch batch = db.batch();
        boolean hasSeenUpdates = false;
        for (DocumentChange dc : changes) {
            if (dc.getType() != DocumentChange.Type.ADDED) continue;
            MessageModel msg = dc.getDocument().toObject(MessageModel.class);
            if (msg == null) continue;
            boolean isIncoming = !TextUtils.isEmpty(msg.getSenderId()) && !currentUser.getUid().equals(msg.getSenderId());
            if (!isIncoming) continue;
            if (isGroup) {
                Map<String, Boolean> delivered = msg.getDeliveredTo();
                Map<String, Boolean> seenBy = msg.getSeenBy();
                boolean deliveredSet = delivered != null && Boolean.TRUE.equals(delivered.get(currentUser.getUid()));
                boolean seenSet = seenBy != null && Boolean.TRUE.equals(seenBy.get(currentUser.getUid()));
                if (!deliveredSet) {
                    batch.update(dc.getDocument().getReference(), "deliveredTo." + currentUser.getUid(), true);
                    hasSeenUpdates = true;
                }
                if (!seenSet) {
                    batch.update(dc.getDocument().getReference(), "seenBy." + currentUser.getUid(), true);
                    hasSeenUpdates = true;
                }
            } else if (!"seen".equals(msg.getStatus())) {
                batch.update(dc.getDocument().getReference(), "status", "seen");
                hasSeenUpdates = true;
            }
        }
        if (hasSeenUpdates) {
            batch.commit().addOnFailureListener(e -> Log.e("ChatActivity", "Failed to update seen states", e));
        }
    }

    private void setupPaginationScroll() {
        rvMessages.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (lm == null) return;
                int firstVisible = lm.findFirstVisibleItemPosition();
                if (paginatorController.shouldLoadOlder(firstVisible, isLoadingOlder, hasMoreOlder)) loadOlderMessages();
            }
        });
    }

    private void setupSwipeToReply() {
        ItemTouchHelper.SimpleCallback callback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int pos = viewHolder.getBindingAdapterPosition();
                if (pos >= 0 && pos < messageList.size()) {
    replyManager.startReply(messageList.get(pos));
}
                messageAdapter.notifyItemChanged(pos);
            }
        };
        new ItemTouchHelper(callback).attachToRecyclerView(rvMessages);
    }

    private void setupScrollToBottomButton() {
        if (fabScrollBottom == null) return;
        fabScrollBottom.setOnClickListener(v -> rvMessages.smoothScrollToPosition(Math.max(messageList.size() - 1, 0)));
        rvMessages.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (lm == null) return;
                int lastVisible = lm.findLastVisibleItemPosition();
                boolean farFromBottom = lastVisible < Math.max(0, messageList.size() - 4);
                fabScrollBottom.setVisibility(farFromBottom ? View.VISIBLE : View.GONE);
            }
        });
    }

    private void maybeAutoScrollOnNewMessage() {
        LinearLayoutManager lm = (LinearLayoutManager) rvMessages.getLayoutManager();
        if (lm == null) return;
        int lastVisible = lm.findLastVisibleItemPosition();
        if (lastVisible >= messageList.size() - 3) {
            rvMessages.smoothScrollToPosition(messageList.size() - 1);
            unreadCount = 0;
            if (tvUnreadDivider != null) tvUnreadDivider.setVisibility(View.GONE);
        }
    }

    private void updateUnreadUi(MessageModel message) {
        if (message == null || currentUser == null) return;
        boolean isIncoming = !TextUtils.equals(currentUser.getUid(), message.getSenderId());
        if (!isIncoming) return;

        LinearLayoutManager lm = (LinearLayoutManager) rvMessages.getLayoutManager();
        if (lm == null) return;
        int lastVisible = lm.findLastVisibleItemPosition();
        boolean atBottom = lastVisible >= Math.max(0, messageList.size() - 3);
        if (!atBottom) {
            unreadCount++;
            if (tvUnreadDivider != null) {
                tvUnreadDivider.setText("Unread messages (" + unreadCount + ")");
                tvUnreadDivider.setVisibility(View.VISIBLE);
            }
        }
    }

    private void attemptJumpToHighlightedMessage() {
        if (TextUtils.isEmpty(pendingHighlightMessageId)) return;
        for (int i = 0; i < messageList.size(); i++) {
            if (TextUtils.equals(pendingHighlightMessageId, messageList.get(i).getMessageId())) {
                rvMessages.scrollToPosition(i);
                messageAdapter.highlightMessage(pendingHighlightMessageId);
                pendingHighlightMessageId = "";
                return;
            }
        }
    }

    private void applyMessageFilter(String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.trim().toLowerCase();

        messageList.clear();
        if (TextUtils.isEmpty(query)) {
            messageList.addAll(allMessages);
        } else {
            for (MessageModel m : allMessages) {
                if (matchesMessageQuery(m, query)) {
                    messageList.add(m);
                }
            }
        }

        messageAdapter.notifyDataSetChanged();
        if (!messageList.isEmpty()) {
            rvMessages.scrollToPosition(messageList.size() - 1);
        }
    }

    private boolean matchesMessageQuery(MessageModel message, String query) {
        if (message == null) return false;

        String text = message.getMessageText();
        if (!TextUtils.isEmpty(text) && text.toLowerCase().contains(query)) return true;

        String type = message.getMessageType();
        if (!TextUtils.isEmpty(type) && type.toLowerCase().contains(query)) return true;

        String pollQuestion = message.getPollQuestion();
        if (!TextUtils.isEmpty(pollQuestion) && pollQuestion.toLowerCase().contains(query)) return true;

        String mediaName = message.getMediaName();
        return !TextUtils.isEmpty(mediaName) && mediaName.toLowerCase().contains(query);
    }

    private void handlePickedContact(Uri contactUri) {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(
                    contactUri,
                    new String[]{
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                            ContactsContract.CommonDataKinds.Phone.NUMBER
                    },
                    null,
                    null,
                    null
            );

            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                int numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);

                String name = nameIndex >= 0 ? cursor.getString(nameIndex) : "Unknown";
                String number = numberIndex >= 0 ? cursor.getString(numberIndex) : "";

                if (TextUtils.isEmpty(number)) {
                    showToast("Selected contact has no phone number");
                    return;
                }

                sendContactMessage(name, number);
            } else {
                showToast("Unable to read contact");
            }
        } catch (Exception e) {
            Log.e("ChatActivity", "Contact pick error", e);
            showToast("Failed to pick contact");
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    private void sendContactMessage(String name, String number) {
        if (currentUser == null) return;
        if (!ChatPermissionHelper.allowComposerMessaging(
        isGroup,
        canSendDirectMessage,
        restrictionState
)) {

    Toast.makeText(
            this,
            "Messaging is unavailable in this chat",
            Toast.LENGTH_SHORT
    ).show();

    return;
}

        String messageId = UUID.randomUUID().toString();
        long timestamp = System.currentTimeMillis();

        String contactText = "\uD83D\uDCDE " + name + " - " + number;

        MessageModel message = new MessageModel(
                messageId,
                currentUser.getUid(),
                contactText,
                timestamp,
                "sent",
                "contact"
        );
        MessageFactory.initializeGroupTracking(
        message,
        isGroup,
        currentUser.getUid()
        );

        db.collection("chats")
                .document(chatId)
                .collection("messages")
                .document(messageId)
                .set(message)
                .addOnSuccessListener(unused -> {
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("lastMessage", "📞 Contact");
                    updates.put("lastMessageTimestamp", timestamp);

                    db.collection("chats")
                            .document(chatId)
                            .update(updates)
                            .addOnFailureListener(e ->
                                    Log.e("ChatActivity", "Failed to update chat last message", e));
                })
                .addOnFailureListener(e ->
                        showToast("Failed to send contact"));
    }

    private void listenForOtherUserPresence() {
        userListener = db.collection("users")
                .document(otherUserId)
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null || !value.exists()) return;

                    com.vchat.app.models.UserModel user = value.toObject(com.vchat.app.models.UserModel.class);
                    if (user == null) return;

                    if (chatId.equals(user.getTypingTo())) {
                        toolbarChat.setSubtitle("Typing...");
                    } else if (user.isOnline()) {
                        toolbarChat.setSubtitle("Online");
                    } else {
                        toolbarChat.setSubtitle(com.vchat.app.utils.TimeUtils.getLastSeenFormatted(user.getLastSeen()));
                    }
                });
    }

    private void setupTypingIndicator() {
        if (isGroup) return;
        typingManager.setupTypingIndicator(); // Delegate to TypingManager
    }
    private void updateTypingStatus(String typingTo) {
        if (currentUser == null) return;
        if (
        ChatPermissionHelper.isDirectChatRestricted(
                isGroup,
                canSendDirectMessage,
                restrictionState
        )
                && !TextUtils.isEmpty(typingTo)
) {
    return;
}
        // This 'lastTypingValue' refers to the one managed by the TypingManager.
        // It's removed from ChatActivity, so we need to access it via TypingManager.
        if (typingTo.equals(typingManager.getLastTypingValue())) return;

        typingManager.setLastTypingValue(typingTo); // Update TypingManager's internal state
        db.collection("users")
                .document(currentUser.getUid())
                .update("typingTo", typingTo)
                .addOnFailureListener(e -> Log.e("ChatActivity", "Typing status update failed", e));
    }
    private void sendMessage() {
        String messageText = etMessage.getText().toString().trim();
        Log.d(DM_DEBUG_TAG, "sendMessage() entered; currentUserNull=" + (currentUser == null)
                + ", currentUserId=" + (currentUser == null ? "null" : currentUser.getUid())
                + ", otherUserId=" + otherUserId
                + ", chatId=" + chatId
                + ", isGroup=" + isGroup
                + ", messageLen=" + (messageText == null ? -1 : messageText.length()));
        if (TextUtils.isEmpty(messageText) || currentUser == null) return;
        Log.d(
        "ChatActivity",
        "sendMessage requested len="
                + messageText.length()
                + ", restricted="
                + ChatPermissionHelper.isDirectChatRestricted(
                        isGroup,
                        canSendDirectMessage,
                        restrictionState
                )
                + ", state="
                + restrictionState
);
        if (!ChatPermissionHelper.allowComposerMessaging(
        isGroup,
        canSendDirectMessage,
        restrictionState
)) {

    showToast("Messaging is unavailable in this chat");

    return;
}
        if (isGroup && isAdminOnlyMessaging) {

    groupPermissionManager.checkAdminPermission(
            chatId,
            currentUser.getUid(),
            allowed -> {

                if (!allowed) {

                    showToast("Only admins can send messages in this group");

                    return;
                }

                sendTextMessageInternal(messageText);
            }
    );

    return;
}
        sendTextMessageInternal(messageText);
    }

    private void sendTextMessageInternal(String messageText) {
        if (currentUser == null) return;
        Log.d(DM_DEBUG_TAG, "sendTextMessageInternal() start; currentUserId=" + currentUser.getUid()
                + ", otherUserId=" + otherUserId
                + ", chatId=" + chatId
                + ", text='" + messageText + "'");
        Log.d("ChatActivity", "sendTextMessageInternal start chatId=" + chatId + ", len=" + (messageText == null ? 0 : messageText.length()));

        if (TextUtils.isEmpty(chatId)) {
            Log.d(DM_DEBUG_TAG, "ABORT: chatId is null/empty before Firestore write");
            showToast("Invalid chat. Please try again.");
            return;
        }
        if (TextUtils.isEmpty(messageText)) {
            Log.d(DM_DEBUG_TAG, "ABORT: messageText is null/empty before Firestore write");
            showToast("Message cannot be empty.");
            return;
        }

        MessageModel message =
        MessageFactory.createTextMessage(
                currentUser.getUid(),
                messageText,
                composerController.getReplyToMessageId(),
                composerController.getReplyPreview(),
                isGroup
        );

        Log.d(DM_DEBUG_TAG, "Message model created; messageId=" + message.getMessageId()
                + ", senderId=" + message.getSenderId()
                + ", timestamp=" + message.getTimestamp()
                + ", type=" + message.getMessageType()
                + ", textNull=" + (message.getMessageText() == null)
                + ", senderNull=" + (message.getSenderId() == null));

        final String chatDocPath = "chats/" + chatId;
        final String messageDocPath =
        chatDocPath + "/messages/" + message.getMessageId();
        Log.d(DM_DEBUG_TAG, "Firestore write target chatDocPath=" + chatDocPath + ", messageDocPath=" + messageDocPath);
        Log.d(DM_DEBUG_TAG, "Verifying participant validation before set(); operations expected: get() -> set() -> update()");

        messageSender.sendTextMessage(
        chatId,
        currentUser.getUid(),
        otherUserId,
        isGroup,
        messageText,
        message,
        new MessageSender.SendCallback() {

            @Override
            public void onSuccess() {

                Log.d(
                        "ChatActivity",
                        "sendTextMessageInternal success"
                );

                etMessage.setText("");

                updateTypingStatus("");

                replyManager.clearReplyState();
            }

            @Override
            public void onError(String error) {

                Log.e(
                        "ChatActivity",
                        "send failed: " + error
                );

               showToast("Failed to send message: " + error);
            }
        }
);
    }

    
    private void showGroupMessageInfo(MessageModel message) {
        db.collection("chats")
                .document(chatId)
                .get()
                .addOnSuccessListener(chatSnap -> {
                    Map<String, Boolean> participants = (Map<String, Boolean>) chatSnap.get("participants");
                    Map<String, Boolean> deliveredTo = message.getDeliveredTo();
                    Map<String, Boolean> seenBy = message.getSeenBy();

                    int total = participants == null ? 0 : participants.size();
                    List<String> deliveredIds = new ArrayList<>();
                    List<String> seenIds = new ArrayList<>();
                    List<String> pendingIds = new ArrayList<>();

                    if (participants != null) {
                        for (String uid : participants.keySet()) {
                            boolean isSeen = seenBy != null && Boolean.TRUE.equals(seenBy.get(uid));
                            boolean isDelivered = deliveredTo != null && Boolean.TRUE.equals(deliveredTo.get(uid));
                            if (isSeen) {
                                seenIds.add(uid);
                            } else if (isDelivered) {
                                deliveredIds.add(uid);
                            } else {
                                pendingIds.add(uid);
                            }
                        }
                    }

                    int deliveredCount = deliveredIds.size() + seenIds.size();
                    String info = "Sent: " + total
                            + "   Delivered: " + deliveredCount
                            + "   Seen: " + seenIds.size()
                            + "   Pending: " + pendingIds.size();
                    showMessageInfoBottomSheet(info, deliveredIds, seenIds, pendingIds);
                })
                .addOnFailureListener(e -> showToast("Unable to load message info"));
    }

    private void showMessageInfoBottomSheet(String countsSummary, List<String> deliveredIds, List<String> seenIds, List<String> pendingIds) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View content = getLayoutInflater().inflate(R.layout.bottomsheet_message_info, null, false);
        dialog.setContentView(content);

        TextView tvCounts = content.findViewById(R.id.tv_info_counts);
        LinearLayout deliveredContainer = content.findViewById(R.id.layout_delivered_users);
        LinearLayout seenContainer = content.findViewById(R.id.layout_seen_users);
        LinearLayout pendingContainer = content.findViewById(R.id.layout_pending_users);
        tvCounts.setText(countsSummary);

        List<String> allUids = new ArrayList<>();
        allUids.addAll(deliveredIds);
        allUids.addAll(seenIds);
        allUids.addAll(pendingIds);
        if (allUids.isEmpty()) {
            addUserInfoRow(pendingContainer, "No participants", "");
            dialog.show();
            return;
        }

        List<Task<DocumentSnapshot>> tasks = new ArrayList<>();
        for (String uid : allUids) {
            tasks.add(db.collection("users").document(uid).get());
        }

        Tasks.whenAllSuccess(tasks)
                .addOnSuccessListener(results -> {
                    Map<String, String> usernameByUid = new HashMap<>();
                    for (Object result : results) {
                        DocumentSnapshot doc = (DocumentSnapshot) result;
                        if (doc == null || !doc.exists()) continue;
                        String uid = doc.getId();
                        String username = doc.getString("username");
                        usernameByUid.put(uid, TextUtils.isEmpty(username) ? uid : username);
                    }

                    fillUserInfoSection(deliveredContainer, deliveredIds, usernameByUid, "Delivered");
                    fillUserInfoSection(seenContainer, seenIds, usernameByUid, "Seen");
                    fillUserInfoSection(pendingContainer, pendingIds, usernameByUid, "Pending");
                })
                .addOnFailureListener(e -> {
                    fillUserInfoSection(deliveredContainer, deliveredIds, new HashMap<>(), "Delivered");
                    fillUserInfoSection(seenContainer, seenIds, new HashMap<>(), "Seen");
                    fillUserInfoSection(pendingContainer, pendingIds, new HashMap<>(), "Pending");
                });

        dialog.show();
    }

    private void fillUserInfoSection(LinearLayout container, List<String> userIds, Map<String, String> usernameByUid, String chipText) {
        if (container == null) return;
        container.removeAllViews();
        if (userIds == null || userIds.isEmpty()) {
            addUserInfoRow(container, "None", chipText);
            return;
        }
        for (String uid : userIds) {
            String name = usernameByUid.get(uid);
            addUserInfoRow(container, TextUtils.isEmpty(name) ? uid : name, chipText);
        }
    }

    private void addUserInfoRow(LinearLayout container, String name, String chip) {
        View row = getLayoutInflater().inflate(R.layout.item_message_info_user, container, false);
        TextView tvName = row.findViewById(R.id.tv_user_name);
        TextView tvChip = row.findViewById(R.id.tv_user_chip);
        tvName.setText(name);
        tvChip.setText(chip);
        container.addView(row);
    }

    private void listenGroupMessagingRules() {
        if (!isGroup) return;
        messagesListener = db.collection("chats")
                .document(chatId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null || snapshot == null || !snapshot.exists()) return;
                    String liveGroupName = snapshot.getString("chatName");
                    if (!TextUtils.isEmpty(liveGroupName)) {
                        if (getSupportActionBar() != null) {
                            getSupportActionBar().setTitle(liveGroupName.trim());
                        } else {
                            toolbarChat.setTitle(liveGroupName.trim());
                        }
                    }
                    Map<String, Boolean> participants = (Map<String, Boolean>) snapshot.get("participants");
                    int memberCount = participants == null ? 0 : participants.size();
                    String subtitle = memberCount > 0
                            ? (memberCount == 1 ? "1 member" : memberCount + " members")
                            : "Group chat";
                    toolbarChat.setSubtitle(subtitle);
                    Boolean onlyAdmins = snapshot.getBoolean("groupSettings.onlyAdminsCanMessage");
                    isAdminOnlyMessaging = Boolean.TRUE.equals(onlyAdmins);
                });
    }

    private void listenRelationshipState() {
        if (currentUser == null || TextUtils.isEmpty(otherUserId)) return;
        String relDocPath = "users/" + currentUser.getUid() + "/relationships/" + otherUserId;
        Log.d(REL_DEBUG_TAG, "listenRelationshipState start; path=" + relDocPath);
        relationshipListener = db.collection("users")
                .document(currentUser.getUid())
                .collection("relationships")
                .document(otherUserId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        Log.d("ChatActivity", "Relationship listener error for " + otherUserId + ", exception=" + Log.getStackTraceString(error));
                        Log.d(REL_DEBUG_TAG, "snapshot error path=" + relDocPath + ", exception=" + Log.getStackTraceString(error));
                        return;
                    }
                    if (snapshot == null || !snapshot.exists()) {
                        Log.d(REL_DEBUG_TAG, "snapshot missing path=" + relDocPath + ", snapshotNull=" + (snapshot == null));
                        resolvedRelationshipState = RelationshipStateHelper.CANON_UNKNOWN;
                        Log.d("ChatActivity", "Relationship missing for " + otherUserId + ", applying unknown/not-friends restriction");
                        applyRestriction("unknown", "Not friends", "Send friend request again to continue chatting.");
                        Log.d(REL_CLEANUP_TAG, "source=ChatActivity rawDbState=<missing> normalizedState=unknown finalUiState=NOT FRIENDS otherUserId=" + otherUserId);
                        return;
                    }
                    Log.d(REL_DEBUG_TAG, "snapshot received path=" + relDocPath + ", data=" + String.valueOf(snapshot.getData()));
                    String state = snapshot != null ? snapshot.getString("state") : "";
                    String blockedBy = snapshot != null ? snapshot.getString("blockedBy") : "";
                    String initiatedBy = snapshot != null ? snapshot.getString("initiatedBy") : "";
                    Log.d(REL_DEBUG_TAG, "raw fields state=" + state + ", blockedBy=" + blockedBy + ", initiatedBy=" + initiatedBy);
                    Log.d("ChatActivity", "Relationship resolved state=" + state + ", blockedBy=" + blockedBy + ", otherUserId=" + otherUserId);

                    String canonicalState = RelationshipStateHelper.resolveCanonicalState(state, blockedBy, currentUser.getUid());
                    if (!TextUtils.isEmpty(blockedBy)) {
                        canonicalState = TextUtils.equals(currentUser.getUid(), blockedBy)
                                ? RelationshipStateHelper.CANON_BLOCKED
                                : RelationshipStateHelper.CANON_BLOCKED_BY_USER;
                    }
                    Log.d(REL_SYNC_TAG, "source=ChatActivity rawState=" + state + " normalizedState=" + canonicalState
                            + " blockedBy=" + blockedBy + " otherUserId=" + otherUserId);
                    Log.d(REL_DEBUG_TAG, "before assign resolvedRelationshipState=" + resolvedRelationshipState + ", canonicalState=" + canonicalState);
                    resolvedRelationshipState = canonicalState;
                    Log.d(REL_DEBUG_TAG, "assigned resolvedRelationshipState=" + resolvedRelationshipState + " from canonical state");

                    String effectiveState = resolvedRelationshipState;
                    Log.d(REL_DEBUG_TAG, "effectiveState=" + effectiveState + " before restriction apply");

                    if (RelationshipStateHelper.CANON_BLOCKED.equals(effectiveState)) {
                        applyRestriction("blocked_by_me", "You blocked this user", "You can read chat history but cannot send messages.");
                    } else if (RelationshipStateHelper.CANON_BLOCKED_BY_USER.equals(effectiveState)) {
                        applyRestriction("blocked_me", "You were blocked by this user", "Messaging is disabled in this chat.");
                    } else if (RelationshipStateHelper.CANON_REMOVED.equals(effectiveState)) {
                        applyRestriction("removed", "You are no longer friends", "Send friend request again to continue chatting.");
                    } else if (RelationshipStateHelper.CANON_PENDING.equals(effectiveState)) {
                        applyRestriction("pending", "Friend request pending", "You can chat after request is accepted.");
                    } else if (RelationshipStateHelper.CANON_FRIEND.equals(effectiveState)) {
                        Log.d(REL_DEBUG_TAG, "valid friend state resolved; applying unrestricted");
                        applyRestriction("", "", "");
                    } else {
                        Log.d(REL_SYNC_TAG, "source=ChatActivity finalUIMapping state=unknown -> NOT FRIENDS");
                        applyRestriction("unknown", "Not friends", "Send friend request again to continue chatting.");
                    }
                    String uiState = RelationshipStateHelper.CANON_FRIEND.equals(effectiveState) ? "FRIEND"
                            : RelationshipStateHelper.CANON_PENDING.equals(effectiveState) ? "REQUEST PENDING"
                            : RelationshipStateHelper.CANON_REMOVED.equals(effectiveState) ? "NOT FRIENDS"
                            : RelationshipStateHelper.CANON_BLOCKED.equals(effectiveState) ? "BLOCKED"
                            : RelationshipStateHelper.CANON_BLOCKED_BY_USER.equals(effectiveState) ? "BLOCKED YOU"
                            : "NOT FRIENDS";
                    Log.d(REL_CLEANUP_TAG, "source=ChatActivity rawDbState=" + state + " normalizedState=" + canonicalState
                            + " finalUiState=" + uiState + " otherUserId=" + otherUserId);
                });
    }

    private void applyRestriction(String state, String title, String subtitle) {
        Log.d(REL_DEBUG_TAG, "before assign restrictionState=" + restrictionState + ", canSendDirectMessage=" + canSendDirectMessage
                + ", incomingState=" + state + ", resolvedRelationshipState=" + resolvedRelationshipState);
        restrictionState = state == null ? "" : state;
        canSendDirectMessage = isGroup || TextUtils.isEmpty(restrictionState);
        boolean restricted = !TextUtils.isEmpty(restrictionState);
        Log.d(REL_DEBUG_TAG, "after assign restrictionState=" + restrictionState + ", canSendDirectMessage=" + canSendDirectMessage
                + ", restricted=" + restricted + ", isGroup=" + isGroup);
        Log.d("ChatActivity", "applyRestriction state=" + restrictionState + ", restricted=" + restricted + ", canSendDirectMessage=" + canSendDirectMessage);
        if (layoutRestrictionBanner != null) {
            layoutRestrictionBanner.setVisibility(restricted ? View.VISIBLE : View.GONE);
        }
        if (tvRestrictionTitle != null) tvRestrictionTitle.setText(title);
        if (tvRestrictionSubtitle != null) tvRestrictionSubtitle.setText(subtitle);
        if (etMessage != null) etMessage.setEnabled(!restricted);
        if (btnSend != null) btnSend.setEnabled(!restricted);
        if (btnAttach != null) btnAttach.setEnabled(!restricted);
        if (btnSticker != null) btnSticker.setEnabled(!restricted);
        if (etMessage != null) etMessage.setVisibility(restricted ? View.GONE : View.VISIBLE);
        if (btnSend != null) btnSend.setVisibility(restricted ? View.GONE : View.VISIBLE);
        if (btnAttach != null) btnAttach.setVisibility(restricted ? View.GONE : View.VISIBLE);
        if (btnSticker != null) btnSticker.setVisibility(restricted ? View.GONE : View.VISIBLE);
        if (etMessage != null) etMessage.setAlpha(restricted ? 0.6f : 1f);
        if (btnSend != null) btnSend.setAlpha(restricted ? 0.6f : 1f);
        if (btnAttach != null) btnAttach.setAlpha(restricted ? 0.6f : 1f);
        if (btnSticker != null) btnSticker.setAlpha(restricted ? 0.6f : 1f);
        if (restricted) {
            updateTypingStatus("");
            closeAttachOptions();
            replyManager.clearReplyState();
            hideKeyboardNow();
        }
        boolean composerVisible = etMessage != null && etMessage.getVisibility() == View.VISIBLE;
        boolean composerEnabled = etMessage != null && etMessage.isEnabled() && (btnSend != null && btnSend.isEnabled());
        boolean actionsAvailable = !restricted;
        Log.d(CHAT_RESTRICT_TAG, "normalizedState=" + resolvedRelationshipState
                + " composerVisible=" + composerVisible
                + " composerEnabled=" + composerEnabled
                + " actionAvailability=" + actionsAvailable
                + " restrictionReason=" + restrictionState);
    }

    private void hideKeyboardNow() {
        if (etMessage == null) return;
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(etMessage.getWindowToken(), 0);
        }
        etMessage.clearFocus();
    }


    

    private boolean allowInteractiveMessageAction(String toast) {

    boolean restricted =
            ChatPermissionHelper.isDirectChatRestricted(
                    isGroup,
                    canSendDirectMessage,
                    restrictionState
            );

    Log.d(
            "ChatActivity",
            "allowInteractiveMessageAction restricted="
                    + restricted
                    + ", state="
                    + restrictionState
                    + ", canSend="
                    + canSendDirectMessage
    );

    if (!restricted) {
        return true;
    }

    showToast(toast);

    return false;
}

    private void validateChatAccess() {
        // Update UI based on current relationship state
        runOnUiThread(() -> {
            boolean shouldShowInput = !isGroup && canSendDirectMessage;
            btnSend.setVisibility(shouldShowInput ? View.VISIBLE : View.GONE);
            etMessage.setVisibility(shouldShowInput ? View.VISIBLE : View.GONE);
        });
        if (currentUser == null || TextUtils.isEmpty(chatId)) return;
        Log.d(REL_DEBUG_TAG, "validateChatAccess start chatId=" + chatId + ", otherUserId=" + otherUserId);
        db.collection("chats").document(chatId).get().addOnSuccessListener(snapshot -> {
            if (!isGroup) {
                if (snapshot == null || !snapshot.exists()) {
                    Log.d(REL_DEBUG_TAG, "validateChatAccess missing chat doc; will apply removed restriction");
                    Log.d("ChatActivity", "validateChatAccess failed: chat does not exist chatId=" + chatId);
                    applyRestriction("removed", "You are no longer friends", "Send friend request again to continue chatting.");
                    return;
                }
                Map<String, Boolean> participants = (Map<String, Boolean>) snapshot.get("participants");
                boolean iAmParticipant = participants != null && Boolean.TRUE.equals(participants.get(currentUser.getUid()));
                boolean otherParticipant = participants != null && Boolean.TRUE.equals(participants.get(otherUserId));
                Log.d(REL_DEBUG_TAG, "validateChatAccess participants=" + String.valueOf(participants)
                        + ", iAmParticipant=" + iAmParticipant + ", otherParticipant=" + otherParticipant);
                Log.d("ChatActivity", "validateChatAccess participants iAmParticipant=" + iAmParticipant + ", otherParticipant=" + otherParticipant);
                if (!iAmParticipant || !otherParticipant) {
                    Log.d(REL_DEBUG_TAG, "validateChatAccess applying removed due participant mismatch");
                    applyRestriction("removed", "You are no longer friends", "Send friend request again to continue chatting.");
                } else if ("removed".equals(restrictionState)) {
                    Log.d("ChatActivity", "Clearing stale removed restriction due valid chat participants");
                    Log.d(REL_DEBUG_TAG, "validateChatAccess clearing stale removed restriction");
                    applyRestriction("", "", "");
                }
            }
        }).addOnFailureListener(e -> {
            if (!isGroup) {
                Log.d(REL_DEBUG_TAG, "validateChatAccess read failed; exception=" + Log.getStackTraceString(e));
                Log.d("ChatActivity", "validateChatAccess failed to read chat " + chatId, e);
                applyRestriction("removed", "You are no longer friends", "Send friend request again to continue chatting.");
            }
        });
    }

    

    @Override
    public void onBackPressed() {
        if (layoutAttachOptions != null
                && layoutAttachOptions.getVisibility() == android.view.View.VISIBLE) {
            closeAttachOptions();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onPause() {
        super.onPause();
        typingManager.onPause(); // Delegate to TypingManager
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (messagesListener != null) {
            messagesListener.remove();
            messagesListener = null;
        }
        if (newMessagesListener != null) {
            newMessagesListener.remove();
            newMessagesListener = null;
        }

        if (userListener != null) { // This listener is now managed by TypingManager
            userListener.remove();
            userListener = null;
        }
        if (pinnedListener != null) {
            pinnedListener.remove();
            pinnedListener = null;
        }
        if (relationshipListener != null) {
            relationshipListener.remove();
            relationshipListener = null;
        }

        if (messageAdapter != null) {
            messageAdapter.clear();
        }
        if (selectionActionMode != null) {
            selectionActionMode.finish();
            selectionActionMode = null;
        }

        typingManager.onDestroy(); // Call destroy on TypingManager
    }
}
