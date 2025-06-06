// Pin Manager Module
class PinManager {
    constructor() {
        this.loadingElement = null;
        this.notificationElement = null;
    }

    canPinMessage() {
        // Enhanced permission check based on current chat context
        if (window.chatManager) {
            const chatInfo = window.chatManager.getCurrentChatInfo();
            if (chatInfo.type === 'room') {
                // In rooms: check user role (ADMIN/OWNER can pin)
                // For now, simplified to allow all users
                return true;
            } else if (chatInfo.type === 'conversation') {
                // In conversations: both participants can pin
                return true;
            }
        }
        return false;
    }

    showNotification(text) {
        if (!this.notificationElement) {
            this.notificationElement = document.createElement('div');
            this.notificationElement.id = 'tempNotification';
            this.notificationElement.className = 'temp-notification';
            document.body.appendChild(this.notificationElement);
        }

        this.notificationElement.textContent = text;
        this.notificationElement.classList.add('show');

        setTimeout(() => {
            this.notificationElement.classList.remove('show');
        }, 3000);
    }

    showLoadingMessage(text) {
        if (!this.loadingElement) {
            this.loadingElement = document.createElement('div');
            this.loadingElement.id = 'scrollLoadingMessage';
            this.loadingElement.className = 'scroll-loading-message';
            document.body.appendChild(this.loadingElement);
        }

        this.loadingElement.textContent = text;
        this.loadingElement.classList.add('show');
    }

    hideLoadingMessage() {
        if (this.loadingElement) {
            this.loadingElement.classList.remove('show');
        }
    }

    async togglePinMessage(messageId, pinned) {
        try {
            const pinBtn = document.querySelector(`[data-message-id="${messageId}"] .pin-btn`);
            if (pinBtn) {
                pinBtn.disabled = true;
                pinBtn.style.opacity = '0.5';
            }

            const token = window.authToken || window.authManager?.getAuthToken();
            const response = await fetch(`/api/messages/${messageId}/pin?pinned=${pinned}`, {
                method: 'POST',
                headers: {
                    'Authorization': 'Bearer ' + token
                }
            });

            if (response.ok) {
                console.log(`Message ${messageId} ${pinned ? 'pinned' : 'unpinned'} successfully`);
                // UI will be updated via WebSocket response
            } else {
                const error = await response.text();
                alert('Lỗi: ' + error);

                if (pinBtn) {
                    pinBtn.disabled = false;
                    pinBtn.style.opacity = '1';
                }
            }
        } catch (error) {
            console.error('Error toggling pin message:', error);
            alert('Không thể ' + (pinned ? 'ghim' : 'bỏ ghim') + ' tin nhắn');

            const pinBtn = document.querySelector(`[data-message-id="${messageId}"] .pin-btn`);
            if (pinBtn) {
                pinBtn.disabled = false;
                pinBtn.style.opacity = '1';
            }
        }
    }

    async loadPinnedMessages() {
        if (!window.chatManager) return;

        const chatInfo = window.chatManager.getCurrentChatInfo();
        if (!chatInfo.type || !chatInfo.id) return;

        try {
            const param = chatInfo.type === 'room' ?
                `roomId=${chatInfo.id}` :
                `conversationId=${chatInfo.id}`;

            const token = window.authToken || window.authManager?.getAuthToken();
            const response = await fetch(`/api/messages/pinned?${param}`, {
                headers: {
                    'Authorization': 'Bearer ' + token
                }
            });

            if (response.ok) {
                const pinnedMessages = await response.json();
                this.displayPinnedMessages(pinnedMessages);
            }
        } catch (error) {
            console.error('Error loading pinned messages:', error);
        }
    }

