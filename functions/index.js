const functions = require("firebase-functions");
const admin = require("firebase-admin");
admin.initializeApp();

/**
 * Triggers when a new message is written to a chat subcollection.
 * Handles both one-to-one chats and group chats.
 */
exports.onMessageCreated = functions.firestore
  .document("chats/{chatId}/messages/{messageId}")
  .onCreate(async (snapshot, context) => {
    const messageData = snapshot.data();
    if (!messageData) return null;

    const senderId = messageData.senderId;
    if (senderId === "system") return null; // Skip system messages

    const chatId = context.params.chatId;
    const messageText = messageData.messageText || "New message received";
    const messageType = messageData.messageType || "text";

    try {
      // 1. Fetch Chat Metadata
      const chatDoc = await admin.firestore().collection("chats").document(chatId).get();
      if (!chatDoc.exists) return null;

      const chatData = chatDoc.data();
      const participants = chatData.participants || {};
      const isGroup = !!chatData.isGroup;
      const groupName = chatData.chatName || "";

      // 2. Fetch Sender Profile
      const senderDoc = await admin.firestore().collection("users").document(senderId).get();
      const senderUsername = senderDoc.exists ? (senderDoc.data().username || "User") : "User";

      // 3. Determine body text based on messageType
      let displayBody = messageText;
      if (messageType === "image") {
        displayBody = "🖼 Photo";
      } else if (messageType === "contact") {
        displayBody = "📞 Contact";
      } else if (messageType === "poll") {
        displayBody = "📊 Poll: " + (messageData.pollQuestion || "New Poll");
      } else if (messageType === "event") {
        displayBody = "📅 Event: " + (messageData.eventName || "New Event");
      }

      // 4. Iterate and Notify all other participants
      const recipientIds = Object.keys(participants).filter(uid => uid !== senderId);

      const promises = recipientIds.map(async (recipientId) => {
        const recipientDoc = await admin.firestore().collection("users").document(recipientId).get();
        if (!recipientDoc.exists) return;

        const recipientData = recipientDoc.data();
        const fcmToken = recipientData.fcmToken;
        const activeChat = recipientData.activeChat;

        // Skip if FCM token is missing, or if they have the chat open currently
        if (!fcmToken || activeChat === chatId) {
          return;
        }

        // Build WhatsApp-style data payload
        const payload = {
          token: fcmToken,
          data: {
            type: "chat_message",
            chatId: chatId,
            isGroup: isGroup ? "true" : "false",
            groupName: groupName,
            otherUserId: senderId,
            otherUserName: senderUsername,
            title: isGroup ? groupName : senderUsername,
            body: displayBody
          },
          android: {
            priority: "high"
          }
        };

        return admin.messaging().send(payload)
          .then((response) => {
            console.log(`Notification sent successfully to ${recipientId}:`, response);
          })
          .catch((error) => {
            console.error(`Error sending notification to ${recipientId}:`, error);
          });
      });

      await Promise.all(promises);
    } catch (error) {
      console.error("onMessageCreated main task error:", error);
    }
    return null;
  });

/**
 * Triggers when a new friend request is sent.
 */
exports.onRequestCreated = functions.firestore
  .document("chat_requests/{requestId}")
  .onCreate(async (snapshot, context) => {
    const requestData = snapshot.data();
    if (!requestData || requestData.status !== "pending") return null;

    const senderId = requestData.senderId;
    const receiverId = requestData.receiverId;

    try {
      // 1. Fetch Sender Username
      const senderDoc = await admin.firestore().collection("users").document(senderId).get();
      const senderUsername = senderDoc.exists ? (senderDoc.data().username || "User") : "User";

      // 2. Fetch Receiver FCM Token
      const receiverDoc = await admin.firestore().collection("users").document(receiverId).get();
      if (!receiverDoc.exists) return null;

      const receiverData = receiverDoc.data();
      const fcmToken = receiverData.fcmToken;

      if (!fcmToken) return null;

      // Build data-only payload
      const payload = {
        token: fcmToken,
        data: {
          type: "chat_request",
          otherUserId: senderId,
          otherUserName: senderUsername,
          title: "New Friend Request",
          body: `${senderUsername} has sent you a friend request.`
        },
        android: {
          priority: "high"
        }
      };

      const response = await admin.messaging().send(payload);
      console.log(`Friend request notification sent to ${receiverId}:`, response);
    } catch (error) {
      console.error("onRequestCreated error:", error);
    }
    return null;
  });
