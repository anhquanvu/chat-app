# Chat Backend - Spring Boot WebSocket Application

Một ứng dụng chat real-time được xây dựng với Spring Boot và WebSocket STOMP, hỗ trợ gửi tin nhắn, chia sẻ file và voice message.

## Tính năng

### Authentication System
- ✅ JWT authentication với refresh tokens
- ✅ Role-based access control (phân quyền theo vai trò)
- ✅ Quản lý đa thiết bị (multi-device session management)

### Real-time Messaging
- ✅ WebSocket với STOMP protocol đảm bảo real-time messaging ổn định và mở rộng được
- ✅ Typing indicators (hiển thị trạng thái đang gõ)
- ✅ Online/offline status (trạng thái trực tuyến/ngắt kết nối)
- ✅ Message broadcasting (phát tin nhắn đến nhiều người nhận)

### Contact Management
- ✅ Hệ thống gửi và nhận lời mời kết bạn (friend request system)
- ✅ Chặn/mở chặn liên hệ (contact blocking/unblocking)
- ✅ Danh bạ ưa thích (favorite contacts)
- ✅ Tìm kiếm liên hệ (contact search)

### Conversation System
- ✅ Nhắn tin trực tiếp 1-1 (one-on-one direct messaging)
- ✅ Phòng chat nhóm (group chat rooms)
- ✅ Phân trang lịch sử tin nhắn (message history pagination)
- ✅ Quản lý cuộc trò chuyện: ghim, lưu trữ, xoá (pin, archive, delete conversation)

### Message Features
- ✅ Phản ứng tin nhắn bằng emoji (message reactions)
- ✅ Xác nhận đã đọc (read receipts)
- ✅ Chỉnh sửa và xoá tin nhắn (message editing/deletion)
- ✅ Trả lời tin nhắn (reply to messages)
- ✅ Tìm kiếm tin nhắn (message search)

### File Sharing
- ✅ Upload và tải file (file upload/download)
- ✅ Tự động tạo thumbnail cho file media
- ✅ Kiểm tra hợp lệ loại file (file type validation)
- ✅ Theo dõi tiến trình upload/download (progress tracking)

### Security & Performance
- ✅ Giới hạn tốc độ gửi yêu cầu (rate limiting)
- ✅ Xác thực đầu vào (input validation)
- ✅ Mã hóa tin nhắn (message encryption - tùy chọn)
- ✅ Connection pooling cho database và WebSocket connections
- ✅ Redis caching để tăng tốc truy xuất dữ liệu

### Production Ready
- ✅ Docker containerization & Docker Compose hỗ trợ triển khai nhanh chóng
- ✅ Nginx reverse proxy tối ưu phân phối tải và bảo mật
- ✅ Migration database tự động (flyway/liquibase)
- ✅ Health checks endpoint cho hệ thống
- ✅ Monitoring & metrics tích hợp sẵn (như Micrometer, Prometheus)
- ✅ Logging chi tiết, có phân biệt môi trường (development/production)

---

## Thống kê dự án

- 📁 Tổng số file Java: 70+ lớp
- 📡 API Endpoints: 40+ REST endpoints
- 🔌 WebSocket Channels: 6 kênh real-time riêng biệt
- 🗄️ Database Tables: 12 bảng với nhiều quan hệ chặt chẽ (relationship)
- ⚙️ Configuration Files: 10+ lớp config đa dạng
- 🧪 Đã chuẩn bị đầy đủ môi trường và test cases cho testing
- 🚀 Sẵn sàng deploy lên server thực tế với Docker và Docker Compose

---

## Yêu cầu hệ thống

- Java 17+
- Gradle 7+
- Docker & Docker Compose (tuỳ chọn)

---

## Cài đặt và chạy

### 1. Clone repository
```bash
git clone <repository-url>
cd chat-backend
