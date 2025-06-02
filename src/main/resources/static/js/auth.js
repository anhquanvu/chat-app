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

    clearAuthErrors();
}

function clearAuthErrors() {
    document.getElementById('loginError').textContent = '';
    document.getElementById('registerError').textContent = '';
}

// Login form handler
function setupLoginForm() {
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
            const response = await fetch(APP_CONFIG.API_BASE_URL + API_ENDPOINTS.AUTH.SIGNIN, {
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
}

// Register form handler
function setupRegisterForm() {
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
            const response = await fetch(APP_CONFIG.API_BASE_URL + API_ENDPOINTS.AUTH.SIGNUP, {
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
}

function handleAuthSuccess(data) {
    authToken = data.accessToken;
    refreshToken = data.refreshToken;
    currentUser = {
        id: data.userId,
        username: data.username,
        fullName: data.fullName,
        email: data.email
    };

    // Store in localStorage
    localStorage.setItem(STORAGE_KEYS.AUTH_TOKEN, authToken);
    localStorage.setItem(STORAGE_KEYS.REFRESH_TOKEN, refreshToken);
    localStorage.setItem(STORAGE_KEYS.CURRENT_USER, JSON.stringify(currentUser));

    // Update UI
    updateUserInfo();
    showChatInterface();

    // Initialize chat
    connectWebSocket();
    loadInitialData();

    console.log('✅ User authenticated:', currentUser.username);
}

function updateUserInfo() {
    document.getElementById('currentUsername').textContent = currentUser.fullName || currentUser.username;
    document.getElementById('userAvatar').textContent = (currentUser.fullName || currentUser.username).charAt(0).toUpperCase();
    document.getElementById('userRole').textContent = 'Thành viên';
}

function showChatInterface() {
    document.getElementById('authModal').classList.add('hidden');
    document.getElementById('chatContainer').classList.remove('hidden');
}

function logout() {
    // Call logout API
    if (authToken) {
        fetch(APP_CONFIG.API_BASE_URL + API_ENDPOINTS.AUTH.LOGOUT, {
            method: 'POST',
            headers: {
                'Authorization': 'Bearer ' + authToken
            }
        }).catch(error => console.error('Logout error:', error));
    }

    // Disconnect WebSocket
    if (stompClient) {
        stompClient.disconnect();
        stompClient = null;
    }

    // Clear storage
    localStorage.removeItem(STORAGE_KEYS.AUTH_TOKEN);
    localStorage.removeItem(STORAGE_KEYS.REFRESH_TOKEN);
    localStorage.removeItem(STORAGE_KEYS.CURRENT_USER);

    // Reset global state
    authToken = null;
    refreshToken = null;
    currentUser = null;
    currentChatType = null;
    currentChatId = null;
    connected = false;

    // Show auth modal
    document.getElementById('authModal').classList.remove('hidden');
    document.getElementById('chatContainer').classList.add('hidden');

    // Reset forms
    document.getElementById('loginForm').reset();
    document.getElementById('registerForm').reset();
    clearAuthErrors();

    console.log('👋 User logged out');
}

async function refreshAuthToken() {
    if (!refreshToken) {
        logout();
        return;
    }

    try {
        const response = await fetch(APP_CONFIG.API_BASE_URL + API_ENDPOINTS.AUTH.REFRESH, {
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

            localStorage.setItem(STORAGE_KEYS.AUTH_TOKEN, authToken);
            localStorage.setItem(STORAGE_KEYS.REFRESH_TOKEN, refreshToken);

            connectWebSocket();
            console.log('🔄 Token refreshed');
        } else {
            console.warn('Token refresh failed');
            logout();
        }
    } catch (error) {
        console.error('Token refresh error:', error);
        logout();
    }
}

function initializeAuthFromStorage() {
    const storedToken = localStorage.getItem(STORAGE_KEYS.AUTH_TOKEN);
    const storedRefreshToken = localStorage.getItem(STORAGE_KEYS.REFRESH_TOKEN);
    const storedUser = localStorage.getItem(STORAGE_KEYS.CURRENT_USER);

    if (storedToken && storedRefreshToken && storedUser) {
        try {
            authToken = storedToken;
            refreshToken = storedRefreshToken;
            currentUser = JSON.parse(storedUser);

            updateUserInfo();
            showChatInterface();

            connectWebSocket();
            loadInitialData();

            console.log('🔐 Restored authentication for:', currentUser.username);
        } catch (error) {
            console.error('Error parsing stored user data:', error);
            logout();
        }
    }
}

// Setup periodic token refresh
function setupTokenRefresh() {
    setInterval(() => {
        if (refreshToken && authToken) {
            refreshAuthToken();
        }
    }, APP_CONFIG.TOKEN_REFRESH_INTERVAL);
}

console.log('🔐 Auth module loaded');