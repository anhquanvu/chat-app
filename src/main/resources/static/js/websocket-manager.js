// WebSocket Connection Manager - Complete Version with Real-time User Status
class WebSocketManager {
    constructor() {
        this.stompClient = null;
        this.connected = false;
        this.isConnecting = false;
        this.connectionRetryCount = 0;
        this.maxRetries = 3;
        this.globalSubscriptions = new Map();
        this.chatSubscriptions = new Map();
        this.heartbeatInterval = null;
    }

    async connect(authToken, currentUser) {
        if (!authToken || !currentUser) {
            console.error('No auth token or user data');
            return false;
        }

        // Prevent duplicate connections
        if (this.connected || this.isConnecting) {
            console.log('WebSocket already connected or connecting, skipping...');
            return true;
        }

        // Disconnect existing connection if any
        if (this.stompClient && this.stompClient.connected) {
            console.log('Disconnecting existing WebSocket connection...');
            await this.disconnect();
        }

        return this.establishConnection(authToken, currentUser);
    }

    establishConnection(authToken, currentUser) {
        return new Promise((resolve, reject) => {
            this.isConnecting = true;
            console.log('Establishing WebSocket connection...');

            const socket = new SockJS('/ws');
            this.stompClient = Stomp.over(socket);

            // Disable debug in production
            this.stompClient.debug = function (str) {
                // console.log('STOMP: ' + str);
            };

            // Configure heartbeat
            this.stompClient.heartbeat.outgoing = 20000;
            this.stompClient.heartbeat.incoming = 20000;

            const connectHeaders = {
                'Authorization': 'Bearer ' + authToken,
                'username': currentUser.username,
                'userId': currentUser.id.toString()
            };

            this.stompClient.connect(
                connectHeaders,
                (frame) => {
                    console.log('✅ WebSocket Connected:', frame);
                    this.connected = true;
                    this.isConnecting = false;
                    this.connectionRetryCount = 0;

                    // Setup global subscriptions
                    this.setupGlobalSubscriptions();

                    resolve(true);
                },
                (error) => {
                    console.error('❌ WebSocket Connection Error:', error);
                    this.connected = false;
                    this.isConnecting = false;

                    if (error.headers && error.headers.message === 'Unauthorized') {
                        reject(new Error('AUTH_ERROR'));
                    } else {
                        reject(new Error('CONNECTION_ERROR'));
                    }
                }
            );

            // Handle disconnect events
            this.stompClient.ws.onclose = (event) => {
                console.log('WebSocket connection closed:', event.code, event.reason);
                this.connected = false;
                this.isConnecting = false;
                this.clearAllSubscriptions();

                // Only retry if not a manual logout
                if (authToken && event.code !== 1000) {
                    this.handleConnectionRetry();
                }
            };
        });
    }

    setupGlobalSubscriptions() {
        if (!this.stompClient || !this.connected) return;

        // Clear existing global subscriptions
        this.clearGlobalSubscriptions();

        try {
            // Subscribe to user status updates (NEW)
            const userStatusSub = this.stompClient.subscribe('/topic/user-status', (message) => {
                try {
                    const userStatus = JSON.parse(message.body);
                    this.handleUserStatusUpdate(userStatus);
                } catch (error) {
                    console.error('Error parsing user status update:', error);
                }
            });
            this.globalSubscriptions.set('userStatus', userStatusSub);

            // Subscribe to personal notifications
            const personalNotificationSub = this.stompClient.subscribe('/user/queue/message-status', (message) => {
                console.log('Personal notification:', message.body);
                try {
                    const response = JSON.parse(message.body);
                    if (response.type === 'MESSAGE_STATUS' && window.handleMessageStatusUpdate) {
                        window.handleMessageStatusUpdate(response.data);
                    }
                } catch (error) {
                    console.error('Error processing personal message status:', error);
                }
            });
            this.globalSubscriptions.set('personalNotification', personalNotificationSub);

            // Subscribe to read receipts
            const readReceiptSub = this.stompClient.subscribe('/user/queue/read-receipts', (message) => {
                try {
                    const response = JSON.parse(message.body);
                    if (response.type === 'READ_RECEIPT_UPDATE') {
                        if (window.handleReadReceiptUpdate) {
                            window.handleReadReceiptUpdate(response.data);
                        }
                    } else if (response.type === 'BATCH_READ_RECEIPT_UPDATE') {
                        if (window.handleBatchReadReceiptUpdate) {
                            window.handleBatchReadReceiptUpdate(response.data);
                        }
                    }
                } catch (error) {
                    console.error('Error processing read receipt:', error);
                }
            });
            this.globalSubscriptions.set('readReceipt', readReceiptSub);

            // Subscribe to reaction updates
            const reactionSub = this.stompClient.subscribe('/user/queue/reactions', (message) => {
                try {
                    const response = JSON.parse(message.body);
                    if (response.type === 'REACTION_UPDATE' && window.handleReactionUpdate) {
                        window.handleReactionUpdate(response.data);
                    }
                } catch (error) {
                    console.error('Error processing reaction update:', error);
                }
            });
            this.globalSubscriptions.set('reaction', reactionSub);

            console.log('✅ Global WebSocket subscriptions established');

        } catch (error) {
            console.error('Error setting up global subscriptions:', error);
        }
    }

