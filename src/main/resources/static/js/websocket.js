// WebSocket connection and message handling
function connectWebSocket() {
    if (!authToken || !currentUser) {
        console.error('No auth token or user data for WebSocket connection');
        return;
    }

    const socket = new SockJS(APP_CONFIG.WS_ENDPOINT);
    stompClient = Stomp.over(socket);
    stompClient.debug = null; // Disable debug logs

    const connectHeaders = {
        'Authorization': 'Bearer ' + authToken
    };

    stompClient.connect(connectHeaders, function (frame) {
        console.log('🔌 Connected to WebSocket:', frame);
        connected = true;

        // Subscribe to user status updates
        stompClient.subscribe(WS_DESTINATIONS.TOPICS.USER_STATUS, function (message) {
            const userStatus = JSON.parse(message.body);
            updateUserStatus(userStatus);
        });

    }, function(error) {
        console.error('❌ WebSocket connection error:', error);
        connected = false;

        if (error.includes('401') || error.includes('403')) {
            refreshAuthToken();
        }
    });
}

function subscribeToChat() {
    if (!stompClient || !connected || !currentChatType || !currentChatId) {
        return;
    }

    const destination = currentChatType === 'room'
        ? `${WS_DESTINATIONS.TOPICS.ROOM}/${currentChatId}`
        : `${WS_DESTINATIONS.TOPICS.CONVERSATION}/${currentChatId}`;

    stompClient.subscribe(destination, function (message) {
        const response = JSON.parse(message.body);
        handleWebSocketMessage(response);
    });

    console.log('🎯 Subscribed to:', destination);
}

function handleWebSocketMessage(response) {
    console.log('📨 WebSocket message received:', response);

    switch (response.type) {
        case 'MESSAGE':
            if (response.action === 'SEND') {
                showMessage(response.data);
            } else if (response.action === 'UPDATE') {
                updateMessage(response.data);
            } else if (response.action === 'DELETE') {
                deleteMessage(response.data);
            } else if (response.action === 'STATUS_UPDATE') {
                updateMessageStatus(response.data);
            }
            break;

        case 'TYPING':
            handleTypingNotification(response.data);
            break;

        case 'REACTION':
            handleReactionUpdate(response.data);
            break;

        default:
            // Handle legacy message format
            if (response.data) {
                if (response.data.type === MESSAGE_TYPES.CHAT) {
                    showMessage(response.data);
                } else if (response.data.isTyping !== undefined) {
                    handleTypingNotification(response.data);
                }
            }
            break;
    }
}

function sendMessage() {
    const messageContent = document.getElementById('messageInput').value.trim();
    if (!messageContent || !stompClient || !connected || !currentChatType || !currentChatId) {
        return;
    }

    const chatMessage = {
        content: messageContent,
        type: MESSAGE_TYPES.CHAT
    };

    const destination = currentChatType === 'room'
        ? `${WS_DESTINATIONS.CHAT.ROOM}/${currentChatId}`
        : `${WS_DESTINATIONS.CHAT.CONVERSATION}/${currentChatId}`;

    stompClient.send(destination, {}, JSON.stringify(chatMessage));

    document.getElementById('messageInput').value = '';
    sendStopTyping();

    console.log('📤 Message sent to:', destination);
}

function sendTyping() {
    if (!stompClient || !connected || !currentChatType || !currentChatId) {
        return;
    }

    const typingMessage = { typing: true };
    const destination = currentChatType === 'room'
        ? `${WS_DESTINATIONS.CHAT.TYPING_ROOM}/${currentChatId}`
        : `${WS_DESTINATIONS.CHAT.TYPING_CONVERSATION}/${currentChatId}`;

    stompClient.send(destination, {}, JSON.stringify(typingMessage));
}

function sendStopTyping() {
    if (!stompClient || !connected || !currentChatType || !currentChatId) {
        return;
    }

    const stopTypingMessage = { typing: false };
    const destination = currentChatType === 'room'
        ? `${WS_DESTINATIONS.CHAT.TYPING_ROOM}/${currentChatId}`
        : `${WS_DESTINATIONS.CHAT.TYPING_CONVERSATION}/${currentChatId}`;

    stompClient.send(destination, {}, JSON.stringify(stopTypingMessage));
    isCurrentlyTyping = false;
}

function handleTypingNotification(data) {
    // Skip own typing notifications
    if (data.userId === currentUser.id) return;

    const typingData = data.data || data;
    const username = typingData.username || typingData.senderUsername;
    const isTyping = typingData.isTyping !== undefined ? typingData.isTyping : typingData.typing;

    if (isTyping) {
        typingUsers.add(username);
    } else {
        typingUsers.delete(username);
    }

    updateTypingIndicator();
}

function updateTypingIndicator() {
    const indicator = document.getElementById('typingIndicator');
    const textElement = document.getElementById('typingText');

    if (typingUsers.size > 0) {
        const userList = Array.from(typingUsers);
        let typingText = '';

        if (userList.length === 1) {
            typingText = `${userList[0]} đang nhập`;
        } else if (userList.length === 2) {
            typingText = `${userList[0]} và ${userList[1]} đang nhập`;
        } else {
            typingText = `${userList.length} người đang nhập`;
        }

        textElement.textContent = typingText;
        indicator.classList.add('show');
    } else {
        indicator.classList.remove('show');
    }
}

function handleReactionUpdate(reactions) {
    console.log('👍 Reaction update:', reactions);
    // This would typically update the UI based on the reaction data
    // Implementation depends on the message structure and UI requirements
}

function updateUserStatus(userStatus) {
    console.log('👤 User status update:', userStatus);
    // Update online status in contact list and other UI elements
    // Implementation depends on the UI requirements
}

function setupTypingDetection() {
    const messageInput = document.getElementById('messageInput');

    messageInput.addEventListener('input', function() {
        if (!connected) return;

        if (!isCurrentlyTyping) {
            sendTyping();
            isCurrentlyTyping = true;
        }

        clearTimeout(typingTimer);
        typingTimer = setTimeout(() => {
            sendStopTyping();
        }, APP_CONFIG.TYPING_TIMEOUT);
    });
}

console.log('🌐 WebSocket module loaded');