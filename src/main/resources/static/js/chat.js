// Chat functionality
function switchSidebarTab(tab) {
    document.querySelectorAll('.sidebar-tab').forEach(t => t.classList.remove('active'));
    document.querySelectorAll('.sidebar-panel').forEach(p => p.classList.remove('active'));

    event.target.classList.add('active');
    document.getElementById(tab + 'Panel').classList.add('active');

    // Load data based on selected tab
    if (tab === 'conversations') {
        loadConversations();
    } else if (tab === 'rooms') {
        loadRooms();
    } else if (tab === 'contacts') {
        loadContacts();
    }
}

async function loadInitialData() {
    await Promise.all([
        loadConversations(),
        loadRooms(),
        loadContacts()
    ]);
}

// Load conversations
async function loadConversations() {
    try {
        const response = await fetch(`${APP_CONFIG.API_BASE_URL}${API_ENDPOINTS.CONVERSATIONS.BASE}?page=0&size=50`, {
            headers: {
                'Authorization': 'Bearer ' + authToken
            }
        });

        if (response.ok) {
            const data = await response.json();
            displayConversations(data.conversations || []);
        }
    } catch (error) {
        console.error('Error loading conversations:', error);
        document.getElementById('conversationsList').innerHTML =
            '<div style="text-align: center; padding: 20px; color: #dc3545;">Lỗi tải dữ liệu</div>';
    }
}

function displayConversations(conversations) {
    const container = document.getElementById('conversationsList');

    if (conversations.length === 0) {
        container.innerHTML = '<div style="text-align: center; padding: 20px; color: #666;">Chưa có cuộc trò chuyện nào</div>';
        return;
    }

    container.innerHTML = conversations.map(conv => `
        <div class="list-item" onclick="openConversation(${conv.id}, '${conv.participant?.fullName || conv.participant?.username}')">
            <div class="item-header">
                <div class="item-name">${conv.participant?.fullName || conv.participant?.username}</div>
                <div class="item-time">${formatTime(conv.lastMessageAt)}</div>
                ${conv.unreadCount > 0 ? `<div class="item-badge">${conv.unreadCount}</div>` : ''}
            </div>
            <div class="item-preview">${conv.lastMessage?.content || 'Chưa có tin nhắn'}</div>
        </div>
    `).join('');
}

// Load rooms
async function loadRooms() {
    try {
        const response = await fetch(`${APP_CONFIG.API_BASE_URL}${API_ENDPOINTS.ROOMS.BASE}?page=0&size=50`, {
            headers: {
                'Authorization': 'Bearer ' + authToken
            }
        });

        if (response.ok) {
            const data = await response.json();
            displayRooms(data.content || []);
        }
    } catch (error) {
        console.error('Error loading rooms:', error);
        document.getElementById('roomsList').innerHTML =
            '<div style="text-align: center; padding: 20px; color: #dc3545;">Lỗi tải dữ liệu</div>';
    }
}

function displayRooms(rooms) {
    const container = document.getElementById('roomsList');

    if (rooms.length === 0) {
        container.innerHTML = '<div style="text-align: center; padding: 20px; color: #666;">Chưa tham gia phòng nào</div>';
        return;
    }

    container.innerHTML = rooms.map(room => `
        <div class="list-item" onclick="openRoom(${room.id}, '${room.name}')">
            <div class="item-header">
                <div class="item-name">${room.name}</div>
                <div class="item-time">${formatTime(room.lastActivityAt)}</div>
                ${room.unreadCount > 0 ? `<div class="item-badge">${room.unreadCount}</div>` : ''}
            </div>
            <div class="item-preview">
                👥 ${room.memberCount} thành viên • ${room.lastMessage?.content || 'Chưa có tin nhắn'}
            </div>
        </div>
    `).join('');
}

// Load contacts
async function loadContacts() {
    try {
        const response = await fetch(`${APP_CONFIG.API_BASE_URL}${API_ENDPOINTS.USERS.CONTACTS}?status=ACCEPTED&page=0&size=50`, {
            headers: {
                'Authorization': 'Bearer ' + authToken
            }
        });

        if (response.ok) {
            const data = await response.json();
            displayContacts(data.content || []);
        }
    } catch (error) {
        console.error('Error loading contacts:', error);
        document.getElementById('contactsList').innerHTML =
            '<div style="text-align: center; padding: 20px; color: #dc3545;">Lỗi tải dữ liệu</div>';
    }
}

function displayContacts(contacts) {
    const container = document.getElementById('contactsList');

    if (contacts.length === 0) {
        container.innerHTML = '<div style="text-align: center; padding: 20px; color: #666;">Chưa có bạn bè nào</div>';
        return;
    }

    container.innerHTML = contacts.map(contact => `
        <div class="list-item" onclick="startDirectChat(${contact.contact.id}, '${contact.contact.fullName || contact.contact.username}')">
            <div class="item-header">
                <div class="item-name">${contact.contact.fullName || contact.contact.username}</div>
                ${contact.contact.isOnline ? '<div class="online-indicator"></div>' : ''}
            </div>
            <div class="item-preview">@${contact.contact.username}</div>
        </div>
    `).join('');
}