    displayPinnedMessages(pinnedMessages) {
        if (pinnedMessages.length === 0) {
            const existingContainer = document.getElementById('pinnedMessagesContainer');
            if (existingContainer) {
                existingContainer.remove();
            }
            return;
        }

        let pinnedContainer = document.getElementById('pinnedMessagesContainer');
        if (!pinnedContainer) {
            pinnedContainer = document.createElement('div');
            pinnedContainer.id = 'pinnedMessagesContainer';
            pinnedContainer.className = 'pinned-messages-container';

            const chatContent = document.getElementById('activeChatContent');
            const messagesContainer = document.getElementById('messagesContainer');
            chatContent.insertBefore(pinnedContainer, messagesContainer);
        }

        pinnedContainer.innerHTML = `
            <div class="pinned-messages-header" onclick="pinManager.togglePinnedMessagesVisibility()">
                📌 ${pinnedMessages.length} tin nhắn đã ghim
                <button class="toggle-pinned-btn" id="togglePinnedBtn">▼</button>
            </div>
            <div class="pinned-messages-list" id="pinnedMessagesList">
                ${pinnedMessages.map(msg => `
                    <div class="pinned-message-item" data-message-id="${msg.id}">
                        <div class="pinned-message-content">${msg.content}</div>
                        <div class="pinned-message-info">
                            <span>${msg.senderName}</span>
                            <span>${this.formatTime(msg.timestamp)}</span>
                            <button onclick="pinManager.scrollToMessage('${msg.id}')" class="goto-message-btn">Đi tới</button>
                        </div>
                    </div>
                `).join('')}
            </div>
        `;
    }

    togglePinnedMessagesVisibility() {
        const pinnedList = document.getElementById('pinnedMessagesList');
        const toggleBtn = document.getElementById('togglePinnedBtn');

        if (pinnedList && toggleBtn) {
            if (pinnedList.style.display === 'none') {
                pinnedList.style.display = 'block';
                toggleBtn.textContent = '▼';
            } else {
                pinnedList.style.display = 'none';
                toggleBtn.textContent = '▶';
            }
        }
    }

    handleMessagePinUpdate(message, action) {
        console.log('Handling pin update:', { messageId: message.id, action, isPinned: message.isPinned });

        const messageElement = document.querySelector(`[data-message-id="${message.id}"]`);
        if (messageElement) {
            this.updateMessagePinStatus(messageElement, message);
        }

        this.loadPinnedMessages();

        const actionText = action === 'PIN' ? 'đã ghim' : 'đã bỏ ghim';
        this.showNotification(`Tin nhắn ${actionText} bởi ${message.senderName}`);
    }

    updateMessagePinStatus(messageElement, message) {
        let pinnedIndicator = messageElement.querySelector('.message-pinned-indicator');

        if (message.isPinned) {
            if (!pinnedIndicator) {
                pinnedIndicator = document.createElement('div');
                pinnedIndicator.className = 'message-pinned-indicator';

                const messageContent = messageElement.querySelector('.message-content');
                messageElement.insertBefore(pinnedIndicator, messageContent);
            }
            pinnedIndicator.textContent = `📌 Tin nhắn đã được ghim${message.pinnedByUsername ? ` bởi ${message.pinnedByUsername}` : ''}`;
        } else {
            if (pinnedIndicator) {
                pinnedIndicator.remove();
            }
        }

        const pinBtn = messageElement.querySelector('.pin-btn');
        if (pinBtn) {
            pinBtn.textContent = message.isPinned ? '📌' : '📍';
            pinBtn.title = message.isPinned ? 'Bỏ ghim' : 'Ghim tin nhắn';
            pinBtn.disabled = false;
            pinBtn.style.opacity = '1';
            pinBtn.onclick = () => this.togglePinMessage(message.id, !message.isPinned);
        }
    }

    async scrollToMessage(messageId) {
        let messageElement = document.querySelector(`[data-message-id="${messageId}"]`);

        if (messageElement) {
            this.scrollToExistingMessage(messageElement);
            return;
        }

        try {
            this.showLoadingMessage('Đang tìm tin nhắn...');

            const pageInfo = await this.getMessagePageInfo(messageId);
            await this.loadMessagePage(pageInfo);

            messageElement = document.querySelector(`[data-message-id="${messageId}"]`);
            if (messageElement) {
                this.scrollToExistingMessage(messageElement);
            } else {
                this.hideLoadingMessage();
                this.showNotification('Không thể cuộn đến tin nhắn');
            }

        } catch (error) {
            console.error('Error finding message:', error);
            this.hideLoadingMessage();
            this.showNotification('Lỗi khi tìm tin nhắn: ' + error.message);
        }
    }

    scrollToExistingMessage(messageElement) {
        messageElement.scrollIntoView({ behavior: 'smooth', block: 'center' });

        messageElement.classList.add('highlighted-message');
        setTimeout(() => {
            messageElement.classList.remove('highlighted-message');
        }, 2000);

        this.hideLoadingMessage();
    }

