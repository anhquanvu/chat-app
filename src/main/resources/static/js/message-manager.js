// Message Manager
class MessageManager {
    constructor() {
        this.currentMessageForReaction = null;
    }

    displayMessages(messages, appendToTop = false) {
        const wrapper = document.getElementById('messagesWrapper');
        const typingIndicator = document.getElementById('typingIndicator');

        if (!appendToTop) {
            wrapper.innerHTML = '';
            wrapper.appendChild(typingIndicator);
        }

        const sortedMessages = messages.slice().reverse();

        for (const message of sortedMessages) {
            this.showMessage(message, false); // Don't scroll for each message
        }

        if (!appendToTop) {
            this.scrollToBottom();
        }
    }

    showMessage(message, shouldScroll = true) {
        const wrapper = document.getElementById('messagesWrapper');
        const typingIndicator = document.getElementById('typingIndicator');

        // Check if message already exists
        const existingMessage = wrapper.querySelector(`[data-message-id="${message.id}"]`);
        if (existingMessage) {
            return;
        }

        const messageElement = this.createMessageElement(message);

        // Insert before typing indicator
        wrapper.insertBefore(messageElement, typingIndicator);

        // Observer for visibility tracking
        if (window.messageVisibilityTracker) {
            window.messageVisibilityTracker.observeNewMessage(messageElement);
        }

        // Auto scroll for new messages
        if (shouldScroll) {
            const container = document.getElementById('messagesContainer');
            const isAtBottom = container.scrollHeight - container.clientHeight <= container.scrollTop + 100;

            // Only scroll if user is near bottom or it's their own message
            if (isAtBottom || (window.currentUser && message.senderId === window.currentUser.id)) {
                requestAnimationFrame(() => {
                    container.scrollTop = container.scrollHeight;
                });
            }
        }

        // Mark message as read if not own message
        if (window.currentUser && message.senderId !== window.currentUser.id && window.chatManager) {
            const chatInfo = window.chatManager.getCurrentChatInfo();
            if (chatInfo.type && chatInfo.id) {
                setTimeout(() => this.markMessageAsRead(message.id), 1000);
            }
        }
    }

    createMessageElement(message) {
        const messageElement = document.createElement('div');
        const isOwnMessage = window.currentUser && message.senderId === window.currentUser.id;
        let messageClass = 'message ';

        if (message.type === 'JOIN' || message.type === 'LEAVE') {
            messageClass += 'system';
            messageElement.innerHTML = `
                <div class="message-content">${message.content || `${message.senderName} đã ${message.type === 'JOIN' ? 'tham gia' : 'rời khỏi'} phòng chat`}</div>
            `;
        } else if (message.type === 'CHAT') {
            messageClass += isOwnMessage ? 'own' : 'chat';
            const timestamp = new Date(message.timestamp).toLocaleTimeString('vi-VN', {
                hour: '2-digit',
                minute: '2-digit'
            });

            const messageId = message.id;
            const statusIcon = this.getStatusIcon(message.status);
            const reactions = message.reactions || [];

            messageElement.innerHTML = `
                ${!isOwnMessage ? `<div class="message-header">
                    <span class="message-sender">${message.senderName}</span>
                    <span class="message-time">${timestamp}</span>
                </div>` : ''}
                ${message.isPinned ? `<div class="message-pinned-indicator">📌 Tin nhắn đã được ghim${message.pinnedByUsername ? ` bởi ${message.pinnedByUsername}` : ''}</div>` : ''}
                <div class="message-content">${message.content}</div>
                <div class="message-reactions" id="reactions-${messageId}">
                    ${this.renderReactions(reactions, messageId)}
                    <div class="add-reaction-btn" onclick="messageManager.showReactionPicker('${messageId}', event)">➕</div>
                </div>
                <div class="message-footer">
                    <div class="message-actions">
                        ${this.canPinMessage() ? `<button class="pin-btn" onclick="window.togglePinMessage && togglePinMessage('${messageId}', ${!message.isPinned})" title="${message.isPinned ? 'Bỏ ghim' : 'Ghim tin nhắn'}">
                            ${message.isPinned ? '📌' : '📍'}
                        </button>` : ''}
                    </div>
                    ${isOwnMessage ? `<div class="message-status">
                        <span class="status-icon ${message.status ? message.status.toLowerCase() : 'sent'}">${statusIcon}</span>
                        <span class="status-text">${this.getStatusText(message.status)}</span>
                    </div>` : ''}
                    ${isOwnMessage ? `<div class="message-time">${timestamp}</div>` : ''}
                </div>
            `;

            if (isOwnMessage) {
                messageElement.addEventListener('dblclick', () => this.editMessage(messageId, message.content));
            }
        }

        messageElement.className = messageClass;
        messageElement.dataset.messageId = message.id;
        messageElement.dataset.senderId = message.senderId;

        return messageElement;
    }

