// Global variables
    let stompClient = null;
    let currentUser = null;
    let authToken = null;
    let refreshToken = null;
    let connected = false;
    let currentChatType = null;
    let currentChatId = null;
    let selectedUserId = null;
    let typingTimer = null;
    let isCurrentlyTyping = false;
    let typingUsers = new Set();
    let currentMessageForReaction = null;
    let currentChatKey = null;
    let readReceipts = new Map();

    // Authentication functions
    function switchTab(tab) {
        document.querySelectorAll('.auth-tab').forEach(t => t.classList.remove('active'));
        document.querySelectorAll('.auth-form').forEach(f => f.classList.remove('active'));

        if (tab === 'login') {
            document.querySelector('.auth-tab').classList.add('active');
            document.getElementById('loginForm').classList.add('active');
        } else {
            document.querySelectorAll('.auth-tab')[1].classList.add('active');
            document.getElementById('registerForm').classList.add('active');
        }

        document.getElementById('loginError').textContent = '';
        document.getElementById('registerError').textContent = '';
    }

    // Sidebar navigation
    function switchSidebarTab(tab, event) {
        leaveCurrentChat();
        document.querySelectorAll('.sidebar-tab').forEach(t => t.classList.remove('active'));
        document.querySelectorAll('.sidebar-panel').forEach(p => p.classList.remove('active'));

        if (event && event.target) {
            event.target.classList.add('active');
        } else {
            const tabElement = document.querySelector(`.sidebar-tab[onclick*="${tab}"]`);
            if (tabElement) {
                tabElement.classList.add('active');
            }
        }

        document.getElementById(tab + 'Panel').classList.add('active');

        if (tab === 'conversations') {
            loadConversations();
        } else if (tab === 'rooms') {
            loadRooms();
        } else if (tab === 'contacts') {
            loadContacts();
        }
    }

    // Authentication handlers
    document.getElementById('loginForm').addEventListener('submit', async (e) => {
        e.preventDefault();

        const username = document.getElementById('loginUsername').value.trim();
        const password = document.getElementById('loginPassword').value;
        const errorDiv = document.getElementById('loginError');

        if (!username || !password) {
            errorDiv.textContent = 'Vui lòng nhập đầy đủ thông tin';
            return;
        }

        try {
            const response = await fetch('/api/auth/signin', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    username: username,
                    password: password
                })
            });

            if (response.ok) {
                const data = await response.json();
                handleAuthSuccess(data);
            } else {
                const error = await response.text();
                errorDiv.textContent = error || 'Đăng nhập thất bại';
            }
        } catch (error) {
            console.error('Login error:', error);
            errorDiv.textContent = 'Lỗi kết nối đến server';
        }
    });

    document.getElementById('registerForm').addEventListener('submit', async (e) => {
        e.preventDefault();

        const username = document.getElementById('regUsername').value.trim();
        const email = document.getElementById('regEmail').value.trim();
        const fullName = document.getElementById('regFullName').value.trim();
        const phoneNumber = document.getElementById('regPhone').value.trim();
        const password = document.getElementById('regPassword').value;
        const errorDiv = document.getElementById('registerError');

        if (!username || !email || !password) {
            errorDiv.textContent = 'Vui lòng nhập đầy đủ thông tin bắt buộc';
            return;
        }

        if (password.length < 6) {
            errorDiv.textContent = 'Mật khẩu phải có ít nhất 6 ký tự';
            return;
        }

        try {
            const response = await fetch('/api/auth/signup', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    username: username,
                    email: email,
                    fullName: fullName,
                    phoneNumber: phoneNumber,
                    password: password
                })
            });

            if (response.ok) {
                const data = await response.json();
                handleAuthSuccess(data);
            } else {
                const error = await response.text();
                errorDiv.textContent = error || 'Đăng ký thất bại';
            }
        } catch (error) {
            console.error('Register error:', error);
            errorDiv.textContent = 'Lỗi kết nối đến server';
        }
    });

    function handleAuthSuccess(data) {
        authToken = data.accessToken;
        refreshToken = data.refreshToken;
        currentUser = {
            id: data.userId,
            username: data.username,
            fullName: data.fullName,
            email: data.email
        };

        if (!currentUser.id || !currentUser.username) {
            console.error('Invalid user data received:', currentUser);
            return;
        }

        localStorage.setItem('authToken', authToken);
        localStorage.setItem('refreshToken', refreshToken);
        localStorage.setItem('currentUser', JSON.stringify(currentUser));

        document.getElementById('currentUsername').textContent = currentUser.fullName || currentUser.username;
        document.getElementById('userAvatar').textContent = (currentUser.fullName || currentUser.username).charAt(0).toUpperCase();
        document.getElementById('userRole').textContent = 'Thành viên';

        document.getElementById('authModal').classList.add('hidden');
        document.getElementById('chatContainer').classList.remove('hidden');

        connectWebSocket();
        loadInitialData();
    }