// Open chat functions
function openConversation(conversationId, title) {
    currentChatType = 'conversation';
    currentChatId = conversationId;

    updateActiveChat(title);
    subscribeToChat();
    loadConversationMessages(conversationId);
}

function openRoom(roomId, title) {
    currentChatType = 'room';
    currentChatId = roomId;

    updateActiveChat(title);
    subscribeToChat();
    loadRoomMessages(roomId);
}

function updateActiveChat(title) {
    // Update active state
    document.querySelectorAll('.list-item').forEach(item => item.classList.remove('active'));
    event?.target?.closest('.list-item')?.classList.add('active');

    // Show chat content
    document.getElementById('emptyChatState').classList.add('hidden');
    document.getElementById('activeChatContent').classList.remove('hidden');
    document.getElementById('chatTitle').textContent = title;

    // Clear typing users
    typingUsers.clear();
    updateTypingIndicator();
}

async function startDirectChat(userId, userName) {
    try {
        const response = await fetch(`${APP_CONFIG.API_BASE_URL}${API_ENDPOINTS.CONVERSATIONS.DIRECT}/${userId}`, {
            method: 'GET',
            headers: {
                'Authorization': 'Bearer ' + authToken
            }
        });

        if (response.ok) {
            const conversation = await response.json();

            // Switch to conversations tab
            switchSidebarTab('conversations');

            // Open the conversation
            setTimeout(() => {
                openConversation(conversation.id, userName);
                loadConversations();
            }, 100);
        }
    } catch (error) {
        console.error('Error starting direct chat:', error);
        alert('Không thể bắt đầu cuộc trò chuyện');
    }
}

// Load messages functions
async function loadConversationMessages(conversationId) {
    try {
        const response = await fetch(`${APP_CONFIG.API_BASE_URL}/conversations/${conversationId}/messages?page=0&size=50`, {
            headers: {
                'Authorization': 'Bearer ' + authToken
            }
        });

        if (response.ok) {
            const data = await response.json();
            displayMessages(data.content || []);
        }
    } catch (error) {
        console.error('Error loading conversation messages:', error);
    }
}

async function loadRoomMessages(roomId) {
    try {
        const response = await fetch(`${APP_CONFIG.API_BASE_URL}/rooms/${roomId}/messages?page=0&size=50`, {
            headers: {
                'Authorization': 'Bearer ' + authToken
            }
        });

        if (response.ok) {
            const data = await response.json();
            displayMessages(data.content || []);
        }
    } catch (error) {
        console.error('Error loading room messages:', error);
    }
}

function displayMessages(messages) {
    const container = document.getElementById('messagesContainer');
    container.innerHTML = '';

    if (messages.length === 0) {
        container.innerHTML = '<div class="message system"><div class="message-content">Chưa có tin nhắn nào. Hãy bắt đầu cuộc trò chuyện! 🎉</div></div>';
        return;
    }

    // Reverse to show oldest first
    messages.reverse().forEach(message => showMessage(message));
    container.scrollTop = container.scrollHeight;
}

function showMessage(message) {
    const container = document.getElementById('messagesContainer');

    // Check if message already exists (avoid duplicates)
    const existingMessage = container.querySelector(`[data-message-id="${message.id}"]`);
    if (existingMessage) {
        return;
    }

    const messageElement = document.createElement('div');
    const isOwnMessage = message.senderId === currentUser.id;
    let messageClass = 'message ';

    if (message.type === MESSAGE_TYPES.JOIN || message.type === MESSAGE_TYPES.LEAVE) {
        messageClass += 'system';
        messageElement.innerHTML = `
            <div class="message-content">${message.content || `${message.senderName} đã ${message.type === MESSAGE_TYPES.JOIN ? 'tham gia' : 'rời khỏi'} phòng chat`}</div>
        `;
    } else if (message.type === MESSAGE_TYPES.CHAT) {
        messageClass += isOwnMessage ? 'own' : 'chat';
        const timestamp = new Date(message.timestamp).toLocaleTimeString('vi-VN', {
            hour: '2-digit',
            minute: '2-digit'
        });

        const messageId = message.id;
        const statusIcon = getStatusIcon(message.status);
        const reactions = message.reactions || [];

        messageElement.innerHTML = `
            ${!isOwnMessage ? `<div class="message-header">
                <span class="message-sender">${message.senderName}</span>
                <span class="message-time">${timestamp}</span>
            </div>` : ''}
            <div class="message-content">${message.content}</div>
            ${reactions.length > 0 ? `<div class="message-reactions">
                ${reactions.map(reaction => `
                    <div class="reaction-item ${reaction.currentUserReacted ? 'own-reaction' : ''}"
                         onclick="toggleReaction('${messageId}', '${reaction.type}')">
                        <span class="reaction-emoji">${reaction.emoji}</span>
                        <span class="reaction-count">${reaction.count}</span>
                    </div>
                `).join('')}
                <div class="add-reaction-btn" onclick="showReactionPicker('${messageId}', event)">➕</div>
            </div>` : `<div class="message-reactions">
                <div class="add-reaction-btn" onclick="showReactionPicker('${messageId}', event)">➕</div>
            </div>`}
            <div class="message-footer">
                ${isOwnMessage ? `<div class="message-status">
                    <span class="status-icon ${message.status ? message.status.toLowerCase() : 'sent'}">${statusIcon}</span>
                    <span class="status-text">${getStatusText(message.status)}</span>
                </div>` : ''}
                ${isOwnMessage ? `<div class="message-time">${timestamp}</div>` : ''}
            </div>
        `;

        // Add double-click to edit for own messages
        if (isOwnMessage) {
            messageElement.addEventListener('dblclick', () => editMessage(messageId, message.content));
        }
    }

    messageElement.className = messageClass;
    messageElement.dataset.messageId = message.id;
    container.appendChild(messageElement);
    container.scrollTop = container.scrollHeight;

    // Mark message as read if not own message
    if (!isOwnMessage && currentChatType && currentChatId) {
        setTimeout(() => markMessageAsRead(message.id), 1000);
    }
}