    renderReactions(reactions, messageId) {
        if (!reactions || reactions.length === 0) {
            return '';
        }

        return reactions.map(reaction => `
            <div class="reaction-item ${reaction.currentUserReacted ? 'own-reaction' : ''}"
                 onclick="messageManager.toggleReaction('${messageId}', '${reaction.type}')">
                <span class="reaction-emoji">${reaction.emoji}</span>
                <span class="reaction-count">${reaction.count}</span>
            </div>
        `).join('');
    }

    getStatusIcon(status) {
        const icons = {
            'SENDING': '🕐',
            'SENT': '✓',
            'DELIVERED': '✓✓',
            'READ': '✓✓',
            'FAILED': '❌'
        };
        return icons[status] || '✓';
    }

    getStatusText(status) {
        const texts = {
            'SENDING': 'Đang gửi',
            'SENT': 'Đã gửi',
            'DELIVERED': 'Đã nhận',
            'READ': 'Đã xem',
            'FAILED': 'Thất bại'
        };
        return texts[status] || 'Đã gửi';
    }

    canPinMessage() {
        // Simplified permission check - can be enhanced later
        return true;
    }

    scrollToBottom() {
        const container = document.getElementById('messagesContainer');
        if (container) {
            requestAnimationFrame(() => {
                container.scrollTop = container.scrollHeight;
            });
        }
    }

