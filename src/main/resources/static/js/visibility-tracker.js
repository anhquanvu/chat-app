// Visibility Tracker Module
class VisibilityTracker {
    constructor() {
        this.observer = null;
        this.visibilityTimeouts = new Map();
        this.trackedMessages = new Set();
        this.scrollTimeout = null;
        this.isUserActiveInChat = true;

        this.init();
    }

    init() {
        if (!window.IntersectionObserver) {
            console.warn('IntersectionObserver not supported');
            return;
        }

        this.observer = new IntersectionObserver((entries) => {
            entries.forEach(entry => this.handleVisibilityChange(entry));
        }, {
            threshold: 0.5,
            rootMargin: '0px 0px -50px 0px'
        });

        this.setupScrollTracking();
        this.observeExistingMessages();
    }

    setupScrollTracking() {
        const messagesContainer = document.getElementById('messagesContainer');
        if (messagesContainer) {
            messagesContainer.addEventListener('scroll', () => {
                this.isUserActiveInChat = true;

                clearTimeout(this.scrollTimeout);
                this.scrollTimeout = setTimeout(() => {
                    this.isUserActiveInChat = false;
                }, 2000); // User inactive after 2 seconds of no scrolling
            });
        }
    }

    handleVisibilityChange(entry) {
        const messageElement = entry.target;
        const messageId = messageElement.dataset.messageId;
        const senderId = messageElement.dataset.senderId;
        const isVisible = entry.isIntersecting;

        // Only track messages from other users
        if (!messageId || !senderId || !window.currentUser || senderId === window.currentUser.id.toString()) {
            return;
        }

        // Clear existing timeout
        if (this.visibilityTimeouts.has(messageId)) {
            clearTimeout(this.visibilityTimeouts.get(messageId));
        }

        // Debounce visibility updates
        const timeout = setTimeout(() => {
            this.sendVisibilityUpdate(messageId, isVisible);
        }, 300);

        this.visibilityTimeouts.set(messageId, timeout);
    }

    sendVisibilityUpdate(messageId, visible) {
        if (window.wsManager && window.wsManager.isConnected()) {
            window.wsManager.sendVisibilityUpdate(messageId, visible);
            console.debug(`Message visibility updated: ${messageId} - ${visible ? 'visible' : 'hidden'}`);
        }
    }

    observeExistingMessages() {
        if (!this.observer) return;

        document.querySelectorAll('.message[data-message-id]').forEach(messageEl => {
            const messageId = messageEl.dataset.messageId;
            if (!this.trackedMessages.has(messageId)) {
                this.observer.observe(messageEl);
                this.trackedMessages.add(messageId);
            }
        });
    }

    observeNewMessage(messageElement) {
        if (!this.observer || !messageElement) return;

        const messageId = messageElement.dataset.messageId;
        if (messageId && !this.trackedMessages.has(messageId)) {
            this.observer.observe(messageElement);
            this.trackedMessages.add(messageId);
        }
    }

    cleanup() {
        if (this.observer) {
            this.observer.disconnect();
        }

        this.visibilityTimeouts.forEach(timeout => clearTimeout(timeout));
        this.visibilityTimeouts.clear();
        this.trackedMessages.clear();

        if (this.scrollTimeout) {
            clearTimeout(this.scrollTimeout);
        }
    }

    isActive() {
        return this.isUserActiveInChat;
    }
}

// Initialize and export global instance
window.messageVisibilityTracker = new VisibilityTracker();

// Cleanup on page unload
window.addEventListener('beforeunload', () => {
    if (window.messageVisibilityTracker) {
        window.messageVisibilityTracker.cleanup();
    }
});