    async getMessagePageInfo(messageId) {
        try {
            const token = window.authToken || window.authManager?.getAuthToken();
            const response = await fetch(`/api/messages/${messageId}/page?pageSize=50`, {
                headers: {
                    'Authorization': 'Bearer ' + token
                }
            });

            if (!response.ok) {
                throw new Error('Không thể tìm thấy tin nhắn');
            }

            return await response.json();
        } catch (error) {
            console.error('Error getting message page info:', error);
            throw error;
        }
    }

    async loadMessagePage(pageInfo) {
        const { pageNumber, roomId, conversationId } = pageInfo;

        try {
            let messagesResponse;
            const token = window.authToken || window.authManager?.getAuthToken();

            if (roomId) {
                messagesResponse = await fetch(`/api/rooms/${roomId}/messages?page=${pageNumber}&size=50`, {
                    headers: {
                        'Authorization': 'Bearer ' + token
                    }
                });
            } else if (conversationId) {
                messagesResponse = await fetch(`/api/conversations/${conversationId}/messages?page=${pageNumber}&size=50`, {
                    headers: {
                        'Authorization': 'Bearer ' + token
                    }
                });
            }

            if (messagesResponse && messagesResponse.ok) {
                const data = await messagesResponse.json();

                const wrapper = document.getElementById('messagesWrapper');
                const typingIndicator = document.getElementById('typingIndicator');
                wrapper.innerHTML = '';
                wrapper.appendChild(typingIndicator);

                if (window.displayMessages) {
                    window.displayMessages(data.content || [], false);
                }

                if (pageNumber > 0) {
                    await this.loadContextMessages(roomId, conversationId, pageNumber);
                }

            } else {
                throw new Error('Không thể tải trang tin nhắn');
            }

        } catch (error) {
            console.error('Error loading message page:', error);
            throw error;
        }
    }

    async loadContextMessages(roomId, conversationId, targetPage) {
        const contextPage = Math.max(0, targetPage - 1);

        try {
            let messagesResponse;
            const token = window.authToken || window.authManager?.getAuthToken();

            if (roomId) {
                messagesResponse = await fetch(`/api/rooms/${roomId}/messages?page=${contextPage}&size=50`, {
                    headers: {
                        'Authorization': 'Bearer ' + token
                    }
                });
            } else if (conversationId) {
                messagesResponse = await fetch(`/api/conversations/${conversationId}/messages?page=${contextPage}&size=50`, {
                    headers: {
                        'Authorization': 'Bearer ' + token
                    }
                });
            }

            if (messagesResponse && messagesResponse.ok) {
                const data = await messagesResponse.json();
                const contextMessages = data.content || [];

                for (const message of contextMessages.reverse()) {
                    this.prependMessage(message);
                }
            }

        } catch (error) {
            console.error('Error loading context messages:', error);
        }
    }

    prependMessage(message) {
        const wrapper = document.getElementById('messagesWrapper');
        const firstMessage = wrapper.querySelector('.message');

        if (window.createMessageElement) {
            const messageElement = window.createMessageElement(message);

            if (firstMessage) {
                wrapper.insertBefore(messageElement, firstMessage);
            } else {
                const typingIndicator = document.getElementById('typingIndicator');
                wrapper.insertBefore(messageElement, typingIndicator);
            }
        }
    }

    formatTime(dateString) {
        if (!dateString) return '';

        const date = new Date(dateString);
        const now = new Date();
        const diff = now - date;

        if (diff < 24 * 60 * 60 * 1000) {
            return date.toLocaleTimeString('vi-VN', {
                hour: '2-digit',
                minute: '2-digit'
            });
        } else {
            return date.toLocaleDateString('vi-VN', {
                day: '2-digit',
                month: '2-digit'
            });
        }
    }
}

// Export global functions for backward compatibility
window.pinManager = new PinManager();

// Export legacy functions
window.canPinMessage = () => window.pinManager.canPinMessage();
window.togglePinMessage = (messageId, pinned) => window.pinManager.togglePinMessage(messageId, pinned);
window.loadPinnedMessages = () => window.pinManager.loadPinnedMessages();
window.handleMessagePinUpdate = (message, action) => window.pinManager.handleMessagePinUpdate(message, action);
window.scrollToMessage = (messageId) => window.pinManager.scrollToMessage(messageId);