    async markMessageAsRead(messageId) {
        try {
            const chatInfo = window.chatManager.getCurrentChatInfo();
            await fetch('/api/messages/read', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': 'Bearer ' + window.authToken
                },
                body: JSON.stringify({
                    messageId: messageId,
                    roomId: chatInfo.type === 'room' ? chatInfo.id : null,
                    conversationId: chatInfo.type === 'conversation' ? chatInfo.id : null
                })
            });
        } catch (error) {
            console.error('Error marking message as read:', error);
        }
    }

    async editMessage(messageId, currentContent) {
        const newContent = prompt('Chỉnh sửa tin nhắn:', currentContent);
        if (newContent && newContent !== currentContent) {
            try {
                const response = await fetch(`/api/messages/${messageId}`, {
                    method: 'PUT',
                    headers: {
                        'Content-Type': 'application/json',
                        'Authorization': 'Bearer ' + window.authToken
                    },
                    body: JSON.stringify(newContent)
                });

                if (response.ok) {
                    // Message will be updated via WebSocket
                    console.log('Message edited successfully');
                } else {
                    const error = await response.text();
                    alert('Lỗi chỉnh sửa: ' + error);
                }
            } catch (error) {
                console.error('Error editing message:', error);
                alert('Không thể chỉnh sửa tin nhắn');
            }
        }
    }

    showReactionPicker(messageId, event) {
        event.stopPropagation();

        const picker = document.getElementById('reactionPicker');
        this.currentMessageForReaction = messageId;

        picker.style.left = event.pageX + 'px';
        picker.style.top = (event.pageY - 50) + 'px';
        picker.classList.add('show');
    }

    hideReactionPicker() {
        document.getElementById('reactionPicker').classList.remove('show');
        this.currentMessageForReaction = null;
    }

    async addReaction(reactionType) {
        if (!this.currentMessageForReaction) return;

        try {
            const response = await fetch(`/api/messages/${this.currentMessageForReaction}/reactions`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': 'Bearer ' + window.authToken
                },
                body: JSON.stringify({
                    messageId: this.currentMessageForReaction,
                    type: reactionType
                })
            });

            if (response.ok) {
                await this.loadMessageReactions(this.currentMessageForReaction);
                this.hideReactionPicker();
            } else {
                console.error('Failed to add reaction');
            }
        } catch (error) {
            console.error('Error adding reaction:', error);
        }
    }

    async toggleReaction(messageId, reactionType) {
        try {
            const response = await fetch(`/api/messages/${messageId}/reactions`, {
                method: 'DELETE',
                headers: {
                    'Authorization': 'Bearer ' + window.authToken
                }
            });

            if (response.ok) {
                console.log('Reaction removed successfully');
            } else {
                console.error('Failed to remove reaction');
            }
        } catch (error) {
            console.error('Error removing reaction:', error);
        }
    }

    async loadMessageReactions(messageId) {
        try {
            const response = await fetch(`/api/messages/${messageId}/reactions`, {
                headers: {
                    'Authorization': 'Bearer ' + window.authToken
                }
            });

            if (response.ok) {
                const reactions = await response.json();
                this.handleReactionUpdate({
                    messageId: messageId,
                    reactions: reactions
                });
            }
        } catch (error) {
            console.error('Error loading message reactions:', error);
        }
    }

    handleReactionUpdate(data) {
        let messageId, reactions;

        if (Array.isArray(data)) {
            reactions = data;
            messageId = this.currentMessageForReaction;
        } else if (data.messageId && data.reactions) {
            messageId = data.messageId;
            reactions = data.reactions;
        } else {
            console.warn('Invalid reaction update data:', data);
            return;
        }

        const reactionsContainer = document.getElementById(`reactions-${messageId}`);
        if (reactionsContainer) {
            reactionsContainer.innerHTML = `
                ${this.renderReactions(reactions, messageId)}
                <div class="add-reaction-btn" onclick="messageManager.showReactionPicker('${messageId}', event)">➕</div>
            `;
        }
    }

    updateMessage(message) {
        const messageElement = document.querySelector(`[data-message-id="${message.id}"]`);
        if (messageElement) {
            // Update message content
            const contentElement = messageElement.querySelector('.message-content');
            if (contentElement) {
                contentElement.textContent = message.content;
            }

            // Add edited indicator
            if (message.isEdited) {
                let editedIndicator = messageElement.querySelector('.edited-indicator');
                if (!editedIndicator) {
                    editedIndicator = document.createElement('span');
                    editedIndicator.className = 'edited-indicator';
                    editedIndicator.textContent = ' (đã chỉnh sửa)';
                    editedIndicator.style.fontSize = '12px';
                    editedIndicator.style.opacity = '0.7';
                    contentElement.appendChild(editedIndicator);
                }
            }
        }
    }

    removeMessage(messageId) {
        const messageElement = document.querySelector(`[data-message-id="${messageId}"]`);
        if (messageElement) {
            messageElement.style.opacity = '0.5';
            const contentElement = messageElement.querySelector('.message-content');
            if (contentElement) {
                contentElement.textContent = '🗑️ Tin nhắn đã được xóa';
                contentElement.style.fontStyle = 'italic';
            }
        }
    }

    handleMessageStatusUpdate(data) {
        console.log('Message status update received:', data);

        if (data && data.messageId && data.status) {
            this.updateMessageStatus(data.messageId, data.status, data.readBy, data.readerId);
        }
    }

    updateMessageStatus(messageId, status, readerName, readerId) {
        const messageElement = document.querySelector(`[data-message-id="${messageId}"]`);
        if (!messageElement) {
            console.debug('Message element not found for status update:', messageId);
            return;
        }

        // Only update for current user's messages
        if (window.currentUser) {
            const senderId = messageElement.dataset.senderId;
            if (senderId !== window.currentUser.id.toString()) {
                console.debug('Ignoring status update for message not sent by current user');
                return;
            }
        }

        const statusIcon = messageElement.querySelector('.status-icon');
        const statusText = messageElement.querySelector('.status-text');

        if (statusIcon && statusText) {
            // Update icon and class
            statusIcon.textContent = this.getStatusIcon(status);
            statusIcon.className = `status-icon ${status.toLowerCase()}`;

            // Update text with reader info
            let statusTextContent = this.getStatusText(status);
            if (status === 'READ' || status === 'READ'.toUpperCase()) {
                if (readerName) {
                    statusTextContent = `Đã đọc bởi ${readerName}`;
                }
            }
            statusText.textContent = statusTextContent;

            // Add visual effect
            messageElement.classList.add('status-updated');
            setTimeout(() => {
                messageElement.classList.remove('status-updated');
            }, 1000);

            console.log(`Message ${messageId} status updated to ${status}${readerName ? ` by ${readerName}` : ''}`);
        }
    }

    handleReadReceiptUpdate(data) {
        console.log('Processing read receipt:', data);

        if (!data || !data.messageId) {
            console.warn('Invalid read receipt data:', data);
            return;
        }

        const messageElement = document.querySelector(`[data-message-id="${data.messageId}"]`);
        if (!messageElement) {
            console.debug('Message element not found for read receipt:', data.messageId);
            return;
        }

        // Only update for current user's messages
        if (window.currentUser) {
            const senderId = messageElement.dataset.senderId;
            if (senderId !== window.currentUser.id.toString()) {
                console.debug('Ignoring read receipt for message not sent by current user');
                return;
            }
        }

        const statusIcon = messageElement.querySelector('.status-icon');
        const statusText = messageElement.querySelector('.status-text');

        if (statusIcon && statusText) {
            // Update to read status
            statusIcon.textContent = '✓✓';
            statusIcon.className = 'status-icon read';

            // Display who read it
            let readText = 'Đã đọc';
            if (data.readerName) {
                readText = `Đã đọc bởi ${data.readerName}`;
            }
            statusText.textContent = readText;

            // Add visual effect
            messageElement.classList.add('status-updated');
            setTimeout(() => {
                messageElement.classList.remove('status-updated');
            }, 1000);

            console.log(`✅ Message ${data.messageId} marked as read by ${data.readerName || 'unknown'}`);
        } else {
            console.warn('Status elements not found in message:', data.messageId);
        }
    }

    handleBatchReadReceiptUpdate(data) {
        console.log('Processing batch read receipt:', data);

        if (!data || !data.messageIds || !Array.isArray(data.messageIds)) {
            console.warn('Invalid batch read receipt data:', data);
            return;
        }

        let updatedCount = 0;
        data.messageIds.forEach(messageId => {
            const messageElement = document.querySelector(`[data-message-id="${messageId}"]`);
            if (messageElement && window.currentUser && messageElement.dataset.senderId === window.currentUser.id.toString()) {
                this.handleReadReceiptUpdate({
                    messageId: messageId,
                    readerId: data.readerId,
                    readerName: data.readerName,
                    timestamp: data.timestamp
                });
                updatedCount++;
            }
        });

        if (updatedCount > 0) {
            console.log(`✅ Batch updated ${updatedCount} messages as read by ${data.readerName || 'unknown'}`);
        }
    }
}

// Export global instance
window.messageManager = new MessageManager();