function logout() {
    if (stompClient && connected) {
        stompClient.disconnect(function() {
            console.log('STOMP disconnected on logout');
        });
        stompClient = null;
        connected = false;
    }

    // Rest of logout logic...
    if (authToken) {
        fetch('/api/auth/logout', {
            method: 'POST',
            headers: {
                'Authorization': 'Bearer ' + authToken
            }
        }).catch(error => console.error('Logout error:', error));
    }

    localStorage.removeItem('authToken');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('currentUser');

    authToken = null;
    refreshToken = null;
    currentUser = null;
    currentChatType = null;
    currentChatId = null;

    document.getElementById('authModal').classList.remove('hidden');
    document.getElementById('chatContainer').classList.add('hidden');

    document.getElementById('loginForm').reset();
    document.getElementById('registerForm').reset();
    document.getElementById('loginError').textContent = '';
    document.getElementById('registerError').textContent = '';
}

    // WebSocket connection
function connectWebSocket() {
    if (!authToken || !currentUser) {
        console.error('No auth token or user data');
        return;
    }

    const socket = new SockJS('/ws');
    stompClient = Stomp.over(socket);

    // Tắt debug log trong production
    stompClient.debug = function(str) {
        // console.log('STOMP: ' + str);
    };

    // Cấu hình heartbeat cho STOMP client
    stompClient.heartbeat.outgoing = 25000; // Client gửi heartbeat mỗi 25 giây
    stompClient.heartbeat.incoming = 25000; // Expect server heartbeat mỗi 25 giây

    const connectHeaders = {
        'Authorization': 'Bearer ' + authToken,
        // STOMP sẽ tự động negotiate heartbeat với server
    };

    stompClient.connect(connectHeaders,
        function onConnect(frame) {
            console.log('Connected to WebSocket:', frame);
            connected = true;

            // Subscribe to user status updates
            stompClient.subscribe('/topic/user-status', function (message) {
                const userStatus = JSON.parse(message.body);
                updateUserStatus(userStatus);
            });

            // Subscribe to personal notifications
            stompClient.subscribe('/user/queue/message-status', function (message) {
                console.log('Personal notification:', message.body);
            });

            console.log('STOMP heartbeat configured: outgoing=' +
                stompClient.heartbeat.outgoing + 'ms, incoming=' +
                stompClient.heartbeat.incoming + 'ms');

        },
        function onError(error) {
            console.error('WebSocket connection error:', error);
            connected = false;

            // STOMP sẽ tự động phát hiện connection loss thông qua heartbeat
            // Không cần manual detection

            if (error.toString().includes('401') || error.toString().includes('403')) {
                console.log('Authentication error, attempting token refresh...');
                refreshAuthToken();
            } else {
                // Tự động reconnect sau 5 giây
                setTimeout(() => {
                    if (!connected && authToken) {
                        console.log('Attempting WebSocket reconnection...');
                        connectWebSocket();
                    }
                }, 5000);
            }
        }
    );

    // STOMP sẽ tự động handle connection loss và cleanup
    socket.onclose = function() {
        console.log('WebSocket connection closed');
        connected = false;
    };
}

function monitorConnection() {
    setInterval(() => {
        if (stompClient && connected) {
            // Kiểm tra connection status thông qua STOMP
            if (!stompClient.connected) {
                console.warn('STOMP connection lost, attempting reconnect...');
                connected = false;
                connectWebSocket();
            }
        }
    }, 30000); // Kiểm tra mỗi 30 giây
}

document.addEventListener('DOMContentLoaded', function() {
    initializeApp();
    monitorConnection();
});

