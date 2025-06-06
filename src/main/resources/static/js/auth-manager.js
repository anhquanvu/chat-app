// Authentication Manager
class AuthManager {
    constructor() {
        this.authToken = null;
        this.refreshToken = null;
        this.currentUser = null;
        this.refreshInterval = null;
    }

    async handleAuthSuccess(data) {
        this.authToken = data.accessToken;
        this.refreshToken = data.refreshToken;
        this.currentUser = {
            id: data.userId,
            username: data.username,
            fullName: data.fullName,
            email: data.email
        };

        if (!this.currentUser.id || !this.currentUser.username) {
            console.error('Invalid user data received:', this.currentUser);
            throw new Error('Invalid user data');
        }

        // Store in localStorage
        localStorage.setItem('authToken', this.authToken);
        localStorage.setItem('refreshToken', this.refreshToken);
        localStorage.setItem('currentUser', JSON.stringify(this.currentUser));

        // Update global variables for backward compatibility
        window.authToken = this.authToken;
        window.refreshToken = this.refreshToken;
        window.currentUser = this.currentUser;

        // Update UI
        this.updateUserUI();

        // Show chat interface
        document.getElementById('authModal').classList.add('hidden');
        document.getElementById('chatContainer').classList.remove('hidden');

        // Connect WebSocket (only if not already connected)
        if (window.wsManager && !window.wsManager.isConnected()) {
            try {
                await window.wsManager.connect(this.authToken, this.currentUser);
            } catch (error) {
                if (error.message === 'AUTH_ERROR') {
                    await this.refreshAuthToken();
                } else {
                    console.error('WebSocket connection failed:', error);
                }
            }
        }

        // Load initial data
        if (window.loadInitialData) {
            await window.loadInitialData();
        }

        // Start periodic token refresh
        this.startTokenRefresh();
    }

    updateUserUI() {
        const displayName = this.currentUser.fullName || this.currentUser.username;
        document.getElementById('currentUsername').textContent = displayName;
        document.getElementById('userAvatar').textContent = displayName.charAt(0).toUpperCase();
        document.getElementById('userRole').textContent = 'Thành viên';
    }

    async login(username, password) {
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
                await this.handleAuthSuccess(data);
                return { success: true };
            } else {
                const error = await response.text();
                return { success: false, error: error || 'Đăng nhập thất bại' };
            }
        } catch (error) {
            console.error('Login error:', error);
            return { success: false, error: 'Lỗi kết nối đến server' };
        }
    }

    async register(userData) {
        try {
            const response = await fetch('/api/auth/signup', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(userData)
            });

            if (response.ok) {
                const data = await response.json();
                await this.handleAuthSuccess(data);
                return { success: true };
            } else {
                const error = await response.text();
                return { success: false, error: error || 'Đăng ký thất bại' };
            }
        } catch (error) {
            console.error('Register error:', error);
            return { success: false, error: 'Lỗi kết nối đến server' };
        }
    }

    async refreshAuthToken() {
        if (!this.refreshToken) {
            this.logout();
            return false;
        }

        try {
            const response = await fetch('/api/auth/refresh', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: this.refreshToken
            });

            if (response.ok) {
                const data = await response.json();
                this.authToken = data.accessToken;
                this.refreshToken = data.refreshToken;

                // Update storage
                localStorage.setItem('authToken', this.authToken);
                localStorage.setItem('refreshToken', this.refreshToken);

                // Update global variables
                window.authToken = this.authToken;
                window.refreshToken = this.refreshToken;

                // Reconnect WebSocket with new token
                if (window.wsManager) {
                    await window.wsManager.connect(this.authToken, this.currentUser);
                }

                return true;
            } else {
                this.logout();
                return false;
            }
        } catch (error) {
            console.error('Token refresh error:', error);
            this.logout();
            return false;
        }
    }

    async logout() {
        console.log('Logging out...');

        // Stop token refresh
        if (this.refreshInterval) {
            clearInterval(this.refreshInterval);
            this.refreshInterval = null;
        }

        // Leave current chat
        if (window.chatManager) {
            await window.chatManager.leaveCurrentChat();
        }

        // Disconnect WebSocket
        if (window.wsManager) {
            await window.wsManager.disconnect();
        }

        // Call logout API
        if (this.authToken) {
            try {
                await fetch('/api/auth/logout', {
                    method: 'POST',
                    headers: {
                        'Authorization': 'Bearer ' + this.authToken
                    }
                });
            } catch (error) {
                console.error('Logout API error:', error);
            }
        }

        // Clear storage
        localStorage.removeItem('authToken');
        localStorage.removeItem('refreshToken');
        localStorage.removeItem('currentUser');

        // Clear local variables
        this.authToken = null;
        this.refreshToken = null;
        this.currentUser = null;

        // Clear global variables
        window.authToken = null;
        window.refreshToken = null;
        window.currentUser = null;

        // Reset UI
        document.getElementById('authModal').classList.remove('hidden');
        document.getElementById('chatContainer').classList.add('hidden');

        // Clear forms
        document.getElementById('loginForm').reset();
        document.getElementById('registerForm').reset();
        document.getElementById('loginError').textContent = '';
        document.getElementById('registerError').textContent = '';
    }

    async initializeFromStorage() {
        const storedToken = localStorage.getItem('authToken');
        const storedRefreshToken = localStorage.getItem('refreshToken');
        const storedUser = localStorage.getItem('currentUser');

        if (storedToken && storedRefreshToken && storedUser) {
            try {
                this.authToken = storedToken;
                this.refreshToken = storedRefreshToken;
                this.currentUser = JSON.parse(storedUser);

                // Update global variables
                window.authToken = this.authToken;
                window.refreshToken = this.refreshToken;
                window.currentUser = this.currentUser;

                // Update UI
                this.updateUserUI();

                // Show chat interface
                document.getElementById('authModal').classList.add('hidden');
                document.getElementById('chatContainer').classList.remove('hidden');

                // Connect WebSocket
                if (window.wsManager && !window.wsManager.isConnected()) {
                    try {
                        await window.wsManager.connect(this.authToken, this.currentUser);
                    } catch (error) {
                        if (error.message === 'AUTH_ERROR') {
                            await this.refreshAuthToken();
                        } else {
                            console.error('WebSocket connection failed:', error);
                        }
                    }
                }

                // Load initial data
                if (window.loadInitialData) {
                    await window.loadInitialData();
                }

                // Start token refresh
                this.startTokenRefresh();

                return true;
            } catch (error) {
                console.error('Error parsing stored user data:', error);
                this.logout();
                return false;
            }
        }
        return false;
    }

    startTokenRefresh() {
        // Clear existing interval
        if (this.refreshInterval) {
            clearInterval(this.refreshInterval);
        }

        // Refresh token every 20 minutes
        this.refreshInterval = setInterval(() => {
            if (this.refreshToken && this.authToken) {
                this.refreshAuthToken();
            }
        }, 20 * 60 * 1000);
    }

    isAuthenticated() {
        return !!(this.authToken && this.currentUser);
    }

    getAuthToken() {
        return this.authToken;
    }

    getCurrentUser() {
        return this.currentUser;
    }
}

// Export global instance
window.authManager = new AuthManager();