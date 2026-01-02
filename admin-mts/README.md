# Admin Panel - Hướng Dẫn Sử Dụng

## Cách Chạy Admin Panel

### Bước 1: Khởi động Servers

**1. Origin Server (Port 8080):**
```bash
cd origin-server
mvn spring-boot:run
# Hoặc chạy OriginServerApplication từ IDE
```

**2. P2P Tracker (Port 8081):**
```bash
cd p2p-tracker
mvn spring-boot:run
# Hoặc chạy P2PTrackerApplication từ IDE
```

### Bước 2: Chạy Admin Panel

```bash
cd admin-client
mvn javafx:run
# Hoặc chạy AdminApp.main() từ IDE
```

## Tính Năng

### 📊 Dashboard
- Xem tổng quan: Files, Peers, Disk usage
- Real-time stats từ cả 2 servers
- System information

### 📁 File Management
- Xem danh sách files
- Upload files mới
- Delete files
- Regenerate manifest

### 👥 Peer Monitoring
- Xem active swarms
- Monitor peers
- Clear file swarms

### 📈 Statistics
- Placeholder cho tương lai

## Lưu Ý

- **Khi servers chưa chạy**: Admin Panel sẽ hiển thị thông báo "Server not running" thay vì error popup
- **Khi servers đã chạy**: Tất cả tính năng sẽ hoạt động bình thường
- **Cấu hình**: Sửa file `src/main/resources/config.properties` nếu cần thay đổi URLs

## Troubleshooting

**Lỗi "Failed to connect":**
- Kiểm tra Origin Server đã chạy trên port 8080
- Kiểm tra P2P Tracker đã chạy trên port 8081
- Kiểm tra firewall không block ports

**Lỗi "Server not running":**
- Đây là thông báo bình thường khi servers chưa khởi động
- Chỉ cần start 2 servers và refresh lại

