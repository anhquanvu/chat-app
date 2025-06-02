# Chat Backend - Spring Boot WebSocket Application

Một ứng dụng chat real-time được xây dựng với Spring Boot và WebSocket STOMP, hỗ trợ gửi tin nhắn, chia sẻ file và voice message.

## Tính năng

- ✅ Chat real-time với WebSocket STOMP
- ✅ Gửi/nhận tin nhắn text
- ✅ Upload và chia sẻ file (với progress tracking)
- ✅ Gửi và nghe voice message
- ✅ Danh sách user online
- ✅ Typing indicators
- ✅ Message history (lưu trong memory)
- ✅ File storage trên server
- ✅ RESTful API
- ✅ Docker support
- ✅ Nginx reverse proxy
- ✅ Health checks

## Yêu cầu hệ thống

- Java 17+
- Gradle 7+
- Docker & Docker Compose (tùy chọn)

## Cài đặt và chạy

### 1. Clone repository
```bash
git clone <repository-url>
cd chat-backend
