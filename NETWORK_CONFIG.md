# Cấu Hình Mạng và IP Addresses

## 📋 Cấu Hình IP Cố Định

### File Cấu Hình

Mỗi component có file `config.properties` để cấu hình IP:

#### 1. Client (`javafx-client/src/main/resources/config.properties`)
```properties
# Tracker Server IP
tracker.url=http://192.168.1.100:8081

# Origin Server IP
origin.server.url=https://192.168.1.101:8443

# Client Name (hiển thị trong UI)
client.name=Client-1
```

#### 2. Origin Server (`origin-server/src/main/resources/config.properties`)
```properties
# Origin Server IP
server.host=192.168.1.101
server.port=8443
server.name=Origin-Server
```

#### 3. P2P Tracker (`p2p-tracker/src/main/resources/config.properties`)
```properties
# Tracker Server IP
server.host=192.168.1.100
server.port=8081
tracker.name=P2P-Tracker
```

## 🔧 Cách Thiết Lập IP

### Bước 1: Xác định IP của từng máy

**Trên mỗi máy, chạy:**
```bash
# macOS/Linux
ifconfig | grep "inet "

# Windows
ipconfig
```

**Ghi lại IP của:**
- Máy chạy Origin Server: `192.168.1.101`
- Máy chạy P2P Tracker: `192.168.1.100`
- Máy chạy Client: `192.168.1.102` (tự động detect)

### Bước 2: Cập nhật Config Files

**Sửa `javafx-client/src/main/resources/config.properties`:**
```properties
tracker.url=http://192.168.1.100:8081
origin.server.url=https://192.168.1.101:8443
```

**Sửa `origin-server/src/main/resources/config.properties`:**
```properties
server.host=192.168.1.101
```

**Sửa `p2p-tracker/src/main/resources/config.properties`:**
```properties
server.host=192.168.1.100
```

### Bước 3: Rebuild và Chạy

```bash
mvn clean install
# Sau đó chạy các servers và clients
```

## 📊 Flow Logging với IP Addresses

Hệ thống sẽ tự động in ra màn hình trình tự hoạt động với IP addresses:

### Ví dụ Output:

```
╔═══════════════════════════════════════════════════════════════╗
║ CLIENT INITIALIZATION                                          ║
╚═══════════════════════════════════════════════════════════════╝
[14:30:15.123] [INFO] Client Name: Client-1 | IP: 192.168.1.102
[14:30:15.124] [INFO] Local IP Address: 192.168.1.102 | IP: 192.168.1.102
[14:30:15.125] [INFO] Tracker URL: http://192.168.1.100:8081 | IP: 192.168.1.102
[14:30:15.126] [INFO] Origin Server URL: https://192.168.1.101:8443 | IP: 192.168.1.102
═══════════════════════════════════════════════════════════════

╔═══════════════════════════════════════════════════════════════╗
║ DOWNLOAD FLOW STARTED                                          ║
╚═══════════════════════════════════════════════════════════════╝
[14:30:16.100] [STEP 01] Fetching manifest | FROM: 192.168.1.102 → TO: 192.168.1.101:8443
[14:30:16.200] [ORIGIN] GET /manifest/100MB.zip | FROM: 192.168.1.102 → TO: 192.168.1.101:8443
[14:30:16.250] [ORIGIN] ✓ Manifest received | FROM: 192.168.1.101:8443 → TO: 192.168.1.102 | Pieces: 100

[14:30:16.300] [STEP 02] Initializing Tracker Client | FROM: 192.168.1.102 → TO: 192.168.1.100:8081

[14:30:16.400] [STEP 03] Starting Peer Server | FROM: 192.168.1.102 → TO: N/A
[14:30:16.450] [INFO] Peer Server started on 192.168.1.102:6881 | IP: 192.168.1.102

[14:30:16.500] [STEP 04] Announcing to Tracker | FROM: 192.168.1.102:6881 → TO: 192.168.1.100:8081
[14:30:16.550] [TRACKER] POST /tracker/announce | FROM: 192.168.1.102 → TO: 192.168.1.100:8081
[14:30:16.600] [TRACKER] ✓ Announce successful | FROM: Client → TO: 192.168.1.100:8081

[14:30:16.650] [STEP 05] Fetching peer list from Tracker | FROM: 192.168.1.102 → TO: 192.168.1.100:8081
[14:30:16.700] [TRACKER] GET /tracker/peers?fileId=100MB.zip | FROM: 192.168.1.102 → TO: 192.168.1.100:8081
[14:30:16.750] [TRACKER] ✓ Retrieved 1 peers | FROM: 192.168.1.100:8081 → TO: 192.168.1.102

[14:30:16.800] [STEP 06] Starting Download | FROM: 192.168.1.102 → TO: N/A
═══════════════════════════════════════════════════════════════

[14:30:17.100] [DOWNLOAD] Worker pool-1-thread-1 downloading piece 0 | Sources: 3 | First source: 192.168.1.101
[14:30:17.200] [DOWNLOAD] ✓ Piece 0 completed | FROM: 192.168.1.101 | Size: 1024 KB | Total: 1 MB

[14:30:17.300] [DOWNLOAD] Worker pool-1-thread-2 downloading piece 1 | Sources: 3 | First source: 192.168.1.101
[14:30:17.400] [DOWNLOAD] ✓ Piece 1 completed | FROM: 192.168.1.101 | Size: 1024 KB | Total: 2 MB
```