function getStatusIcon(status) {
    return STATUS_ICONS[status] || STATUS_ICONS[MESSAGE_STATUS.SENT];
}

function getStatusText(status) {
    return STATUS_TEXTS[status] || STATUS_TEXTS[MESSAGE_STATUS.SENT];
}

// Message actions
async function markMessageAsRead(messageId) {
    try {
        await fetch(`${APP_CONFIG.API_BASE_URL}${API_ENDPOINTS.MESSAGES.READ}`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': 'Bearer ' + authToken
            },
            body: JSON.stringify({
                messageId: messageId,
                roomId: currentChatType === 'room' ? currentChatId : null,
                conversationId: currentChatType === 'conversation' ? currentChatId : null
            })
        });
    } catch (error) {
        console.error('Error marking message as read:', error);
    }
}

function editMessage(messageId, currentContent) {
    const newContent = prompt('Chỉnh sửa tin nhắn:', currentContent);
    if (newContent && newContent !== currentContent) {
        fetch(`${APP_CONFIG.API_BASE_URL}/messages/${messageId}`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': 'Bearer ' + authToken
            },
            body: JSON.stringify(newContent)
        }).then(response => {
            if (response.ok) {
                // Message will be updated via WebSocket
            }
        }).catch(error => {
            console.error('Error editing message:', error);
        });
    }
}

function updateMessage(messageData) {
    const messageElement = document.querySelector(`[data-message-id="${messageData.id}"]`);
    if (messageElement) {
        const contentElement = messageElement.querySelector('.message-content');
        if (contentElement) {
            contentElement.textContent = messageData.content;
        }

        // Add edited indicator
        const messageFooter = messageElement.querySelector('.message-footer');
        if (messageFooter && !messageFooter.querySelector('.edited-indicator')) {
            const editedIndicator = document.createElement('span');
            editedIndicator.className = 'edited-indicator';
            editedIndicator.textContent = '(đã chỉnh sửa)';
            editedIndicator.style.fontSize = '12px';
            editedIndicator.style.opacity = '0.7';
            editedIndicator.style.fontStyle = 'italic';
            messageFooter.insertBefore(editedIndicator, messageFooter.firstChild);
        }
    }
}

function deleteMessage(messageId) {
    const messageElement = document.querySelector(`[data-message-id="${messageId}"]`);
    if (messageElement) {
        messageElement.remove();
    }
}

function updateMessageStatus(message) {
    const messageElement = document.querySelector(`[data-message-id="${message.id}"]`);
    if (messageElement) {
        const statusElement = messageElement.querySelector('.message-status');
        if (statusElement) {
            const iconElement = statusElement.querySelector('.status-icon');
            const textElement = statusElement.querySelector('.status-text');

            if (iconElement) iconElement.textContent = getStatusIcon(message.status);
            if (textElement) textElement.textContent = getStatusText(message.status);
        }
    }
}

// Setup message input handlers
function setupMessageInput() {
    const messageInput = document.getElementById('messageInput');

    messageInput.addEventListener('keypress', function(e) {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            sendMessage();
        }
    });
}

// Search functions
function searchInChat() {
    const keyword = prompt('Nhập từ khóa tìm kiếm:');
    if (keyword && keyword.trim()) {
        // Implement search functionality
        console.log('Searching for:', keyword);
    }
}

function showChatInfo() {
    alert('Thông tin chat - Chức năng đang phát triển');
}

console.log('💬 Chat module loaded');