    // NEW: Handle user status updates
    handleUserStatusUpdate(userStatus) {
        const { userId, username, status, sessionId } = userStatus;

        console.log(`🔄 User ${username} is now ${status}`);

        // Update global user status tracking
        if (!window.userStatusManager) {
            window.userStatusManager = new Map();
        }

        window.userStatusManager.set(userId, {
            status: status,
            lastSeen: new Date(),
            sessionId: sessionId
        });

        // Update UI immediately
        this.updateUserStatusInUI(userId, status);

        // Dispatch custom event for other components
        window.dispatchEvent(new CustomEvent('userStatusChanged', {
            detail: { userId, username, status, sessionId }
        }));

        // Update existing user status function if available
        if (window.updateUserStatus) {
            window.updateUserStatus(userStatus);
        }
    }

    // NEW: Update user status in UI elements
    updateUserStatusInUI(userId, status) {
        const isOnline = status === 'ONLINE';

        // Update trong danh sách contacts
        document.querySelectorAll(`[data-user-id="${userId}"]`).forEach(element => {
            const indicator = element.querySelector('.online-indicator');
            if (indicator) {
                indicator.style.display = isOnline ? 'block' : 'none';
            }

            // Cập nhật class cho styling
            element.classList.toggle('user-online', isOnline);
            element.classList.toggle('user-offline', !isOnline);

            // Cập nhật status text nếu có
            const statusText = element.querySelector('.user-status-text');
            if (statusText) {
                statusText.textContent = isOnline ? '• Đang hoạt động' : '• Không hoạt động';
                statusText.className = `user-status-text ${isOnline ? 'online' : 'offline'}`;
            }
        });

        // Update trong user search results
        document.querySelectorAll('.user-item').forEach(userItem => {
            const userIdAttr = userItem.getAttribute('onclick');
            if (userIdAttr && userIdAttr.includes(userId)) {
                const statusBadge = userItem.querySelector('.status-badge');
                if (statusBadge) {
                    statusBadge.textContent = isOnline ? 'Online' : 'Offline';
                    statusBadge.className = `status-badge ${isOnline ? 'status-online' : 'status-offline'}`;
                }
            }
        });

        // Update trong chat header nếu đang chat với user này
        const chatTitle = document.querySelector('.chat-title');
        if (chatTitle && chatTitle.dataset.userId === userId.toString()) {
            const chatStatus = document.querySelector('.chat-user-status');
            if (chatStatus) {
                chatStatus.textContent = isOnline ? 'Đang hoạt động' : 'Không hoạt động';
                chatStatus.className = `chat-user-status ${isOnline ? 'online' : 'offline'}`;
            }
        }
    }

    subscribeToChatUpdates(chatType, chatId, messageHandler, typingHandler) {
        if (!this.stompClient || !this.connected) {
            console.warn('Cannot subscribe: WebSocket not connected');
            return;
        }

        const chatKey = `${chatType}:${chatId}`;

        // Unsubscribe from previous chat if exists
        this.clearChatSubscriptions();

        try {
            // Subscribe to messages
            const messageDestination = chatType === 'room' ? `/topic/room/${chatId}` : `/topic/conversation/${chatId}`;
            const messageSub = this.stompClient.subscribe(messageDestination, (message) => {
                try {
                    const response = JSON.parse(message.body);
                    if (messageHandler) {
                        messageHandler(response);
                    }
                } catch (error) {
                    console.error('Error processing message:', error);
                }
            });

            // Subscribe to typing indicators
            const typingDestination = chatType === 'room' ? `/topic/room/${chatId}/typing` : `/topic/conversation/${chatId}/typing`;
            const typingSub = this.stompClient.subscribe(typingDestination, (message) => {
                try {
                    const response = JSON.parse(message.body);
                    if (typingHandler) {
                        typingHandler(response);
                    }
                } catch (error) {
                    console.error('Error processing typing indicator:', error);
                }
            });

            this.chatSubscriptions.set(`${chatKey}:message`, messageSub);
            this.chatSubscriptions.set(`${chatKey}:typing`, typingSub);

            console.log(`✅ Subscribed to ${chatKey} updates`);

        } catch (error) {
            console.error('Error subscribing to chat updates:', error);
        }
    }

    sendMessage(chatType, chatId, messageData) {
        if (!this.stompClient || !this.connected) {
            console.warn('Cannot send message: WebSocket not connected');
            return false;
        }

        try {
            const destination = chatType === 'room' ? `/app/room/${chatId}/send` : `/app/conversation/${chatId}/send`;
            this.stompClient.send(destination, {}, JSON.stringify(messageData));
            return true;
        } catch (error) {
            console.error('Error sending message:', error);
            return false;
        }
    }

