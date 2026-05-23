package com.vchat.app.chat;

import android.text.TextUtils;

import com.vchat.app.models.MessageModel;

public class ChatComposerController {
    private String replyToMessageId = "";
    private String replyPreview = "";

    public void setReplyMessage(MessageModel message) {
        if (message == null) return;
        replyToMessageId = message.getMessageId();
        replyPreview = message.getMessageText();
        if (!TextUtils.isEmpty(replyPreview) && replyPreview.length() > 60) {
            replyPreview = replyPreview.substring(0, 60) + "...";
        }
    }

    public String getReplyToMessageId() {
        return replyToMessageId;
    }

    public String getReplyPreview() {
        return replyPreview;
    }

    public void clearReply() {
        replyToMessageId = "";
        replyPreview = "";
    }
}