async function refreshAuthToken() {
        if (!refreshToken) {
            logout();
            return;
        }

        try {
            const response = await fetch('/api/auth/refresh', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: refreshToken
            });

            if (response.ok) {
                const data = await response.json();
                authToken = data.accessToken;
                refreshToken = data.refreshToken;

                localStorage.setItem('authToken', authToken);
                localStorage.setItem('refreshToken', refreshToken);

                connectWebSocket();
            } else {
                logout();
            }
        } catch (error) {
            console.error('Token refresh error:', error);
            logout();
        }
    }

    // Load initial data
    async function loadInitialData() {
        await Promise.all([
            loadConversations(),
            loadRooms(),
            loadContacts()
        ]);
    }

    // Data loading functions
    async function loadConversations() {
        try {
            const response = await fetch('/api/conversations?page=0&size=50', {
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

    async function loadRooms() {
        try {
            const response = await fetch('/api/rooms?page=0&size=50', {
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

    async function loadContacts() {
        try {
            const response = await fetch('/api/users/contacts?status=ACCEPTED&page=0&size=50', {
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

    // Chat opening functions
    function openConversation(conversationId, title) {
        currentChatType = 'conversation';
        currentChatId = conversationId;

        document.querySelectorAll('#conversationsList .list-item').forEach(item => item.classList.remove('active'));
        event.target.closest('.list-item').classList.add('active');

        document.getElementById('emptyChatState').classList.add('hidden');
        document.getElementById('activeChatContent').classList.remove('hidden');
        document.getElementById('chatTitle').textContent = title;

        stompClient.subscribe(`/topic/conversation/${conversationId}`, function (message) {
            try {
                const response = JSON.parse(message.body);
                console.log('Conversation message received:', response);

                if (response.type === 'MESSAGE' && response.action === 'SEND') {
                    showMessage(response.data);
                } else if (response.type === 'TYPING') {
                    handleTypingNotification(response);
                } else if (response.type === 'REACTION') {
                    handleReactionUpdate(response.data);
                } else if (response.type === 'MESSAGE' && response.action === 'UPDATE') {
                    updateMessage(response.data);
                } else if (response.type === 'MESSAGE' && response.action === 'DELETE') {
                    removeMessage(response.data);
                } else if (response.type === 'MESSAGE_STATUS') {
                    handleMessageStatusUpdate(response.data);
                }
            } catch (error) {
                console.error('Error processing WebSocket message:', error, message.body);
            }
        });

        stompClient.subscribe(`/user/queue/read-receipts`, function (message) {
            const readStatus = JSON.parse(message.body);
            handleReadReceiptUpdate(readStatus.data);
        });

        stompClient.send(`/app/chat/conversation/${conversationId}/enter`, {}, JSON.stringify({}));

        loadConversationMessages(conversationId);
    }

    function openRoom(roomId, title) {
        currentChatType = 'room';
        currentChatId = roomId;

        document.querySelectorAll('#roomsList .list-item').forEach(item => item.classList.remove('active'));
        event.target.closest('.list-item').classList.add('active');

        document.getElementById('emptyChatState').classList.add('hidden');
        document.getElementById('activeChatContent').classList.remove('hidden');
        document.getElementById('chatTitle').textContent = title;

        stompClient.subscribe(`/topic/room/${roomId}`, function (message) {
            try {
                const response = JSON.parse(message.body);
                console.log('Room message received:', response);

                if (response.type === 'MESSAGE' && response.action === 'SEND') {
                    showMessage(response.data);
                } else if (response.type === 'TYPING') {
                    handleTypingNotification(response);
                } else if (response.type === 'REACTION') {
                    handleReactionUpdate(response.data);
                } else if (response.type === 'MESSAGE' && response.action === 'UPDATE') {
                    updateMessage(response.data);
                } else if (response.type === 'MESSAGE' && response.action === 'DELETE') {
                    removeMessage(response.data);
                } else if (response.type === 'MESSAGE_STATUS') {
                    handleMessageStatusUpdate(response.data);
                }
            } catch (error) {
                console.error('Error processing WebSocket message:', error, message.body);
            }
        });

        stompClient.send(`/app/chat/room/${roomId}/enter`, {}, JSON.stringify({}));

        loadRoomMessages(roomId);
    }

function leaveCurrentChat() {
    if (!currentChatKey || !stompClient || !connected) return;

    if (currentChatType === 'room') {
        stompClient.send(`/app/chat/room/${currentChatId}/leave`, {}, JSON.stringify({}));
    } else if (currentChatType === 'conversation') {
        stompClient.send(`/app/chat/conversation/${currentChatId}/leave`, {}, JSON.stringify({}));
    }
}

function handleReadReceiptUpdate(data) {
    const messageId = data.messageId;
    const readerName = data.readerName;

    if (!readReceipts.has(messageId)) {
        readReceipts.set(messageId, new Set());
    }
    readReceipts.get(messageId).add(readerName);

    // Update UI to show read status
    updateMessageReadStatus(messageId);
}

function updateMessageReadStatus(messageId) {
    const messageElement = document.querySelector(`[data-message-id="${messageId}"]`);
    if (!messageElement) return;

    const readers = readReceipts.get(messageId);
    if (!readers || readers.size === 0) return;

    const statusElement = messageElement.querySelector('.message-footer .message-status');
    if (statusElement) {
        const readersList = Array.from(readers).join(', ');
        statusElement.innerHTML = `
            <span class="status-icon read">✓✓</span>
            <span class="status-text">Đã xem bởi ${readersList}</span>
        `;
    }
}

window.addEventListener('beforeunload', function() {
    leaveCurrentChat();
    if (stompClient && connected) {
        stompClient.disconnect();
    }
});

function handleMessageStatusUpdate(data) {
    if (data && data.messageId && data.status) {
        const messageElement = document.querySelector(`[data-message-id="${data.messageId}"]`);
        if (messageElement) {
            const statusIcon = messageElement.querySelector('.status-icon');
            const statusText = messageElement.querySelector('.status-text');

            if (statusIcon) {
                statusIcon.textContent = getStatusIcon(data.status);
                statusIcon.className = `status-icon ${data.status.toLowerCase()}`;
            }

            if (statusText) {
                statusText.textContent = getStatusText(data.status);
            }
        }
    }
}

    async function startDirectChat(userId, userName) {
        try {
            const response = await fetch(`/api/conversations/direct/${userId}`, {
                method: 'GET',
                headers: {
                    'Authorization': 'Bearer ' + authToken
                }
            });

            if (response.ok) {
                const conversation = await response.json();
                switchSidebarTab('conversations');
                openConversation(conversation.id, userName);
                loadConversations();
            }
        } catch (error) {
            console.error('Error starting direct chat:', error);
            alert('Không thể bắt đầu cuộc trò chuyện');
        }
    }

    // Message loading functions
    async function loadConversationMessages(conversationId) {
        try {
            const response = await fetch(`/api/conversations/${conversationId}/messages?page=0&size=50`, {
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
            const response = await fetch(`/api/rooms/${roomId}/messages?page=0&size=50`, {
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

function displayMessages(messages, appendToTop = false) {
    const wrapper = document.getElementById('messagesWrapper');
    const typingIndicator = document.getElementById('typingIndicator');

    if (!appendToTop) {
        wrapper.innerHTML = '';
        wrapper.appendChild(typingIndicator);
    }

    const sortedMessages = messages.slice().reverse();

    for (const message of sortedMessages) {
        showMessage(message, false); // Không cuộn từng message
    }

    if (!appendToTop) {
        scrollToBottom();
    }
}



// FIXED: Message display function
    function showMessage(message, shouldScroll = true) {
        const wrapper = document.getElementById('messagesWrapper');
        const typingIndicator = document.getElementById('typingIndicator');

        // Check if message already exists
        const existingMessage = wrapper.querySelector(`[data-message-id="${message.id}"]`);
        if (existingMessage) {
            return;
        }

        const messageElement = document.createElement('div');
        const isOwnMessage = message.senderId === currentUser.id;
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
            const statusIcon = getStatusIcon(message.status);
            const reactions = message.reactions || [];

            messageElement.innerHTML = `
                ${!isOwnMessage ? `<div class="message-header">
                    <span class="message-sender">${message.senderName}</span>
                    <span class="message-time">${timestamp}</span>
                </div>` : ''}
                <div class="message-content">${message.content}</div>
                <div class="message-reactions" id="reactions-${messageId}">
                    ${renderReactions(reactions, messageId)}
                    <div class="add-reaction-btn" onclick="showReactionPicker('${messageId}', event)">➕</div>
                </div>
                <div class="message-footer">
                    ${isOwnMessage ? `<div class="message-status">
                        <span class="status-icon ${message.status ? message.status.toLowerCase() : 'sent'}">${statusIcon}</span>
                        <span class="status-text">${getStatusText(message.status)}</span>
                    </div>` : ''}
                    ${isOwnMessage ? `<div class="message-time">${timestamp}</div>` : ''}
                </div>
            `;

            if (isOwnMessage) {
                messageElement.addEventListener('dblclick', () => editMessage(messageId, message.content));
            }
        }

        messageElement.className = messageClass;
        messageElement.dataset.messageId = message.id;

        // Insert before typing indicator
        wrapper.insertBefore(messageElement, typingIndicator);

        // Auto scroll for new messages
        if (shouldScroll) {
            const container = document.getElementById('messagesContainer');
            const isAtBottom = container.scrollHeight - container.clientHeight <= container.scrollTop + 100;

            // Chỉ cuộn nếu người dùng đang ở gần đáy hoặc đây là tin của chính mình
            if (isAtBottom || isOwnMessage) {
                requestAnimationFrame(() => {
                    container.scrollTop = container.scrollHeight;
                });
            }
        }

        // Mark message as read if not own message
        if (!isOwnMessage && currentChatType && currentChatId) {
            setTimeout(() => markMessageAsRead(message.id), 1000);
        }
    }

    // FIXED: Scroll to bottom function
    function scrollToBottom() {
        const container = document.getElementById('messagesContainer');
        if (container) {
            requestAnimationFrame(() => {
                container.scrollTop = container.scrollHeight;
            });
        }
    }

    function renderReactions(reactions, messageId) {
        if (!reactions || reactions.length === 0) {
            return '';
        }

        return reactions.map(reaction => `
            <div class="reaction-item ${reaction.currentUserReacted ? 'own-reaction' : ''}"
                 onclick="toggleReaction('${messageId}', '${reaction.type}')">
                <span class="reaction-emoji">${reaction.emoji}</span>
                <span class="reaction-count">${reaction.count}</span>
            </div>
        `).join('');
    }

    function getStatusIcon(status) {
        const icons = {
            'SENDING': '🕐',
            'SENT': '✓',
            'DELIVERED': '✓✓',
            'READ': '✓✓',
            'FAILED': '❌'
        };
        return icons[status] || '✓';
    }

    function getStatusText(status) {
        const texts = {
            'SENDING': 'Đang gửi',
            'SENT': 'Đã gửi',
            'DELIVERED': 'Đã nhận',
            'READ': 'Đã xem',
            'FAILED': 'Thất bại'
        };
        return texts[status] || 'Đã gửi';
    }

    // Message sending
    function sendMessage() {
        const messageInput = document.getElementById('messageInput');
        const messageContent = messageInput.value.trim();
        if (!messageContent || !stompClient || !connected || !currentChatType || !currentChatId) return;

        const chatMessage = {
            content: messageContent,
            type: 'CHAT'
        };

        if (currentChatType === 'room') {
            stompClient.send(`/app/chat/room/${currentChatId}`, {}, JSON.stringify(chatMessage));
        } else if (currentChatType === 'conversation') {
            stompClient.send(`/app/chat/conversation/${currentChatId}`, {}, JSON.stringify(chatMessage));
        }

        messageInput.value = '';
        messageInput.style.height = 'auto';
        sendStopTyping();
    }

    // Typing functions
    function sendTyping() {
        if (!stompClient || !connected || !currentChatType || !currentChatId) {
            return;
        }

        const typingMessage = {
            typing: true
        };

        if (currentChatType === 'room') {
            stompClient.send(`/app/chat/typing/room/${currentChatId}`, {}, JSON.stringify(typingMessage));
        } else if (currentChatType === 'conversation') {
            stompClient.send(`/app/chat/typing/conversation/${currentChatId}`, {}, JSON.stringify(typingMessage));
        }
    }

    function sendStopTyping() {
        if (!stompClient || !connected || !currentChatType || !currentChatId) {
            return;
        }

        const stopTypingMessage = {
            typing: false
        };

        if (currentChatType === 'room') {
            stompClient.send(`/app/chat/typing/room/${currentChatId}`, {}, JSON.stringify(stopTypingMessage));
        } else if (currentChatType === 'conversation') {
            stompClient.send(`/app/chat/typing/conversation/${currentChatId}`, {}, JSON.stringify(stopTypingMessage));
        }

        isCurrentlyTyping = false;
    }

function handleTypingNotification(response) {
    console.log('Processing typing notification:', response);
    console.log('Response data:', response.data);

    try {
        let userId, username, isTyping;

        // Xử lý cấu trúc WebSocketResponse từ backend
        if (response.type === 'TYPING' && response.data) {
            // Cấu trúc: WebSocketResponse<TypingIndicator>
            const data = response.data;
            userId = data.userId;
            username = data.username || data.fullName;

            // Xác định isTyping từ action hoặc data.isTyping
            if (response.action === 'START') {
                isTyping = true;
            } else if (response.action === 'STOP') {
                isTyping = false;
            } else {
                isTyping = data.isTyping;
            }
        } else if (response.data && response.data.userId !== undefined) {
            // Fallback: data object trực tiếp
            const data = response.data;
            userId = data.userId;
            username = data.username || data.fullName;
            isTyping = data.isTyping;
        } else if (response.userId !== undefined) {
            // Fallback: response trực tiếp (backward compatibility)
            userId = response.userId;
            username = response.username || response.senderUsername;
            isTyping = response.isTyping;
        } else {
            console.warn('Unknown typing notification structure:', response);
            return;
        }

        console.log('Extracted values:', {userId, username, isTyping, currentUserId: currentUser.id});

        // Validate required fields
        if (userId === undefined || username === undefined || isTyping === undefined) {
            console.warn('Missing required fields in typing notification:', {userId, username, isTyping});
            return;
        }

        // Ignore own typing notifications
        if (userId === currentUser.id) {
            console.log('Ignoring own typing notification');
            return;
        }

        // Update typing users set
        if (isTyping) {
            console.log('Adding user to typing list:', username);
            typingUsers.add(username);
        } else {
            console.log('Removing user from typing list:', username);
            typingUsers.delete(username);
        }

        console.log('Current typing users:', Array.from(typingUsers));

        // Update UI
        updateTypingIndicator();

        console.log('Typing notification processed:', {
            username,
            isTyping,
            totalTypingUsers: typingUsers.size
        });

    } catch (error) {
        console.error('Error processing typing notification:', error, response);
    }
}

function updateTypingIndicator() {
    try {
        const indicator = document.getElementById('typingIndicator');
        const textElement = document.getElementById('typingText');

        if (!indicator || !textElement) {
            console.warn('Typing indicator elements not found');
            return;
        }

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


// Message reactions
    function showReactionPicker(messageId, event) {
        event.stopPropagation();

        const picker = document.getElementById('reactionPicker');
        currentMessageForReaction = messageId;

        picker.style.left = event.pageX + 'px';
        picker.style.top = (event.pageY - 50) + 'px';
        picker.classList.add('show');
    }

    function addReaction(reactionType) {
        if (!currentMessageForReaction) return;

        const request = {
            messageId: currentMessageForReaction,
            type: reactionType
        };

        fetch(`/api/messages/${currentMessageForReaction}/reactions`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': 'Bearer ' + authToken
            },
            body: JSON.stringify(request)
        }).then(response => {
            if (response.ok) {
                loadMessageReactions(currentMessageForReaction);
                hideReactionPicker();
            }
        }).catch(error => {
            console.error('Error adding reaction:', error);
        });
    }

    async function loadMessageReactions(messageId) {
        try {
            const response = await fetch(`/api/messages/${messageId}/reactions`, {
                headers: {
                    'Authorization': 'Bearer ' + authToken
                }
            });

            if (response.ok) {
                const reactions = await response.json();
                handleReactionUpdate({
                    messageId: messageId,
                    reactions: reactions
                });
            }
        } catch (error) {
            console.error('Error loading message reactions:', error);
        }
    }

    function toggleReaction(messageId, reactionType) {
        fetch(`/api/messages/${messageId}/reactions`, {
            method: 'DELETE',
            headers: {
                'Authorization': 'Bearer ' + authToken
            }
        }).then(response => {
            if (response.ok) {
                console.log('Reaction removed successfully');
            } else {
                console.error('Failed to remove reaction');
            }
        }).catch(error => {
            console.error('Error removing reaction:', error);
        });
    }

    function hideReactionPicker() {
        document.getElementById('reactionPicker').classList.remove('show');
        currentMessageForReaction = null;
    }

    function handleReactionUpdate(data) {
        let messageId, reactions;

        if (Array.isArray(data)) {
            reactions = data;
            messageId = currentMessageForReaction;
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
                ${renderReactions(reactions, messageId)}
                <div class="add-reaction-btn" onclick="showReactionPicker('${messageId}', event)">➕</div>
            `;
        }
    }

    // Message actions
    async function markMessageAsRead(messageId) {
        try {
            await fetch('/api/messages/read', {
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
            fetch(`/api/messages/${messageId}`, {
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

    // Modal functions
    function showAddConversationModal() {
        document.getElementById('addConversationModal').classList.add('show');
        selectedUserId = null;
        document.getElementById('startConversationBtn').disabled = true;
    }

    function showCreateRoomModal() {
        document.getElementById('createRoomModal').classList.add('show');
    }

    function showAddContactModal() {
        document.getElementById('addContactModal').classList.add('show');
    }

    function closeModal(modalId) {
        document.getElementById(modalId).classList.remove('show');
    }

    // Search functionality
    let searchTimeout;
    document.getElementById('userSearchInput').addEventListener('input', function() {
        clearTimeout(searchTimeout);
        const keyword = this.value.trim();

        if (keyword.length < 2) {
            document.getElementById('userSearchResults').innerHTML =
                '<div style="text-align: center; padding: 20px; color: #666;">Nhập ít nhất 2 ký tự để tìm kiếm</div>';
            return;
        }

        searchTimeout = setTimeout(() => searchUsers(keyword, 'userSearchResults'), 500);
    });

    document.getElementById('contactSearchInput').addEventListener('input', function() {
        clearTimeout(searchTimeout);
        const keyword = this.value.trim();

        if (keyword.length < 2) {
            document.getElementById('contactSearchResults').innerHTML =
                '<div style="text-align: center; padding: 20px; color: #666;">Nhập ít nhất 2 ký tự để tìm kiếm</div>';
            return;
        }

        searchTimeout = setTimeout(() => searchUsers(keyword, 'contactSearchResults'), 500);
    });

    async function searchUsers(keyword, resultContainerId) {
        try {
            const response = await fetch(`/api/users/search?keyword=${encodeURIComponent(keyword)}&page=0&size=20`, {
                headers: {
                    'Authorization': 'Bearer ' + authToken
                }
            });

            if (response.ok) {
                const data = await response.json();
                displayUserSearchResults(data.users || [], resultContainerId);
            }
        } catch (error) {
            console.error('Error searching users:', error);
            document.getElementById(resultContainerId).innerHTML =
                '<div style="text-align: center; padding: 20px; color: #dc3545;">Lỗi tìm kiếm</div>';
        }
    }

    function displayUserSearchResults(users, containerId) {
        const container = document.getElementById(containerId);

        if (users.length === 0) {
            container.innerHTML = '<div style="text-align: center; padding: 20px; color: #666;">Không tìm thấy người dùng nào</div>';
            return;
        }

        container.innerHTML = users.map(user => `
            <div class="user-item" onclick="selectUser(${user.id}, '${user.fullName || user.username}', '${containerId}')">
                <div class="user-item-avatar">${(user.fullName || user.username).charAt(0).toUpperCase()}</div>
                <div class="user-item-info">
                    <div class="user-item-name">${user.fullName || user.username}</div>
                    <div class="user-item-username">@${user.username}</div>
                </div>
                ${user.isOnline ? '<div class="status-badge status-online">Online</div>' : '<div class="status-badge status-offline">Offline</div>'}
            </div>
        `).join('');
    }

    function selectUser(userId, userName, containerId) {
        document.querySelectorAll(`#${containerId} .user-item`).forEach(item => item.classList.remove('selected'));
        event.target.closest('.user-item').classList.add('selected');

        if (containerId === 'userSearchResults') {
            selectedUserId = userId;
            document.getElementById('startConversationBtn').disabled = false;
        } else if (containerId === 'contactSearchResults') {
            sendFriendRequest(userId);
        }
    }

    async function startConversation() {
        if (!selectedUserId) return;

        try {
            const response = await fetch(`/api/conversations/direct/${selectedUserId}`, {
                method: 'GET',
                headers: {
                    'Authorization': 'Bearer ' + authToken
                }
            });

            if (response.ok) {
                const conversation = await response.json();
                closeModal('addConversationModal');

                switchSidebarTab('conversations');
                setTimeout(() => {
                    loadConversations();
                    openConversation(conversation.id, conversation.participant?.fullName || conversation.participant?.username);
                }, 500);
            }
        } catch (error) {
            console.error('Error starting conversation:', error);
            alert('Không thể bắt đầu cuộc trò chuyện');
        }
    }

    async function createRoom() {
        const name = document.getElementById('roomName').value.trim();
        const description = document.getElementById('roomDescription').value.trim();
        const type = document.getElementById('roomType').value;

        if (!name) {
            alert('Vui lòng nhập tên phòng');
            return;
        }

        try {
            const response = await fetch('/api/rooms', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': 'Bearer ' + authToken
                },
                body: JSON.stringify({
                    name: name,
                    description: description,
                    type: type
                })
            });

            if (response.ok) {
                const room = await response.json();
                closeModal('createRoomModal');

                document.getElementById('roomName').value = '';
                document.getElementById('roomDescription').value = '';

                switchSidebarTab('rooms');
                setTimeout(() => {
                    loadRooms();
                    openRoom(room.id, room.name);
                }, 500);
            } else {
                const error = await response.text();
                alert('Lỗi tạo phòng: ' + error);
            }
        } catch (error) {
            console.error('Error creating room:', error);
            alert('Không thể tạo phòng');
        }
    }

    async function sendFriendRequest(userId) {
        try {
            const response = await fetch('/api/users/contacts/add', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': 'Bearer ' + authToken
                },
                body: JSON.stringify({
                    contactId: userId
                })
            });

            if (response.ok) {
                alert('Đã gửi lời mời kết bạn!');
                closeModal('addContactModal');
            } else {
                const error = await response.text();
                alert('Lỗi: ' + error);
            }
        } catch (error) {
            console.error('Error sending friend request:', error);
            alert('Không thể gửi lời mời kết bạn');
        }
    }

    // File upload
    function selectFile() {
        document.getElementById('fileInput').click();
    }

    document.getElementById('fileInput').addEventListener('change', function(e) {
        const file = e.target.files[0];
        if (file) {
            uploadFile(file);
        }
    });

    async function uploadFile(file) {
        if (!file || !authToken) return;

        if (file.size > 50 * 1024 * 1024) {
            alert('File quá lớn! Kích thước tối đa là 50MB.');
            return;
        }

        const formData = new FormData();
        formData.append('file', file);

        try {
            const response = await fetch('/api/files/upload', {
                method: 'POST',
                headers: {
                    'Authorization': 'Bearer ' + authToken
                },
                body: formData
            });

            if (response.ok) {
                const fileMessage = await response.json();

                const chatMessage = {
                    content: `Đã gửi một file: ${fileMessage.originalFileName}`,
                    type: 'FILE',
                    fileUploadId: fileMessage.id
                };

                if (currentChatType === 'room') {
                    stompClient.send(`/app/chat/room/${currentChatId}`, {}, JSON.stringify(chatMessage));
                } else if (currentChatType === 'conversation') {
                    stompClient.send(`/app/chat/conversation/${currentChatId}`, {}, JSON.stringify(chatMessage));
                }

                document.getElementById('fileInput').value = '';
            } else {
                const error = await response.text();
                alert('Upload thất bại: ' + error);
            }
        } catch (error) {
            console.error('Upload error:', error);
            alert('Upload thất bại: ' + error.message);
        }
    }

    // Utility functions
    function formatTime(dateString) {
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

    function updateUserStatus(userStatus) {
        // Update online status in contact list
        const contactItems = document.querySelectorAll('#contactsList .list-item');
        contactItems.forEach(item => {
            // Update based on user status
        });
    }

    function searchInChat() {
        alert('Tính năng tìm kiếm đang được phát triển');
    }

    function showChatInfo() {
        alert('Tính năng thông tin chat đang được phát triển');
    }

    // Event listeners
    document.getElementById('messageInput').addEventListener('keypress', function(e) {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            sendMessage();
        }
    });

    document.getElementById('messageInput').addEventListener('input', function() {
        this.style.height = 'auto';
        this.style.height = Math.min(this.scrollHeight, 120) + 'px';

        if (!connected) return;

        if (!isCurrentlyTyping) {
            sendTyping();
            isCurrentlyTyping = true;
        }

        clearTimeout(typingTimer);
        typingTimer = setTimeout(() => {
            sendStopTyping();
        }, 1500);
    });

    document.addEventListener('click', function(e) {
        if (!e.target.closest('.reaction-picker') && !e.target.closest('.add-reaction-btn')) {
            hideReactionPicker();
        }
    });

    document.querySelectorAll('.modal').forEach(modal => {
        modal.addEventListener('click', function(e) {
            if (e.target === this) {
                this.classList.remove('show');
            }
        });
    });

    // Initialize app
    function initializeApp() {
        const storedToken = localStorage.getItem('authToken');
        const storedRefreshToken = localStorage.getItem('refreshToken');
        const storedUser = localStorage.getItem('currentUser');

        if (storedToken && storedRefreshToken && storedUser) {
            try {
                authToken = storedToken;
                refreshToken = storedRefreshToken;
                currentUser = JSON.parse(storedUser);

                document.getElementById('currentUsername').textContent = currentUser.fullName || currentUser.username;
                document.getElementById('userAvatar').textContent = (currentUser.fullName || currentUser.username).charAt(0).toUpperCase();
                document.getElementById('userRole').textContent = 'Thành viên';

                document.getElementById('authModal').classList.add('hidden');
                document.getElementById('chatContainer').classList.remove('hidden');

                connectWebSocket();
                loadInitialData();
            } catch (error) {
                console.error('Error parsing stored user data:', error);
                logout();
            }
        }
    }

    // Initialize when page loads
    document.addEventListener('DOMContentLoaded', function() {
        initializeApp();
    });

    // Periodic token refresh (every 20 minutes)
    setInterval(() => {
        if (refreshToken && authToken) {
            refreshAuthToken();
        }
    }, 20 * 60 * 1000);

    console.log('🚀 Fixed Chat Application with Proper Scrolling initialized');