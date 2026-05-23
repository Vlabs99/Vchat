package com.vchat.app.chat;

import com.vchat.app.models.MessageModel;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MessageFactory {

    public static MessageModel createTextMessage(
            String currentUserId,
            String messageText,
            String replyToMessageId,
            String replyPreview,
            boolean isGroup
    ) {

        MessageModel message = new MessageModel(
                UUID.randomUUID().toString(),
                currentUserId,
                messageText,
                System.currentTimeMillis(),
                "sent",
                "text"
        );

        message.setReplyToMessageId(replyToMessageId);

        message.setReplyPreview(replyPreview);

        initializeGroupTracking(
                message,
                isGroup,
                currentUserId
        );

        return message;
    }

    public static void initializeGroupTracking(
            MessageModel message,
            boolean isGroup,
            String currentUserId
    ) {

        if (!isGroup || message == null || currentUserId == null) {
            return;
        }

        Map<String, Boolean> deliveredTo =
                new HashMap<>();

        deliveredTo.put(currentUserId, true);

        Map<String, Boolean> seenBy =
                new HashMap<>();

        seenBy.put(currentUserId, true);

        message.setDeliveredTo(deliveredTo);

        message.setSeenBy(seenBy);
    }
}