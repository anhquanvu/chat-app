// Configuration and global variables
const APP_CONFIG = {
    API_BASE_URL: '/api',
    WS_ENDPOINT: '/ws',
    TOKEN_REFRESH_INTERVAL: 20 * 60 * 1000, // 20 minutes
    TYPING_TIMEOUT: 1500,
    MESSAGE_MAX_LENGTH: 1000,
    FILE_MAX_SIZE: 50 * 1024 * 1024, // 50MB
    SEARCH_DEBOUNCE: 500
};

// Global state
let stompClient = null;
let currentUser = null;
let authToken = null;
let refreshToken = null;
let connected = false;
let currentChatType = null; // 'room' or 'conversation'
let currentChatId = null;
let selectedUserId = null;
let typingTimer = null;
let isCurrentlyTyping = false;
let typingUsers = new Set();
let currentMessageForReaction = null;
let searchTimeout = null;

// Storage keys
const STORAGE_KEYS = {
    AUTH_TOKEN: 'authToken',
    REFRESH_TOKEN: 'refreshToken',
    CURRENT_USER: 'currentUser'
};

// API endpoints
const API_ENDPOINTS = {
    AUTH: {
        SIGNIN: '/auth/signin',
        SIGNUP: '/auth/signup',
        REFRESH: '/auth/refresh',
        LOGOUT: '/auth/logout'
    },
    CONVERSATIONS: {
        BASE: '/conversations',
        DIRECT: '/conversations/direct',
        MESSAGES: '/conversations/{id}/messages'
    },
    ROOMS: {
        BASE: '/rooms',
        MESSAGES: '/rooms/{id}/messages'
    },
    USERS: {
        SEARCH: '/users/search',
        CONTACTS: '/users/contacts'
    },
    MESSAGES: {
        BASE: '/messages',
        REACTIONS: '/messages/{id}/reactions',
        READ: '/messages/read'
    },
    FILES: {
        UPLOAD: '/files/upload'
    }
};

// WebSocket destinations
const WS_DESTINATIONS = {
    CHAT: {
        ROOM: '/app/chat/room',
        CONVERSATION: '/app/chat/conversation',
        TYPING_ROOM: '/app/chat/typing/room',
        TYPING_CONVERSATION: '/app/chat/typing/conversation'
    },
    TOPICS: {
        ROOM: '/topic/room',
        CONVERSATION: '/topic/conversation',
        USER_STATUS: '/topic/user-status'
    }
};

// Message types
const MESSAGE_TYPES = {
    CHAT: 'CHAT',
    JOIN: 'JOIN',
    LEAVE: 'LEAVE',
    FILE: 'FILE',
    VOICE: 'VOICE',
    TYPING: 'TYPING',
    STOP_TYPING: 'STOP_TYPING'
};

// Reaction types
const REACTION_TYPES = {
    LIKE: 'LIKE',
    LOVE: 'LOVE',
    LAUGH: 'LAUGH',
    WOW: 'WOW',
    CRY: 'CRY',
    ANGRY: 'ANGRY'
};

// Message status
const MESSAGE_STATUS = {
    SENDING: 'SENDING',
    SENT: 'SENT',
    DELIVERED: 'DELIVERED',
    READ: 'READ',
    FAILED: 'FAILED'
};

// Status icons and texts
const STATUS_ICONS = {
    [MESSAGE_STATUS.SENDING]: '🕐',
    [MESSAGE_STATUS.SENT]: '✓',
    [MESSAGE_STATUS.DELIVERED]: '✓✓',
    [MESSAGE_STATUS.READ]: '✓✓',
    [MESSAGE_STATUS.FAILED]: '❌'
};

const STATUS_TEXTS = {
    [MESSAGE_STATUS.SENDING]: 'Đang gửi',
    [MESSAGE_STATUS.SENT]: 'Đã gửi',
    [MESSAGE_STATUS.DELIVERED]: 'Đã nhận',
    [MESSAGE_STATUS.READ]: 'Đã xem',
    [MESSAGE_STATUS.FAILED]: 'Thất bại'
};

// Reaction emojis
const REACTION_EMOJIS = {
    [REACTION_TYPES.LIKE]: '👍',
    [REACTION_TYPES.LOVE]: '❤️',
    [REACTION_TYPES.LAUGH]: '😂',
    [REACTION_TYPES.WOW]: '😮',
    [REACTION_TYPES.CRY]: '😢',
    [REACTION_TYPES.ANGRY]: '😡'
};

console.log('📋 App configuration loaded');