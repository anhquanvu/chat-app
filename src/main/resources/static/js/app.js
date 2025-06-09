// Main Application - Fixed and Modular
// Global variables for backward compatibility
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
let isUserActiveInChat = true;
let scrollTimeout;

// Authentication Tab Switching
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

// Sidebar Navigation
function switchSidebarTab(tab, event) {
    if (window.chatManager) {
        window.chatManager.leaveCurrentChat();
    }

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

// Authentication Form Handlers
document.getElementById('loginForm').addEventListener('submit', async (e) => {
    e.preventDefault();

    const username = document.getElementById('loginUsername').value.trim();
    const password = document.getElementById('loginPassword').value;
    const errorDiv = document.getElementById('loginError');

    if (!username || !password) {
        errorDiv.textContent = 'Vui lòng nhập đầy đủ thông tin';
        return;
    }

    const result = await window.authManager.login(username, password);
    if (!result.success) {
        errorDiv.textContent = result.error;
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

    const result = await window.authManager.register({
        username, email, fullName, phoneNumber, password
    });

    if (!result.success) {
        errorDiv.textContent = result.error;
    }
});

// Legacy functions for backward compatibility
function handleAuthSuccess(data) {
    return window.authManager.handleAuthSuccess(data);
}

function logout() {
    return window.authManager.logout();
}

function connectWebSocket() {
    if (window.authManager && window.wsManager) {
        return window.wsManager.connect(
            window.authManager.getAuthToken(),
            window.authManager.getCurrentUser()
        );
    }
    console.warn('AuthManager or WSManager not available');
}

async function refreshAuthToken() {
    if (window.authManager) {
        return await window.authManager.refreshAuthToken();
    }
    console.warn('AuthManager not available');
    return false;
}

// Data Loading Functions
async function loadInitialData() {
    await Promise.all([
        loadConversations(),
        loadRooms(),
        loadContacts()
    ]);
}

async function loadConversations() {
    try {
        const response = await fetch('/api/conversations?page=0&size=50', {
            headers: {
                'Authorization': 'Bearer ' + (window.authToken || window.authManager?.getAuthToken())
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
                'Authorization': 'Bearer ' + (window.authToken || window.authManager?.getAuthToken())
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
        const response = await fetch('/api/users/all?page=0&size=50', {
            headers: {
                'Authorization': 'Bearer ' + (window.authToken || window.authManager?.getAuthToken())
            }
        });

        if (response.ok) {
            const data = await response.json();
            displayContacts(data.users || []);
        }
    } catch (error) {
        console.error('Error loading contacts:', error);
        document.getElementById('contactsList').innerHTML =
            '<div style="text-align: center; padding: 20px; color: #dc3545;">Lỗi tải dữ liệu</div>';
    }
}

function displayContacts(users) {
    const container = document.getElementById('contactsList');

    if (users.length === 0) {
        container.innerHTML = '<div style="text-align: center; padding: 20px; color: #666;">Không có nhân viên nào</div>';
        return;
    }

    container.innerHTML = users.map(user => `
        <div class="list-item" onclick="startDirectChat(${user.id}, '${user.fullName || user.username}')">
            <div class="item-header">
                <div class="item-name">${user.fullName || user.username}</div>
                ${user.isOnline ? '<div class="online-indicator"></div>' : ''}
            </div>
            <div class="item-preview">@${user.username}</div>
        </div>
    `).join('');
}

// Chat Opening Functions - Updated to use managers
function openConversation(conversationId, title) {
    if (window.chatManager) {
        window.chatManager.openConversation(conversationId, title);
        // Update global variables for backward compatibility
        currentChatType = 'conversation';
        currentChatId = conversationId;
        currentChatKey = `conversation:${conversationId}`;
    }
}

function openRoom(roomId, title) {
    if (window.chatManager) {
        window.chatManager.openRoom(roomId, title);
        // Update global variables for backward compatibility
        currentChatType = 'room';
        currentChatId = roomId;
        currentChatKey = `room:${roomId}`;
    }
}

function leaveCurrentChat() {
    if (window.chatManager) {
        window.chatManager.leaveCurrentChat();
        // Clear global variables
        currentChatType = null;
        currentChatId = null;
        currentChatKey = null;
    }
}

async function startDirectChat(userId, userName) {
    try {
        const response = await fetch(`/api/conversations/direct/${userId}`, {
            method: 'GET',
            headers: {
                'Authorization': 'Bearer ' + (window.authToken || window.authManager?.getAuthToken())
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

// Message Functions - Delegate to MessageManager
function displayMessages(messages, appendToTop = false) {
    if (window.messageManager) {
        window.messageManager.displayMessages(messages, appendToTop);
    }
}

function showMessage(message, shouldScroll = true) {
    if (window.messageManager) {
        window.messageManager.showMessage(message, shouldScroll);
    }
}

function createMessageElement(message) {
    if (window.messageManager) {
        return window.messageManager.createMessageElement(message);
    }
    return document.createElement('div');
}

function handleMessageStatusUpdate(data) {
    if (window.messageManager) {
        window.messageManager.handleMessageStatusUpdate(data);
    }
}

function handleReadReceiptUpdate(data) {
    if (window.messageManager) {
        window.messageManager.handleReadReceiptUpdate(data);
    }
}

function handleBatchReadReceiptUpdate(data) {
    if (window.messageManager) {
        window.messageManager.handleBatchReadReceiptUpdate(data);
    }
}

function handleReactionUpdate(data) {
    if (window.messageManager) {
        window.messageManager.handleReactionUpdate(data);
    }
}

function updateMessage(message) {
    if (window.messageManager) {
        window.messageManager.updateMessage(message);
    }
}

function removeMessage(messageId) {
    if (window.messageManager) {
        window.messageManager.removeMessage(messageId);
    }
}

// Typing Functions - Delegate to ChatManager
function handleTypingNotification(response) {
    if (window.chatManager) {
        window.chatManager.handleTypingNotification(response);
    }
}

function sendMessage() {
    if (window.chatManager) {
        window.chatManager.sendMessage();
    }
}

function sendTyping() {
    if (window.chatManager) {
        window.chatManager.sendTyping();
    }
}

function sendStopTyping() {
    if (window.chatManager) {
        window.chatManager.sendStopTyping();
    }
}

// Message Input Event Handlers
document.getElementById('messageInput').addEventListener('keypress', function (e) {
    if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        sendMessage();
    }
});

document.getElementById('messageInput').addEventListener('input', function () {
    this.style.height = 'auto';
    this.style.height = Math.min(this.scrollHeight, 120) + 'px';

    if (window.chatManager) {
        window.chatManager.handleTypingInput();
    }
});

// Reaction Functions
function showReactionPicker(messageId, event) {
    if (window.messageManager) {
        window.messageManager.showReactionPicker(messageId, event);
    }
}

function addReaction(reactionType) {
    if (window.messageManager) {
        window.messageManager.addReaction(reactionType);
    }
}

function toggleReaction(messageId, reactionType) {
    if (window.messageManager) {
        window.messageManager.toggleReaction(messageId, reactionType);
    }
}

function hideReactionPicker() {
    if (window.messageManager) {
        window.messageManager.hideReactionPicker();
    }
}

// Modal Functions
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

// Search Functions
let searchTimeout;
document.getElementById('userSearchInput').addEventListener('input', function () {
    clearTimeout(searchTimeout);
    const keyword = this.value.trim();

    if (keyword.length < 2) {
        document.getElementById('userSearchResults').innerHTML =
            '<div style="text-align: center; padding: 20px; color: #666;">Nhập ít nhất 2 ký tự để tìm kiếm</div>';
        return;
    }

    searchTimeout = setTimeout(() => searchUsers(keyword, 'userSearchResults'), 500);
});

document.getElementById('contactSearchInput').addEventListener('input', function () {
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
        const response = await fetch(`/api/users/all?keyword=${encodeURIComponent(keyword)}&page=0&size=20`, {
            headers: {
                'Authorization': 'Bearer ' + (window.authToken || window.authManager?.getAuthToken())
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
         // Trong môi trường doanh nghiệp, có thể trực tiếp bắt đầu chat thay vì gửi lời mời kết bạn
         startDirectChatFromSearch(userId, userName);
     }
}

async function startDirectChatFromSearch(userId, userName) {
    try {
        const response = await fetch(`/api/conversations/direct/${userId}`, {
            method: 'GET',
            headers: {
                'Authorization': 'Bearer ' + (window.authToken || window.authManager?.getAuthToken())
            }
        });

        if (response.ok) {
            const conversation = await response.json();
            closeModal('addContactModal');

            switchSidebarTab('conversations');
            setTimeout(() => {
                loadConversations();
                openConversation(conversation.id, userName);
            }, 500);
        }
    } catch (error) {
        console.error('Error starting chat:', error);
        alert('Không thể bắt đầu cuộc trò chuyện');
    }
}

async function startConversation() {
    if (!selectedUserId) return;

    try {
        const response = await fetch(`/api/conversations/direct/${selectedUserId}`, {
            method: 'GET',
            headers: {
                'Authorization': 'Bearer ' + (window.authToken || window.authManager?.getAuthToken())
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
                'Authorization': 'Bearer ' + (window.authToken || window.authManager?.getAuthToken())
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
                'Authorization': 'Bearer ' + (window.authToken || window.authManager?.getAuthToken())
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

// File Upload Functions
function selectFile() {
    document.getElementById('fileInput').click();
}

document.getElementById('fileInput').addEventListener('change', function (e) {
    const file = e.target.files[0];
    if (file) {
        uploadFile(file);
    }
});

async function uploadFile(file) {
    if (!file) return;

    const token = window.authToken || window.authManager?.getAuthToken();
    if (!token) {
        alert('Vui lòng đăng nhập để upload file');
        return;
    }

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
                'Authorization': 'Bearer ' + token
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

            // Send via WebSocket
            if (window.wsManager && window.chatManager) {
                const chatInfo = window.chatManager.getCurrentChatInfo();
                if (chatInfo.type && chatInfo.id) {
                    window.wsManager.sendMessage(chatInfo.type, chatInfo.id, chatMessage);
                }
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

// Utility Functions
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

// Event Listeners
document.addEventListener('click', function (e) {
    if (!e.target.closest('.reaction-picker') && !e.target.closest('.add-reaction-btn')) {
        hideReactionPicker();
    }
});

document.querySelectorAll('.modal').forEach(modal => {
    modal.addEventListener('click', function (e) {
        if (e.target === this) {
            this.classList.remove('show');
        }
    });
});

// Page Unload Handler
window.addEventListener('beforeunload', function () {
    if (window.chatManager) {
        window.chatManager.leaveCurrentChat();
    }
    if (window.wsManager) {
        window.wsManager.disconnect();
    }
});

// Application Initialization
async function initializeApp() {
    console.log('🚀 Initializing Fixed Chat Application...');

    // Initialize from stored credentials
    if (window.authManager) {
        const initialized = await window.authManager.initializeFromStorage();
        if (!initialized) {
            console.log('No stored credentials found');
        }
    }
}

// Initialize when page loads
document.addEventListener('DOMContentLoaded', function () {
    initializeApp();
});

console.log('🚀 Fixed Chat Application with Modular Architecture initialized');