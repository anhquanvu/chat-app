// Pin Message Utility Functions - Add to app.js

// Check if current user has permission to pin messages
function canPinMessage() {
    // Check if current user has permission to pin messages
    // In rooms: ADMIN/OWNER role, in conversations: any participant
    return true; // Simplified for now, can add more logic later
}

// Show notification to user
function showNotification(text) {
    // Create notification element if it doesn't exist
    let notification = document.getElementById('tempNotification');
    if (!notification) {
        notification = document.createElement('div');
        notification.id = 'tempNotification';
        notification.className = 'temp-notification';
        document.body.appendChild(notification);
    }

    notification.textContent = text;
    notification.classList.add('show');

    setTimeout(() => {
        notification.classList.remove('show');
    }, 3000);
}

// Show loading message during scroll to message
function showLoadingMessage(text) {
    let loadingElement = document.getElementById('scrollLoadingMessage');
    if (!loadingElement) {
        loadingElement = document.createElement('div');
        loadingElement.id = 'scrollLoadingMessage';
        loadingElement.className = 'scroll-loading-message';
        document.body.appendChild(loadingElement);
    }

    loadingElement.textContent = text;
    loadingElement.classList.add('show');
}

// Hide loading message
function hideLoadingMessage() {
    const loadingElement = document.getElementById('scrollLoadingMessage');
    if (loadingElement) {
        loadingElement.classList.remove('show');
    }
}

// Pin Message Utility Functions - Add to app.js

// Check if current user has permission to pin messages
function canPinMessage() {
    // Check if current user has permission to pin messages
    // In rooms: ADMIN/OWNER role, in conversations: any participant
    return true; // Simplified for now, can add more logic later
}

// Show notification to user
function showNotification(text) {
    // Create notification element if it doesn't exist
    let notification = document.getElementById('tempNotification');
    if (!notification) {
        notification = document.createElement('div');
        notification.id = 'tempNotification';
        notification.className = 'temp-notification';
        document.body.appendChild(notification);
    }

    notification.textContent = text;
    notification.classList.add('show');

    setTimeout(() => {
        notification.classList.remove('show');
    }, 3000);
}

// Show loading message during scroll to message
function showLoadingMessage(text) {
    let loadingElement = document.getElementById('scrollLoadingMessage');
    if (!loadingElement) {
        loadingElement = document.createElement('div');
        loadingElement.id = 'scrollLoadingMessage';
        loadingElement.className = 'scroll-loading-message';
        document.body.appendChild(loadingElement);
    }

    loadingElement.textContent = text;
    loadingElement.classList.add('show');
}

// Hide loading message
function hideLoadingMessage() {
    const loadingElement = document.getElementById('scrollLoadingMessage');
    if (loadingElement) {
        loadingElement.classList.remove('show');
    }
}

// Pin Message API Functions - Add to app.js

// Toggle pin/unpin message
async function togglePinMessage(messageId, pinned) {
    try {
        // Disable button during request to prevent double clicks
        const pinBtn = document.querySelector(`[data-message-id="${messageId}"] .pin-btn`);
        if (pinBtn) {
            pinBtn.disabled = true;
            pinBtn.style.opacity = '0.5';
        }

        const response = await fetch(`/api/messages/${messageId}/pin?pinned=${pinned}`, {
            method: 'POST',
            headers: {
                'Authorization': 'Bearer ' + authToken
            }
        });

        if (response.ok) {
            console.log(`Message ${messageId} ${pinned ? 'pinned' : 'unpinned'} successfully`);

            // Don't update UI here - wait for WebSocket response
            // This ensures all clients get the same state update
        } else {
            const error = await response.text();
            alert('Lỗi: ' + error);

            // Re-enable button on error
            if (pinBtn) {
                pinBtn.disabled = false;
                pinBtn.style.opacity = '1';
            }
        }
    } catch (error) {
        console.error('Error toggling pin message:', error);
        alert('Không thể ' + (pinned ? 'ghim' : 'bỏ ghim') + ' tin nhắn');

        // Re-enable button on error
        const pinBtn = document.querySelector(`[data-message-id="${messageId}"] .pin-btn`);
        if (pinBtn) {
            pinBtn.disabled = false;
            pinBtn.style.opacity = '1';
        }
    }
}


