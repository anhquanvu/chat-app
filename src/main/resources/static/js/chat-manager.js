// Chat Management Module
class ChatManager {
    constructor() {
        this.currentChatType = null;
        this.currentChatId = null;
        this.currentChatKey = null;
        this.typingUsers = new Set();
        this.typingTimer = null;
        this.isCurrentlyTyping = false;
    }

    async openConversation(conversationId, title) {
        // Leave current chat first
        await this.leaveCurrentChat();

        this.currentChatType = 'conversation';
        this.currentChatId = conversationId;
        this.currentChatKey = `conversation:${conversationId}`;

        // Update UI
        this.updateActiveListItem('conversationsList');
        this.showChatContent(title);

        // Setup WebSocket subscription for this conversation
        if (window.wsManager && window.wsManager.isConnected()) {
            window.wsManager.subscribeToChatUpdates(
                'conversation',
                conversationId,
                this.handleMessageUpdate.bind(this),
                this.handleTypingUpdate.bind(this)
            );

            // Send enter chat notification
            window.wsManager.sendChatEnter('conversation', conversationId);
        }

        // Load conversation messages
        await this.loadConversationMessages(conversationId);

        // Load pinned messages
        setTimeout(() => {
            if (window.loadPinnedMessages) {
                window.loadPinnedMessages();
            }
        }, 500);
    }

    async openRoom(roomId, title) {
        // Leave current chat first
        await this.leaveCurrentChat();

        this.currentChatType = 'room';
        this.currentChatId = roomId;
        this.currentChatKey = `room:${roomId}`;

        // Update UI
        this.updateActiveListItem('roomsList');
        this.showChatContent(title);

        // Setup WebSocket subscription for this room
        if (window.wsManager && window.wsManager.isConnected()) {
            window.wsManager.subscribeToChatUpdates(
                'room',
                roomId,
                this.handleMessageUpdate.bind(this),
                this.handleTypingUpdate.bind(this)
            );

            // Send enter chat notification
            window.wsManager.sendChatEnter('room', roomId);
        }

        // Load room messages
        await this.loadRoomMessages(roomId);

        // Load pinned messages
        setTimeout(() => {
            if (window.loadPinnedMessages) {
                window.loadPinnedMessages();
            }
        }, 500);
    }

    async leaveCurrentChat() {
        if (!this.currentChatKey) return;

        console.log('Leaving current chat:', this.currentChatKey);

        // Send leave notification
        if (window.wsManager && window.wsManager.isConnected() && this.currentChatType && this.currentChatId) {
            window.wsManager.sendChatLeave(this.currentChatType, this.currentChatId);
        }

        // Clear chat subscription
        if (window.wsManager) {
            window.wsManager.clearChatSubscription(this.currentChatKey);
        }

        // Clear current chat info
        this.currentChatType = null;
        this.currentChatId = null;
        this.currentChatKey = null;

        // Clear typing users
        this.typingUsers.clear();
        this.updateTypingIndicator();
    }

    updateActiveListItem(listId) {
        // Remove active class from all items in the list
        document.querySelectorAll(`#${listId} .list-item`).forEach(item => {
            item.classList.remove('active');
        });

        // Add active class to clicked item
        if (event && event.target) {
            const listItem = event.target.closest('.list-item');
            if (listItem) {
                listItem.classList.add('active');
            }
        }
    }

    showChatContent(title) {
        document.getElementById('emptyChatState').classList.add('hidden');
        document.getElementById('activeChatContent').classList.remove('hidden');
        document.getElementById('chatTitle').textContent = title;
    }

    handleMessageUpdate(response) {
        switch (response.action) {
            case 'SEND':
                if (window.showMessage) {
                    window.showMessage(response.data);
                }
                break;
            case 'UPDATE':
                if (window.updateMessage) {
                    window.updateMessage(response.data);
                }
                break;
            case 'DELETE':
                if (window.removeMessage) {
                    window.removeMessage(response.data);
                }
                break;
            case 'PIN':
            case 'UNPIN':
                if (window.handleMessagePinUpdate) {
                    window.handleMessagePinUpdate(response.data, response.action);
                }
                break;
        }
    }

    handleTypingUpdate(response) {
        if (window.handleTypingNotification) {
            window.handleTypingNotification(response);
        }
    }

    async loadConversationMessages(conversationId) {
        try {
            const response = await fetch(`/api/conversations/${conversationId}/messages?page=0&size=50`, {
                headers: {
                    'Authorization': 'Bearer ' + window.authToken
                }
            });

            if (response.ok) {
                const data = await response.json();
                if (window.displayMessages) {
                    window.displayMessages(data.content || []);
                }
            }
        } catch (error) {
            console.error('Error loading conversation messages:', error);
        }
    }

