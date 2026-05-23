package com.vchat.app.notifications;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.vchat.app.R;
import com.vchat.app.activities.ChatActivity;
import com.vchat.app.activities.MainActivity;

import java.util.Map;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    public static final String CHANNEL_REQUESTS = "vchat_requests";
    public static final String CHANNEL_CHATS = "vchat_chats";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannels();
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        super.onMessageReceived(message);

        Map<String, String> data = message.getData();
        String type = data.get("type");
        String title = pickFirst(message.getNotification() != null ? message.getNotification().getTitle() : null, data.get("title"), "VChat");
        String body = pickFirst(message.getNotification() != null ? message.getNotification().getBody() : null, data.get("body"), "You have a new message");

        Intent openIntent;
        if (("chat_message".equals(type) || "request_accepted".equals(type)) && !TextUtils.isEmpty(data.get("chatId"))) {
            openIntent = new Intent(this, ChatActivity.class);
            openIntent.putExtra("chatId", data.get("chatId"));
            openIntent.putExtra("isGroup", "true".equalsIgnoreCase(data.get("isGroup")));
            openIntent.putExtra("groupName", data.get("groupName"));
            openIntent.putExtra("otherUserId", data.get("otherUserId"));
            openIntent.putExtra("otherUserName", data.get("otherUserName"));
        } else {
            openIntent = new Intent(this, MainActivity.class);
        }

        openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        int requestCode = (int) (System.currentTimeMillis() % Integer.MAX_VALUE);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                requestCode,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String channel = "chat_request".equals(type) ? CHANNEL_REQUESTS : CHANNEL_CHATS;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channel)
                .setSmallIcon(android.R.drawable.sym_action_chat)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent);

        NotificationManagerCompat.from(this).notify(requestCode, builder.build());
    }

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        saveTokenToFirestore(token);
    }

    private void saveTokenToFirestore(@NonNull String token) {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;

        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .update("fcmToken", token)
                .addOnFailureListener(e -> Log.e("MyFMS", "Failed to save token", e));
    }

    private String pickFirst(String a, String b, String fallback) {
        if (!TextUtils.isEmpty(a)) return a;
        if (!TextUtils.isEmpty(b)) return b;
        return fallback;
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        NotificationChannel requestChannel = new NotificationChannel(
                CHANNEL_REQUESTS,
                "Requests",
                NotificationManager.IMPORTANCE_HIGH
        );
        requestChannel.setDescription("Chat request notifications");

        NotificationChannel chatChannel = new NotificationChannel(
                CHANNEL_CHATS,
                "Chats",
                NotificationManager.IMPORTANCE_HIGH
        );
        chatChannel.setDescription("Message notifications");

        manager.createNotificationChannel(requestChannel);
        manager.createNotificationChannel(chatChannel);
    }
}
