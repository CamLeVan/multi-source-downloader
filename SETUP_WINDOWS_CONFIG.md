# ⚙️ Cấu Hình Cho Máy Windows (IP: 192.168.1.214)

## 📋 Tóm Tắt Setup
- **Máy Windows (IP: 192.168.1.214)**: Origin Server
- **Máy macOS (IP: 192.168.1.83)**: P2P Tracker + JavaFX Client

---

## 🔧 BƯỚC 1: Cập Nhật Config File

### Trên máy Windows, mở file:
```
origin-server\src\main\resources\config.properties
```

### Thay đổi nội dung thành:
```properties
# Origin Server Configuration
server.host=192.168.1.214
server.port=8443
server.name=Origin-Server

# File Server
file.directory=server_files

# Logging
log.level=INFO
log.show.ip=true
log.show.flow=true
```

**⚠️ QUAN TRỌNG:** Đảm bảo `server.host=192.168.1.214` (IP của máy Windows)

---

## 🔥 BƯỚC 2: Mở Firewall

### Chạy Command Prompt **AS ADMINISTRATOR**:

```cmd
netsh advfirewall firewall add rule name="Origin Server" dir=in action=allow protocol=TCP localport=8443
netsh advfirewall firewall add rule name="P2P Peer Port" dir=in action=allow protocol=TCP localport=6881
```

### Kiểm tra firewall rule đã tạo:
```cmd
netsh advfirewall firewall show rule name="Origin Server"
```

---

## 🏗️ BƯỚC 3: Build Project (Nếu chưa)

```cmd
cd đường_dẫn_đến\multi-source-downloader
mvn clean install
```

---

## 🚀 BƯỚC 4: Chạy Origin Server

```cmd
cd origin-server
mvn spring-boot:run
```

### ✅ Log Thành Công:
```
[INFO] ═══════════════════════════════════════════════════════════════
[INFO] 🚀 Origin Server Configuration
[INFO] ═══════════════════════════════════════════════════════════════
[INFO] Server Name    : Origin-Server
[INFO] Server Host    : 192.168.1.214
[INFO] Server Port    : 8443
[INFO] File Directory : server_files
[INFO] ═══════════════════════════════════════════════════════════════
```

**⚠️ QUAN TRỌNG:** 
- Kiểm tra log có hiển thị đúng IP `192.168.1.214`
- Nếu thấy `localhost` hoặc IP khác → Config chưa đúng!

---

## ✅ BƯỚC 5: Kiểm Tra Kết Nối

### Từ máy Windows, test local:
```cmd
curl -k https://localhost:8443/manifest/100MB.zip
```

### Từ máy macOS, test từ xa:
```bash
curl -k https://192.168.1.214:8443/manifest/100MB.zip
```

Nếu thấy JSON manifest → ✅ Thành công!

---

## 🐛 Troubleshooting

### Lỗi: "Address already in use: bind"
Port 8443 đang được dùng. Kiểm tra:
```cmd
netstat -ano | findstr :8443
taskkill /PID <PID> /F
```

### Lỗi: "Connection refused" từ macOS
1. **Kiểm tra firewall đã mở chưa**
2. **Kiểm tra IP trong config đúng chưa**
3. **Ping thử từ macOS:**
   ```bash
   ping 192.168.1.214
   ```

### Lỗi: File không tìm thấy
Server sẽ tự động tạo file test `100MB.zip` khi khởi động lần đầu.

---

## 📊 Network Topology

```
┌─────────────────────────────────────────────────────────┐
│                                                         │
│  Windows (192.168.1.214)                               │
│  ┌────────────────────────────────┐                    │
│  │   Origin Server (Port 8443)    │                    │
│  │   - Phục vụ file downloads     │                    │
│  │   - HTTPS with self-signed SSL │                    │
│  └────────────────────────────────┘                    │
│                    ▲                                    │
└────────────────────┼───────────────────────────────────┘
                     │ HTTPS GET /manifest
                     │ HTTPS GET /files/...
                     │
┌────────────────────┼───────────────────────────────────┐
│                    ▼                                    │
│  macOS (192.168.1.83)                                  │
│  ┌────────────────────────────────┐                    │
│  │   P2P Tracker (Port 8081)      │                    │
│  │   - Quản lý peer list          │                    │
│  └────────────────────────────────┘                    │
│                                                         │
│  ┌────────────────────────────────┐                    │
│  │   JavaFX Client                │                    │
│  │   - Download UI                │                    │
│  │   - Peer Server (Port 6881)    │                    │
│  │   - Virtual Filesystem Support │                    │
│  └────────────────────────────────┘                    │
└─────────────────────────────────────────────────────────┘
```

---

## 🎯 Sau Khi Origin Server Chạy

**Sang máy macOS** và làm theo hướng dẫn tiếp theo để chạy Tracker và Client.

Server sẵn sàng khi thấy log:
```
✅ Origin Server is ready to serve files!
📁 Available files in server_files/:
   - 100MB.zip (104,857,600 bytes)
```

---

## 📝 Quick Commands

```cmd
# Build
mvn clean install

# Run Origin Server
cd origin-server
mvn spring-boot:run

# Check port
netstat -ano | findstr :8443

# Check firewall
netsh advfirewall firewall show rule name="Origin Server"

# Test endpoint
curl -k https://localhost:8443/manifest/100MB.zip
```