// Load pinned messages for current chat
async function loadPinnedMessages() {
    if (!currentChatType || !currentChatId) return;

    try {
        const param = currentChatType === 'room' ? `roomId=${currentChatId}` : `conversationId=${currentChatId}`;
        const response = await fetch(`/api/messages/pinned?${param}`, {
            headers: {
                'Authorization': 'Bearer ' + authToken
            }
        });

        if (response.ok) {
            const pinnedMessages = await response.json();
            displayPinnedMessages(pinnedMessages);
        }
    } catch (error) {
        console.error('Error loading pinned messages:', error);
    }
}

// Get page information for a specific message
async function getMessagePageInfo(messageId) {
    try {
        const response = await fetch(`/api/messages/${messageId}/page?pageSize=50`, {
            headers: {
                'Authorization': 'Bearer ' + authToken
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

// Pin Message Display Functions - Add to app.js

// Display pinned messages container
function displayPinnedMessages(pinnedMessages) {
    if (pinnedMessages.length === 0) {
        // Remove pinned container if no pinned messages
        const existingContainer = document.getElementById('pinnedMessagesContainer');
        if (existingContainer) {
            existingContainer.remove();
        }
        return;
    }

    // Create or update pinned messages container
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
        <div class="pinned-messages-header" onclick="togglePinnedMessagesVisibility()">
            📌 ${pinnedMessages.length} tin nhắn đã ghim
            <button class="toggle-pinned-btn" id="togglePinnedBtn">▼</button>
        </div>
        <div class="pinned-messages-list" id="pinnedMessagesList">
            ${pinnedMessages.map(msg => `
                <div class="pinned-message-item" data-message-id="${msg.id}">
                    <div class="pinned-message-content">${msg.content}</div>
                    <div class="pinned-message-info">
                        <span>${msg.senderName}</span>
                        <span>${formatTime(msg.timestamp)}</span>
                        <button onclick="scrollToMessage('${msg.id}')" class="goto-message-btn">Đi tới</button>
                    </div>
                </div>
            `).join('')}
        </div>
    `;
}

// Toggle visibility of pinned messages list
function togglePinnedMessagesVisibility() {
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

// Handle real-time pin/unpin updates from WebSocket
function handleMessagePinUpdate(message, action) {
    console.log('Handling pin update:', { messageId: message.id, action, isPinned: message.isPinned });

    const messageElement = document.querySelector(`[data-message-id="${message.id}"]`);
    if (messageElement) {
        updateMessagePinStatus(messageElement, message);
    }

    // Reload pinned messages to update the pinned container
    loadPinnedMessages();

    // Show notification
    const actionText = action === 'PIN' ? 'đã ghim' : 'đã bỏ ghim';
    showNotification(`Tin nhắn ${actionText} bởi ${message.senderName}`);
}

// Update pin status of a message element
function updateMessagePinStatus(messageElement, message) {
    // Update pin indicator
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

    // Update pin button
    const pinBtn = messageElement.querySelector('.pin-btn');
    if (pinBtn) {
        pinBtn.textContent = message.isPinned ? '📌' : '📍';
        pinBtn.title = message.isPinned ? 'Bỏ ghim' : 'Ghim tin nhắn';
        pinBtn.onclick = () => togglePinMessage(message.id, !message.isPinned);
    }
}

// Scroll to Message Functions - Add to app.js

// Main function to scroll to a specific message
async function scrollToMessage(messageId) {
    // First check if message is already loaded in current view
    let messageElement = document.querySelector(`[data-message-id="${messageId}"]`);

    if (messageElement) {
        // Message is already visible, just scroll to it
        scrollToExistingMessage(messageElement);
        return;
    }

    // Message not visible, need to find which page it's on
    try {
        showLoadingMessage('Đang tìm tin nhắn...');

        const pageInfo = await getMessagePageInfo(messageId);

        // Load the specific page containing the message
        await loadMessagePage(pageInfo);

        // Now try to scroll to the message
        messageElement = document.querySelector(`[data-message-id="${messageId}"]`);
        if (messageElement) {
            scrollToExistingMessage(messageElement);
        } else {
            hideLoadingMessage();
            showNotification('Không thể cuộn đến tin nhắn');
        }

    } catch (error) {
        console.error('Error finding message:', error);
        hideLoadingMessage();
        showNotification('Lỗi khi tìm tin nhắn: ' + error.message);
    }
}

// Scroll to existing message in view
function scrollToExistingMessage(messageElement) {
    messageElement.scrollIntoView({ behavior: 'smooth', block: 'center' });

    // Highlight the message temporarily
    messageElement.classList.add('highlighted-message');
    setTimeout(() => {
        messageElement.classList.remove('highlighted-message');
    }, 2000);

    hideLoadingMessage();
}

// Load specific page containing the target message
async function loadMessagePage(pageInfo) {
    const { pageNumber, roomId, conversationId } = pageInfo;

    try {
        let messagesResponse;

        if (roomId) {
            messagesResponse = await fetch(`/api/rooms/${roomId}/messages?page=${pageNumber}&size=50`, {
                headers: {
                    'Authorization': 'Bearer ' + authToken
                }
            });
        } else if (conversationId) {
            messagesResponse = await fetch(`/api/conversations/${conversationId}/messages?page=${pageNumber}&size=50`, {
                headers: {
                    'Authorization': 'Bearer ' + authToken
                }
            });
        }

        if (messagesResponse && messagesResponse.ok) {
            const data = await messagesResponse.json();

            // Clear current messages and load the page containing target message
            const wrapper = document.getElementById('messagesWrapper');
            const typingIndicator = document.getElementById('typingIndicator');
            wrapper.innerHTML = '';
            wrapper.appendChild(typingIndicator);

            // Display messages from the specific page
            displayMessages(data.content || [], false);

            // If not the first page, also load some context (previous page)
            if (pageNumber > 0) {
                await loadContextMessages(roomId, conversationId, pageNumber);
            }

        } else {
            throw new Error('Không thể tải trang tin nhắn');
        }

    } catch (error) {
        console.error('Error loading message page:', error);
        throw error;
    }
}

// Load context messages from previous page
async function loadContextMessages(roomId, conversationId, targetPage) {
    // Load one page before target page for context
    const contextPage = Math.max(0, targetPage - 1);

    try {
        let messagesResponse;

        if (roomId) {
            messagesResponse = await fetch(`/api/rooms/${roomId}/messages?page=${contextPage}&size=50`, {
                headers: {
                    'Authorization': 'Bearer ' + authToken
                }
            });
        } else if (conversationId) {
            messagesResponse = await fetch(`/api/conversations/${conversationId}/messages?page=${contextPage}&size=50`, {
                headers: {
                    'Authorization': 'Bearer ' + authToken
                }
            });
        }

        if (messagesResponse && messagesResponse.ok) {
            const data = await messagesResponse.json();

            // Prepend context messages to current view
            const contextMessages = data.content || [];
            for (const message of contextMessages.reverse()) {
                prependMessage(message);
            }
        }

    } catch (error) {
        console.error('Error loading context messages:', error);
        // Continue even if context loading fails
    }
}

// Prepend message to the beginning of messages wrapper
function prependMessage(message) {
    const wrapper = document.getElementById('messagesWrapper');
    const firstMessage = wrapper.querySelector('.message');

    const messageElement = createMessageElement(message);

    if (firstMessage) {
        wrapper.insertBefore(messageElement, firstMessage);
    } else {
        const typingIndicator = document.getElementById('typingIndicator');
        wrapper.insertBefore(messageElement, typingIndicator);
    }
}