## 🎯 Các Loại Log Messages

### 1. Client Initialization
- `[CLIENT INITIALIZATION]` - Khởi tạo client
- Hiển thị: Client name, Local IP, Tracker URL, Origin Server URL

### 2. Download Flow Steps
- `[STEP XX]` - Từng bước trong quá trình download
- Format: `FROM: IP:PORT → TO: IP:PORT`

### 3. Origin Server Interactions
- `[ORIGIN]` - Giao tiếp với Origin Server
- GET manifest, GET file pieces với Range headers

### 4. Tracker Interactions
- `[TRACKER]` - Giao tiếp với P2P Tracker
- POST announce, GET peers list

### 5. Peer-to-Peer
- `[PEER]` - Giao tiếp giữa các peers
- GET piece từ peer khác, serve piece cho peer khác

### 6. Download Progress
- `[DOWNLOAD]` - Tiến trình download từng piece
- Hiển thị source IP, piece size, total downloaded

### 7. Hash Verification
- `[HASH]` - Xác thực hash của piece
- ✓ Verified hoặc ✗ Mismatch với retry

### 8. Retry Logic
- `[RETRY]` - Retry khi hash mismatch hoặc lỗi
- Hiển thị source cũ và source mới

## 🔍 Cách Đọc Logs

### Format chung:
```
[TIMESTAMP] [CATEGORY] Message | FROM: IP:PORT → TO: IP:PORT | Additional Info
```

### Ký hiệu:
- `✓` = Thành công
- `✗` = Thất bại/Lỗi
- `→` = Hướng luồng dữ liệu

### Ví dụ phân tích:

```
[14:30:16.500] [STEP 04] Announcing to Tracker | FROM: 192.168.1.102:6881 → TO: 192.168.1.100:8081
```
**Ý nghĩa:** Client (192.168.1.102:6881) đang announce với Tracker (192.168.1.100:8081)

```
[14:30:17.200] [DOWNLOAD] ✓ Piece 0 completed | FROM: 192.168.1.101 | Size: 1024 KB
```
**Ý nghĩa:** Piece 0 đã tải xong từ Origin Server (192.168.1.101), kích thước 1024 KB

```
[14:30:18.100] [HASH] ✗ Mismatch for piece 5 | FROM: 192.168.1.101 | Expected: abc123... | Got: def456...
[14:30:18.150] [RETRY] Retrying piece 5 | FROM: 192.168.1.101 → TO: 192.168.1.103
```
**Ý nghĩa:** Hash mismatch từ Origin, đang retry từ Peer (192.168.1.103)

## 🛠️ Troubleshooting với IP

### Kiểm tra kết nối:
```bash
# Từ Client, ping Tracker
ping 192.168.1.100

# Từ Client, ping Origin Server
ping 192.168.1.101

# Test Tracker endpoint
curl http://192.168.1.100:8081/tracker/peers?fileId=test

# Test Origin Server (bỏ qua SSL warning)
curl -k https://192.168.1.101:8443/manifest/100MB.zip
```

### Kiểm tra firewall:
```bash
# Linux
sudo ufw allow 8081/tcp  # Tracker
sudo ufw allow 8443/tcp  # Origin Server
sudo ufw allow 6881/tcp  # Peer Server

# macOS
# System Preferences → Security → Firewall → Allow Java
```

## 📝 Notes

- **Auto-detect IP**: Client tự động detect local IP, không cần config
- **Port conflicts**: Peer Server tự động thử ports 6881-6890 nếu bị conflict
- **Log format**: Tất cả logs có timestamp và IP addresses để dễ debug

