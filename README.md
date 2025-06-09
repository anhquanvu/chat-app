# Ứng Dụng Chat Thời Gian Thực

Một ứng dụng chat cấp doanh nghiệp được xây dựng với **Spring Boot** và các công nghệ web hiện đại, hỗ trợ nhắn tin thời gian thực, quản lý người dùng nâng cao và kiến trúc có thể mở rộng phù hợp với môi trường sản xuất.

## Tổng Quan

Ứng dụng cung cấp nền tảng nhắn tin đầy đủ hỗ trợ trò chuyện riêng và nhóm. Tích hợp WebSocket thời gian thực, xác thực mạnh mẽ, phản ứng tin nhắn bằng emoji và nhiều tính năng hiện đại giống như Discord, Slack hay Telegram.

## Tính Năng Chính

### ✅ Xác Thực & Quản Lý Người Dùng
- Xác thực bằng **JWT** kèm refresh token.
- Quản lý hồ sơ người dùng: avatar, tiểu sử, trạng thái online.
- Hỗ trợ phiên người dùng trên nhiều thiết bị.
- Cập nhật trạng thái online/offline thời gian thực.

### ✅ Nhắn Tin Thời Gian Thực
- Sử dụng **WebSocket** và STOMP để gửi/nhận tin tức thời.
- Hỗ trợ chỉ báo đang gõ, trạng thái tin nhắn (đã gửi, đã nhận, đã đọc).
- Gửi tin nhắn 1-1 hoặc theo nhóm.
- Hỗ trợ nội dung văn bản, emoji và đính kèm tệp.

### ✅ Quản Lý Phòng & Cuộc Trò Chuyện
- Tạo, tham gia và quản lý các phòng nhóm.
- Mỗi phòng có mô tả, quản trị viên và phân quyền.
- Hỗ trợ trò chuyện riêng tư và lưu lịch sử đầy đủ.

### ✅ Phản Hồi Tin Nhắn
- Phản ứng bằng emoji theo thời gian thực.
- Xem ai đã phản ứng và loại emoji đã dùng.
- Cập nhật đồng bộ trên tất cả thiết bị.

### ✅ Trả Lời & Luồng Tin Nhắn
- Trả lời tin cụ thể để tạo luồng hội thoại rõ ràng.
- Hiển thị nội dung gốc và người gửi khi trả lời.
- Hỗ trợ trả lời lồng nhau.

### ✅ Ghim Tin Nhắn
- Ghim các tin quan trọng trong phòng.
- Danh sách tin ghim riêng biệt, dễ truy cập.
- Phân quyền ai có thể ghim/bỏ ghim.

### ✅ Tìm Kiếm Nâng Cao
- Tìm tin nhắn theo từ khóa trên toàn hệ thống.
- Lọc theo người gửi, ngày, loại nội dung.
- Hiển thị kết quả có ngữ cảnh và link tới vị trí gốc.

### ✅ Chia Sẻ Tệp & Đính Kèm
- Hỗ trợ ảnh, tài liệu, media, v.v.
- Giới hạn định dạng và dung lượng.
- Hiển thị xem trước và tải về có phân quyền.

### ✅ Quản Lý Danh Bạ
- Gửi/nhận lời mời kết bạn.
- Quản lý danh sách liên hệ và chặn người dùng.
- Tổ chức danh bạ theo nhu cầu cá nhân.

### ✅ Tính Năng Quản Trị
- Quản lý người dùng, phòng, nội dung.
- Kiểm duyệt tin nhắn và theo dõi hoạt động hệ thống.
- Xem thống kê sử dụng và nhật ký hoạt động.

## Kiến Trúc Kỹ Thuật

### 🔧 Backend
- Spring Boot 3.5.0 với Java 17.
- Spring Security (bảo mật), Spring WebSocket (real-time), Spring Data JPA (CSDL).
- Xử lý lỗi, giao dịch và cấu trúc doanh nghiệp.

### 💾 Cơ Sở Dữ Liệu
- MySQL kết hợp HikariCP (connection pool).
- ORM: Hibernate.
- Flyway hỗ trợ migration.
- Index tối ưu cho hiệu suất tìm kiếm tin nhắn.

### ⚡ Giao Tiếp Thời Gian Thực
- WebSocket + STOMP + SockJS fallback.
- Giám sát trạng thái người dùng, typing, message sync.
- Kênh bảo mật xác thực khi kết nối WebSocket.

### 🚀 Hiệu Năng & Caching
- Hỗ trợ cache trong bộ nhớ hoặc Redis.
- Caching cho session, metadata tin nhắn, thông tin phòng.

### 🔐 Bảo Mật
- JWT + Refresh token.
- CORS, rate limiting, kiểm tra đầu vào.
- Phân quyền truy cập, ghi nhật ký truy cập.

## Công Nghệ Sử Dụng

- **Java 17**, **Spring Boot 3.5.0**
- **Gradle** (thay vì Maven)
- **MySQL**, **Hibernate**, **Flyway**
- **WebSocket (STOMP)**, **Spring Security**
- **Redis (tuỳ chọn)**, **Lombok**, **SpringDoc (OpenAPI)**

## Cài Đặt & Triển Khai

### 🧱 Yêu Cầu
- Java 17+  
- Gradle (sử dụng `./gradlew`)  
- MySQL 8+

### ⚙️ Cấu Hình
1. Tạo CSDL tên `chatapp` trong MySQL.
2. Cập nhật `application.yml`:
   - Thông tin kết nối DB
   - Secret key cho JWT
   - CORS, thư mục upload, giới hạn dung lượng

### 🔨 Build & Chạy
```bash
./gradlew build
java -jar build/libs/chatapp-0.0.1-SNAPSHOT.jar
```

Ứng dụng chạy tại: `http://localhost:8080`

## 📘 Tài Liệu API

Khi chạy ứng dụng, tài liệu API có tại:
```
http://localhost:8080/swagger-ui.html
```

Bao gồm mô tả endpoint, schema request/response và khả năng test trực tiếp.

## Triển Khai Thực Tế

### 🧠 Tối Ưu
- Sử dụng HikariCP, caching, cấu hình thread hợp lý.
- Theo dõi CPU/RAM khi tải lớn.

### 🔐 Bảo Mật
- Cấu hình tường lửa, SSL, giới hạn tốc độ.
- Theo dõi log và audit hoạt động hệ thống.

### ⚖️ Khả Năng Mở Rộng
- Hỗ trợ scale ngang (load balancer).
- Redis cho session & cache khi triển khai đa instance.

### 📈 Giám Sát & Bảo Trì
- Dùng Spring Boot Actuator để monitor.
- Kết hợp ELK hoặc Prometheus + Grafana để quan sát log, hiệu suất.

## Bản Quyền

Dự án thuộc quyền sở hữu doanh nghiệp. Việc sử dụng, phân phối hay chỉnh sửa phải tuân theo điều khoản giấy phép kèm theo.

## Hỗ Trợ & Tài Liệu

Vui lòng liên hệ đội ngũ phát triển hoặc tham khảo tài liệu đi kèm để được hỗ trợ, cấu hình và cập nhật tính năng.
