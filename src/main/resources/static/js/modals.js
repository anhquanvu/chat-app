// Modal management and user interactions
function showAddConversationModal() {
    document.getElementById('addConversationModal').classList.add('show');
    selectedUserId = null;
    document.getElementById('startConversationBtn').disabled = true;
    document.getElementById('userSearchInput').value = '';
    document.getElementById('userSearchResults').innerHTML =
        '<div style="text-align: center; padding: 20px; color: #666;">Nhập tên để tìm kiếm người dùng</div>';
}

function showCreateRoomModal() {
    document.getElementById('createRoomModal').classList.add('show');
    // Clear form
    document.getElementById('roomName').value = '';
    document.getElementById('roomDescription').value = '';
    document.getElementById('roomType').value = 'GROUP';
}

function showAddContactModal() {
    document.getElementById('addContactModal').classList.add('show');
    document.getElementById('contactSearchInput').value = '';
    document.getElementById('contactSearchResults').innerHTML =
        '<div style="text-align: center; padding: 20px; color: #666;">Nhập tên để tìm kiếm người dùng</div>';
}

function closeModal(modalId) {
    document.getElementById(modalId).classList.remove('show');
}

// User search functionality
function setupUserSearch() {
    // Search for conversations
    document.getElementById('userSearchInput').addEventListener('input', function() {
        clearTimeout(searchTimeout);
        const keyword = this.value.trim();

        if (keyword.length < 2) {
            document.getElementById('userSearchResults').innerHTML =
                '<div style="text-align: center; padding: 20px; color: #666;">Nhập ít nhất 2 ký tự để tìm kiếm</div>';
            return;
        }

        searchTimeout = setTimeout(() => searchUsers(keyword, 'userSearchResults'), APP_CONFIG.SEARCH_DEBOUNCE);
    });

    // Search for contacts
    document.getElementById('contactSearchInput').addEventListener('input', function() {
        clearTimeout(searchTimeout);
        const keyword = this.value.trim();

        if (keyword.length < 2) {
            document.getElementById('contactSearchResults').innerHTML =
                '<div style="text-align: center; padding: 20px; color: #666;">Nhập ít nhất 2 ký tự để tìm kiếm</div>';
            return;
        }

        searchTimeout = setTimeout(() => searchUsers(keyword, 'contactSearchResults'), APP_CONFIG.SEARCH_DEBOUNCE);
    });
}

async function searchUsers(keyword, resultContainerId) {
    try {
        const response = await fetch(`${APP_CONFIG.API_BASE_URL}${API_ENDPOINTS.USERS.SEARCH}?keyword=${encodeURIComponent(keyword)}&page=0&size=20`, {
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
    // Remove previous selections
    document.querySelectorAll(`#${containerId} .user-item`).forEach(item => item.classList.remove('selected'));

    // Add selection to clicked item
    event.target.closest('.user-item').classList.add('selected');

    if (containerId === 'userSearchResults') {
        selectedUserId = userId;
        document.getElementById('startConversationBtn').disabled = false;
    } else if (containerId === 'contactSearchResults') {
        // Send friend request
        sendFriendRequest(userId);
    }
}

async function startConversation() {
    if (!selectedUserId) return;

    try {
        const response = await fetch(`${APP_CONFIG.API_BASE_URL}${API_ENDPOINTS.CONVERSATIONS.DIRECT}/${selectedUserId}`, {
            method: 'GET',
            headers: {
                'Authorization': 'Bearer ' + authToken
            }
        });

        if (response.ok) {
            const conversation = await response.json();
            closeModal('addConversationModal');

            // Switch to conversations tab and open the conversation
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
        const response = await fetch(`${APP_CONFIG.API_BASE_URL}${API_ENDPOINTS.ROOMS.BASE}`, {
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

            // Switch to rooms tab and reload
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
        const response = await fetch(`${APP_CONFIG.API_BASE_URL}/users/contacts/add`, {
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

// Reaction picker functionality
function showReactionPicker(messageId, event) {
    event.stopPropagation();

    const picker = document.getElementById('reactionPicker');
    currentMessageForReaction = messageId;

    // Position picker near the click
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

    fetch(`${APP_CONFIG.API_BASE_URL}/messages/${currentMessageForReaction}/reactions`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': 'Bearer ' + authToken
        },
        body: JSON.stringify(request)
    }).then(response => {
        if (response.ok) {
            // Reaction will be updated via WebSocket
            hideReactionPicker();
        }
    }).catch(error => {
        console.error('Error adding reaction:', error);
    });
}

function toggleReaction(messageId, reactionType) {
    // Remove reaction if user already reacted with this type
    fetch(`${APP_CONFIG.API_BASE_URL}/messages/${messageId}/reactions`, {
        method: 'DELETE',
        headers: {
            'Authorization': 'Bearer ' + authToken
        }
    }).then(response => {
        if (response.ok) {
            // Reaction will be updated via WebSocket
        }
    }).catch(error => {
        console.error('Error removing reaction:', error);
    });
}

function hideReactionPicker() {
    document.getElementById('reactionPicker').classList.remove('show');
    currentMessageForReaction = null;
}

// File upload functionality
function selectFile() {
    document.getElementById('fileInput').click();
}

function setupFileUpload() {
    document.getElementById('fileInput').addEventListener('change', function(e) {
        const file = e.target.files[0];
        if (file) {
            uploadFile(file);
        }
    });
}

async function uploadFile(file) {
    if (!file || !authToken) return;

    if (file.size > APP_CONFIG.FILE_MAX_SIZE) {
        alert('File quá lớn! Kích thước tối đa là 50MB.');
        return;
    }

    const formData = new FormData();
    formData.append('file', file);

    try {
        const response = await fetch(`${APP_CONFIG.API_BASE_URL}${API_ENDPOINTS.FILES.UPLOAD}`, {
            method: 'POST',
            headers: {
                'Authorization': 'Bearer ' + authToken
            },
            body: formData
        });

        if (response.ok) {
            const fileMessage = await response.json();

            // Send message with file attachment
            const chatMessage = {
                content: `Đã gửi một file: ${fileMessage.originalFileName}`,
                type: MESSAGE_TYPES.FILE,
                fileUploadId: fileMessage.id
            };

            const destination = currentChatType === 'room'
                ? `${WS_DESTINATIONS.CHAT.ROOM}/${currentChatId}`
                : `${WS_DESTINATIONS.CHAT.CONVERSATION}/${currentChatId}`;

            if (stompClient && connected) {
                stompClient.send(destination, {}, JSON.stringify(chatMessage));
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

// Modal event handlers
function setupModalHandlers() {
    // Close modals when clicking outside
    document.querySelectorAll('.modal').forEach(modal => {
        modal.addEventListener('click', function(e) {
            if (e.target === this) {
                this.classList.remove('show');
            }
        });
    });

    // Hide reaction picker when clicking outside
    document.addEventListener('click', function(e) {
        if (!e.target.closest('.reaction-picker') && !e.target.closest('.add-reaction-btn')) {
            hideReactionPicker();
        }
    });

    // Setup user search
    setupUserSearch();

    // Setup file upload
    setupFileUpload();
}

console.log('🗂️ Modals module loaded');