    async loadRoomMessages(roomId) {
        try {
            const response = await fetch(`/api/rooms/${roomId}/messages?page=0&size=50`, {
                headers: {
                    'Authorization': 'Bearer ' + window.authToken
                }
            });

            if (response.ok) {
                const data = await response.json();
                if (window.displayMessages) {
                    window.displayMessages(data.content || []);
                }
            }
        } catch (error) {
            console.error('Error loading room messages:', error);
        }
    }

    sendMessage() {
        const messageInput = document.getElementById('messageInput');
        const messageContent = messageInput.value.trim();

        if (!messageContent || !this.currentChatType || !this.currentChatId) return;

        if (!window.wsManager || !window.wsManager.isConnected()) {
            console.error('WebSocket not connected');
            return;
        }

        const chatMessage = {
            content: messageContent,
            type: 'CHAT'
        };

        const success = window.wsManager.sendMessage(this.currentChatType, this.currentChatId, chatMessage);

        if (success) {
            messageInput.value = '';
            messageInput.style.height = 'auto';
            this.sendStopTyping();
        }
    }

    sendTyping() {
        if (!window.wsManager || !window.wsManager.isConnected() || !this.currentChatType || !this.currentChatId) {
            return;
        }

        window.wsManager.sendTyping(this.currentChatType, this.currentChatId, true);
    }

    sendStopTyping() {
        if (!window.wsManager || !window.wsManager.isConnected() || !this.currentChatType || !this.currentChatId) {
            return;
        }

        window.wsManager.sendTyping(this.currentChatType, this.currentChatId, false);
        this.isCurrentlyTyping = false;
    }

    handleTypingInput() {
        if (!window.wsManager || !window.wsManager.isConnected()) return;

        if (!this.isCurrentlyTyping) {
            this.sendTyping();
            this.isCurrentlyTyping = true;
        }

        clearTimeout(this.typingTimer);
        this.typingTimer = setTimeout(() => {
            this.sendStopTyping();
        }, 1500);
    }

    updateTypingIndicator() {
        try {
            const indicator = document.getElementById('typingIndicator');
            const textElement = document.getElementById('typingText');

            if (!indicator || !textElement) {
                console.warn('Typing indicator elements not found');
                return;
            }

            if (this.typingUsers.size > 0) {
                const userList = Array.from(this.typingUsers);
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

                // Auto scroll when showing typing indicator
                setTimeout(() => {
                    const container = document.getElementById('messagesContainer');
                    if (container) {
                        const isAtBottom = container.scrollHeight - container.clientHeight <= container.scrollTop + 100;
                        if (isAtBottom) {
                            container.scrollTop = container.scrollHeight;
                        }
                    }
                }, 100);
            } else {
                indicator.classList.remove('show');
            }
        } catch (error) {
            console.error('Error updating typing indicator:', error);
        }
    }

    handleTypingNotification(response) {
        console.log('Processing typing notification:', response);

        try {
            let userId, username, isTyping;

            // Parse WebSocket response structure
            if (response.type === 'TYPING' && response.data) {
                const data = response.data;
                userId = data.userId;
                username = data.username || data.fullName;

                if (response.action === 'START') {
                    isTyping = true;
                } else if (response.action === 'STOP') {
                    isTyping = false;
                } else {
                    isTyping = data.isTyping;
                }
            } else if (response.data && response.data.userId !== undefined) {
                const data = response.data;
                userId = data.userId;
                username = data.username || data.fullName;
                isTyping = data.isTyping;
            } else if (response.userId !== undefined) {
                userId = response.userId;
                username = response.username || response.senderUsername;
                isTyping = response.isTyping;
            } else {
                console.warn('Unknown typing notification structure:', response);
                return;
            }

            // Validate required fields
            if (userId === undefined || username === undefined || isTyping === undefined) {
                console.warn('Missing required fields in typing notification:', {userId, username, isTyping});
                return;
            }

            // Ignore own typing notifications
            if (window.currentUser && userId === window.currentUser.id) {
                console.log('Ignoring own typing notification');
                return;
            }

            // Update typing users set
            if (isTyping) {
                console.log('Adding user to typing list:', username);
                this.typingUsers.add(username);
            } else {
                console.log('Removing user from typing list:', username);
                this.typingUsers.delete(username);
            }

            console.log('Current typing users:', Array.from(this.typingUsers));

            // Update UI
            this.updateTypingIndicator();

        } catch (error) {
            console.error('Error processing typing notification:', error, response);
        }
    }

    getCurrentChatInfo() {
        return {
            type: this.currentChatType,
            id: this.currentChatId,
            key: this.currentChatKey
        };
    }
}

// Export global instance
window.chatManager = new ChatManager();