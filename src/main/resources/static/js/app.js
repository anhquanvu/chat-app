// Main application initialization and coordination
class ChatApp {
    constructor() {
        this.initialized = false;
        this.initPromise = null;
    }

    async init() {
        if (this.initialized) return;
        if (this.initPromise) return this.initPromise;

        this.initPromise = this._initialize();
        await this.initPromise;
        this.initialized = true;
    }

    async _initialize() {
        console.log('🚀 Initializing Chat Application...');

        try {
            // Setup authentication forms
            this.setupAuthenticationForms();

            // Setup message input handlers
            this.setupMessageHandlers();

            // Setup modal handlers
            setupModalHandlers();

            // Setup typing detection
            setupTypingDetection();

            // Setup periodic token refresh
            setupTokenRefresh();

            // Try to restore authentication from storage
            initializeAuthFromStorage();

            // Setup visibility change handler for notifications
            this.setupVisibilityHandler();

            // Setup keyboard shortcuts
            this.setupKeyboardShortcuts();

            console.log('✅ Chat Application initialized successfully');

        } catch (error) {
            console.error('❌ Failed to initialize Chat Application:', error);
            handleError(error, 'during application initialization');
        }
    }

    setupAuthenticationForms() {
        setupLoginForm();
        setupRegisterForm();
    }

    setupMessageHandlers() {
        setupMessageInput();

        // Setup send button click
        const sendBtn = document.getElementById('sendBtn');
        if (sendBtn) {
            sendBtn.addEventListener('click', sendMessage);
        }
    }

    setupVisibilityHandler() {
        document.addEventListener('visibilitychange', () => {
            if (document.hidden) {
                // Page is hidden, user might have switched tabs
                console.log('📱 App hidden');
            } else {
                // Page is visible again
                console.log('👁️ App visible');
                // Could mark messages as read here
            }
        });
    }

    setupKeyboardShortcuts() {
        document.addEventListener('keydown', (e) => {
            // Ctrl/Cmd + Enter to send message
            if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
                e.preventDefault();
                sendMessage();
            }

            // Escape to close modals
            if (e.key === 'Escape') {
                this.closeAllModals();
            }

            // Ctrl/Cmd + K for search
            if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
                e.preventDefault();
                searchInChat();
            }

            // Ctrl/Cmd + Shift + N for new conversation
            if ((e.ctrlKey || e.metaKey) && e.shiftKey && e.key === 'N') {
                e.preventDefault();
                showAddConversationModal();
            }

            // Ctrl/Cmd + Shift + R for new room
            if ((e.ctrlKey || e.metaKey) && e.shiftKey && e.key === 'R') {
                e.preventDefault();
                showCreateRoomModal();
            }
        });
    }

    closeAllModals() {
        document.querySelectorAll('.modal.show').forEach(modal => {
            modal.classList.remove('show');
        });
        hideReactionPicker();
    }

    // Global error handler
    setupGlobalErrorHandling() {
        window.addEventListener('error', (event) => {
            console.error('Global error:', event.error);
            handleError(event.error, 'global');
        });

        window.addEventListener('unhandledrejection', (event) => {
            console.error('Unhandled promise rejection:', event.reason);
            handleError(event.reason, 'unhandled promise');
        });
    }

    // Connection status monitoring
    setupConnectionMonitoring() {
        window.addEventListener('online', () => {
            console.log('🌐 Connection restored');
            showToast('Kết nối đã được khôi phục', 'success');

            // Reconnect WebSocket if needed
            if (!connected && authToken) {
                connectWebSocket();
            }
        });

        window.addEventListener('offline', () => {
            console.log('📡 Connection lost');
            showToast('Mất kết nối internet', 'warning');
        });
    }

    // Performance monitoring
    setupPerformanceMonitoring() {
        // Monitor long tasks
        if ('PerformanceObserver' in window) {
            const observer = new PerformanceObserver((list) => {
                list.getEntries().forEach((entry) => {
                    if (entry.duration > 50) {
                        console.warn('Long task detected:', entry.duration + 'ms');
                    }
                });
            });

            observer.observe({ entryTypes: ['longtask'] });
        }
    }

    // Cleanup on page unload
    setupCleanup() {
        window.addEventListener('beforeunload', () => {
            if (stompClient && connected) {
                stompClient.disconnect();
            }
        });
    }

    // Feature detection and polyfills
    checkBrowserSupport() {
        const features = {
            websocket: 'WebSocket' in window,
            notification: 'Notification' in window,
            fileAPI: 'File' in window && 'FileReader' in window,
            localStorage: 'localStorage' in window,
            fetch: 'fetch' in window
        };

        const missingFeatures = Object.entries(features)
            .filter(([, supported]) => !supported)
            .map(([feature]) => feature);

        if (missingFeatures.length > 0) {
            console.warn('Missing browser features:', missingFeatures);
            showToast('Trình duyệt của bạn không hỗ trợ đầy đủ các tính năng', 'warning', 5000);
        }

        return missingFeatures.length === 0;
    }

    // Development helpers
    setupDevelopmentHelpers() {
        if (process?.env?.NODE_ENV === 'development') {
            // Expose useful objects to window for debugging
            window.chatApp = this;
            window.currentUser = () => currentUser;
            window.authToken = () => authToken;
            window.stompClient = () => stompClient;
            window.connected = () => connected;

            console.log('🔧 Development helpers enabled');
        }
    }
}

// Initialize application when DOM is ready
document.addEventListener('DOMContentLoaded', async () => {
    console.log('📄 DOM loaded, starting chat application...');

    const app = new ChatApp();

    // Check browser support
    if (!app.checkBrowserSupport()) {
        console.error('Browser not fully supported');
        return;
    }

    // Setup additional features
    app.setupGlobalErrorHandling();
    app.setupConnectionMonitoring();
    app.setupPerformanceMonitoring();
    app.setupCleanup();
    app.setupDevelopmentHelpers();

    // Initialize the app
    try {
        await app.init();
    } catch (error) {
        console.error('Failed to start application:', error);
        showToast('Không thể khởi động ứng dụng', 'error', 5000);
    }
});

// Service Worker registration (if available)
if ('serviceWorker' in navigator) {
    window.addEventListener('load', () => {
        navigator.serviceWorker.register('/sw.js')
            .then((registration) => {
                console.log('SW registered: ', registration);
            })
            .catch((registrationError) => {
                console.log('SW registration failed: ', registrationError);
            });
    });
}

// Export for testing or external access
if (typeof module !== 'undefined' && module.exports) {
    module.exports = ChatApp;
}

console.log('🎯 Main application module loaded');