# 🍎 Hướng Dẫn Chạy Trên macOS (IP: 192.168.1.83)

## 📋 Tóm Tắt
- **macOS (bạn)**: P2P Tracker + JavaFX Client
- **Windows (192.168.1.214)**: Origin Server

---

## ✅ CHECKLIST TRƯỚC KHI CHẠY

### 1. Config đã được cập nhật ✅
- ✅ `javafx-client/src/main/resources/config.properties`
  - tracker.url: `http://192.168.1.83:8081`
  - origin.server.url: `https://192.168.1.214:8443`
- ✅ `p2p-tracker/src/main/resources/config.properties`
  - server.host: `192.168.1.83`

### 2. Project đã được build ✅
```bash
cd /Users/ttcenter/IdeaProjects/multi-source-downloader
mvn clean install -DskipTests
```

### 3. Máy Windows đã chạy Origin Server
- ⚠️ **QUAN TRỌNG**: Origin Server phải chạy TRƯỚC
- Xem file `SETUP_WINDOWS_CONFIG.md` cho hướng dẫn Windows

### 4. Kiểm tra kết nối đến Windows
```bash
# Test ping
ping 192.168.1.214

# Test Origin Server endpoint
curl -k https://192.168.1.214:8443/manifest/100MB.zip
```

Nếu curl trả về JSON → ✅ Sẵn sàng tiếp tục!

---

## 🚀 BƯỚC 1: Chạy P2P Tracker

### Mở Terminal 1:
```bash
cd /Users/ttcenter/IdeaProjects/multi-source-downloader/p2p-tracker
mvn spring-boot:run
```

### ✅ Log Thành Công:
```
═══════════════════════════════════════════════════════════════
🌐 P2P Tracker Configuration
═══════════════════════════════════════════════════════════════
Tracker Name   : P2P-Tracker
Server Host    : 192.168.1.83
Server Port    : 8081
═══════════════════════════════════════════════════════════════

✅ P2P Tracker started successfully!
🌐 Tracker URL: http://192.168.1.83:8081/tracker
📡 Listening for peer announcements...
```

**⚠️ QUAN TRỌNG:**
- Đảm bảo thấy IP `192.168.1.83` trong log
- Nếu thấy `localhost` hoặc IP khác → Config chưa đúng!
- **KHÔNG TẮT terminal này!** Giữ Tracker chạy.

---

## 🚀 BƯỚC 2: Chạy JavaFX Client

### Mở Terminal 2 (terminal mới):
```bash
cd /Users/ttcenter/IdeaProjects/multi-source-downloader/javafx-client
mvn javafx:run
```

### ✅ Log Thành Công:
```
═══════════════════════════════════════════════════════════════
💻 Client Configuration
═══════════════════════════════════════════════════════════════
Client Name    : Mac-Client
Local IP       : 192.168.1.83
Tracker URL    : http://192.168.1.83:8081
Origin Server  : https://192.168.1.214:8443
Peer Port      : 6881
═══════════════════════════════════════════════════════════════

[INFO] Starting peer server on port 6881...
[INFO] ✅ Peer server started on 192.168.1.83:6881
[INFO] Announcing to tracker...
[INFO] ✅ Announced to tracker successfully
```

**JavaFX UI sẽ mở ra!** 🎉

---

## 🎯 BƯỚC 3: Test Download

### Trong JavaFX UI:

1. **Nhập thông tin download:**
   - **File Name**: `100MB.zip`
   - **Tracker URL**: `http://192.168.1.83:8081` (đã điền sẵn)
   - **Origin Server**: `https://192.168.1.214:8443` (đã điền sẵn)

2. **Click "Add Download"**

3. **Quan sát logs trong Terminal 2:**
   ```
   [STEP 01] Fetching manifest | FROM: 192.168.1.83 → TO: 192.168.1.214:8443
   [ORIGIN] GET /manifest/100MB.zip | FROM: 192.168.1.83 → TO: 192.168.1.214:8443
   [ORIGIN] ✓ Manifest received | Pieces: 100, Size: 100MB
   
   [STEP 02] Announcing to tracker | FROM: 192.168.1.83 → TO: 192.168.1.83:8081
   [TRACKER] ✓ Announce registered
   
   [STEP 03] Starting download...
   [DOWNLOAD] Piece 0/100 downloaded (1MB)
   [DOWNLOAD] Piece 1/100 downloaded (1MB)
   ...
   ```

4. **Quan sát UI:**
   - Progress bar tổng thể
   - Progress bars riêng cho từng nguồn:
     - **Origin**: Tải từ Windows Origin Server
     - **Mirror**: (Nếu có Mirror Server)
     - **Peers**: (Nếu có peers khác)

---

## 📊 Network Flow

```
╔══════════════════════════════════════════════════════════════╗
║                      DOWNLOAD FLOW                           ║
╚══════════════════════════════════════════════════════════════╝

1. Client yêu cầu manifest:
   Mac Client (192.168.1.83)
         ↓ HTTPS GET /manifest/100MB.zip
   Windows Origin (192.168.1.214:8443)
         ↓ Response: JSON manifest với 100 pieces

2. Client announce đến Tracker:
   Mac Client (192.168.1.83)
         ↓ POST /tracker/announce
   Mac Tracker (192.168.1.83:8081)
         ↓ Response: Peer list (hiện tại trống)

3. Client tải pieces:
   Mac Client (192.168.1.83)
         ↓ HTTPS GET /files/100MB.zip?start=0&end=1048576
   Windows Origin (192.168.1.214:8443)
         ↓ Response: Piece 0 data (1MB)
   
   [Lặp lại cho mỗi piece...]
```

