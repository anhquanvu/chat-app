// WebSocket Connection Manager
class WebSocketManager {
    constructor() {
        this.stompClient = null;
        this.connected = false;
        this.isConnecting = false;
        this.connectionRetryCount = 0;
        this.maxRetries = 3;
        this.globalSubscriptions = new Map();
        this.chatSubscriptions = new Map();
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
            this.stompClient.heartbeat.outgoing = 25000;
            this.stompClient.heartbeat.incoming = 25000;

            const connectHeaders = {
                'Authorization': 'Bearer ' + authToken
            };

            this.stompClient.connect(connectHeaders,
                (frame) => {
                    console.log('✅ WebSocket connected successfully:', frame);
                    this.connected = true;
                    this.isConnecting = false;
                    this.connectionRetryCount = 0;

                    this.setupGlobalSubscriptions();
                    resolve(true);
                },
                (error) => {
                    console.error('❌ WebSocket connection error:', error);
                    this.connected = false;
                    this.isConnecting = false;

                    if (error.toString().includes('401') || error.toString().includes('403')) {
                        console.log('Authentication error, need token refresh');
                        reject(new Error('AUTH_ERROR'));
                    } else {
                        this.handleConnectionRetry();
                        reject(error);
                    }
                }
            );

            // Handle socket close
            socket.onclose = (event) => {
                console.log('WebSocket connection closed:', event);
                this.connected = false;
                this.isConnecting = false;

                // Clear all subscriptions on close
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

        // Subscribe to user status updates
        const userStatusSub = this.stompClient.subscribe('/topic/user-status', (message) => {
            const userStatus = JSON.parse(message.body);
            if (window.updateUserStatus) {
                window.updateUserStatus(userStatus);
            }
        });

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

        // Subscribe to read receipts
        const readReceiptSub = this.stompClient.subscribe('/user/queue/read-receipts', (message) => {
            try {
                const response = JSON.parse(message.body);
                console.log('Read receipt received:', response);

                switch (response.type) {
                    case 'MESSAGE_READ':
                        if (window.handleReadReceiptUpdate) {
                            window.handleReadReceiptUpdate(response.data);
                        }
                        break;
                    case 'MESSAGE_BATCH_READ':
                        if (window.handleBatchReadReceiptUpdate) {
                            window.handleBatchReadReceiptUpdate(response.data);
                        }
                        break;
                }
            } catch (error) {
                console.error('Error processing read receipt:', error);
            }
        });

        // Store subscriptions for cleanup
        this.globalSubscriptions.set('userStatus', userStatusSub);
        this.globalSubscriptions.set('personalNotification', personalNotificationSub);
        this.globalSubscriptions.set('readReceipt', readReceiptSub);

        console.log('✅ Global WebSocket subscriptions established');
    }

    subscribeToChatUpdates(chatType, chatId, messageHandler, typingHandler) {
        if (!this.stompClient || !this.connected) {
            console.warn('Cannot subscribe - WebSocket not connected');
            return null;
        }

        const chatKey = `${chatType}:${chatId}`;

        // Clear existing chat subscription
        this.clearChatSubscription(chatKey);

        const destination = `${chatType === 'room' ? '/topic/room' : '/topic/conversation'}/${chatId}`;

        const subscription = this.stompClient.subscribe(destination, (message) => {
            try {
                const response = JSON.parse(message.body);
                console.log(`${chatType} message received:`, response);

                switch (response.type) {
                    case 'MESSAGE':
                        if (messageHandler) {
                            messageHandler(response);
                        }
                        break;
                    case 'TYPING':
                        if (typingHandler) {
                            typingHandler(response);
                        }
                        break;
                    case 'REACTION':
                        if (window.handleReactionUpdate) {
                            window.handleReactionUpdate(response.data);
                        }
                        break;
                    case 'MESSAGE_STATUS':
                        if (window.handleMessageStatusUpdate) {
                            window.handleMessageStatusUpdate(response.data);
                        }
                        break;
                    case 'MESSAGE_READ':
                        if (window.handleReadReceiptUpdate) {
                            window.handleReadReceiptUpdate(response.data);
                        }
                        break;
                    case 'MESSAGE_BATCH_READ':
                        if (window.handleBatchReadReceiptUpdate) {
                            window.handleBatchReadReceiptUpdate(response.data);
                        }
                        break;
                }
            } catch (error) {
                console.error(`Error processing ${chatType} WebSocket message:`, error, message.body);
            }
        });

        this.chatSubscriptions.set(chatKey, subscription);
        return subscription;
    }

    sendChatEnter(chatType, chatId) {
        if (!this.stompClient || !this.connected) return;

        const destination = `/app/chat/${chatType}/${chatId}/enter`;
        this.stompClient.send(destination, {}, JSON.stringify({}));
    }

    sendChatLeave(chatType, chatId) {
        if (!this.stompClient || !this.connected) return;

        const destination = `/app/chat/${chatType}/${chatId}/leave`;
        this.stompClient.send(destination, {}, JSON.stringify({}));
    }

    sendMessage(chatType, chatId, message) {
        if (!this.stompClient || !this.connected) return false;

        const destination = `/app/chat/${chatType}/${chatId}`;
        this.stompClient.send(destination, {}, JSON.stringify(message));
        return true;
    }

    sendTyping(chatType, chatId, isTyping) {
        if (!this.stompClient || !this.connected) return;

        const destination = `/app/chat/typing/${chatType}/${chatId}`;
        this.stompClient.send(destination, {}, JSON.stringify({ typing: isTyping }));
    }

    sendVisibilityUpdate(messageId, visible) {
        if (!this.stompClient || !this.connected) return;

        this.stompClient.send("/app/message/visibility", {}, JSON.stringify({
            messageId: messageId,
            visible: visible
        }));
    }

    clearChatSubscription(chatKey) {
        const subscription = this.chatSubscriptions.get(chatKey);
        if (subscription && subscription.unsubscribe) {
            subscription.unsubscribe();
            this.chatSubscriptions.delete(chatKey);
            console.log(`Cleared chat subscription: ${chatKey}`);
        }
    }

    clearChatSubscriptions() {
        for (const [chatKey, subscription] of this.chatSubscriptions) {
            if (subscription && subscription.unsubscribe) {
                subscription.unsubscribe();
            }
        }
        this.chatSubscriptions.clear();
        console.log('Cleared all chat subscriptions');
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

// Export global instance
window.wsManager = new WebSocketManager();