    sendTyping(chatType, chatId, isTyping) {
        if (!this.stompClient || !this.connected) return;

        try {
            const destination = chatType === 'room' ? `/app/room/${chatId}/typing` : `/app/conversation/${chatId}/typing`;
            this.stompClient.send(destination, {}, JSON.stringify({ isTyping }));
        } catch (error) {
            console.error('Error sending typing indicator:', error);
        }
    }

    sendChatEnter(chatType, chatId) {
        if (!this.stompClient || !this.connected) return;

        try {
            const destination = chatType === 'room' ? `/app/room/${chatId}/enter` : `/app/conversation/${chatId}/enter`;
            this.stompClient.send(destination, {}, JSON.stringify({ action: 'enter' }));
        } catch (error) {
            console.error('Error sending chat enter:', error);
        }
    }

    sendChatLeave(chatType, chatId) {
        if (!this.stompClient || !this.connected) return;

        try {
            const destination = chatType === 'room' ? `/app/room/${chatId}/leave` : `/app/conversation/${chatId}/leave`;
            this.stompClient.send(destination, {}, JSON.stringify({ action: 'leave' }));
        } catch (error) {
            console.error('Error sending chat leave:', error);
        }
    }

    sendVisibilityUpdate(messageId, visible) {
        if (!this.stompClient || !this.connected) return;

        try {
            this.stompClient.send('/app/message/visibility', {}, JSON.stringify({
                messageId: messageId,
                visible: visible
            }));
        } catch (error) {
            console.error('Error sending visibility update:', error);
        }
    }

    clearChatSubscriptions() {
        for (const [key, subscription] of this.chatSubscriptions) {
            if (subscription && subscription.unsubscribe) {
                subscription.unsubscribe();
            }
        }
        this.chatSubscriptions.clear();
    }

    clearGlobalSubscriptions() {
        for (const [key, subscription] of this.globalSubscriptions) {
            if (subscription && subscription.unsubscribe) {
                subscription.unsubscribe();
            }
        }
        this.globalSubscriptions.clear();
    }

    clearAllSubscriptions() {
        this.clearGlobalSubscriptions();
        this.clearChatSubscriptions();
    }

    handleConnectionRetry() {
        if (this.connectionRetryCount >= this.maxRetries) {
            console.error('Max connection retries reached, giving up');
            return;
        }

        this.connectionRetryCount++;
        const retryDelay = Math.min(1000 * Math.pow(2, this.connectionRetryCount), 10000);

        console.log(`Retrying WebSocket connection in ${retryDelay}ms (attempt ${this.connectionRetryCount}/${this.maxRetries})`);

        setTimeout(() => {
            if (!this.connected && window.authToken) {
                console.log(`Attempting WebSocket reconnection (${this.connectionRetryCount}/${this.maxRetries})...`);
                this.connect(window.authToken, window.currentUser);
            }
        }, retryDelay);
    }

    async disconnect() {
        return new Promise((resolve) => {
            if (this.heartbeatInterval) {
                clearInterval(this.heartbeatInterval);
                this.heartbeatInterval = null;
            }

            if (this.stompClient && this.connected) {
                this.clearAllSubscriptions();
                this.stompClient.disconnect(() => {
                    console.log('STOMP disconnected cleanly');
                    this.connected = false;
                    this.isConnecting = false;
                    this.stompClient = null;
                    resolve();
                });
            } else {
                this.connected = false;
                this.isConnecting = false;
                this.stompClient = null;
                resolve();
            }
        });
    }

    isConnected() {
        return this.connected && this.stompClient && this.stompClient.connected;
    }
}

// Initialize user status tracking when WSManager loads
function initializeUserStatusTracking() {
    // Initialize status manager
    if (!window.userStatusManager) {
        window.userStatusManager = new Map();
    }

    // Add CSS styles for user status
    const userStatusStyles = `
        .user-online .item-name {
            font-weight: 600;
        }

        .user-offline .item-name {
            color: #6c757d;
        }

        .user-status-text {
            font-size: 0.8em;
            color: #6c757d;
        }

        .user-status-text.online {
            color: #28a745;
        }

        .online-indicator {
            width: 8px;
            height: 8px;
            background-color: #28a745;
            border-radius: 50%;
            border: 2px solid #fff;
            margin-left: auto;
        }

        .chat-user-status {
            font-size: 0.8em;
            margin-left: 10px;
        }

        .chat-user-status.online {
            color: #28a745;
        }

        .chat-user-status.offline {
            color: #6c757d;
        }

        .status-badge {
            padding: 2px 8px;
            border-radius: 12px;
            font-size: 0.7em;
            font-weight: 500;
            text-transform: uppercase;
        }

        .status-online {
            background-color: #d4edda;
            color: #155724;
        }

        .status-offline {
            background-color: #f8d7da;
            color: #721c24;
        }
    `;

    // Add styles to document
    const styleSheet = document.createElement('style');
    styleSheet.textContent = userStatusStyles;
    document.head.appendChild(styleSheet);

    console.log('✅ User status tracking initialized');
}

// Export global instance
window.wsManager = new WebSocketManager();

// Initialize user status tracking when script loads
document.addEventListener('DOMContentLoaded', function() {
    initializeUserStatusTracking();
});