---

## 🎬 Demo Các Tính Năng

### 1. Multi-Source Download
- Download sẽ tải song song từ Origin Server
- Nếu có Mirror Server, sẽ tải từ cả Mirror
- UI hiển thị bytes downloaded từ mỗi nguồn

### 2. Pause/Resume
- Click nút **Pause** → Download tạm dừng
- Click nút **Resume** → Tiếp tục từ điểm đã dừng
- State được lưu trong `~/.downloader/state/`

### 3. Virtual Filesystem (macOS Only!) 🎉
VirtualFS cho phép bạn mở file khi đang tải!

**Enable VFS:**
1. Cài macFUSE (nếu chưa có):
   ```bash
   brew install macfuse
   ```

2. VFS sẽ tự động mount tại: `~/downloader-vfs/`

3. **Demo:** Download file video lớn, sau đó mở trong VLC:
   ```bash
   open -a VLC ~/downloader-vfs/100MB.zip
   ```

4. File sẽ mở ngay cả khi chưa tải xong!
   - VFS sẽ tự động tải pieces cần thiết on-demand

### 4. Hash Verification
- Mỗi piece được verify bằng SHA-256
- Nếu hash mismatch, tự động retry từ nguồn khác
- Xem log để thấy verification process

---

## 📈 Monitoring

### Xem Logs Real-time:

**Terminal 1 (Tracker):**
```
[14:30:16] [TRACKER] POST /announce | FROM: 192.168.1.83 → TO: Tracker:8081
[14:30:16] [TRACKER] ✓ Peer registered: 192.168.1.83:6881
[14:30:16] [TRACKER] Active peers: 1
```

**Terminal 2 (Client):**
```
[14:30:17] [DOWNLOAD] Progress: 25/100 pieces (25%)
[14:30:18] [DOWNLOAD] Speed: 5.2 MB/s
[14:30:19] [ORIGIN] Downloaded: 25 MB from Origin
```

### Xem Tracker Status:
```bash
curl http://192.168.1.83:8081/tracker/peers?fileId=100MB.zip | jq
```

Response:
```json
[
  {
    "peerId": "Mac-Client-1234",
    "ip": "192.168.1.83",
    "port": 6881,
    "lastSeen": "2025-11-17T11:30:16Z"
  }
]
```

---

## 🐛 Troubleshooting

### Lỗi: "Failed to connect to Origin Server"

**Kiểm tra:**
1. **Origin Server đang chạy trên Windows?**
   ```bash
   curl -k https://192.168.1.214:8443/manifest/100MB.zip
   ```

2. **Firewall Windows đã mở port 8443?**
   - Xem `SETUP_WINDOWS_CONFIG.md` → Firewall

3. **Ping được Windows?**
   ```bash
   ping 192.168.1.214
   ```

### Lỗi: "SSL Certificate Error"

Client đã config để skip SSL verification cho self-signed cert.

Nếu vẫn lỗi, check trong `MainApp.java`:
```java
OkHttpClient httpClient = new OkHttpClient.Builder()
    .hostnameVerifier((hostname, session) -> true)
    .build();
```

### Lỗi: "Tracker not responding"

**Kiểm tra:**
1. **Tracker đang chạy?** (Terminal 1)
2. **Test Tracker endpoint:**
   ```bash
   curl http://192.168.1.83:8081/tracker/peers?fileId=test
   ```
   Should return: `[]`

### Lỗi: "Port 8081 already in use"

Port đang được dùng. Kiểm tra:
```bash
lsof -i :8081
kill -9 <PID>
```

### JavaFX không hiển thị

Kiểm tra Java và JavaFX:
```bash
java --version  # Should be JDK 21+
mvn javafx:run -X  # Debug mode
```

---

## 📝 Quick Commands

```bash
# Build project
cd /Users/ttcenter/IdeaProjects/multi-source-downloader
mvn clean install -DskipTests

# Run Tracker (Terminal 1)
cd p2p-tracker
mvn spring-boot:run

# Run Client (Terminal 2)
cd javafx-client
mvn javafx:run

# Test connections
ping 192.168.1.214
curl -k https://192.168.1.214:8443/manifest/100MB.zip
curl http://192.168.1.83:8081/tracker/peers?fileId=test

# Check ports
lsof -i :8081  # Tracker
lsof -i :6881  # Peer Server

# View logs
tail -f ~/.downloader/logs/client.log
```

---

## 🎯 Next Steps

1. ✅ Chạy Tracker (Terminal 1)
2. ✅ Chạy Client (Terminal 2)
3. ✅ Test download file
4. ✅ Quan sát logs và UI
5. 🎉 Demo các tính năng cho người khác!

---

## 💡 Tips

- **Logs rất quan trọng:** Luôn xem logs để debug
- **IP phải đúng:** Kiểm tra logs có hiển thị đúng IP không
- **Chạy Origin Server trước:** Tracker và Client cần Origin Server
- **VFS cool nhất:** Demo VFS với file video để gây ấn tượng!

**Chúc bạn demo thành công! 🚀**

