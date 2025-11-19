# 🎯 HƯỚNG DẪN DEMO 4 MÁY - CHI TIẾT 100%

## 📋 TỔNG QUAN KIẾN TRÚC

### Kiến trúc 4 máy (MỚI - 2 Host chạy Client, 2 VM chạy Server):
```
┌─────────────────────────────────────────────────────────────┐
│                    Network: 192.168.1.0/24                  │
│              (Tất cả máy phải cùng subnet)                  │
└─────────────────────────────────────────────────────────────┘

┌──────────────────┐         ┌──────────────────┐
│  Máy A           │         │  Máy C           │
│  Windows Host    │         │  macOS Host       │
│  192.168.1.187   │         │  192.168.1.91    │
│                  │         │                  │
│  Client 1        │         │  Client 2        │
│  (JavaFX)        │         │  (JavaFX)        │
│  Port: 6881      │         │  Port: 6881      │
└────────┬─────────┘         └────────┬─────────┘
         │                            │
         │ VM Network (Bridged)       │ VM Network (Bridged)
         │                            │
┌────────▼─────────┐         ┌────────▼─────────┐
│  Máy B           │         │  Máy D            │
│  Ubuntu VM       │         │  Ubuntu VM        │
│  (Windows)       │         │  (macOS)          │
│  192.168.1.231   │         │  192.168.1.229   │
│                  │         │                   │
│  Origin Server   │         │  P2P Tracker      │
│  Port: 8080      │         │  Port: 8081       │
│  File: 2GB.zip  │         │                   │
└──────────────────┘         └───────────────────┘
```

### File test: **2GB.zip** (đã được nâng từ 100MB)

---

## ✅ BƯỚC 1: CHUẨN BỊ - LẤY IP CỦA TẤT CẢ 4 MÁY

### 🔹 Máy A - Windows Host (Client 1)
**IP thực tế:** `192.168.1.187`

**Mở PowerShell hoặc CMD:**
```cmd
cd D:\VKU_learning\HK5\NetworkPrograming\BigProject\theEnd\multi-source-downloader
scripts\get-ip.bat
```

**Ghi lại IP:** `192.168.1.XXX` (ví dụ: `192.168.1.187`)

**Kiểm tra:**
```cmd
ipconfig
# Tìm "IPv4 Address" (không phải 127.0.0.1)
```

---

### 🔹 Máy B - Ubuntu VM trên Windows (Origin Server)
**IP thực tế:** `192.168.1.231`

**Trong VM Ubuntu, mở Terminal:**
```bash
cd /path/to/multi-source-downloader
chmod +x scripts/get-ip.sh
./scripts/get-ip.sh
```

**Hoặc thủ công:**
```bash
hostname -I
# hoặc
ip addr show | grep "inet "
```

**Ghi lại IP:** `192.168.1.XXX` (ví dụ: `192.168.1.231`)

**⚠️ QUAN TRỌNG:** Đảm bảo VM network mode là **Bridged** (không phải NAT)

---

### 🔹 Máy C - macOS Host (Client 2)
**IP thực tế:** `192.168.1.91`

**Mở Terminal:**
```bash
cd /path/to/multi-source-downloader
chmod +x scripts/get-ip.sh
./scripts/get-ip.sh
```

**Hoặc thủ công:**
```bash
ifconfig | grep "inet " | grep -v "127.0.0.1"
```

**Ghi lại IP:** `192.168.1.XXX` (ví dụ: `192.168.1.91`)

---

### 🔹 Máy D - Ubuntu VM trên macOS (P2P Tracker)
**IP thực tế:** `192.168.1.229`

**Trong VM Ubuntu, mở Terminal:**
```bash
cd /path/to/multi-source-downloader
chmod +x scripts/get-ip.sh
./scripts/get-ip.sh
```

**Ghi lại IP:** `192.168.1.XXX` (ví dụ: `192.168.1.104`)

**⚠️ QUAN TRỌNG:** Đảm bảo VM network mode là **Bridged** (không phải NAT)

---

## ✅ BƯỚC 2: CẤU HÌNH VM NETWORK (NẾU CHƯA SETUP)

### 🔧 VirtualBox Setup

**Trên cả 2 VM (Máy B và Máy D):**

1. **Tắt VM** (nếu đang chạy)
2. **VM Settings → Network:**
   - Adapter 1:
     - ✅ Enable Network Adapter
     - Attached to: **Bridged Adapter**
     - Name: [Chọn network adapter của host]
3. **Start lại VM**

**Kiểm tra:**
```bash
# Trong VM
hostname -I
# IP phải cùng subnet với host (ví dụ: 192.168.1.x)
```

---

### 🔧 VMware Setup

**Trên cả 2 VM:**

1. **Tắt VM**
2. **VM Settings → Network Adapter:**
   - Network connection: **Bridged**
   - ✅ Replicate physical network connection state
3. **Start lại VM**

---

### 🔧 Parallels Setup (macOS)

**Trên Máy D (VM trên macOS):**

1. **VM Settings → Hardware → Network:**
   - Network Type: **Shared Network** hoặc **Bridged Ethernet**

---

## ✅ BƯỚC 3: KIỂM TRA KẾT NỐI GIỮA CÁC MÁY

### Test ping từ mỗi máy:

**Từ Máy A (Windows Host - Client 1):**
```cmd
ping 192.168.1.231  # Máy B (Origin Server)
ping 192.168.1.229  # Máy D (Tracker)
ping 192.168.1.91   # Máy C (Client 2)
```

**Từ Máy B (Ubuntu VM - Origin Server):**
```bash
ping -c 4 192.168.1.187  # Máy A (Client 1)
ping -c 4 192.168.1.91   # Máy C (Client 2)
ping -c 4 192.168.1.229  # Máy D (Tracker)
```

**Từ Máy C (macOS Host - Client 2):**
```bash
ping -c 4 192.168.1.187  # Máy A (Client 1)
ping -c 4 192.168.1.231  # Máy B (Origin Server)
ping -c 4 192.168.1.229  # Máy D (Tracker)
```

**Từ Máy D (Ubuntu VM - Tracker):**
```bash
ping -c 4 192.168.1.187  # Máy A (Client 1)
ping -c 4 192.168.1.231  # Máy B (Origin Server)
ping -c 4 192.168.1.91   # Máy C (Client 2)
```

**✅ Tất cả ping phải thành công!** Nếu không, kiểm tra:
- VM network mode (phải là Bridged)
- Firewall settings
- Router/network configuration

---

## ✅ BƯỚC 4: CẤU HÌNH FIREWALL

### 🔹 Máy A - Windows Host (Client 1)

**Mở PowerShell as Administrator:**
```powershell
# Allow P2P Peer Server port (Client cần lắng nghe peers)
netsh advfirewall firewall add rule name="Peer Server" dir=in action=allow protocol=TCP localport=6881

# Kiểm tra
netsh advfirewall firewall show rule name="Peer Server"
```

---

### 🔹 Máy B - Ubuntu VM (Origin Server)

**Trong VM:**
```bash
sudo ufw allow 8080/tcp
sudo ufw status
```

---

### 🔹 Máy C - macOS Host (Client 2)

**System Preferences → Security & Privacy → Firewall:**
- Click "Firewall Options"
- Allow incoming connections for:
  - Java
  - Terminal (nếu cần)

**Hoặc command line:**
```bash
sudo /usr/libexec/ApplicationFirewall/socketfilterfw --add /usr/bin/java
sudo /usr/libexec/ApplicationFirewall/socketfilterfw --unblockapp /usr/bin/java
```

---

### 🔹 Máy D - Ubuntu VM (P2P Tracker)

**Trong VM:**
```bash
sudo ufw allow 8081/tcp
sudo ufw status
```

---

## ✅ BƯỚC 5: CẤU HÌNH TỰ ĐỘNG BẰNG SCRIPT

### 🔹 Máy A - Windows Host (Client 1)

**Mở PowerShell hoặc CMD:**
```cmd
cd D:\VKU_learning\HK5\NetworkPrograming\BigProject\theEnd\multi-source-downloader
cd scripts
setup-4-machines.bat
```

**Chọn:** `1` (Client)

**Nhập thông tin:**
- Tracker IP: `192.168.1.229` (Máy D - Tracker)
- Origin IP: `192.168.1.231` (Máy B - Origin Server)
- Client Name: `Windows-Client-1` (hoặc tên bạn muốn)

**Script sẽ tự động:**
- Detect IP của máy
- Tạo config file với IP đúng
- Hiển thị thông tin cấu hình

**Kiểm tra config:**
```cmd
type ..\javafx-client\src\main\resources\config.properties
```

**Kết quả mong đợi:**
```properties
tracker.url=http://192.168.1.229:8081
origin.server.url=https://192.168.1.231:8443
client.name=Windows-Client-1
```

---

### 🔹 Máy B - Ubuntu VM (Origin Server)

**Trong VM Ubuntu:**
```bash
cd /path/to/multi-source-downloader
cd scripts
chmod +x setup-4-machines.sh
./setup-4-machines.sh
```

**Chọn:** `2` (Origin Server)

**Kiểm tra config:**
```bash
cat ../origin-server/src/main/resources/config.properties
```

**Kết quả mong đợi:**
```properties
server.host=192.168.1.231  # IP của Máy B
server.port=8443
```

---

### 🔹 Máy C - macOS Host (Client 2)

**Mở Terminal:**
```bash
cd /path/to/multi-source-downloader
cd scripts
chmod +x setup-4-machines.sh
./setup-4-machines.sh
```

**Chọn:** `1` (Client)

**Nhập thông tin:**
- Tracker IP: `192.168.1.229` (Máy D - Tracker)
- Origin IP: `192.168.1.231` (Máy B - Origin Server)
- Client Name: `Mac-Client-2` (hoặc tên bạn muốn)

**Kiểm tra config:**
```bash
cat ../javafx-client/src/main/resources/config.properties
```

**Kết quả mong đợi:**
```properties
tracker.url=http://192.168.1.229:8081
origin.server.url=http://192.168.1.231:8080
client.name=Mac-Client-2
```

---

### 🔹 Máy D - Ubuntu VM (P2P Tracker)

**Trong VM Ubuntu:**
```bash
cd /path/to/multi-source-downloader
cd scripts
chmod +x setup-4-machines.sh
./setup-4-machines.sh
```

**Chọn:** `3` (P2P Tracker)

**Kiểm tra config:**
```bash
cat ../p2p-tracker/src/main/resources/config.properties
```

**Kết quả mong đợi:**
```properties
server.host=192.168.1.229  # IP của Máy D
server.port=8081
```

---

## ✅ BƯỚC 6: BUILD PROJECT (TRÊN TẤT CẢ 4 MÁY)

### 🔹 Máy A - Windows Host

**Mở PowerShell:**
```cmd
cd D:\VKU_learning\HK5\NetworkPrograming\BigProject\theEnd\multi-source-downloader
mvn clean install -DskipTests
```

**Đợi build hoàn tất** (có thể mất 2-5 phút)

---

### 🔹 Máy B, C, D

**Tương tự, chạy:**
```bash
cd /path/to/multi-source-downloader
mvn clean install -DskipTests
```

---

## ✅ BƯỚC 7: CHẠY SERVICES (THEO THỨ TỰ)

### ⚠️ QUAN TRỌNG: Phải chạy theo thứ tự!

### 🔹 Bước 1: Start Origin Server (Máy B - Ubuntu VM)

**Trong VM Ubuntu, mở Terminal:**
```bash
cd /path/to/multi-source-downloader/origin-server
mvn spring-boot:run
```

**✅ Logs mong đợi:**
```
Creating a dummy 2GB file for serving: .../origin-server/server_files/2GB.zip
Generating manifest for: .../2GB.zip
...
Started OriginServerApplication in X.XXX seconds
Tomcat started on port(s): 8080 (http)
```

**⚠️ Lưu ý:** 
- Server sẽ tự động tạo file `2GB.zip` (2GB) nếu chưa có
- Quá trình tạo file có thể mất 1-2 phút
- File sẽ được tạo dạng sparse file (tiết kiệm dung lượng)

**Kiểm tra server đã chạy:**
```bash
# Mở Terminal mới
curl http://localhost:8080/files/list
# Kết quả: ["2GB.zip"]
```

**✅ Server đã sẵn sàng khi thấy:**
- Log: `Tomcat started on port(s): 8080`
- API `/files/list` trả về `["2GB.zip"]`

---

### 🔹 Bước 2: Start P2P Tracker (Máy D - Ubuntu VM)

**Trong VM Ubuntu, mở Terminal:**
```bash
cd /path/to/multi-source-downloader/p2p-tracker
mvn spring-boot:run
```

**✅ Logs mong đợi:**
```
Started P2PTrackerApplication in X.XXX seconds
Tomcat started on port(s): 8081 (http)
```

**Kiểm tra tracker đã chạy:**
```bash
# Mở Terminal mới
curl http://localhost:8081/tracker/peers?fileId=test
# Kết quả: [] (empty array là bình thường)
```

**✅ Tracker đã sẵn sàng khi thấy:**
- Log: `Tomcat started on port(s): 8081`
- API `/tracker/peers` trả về `[]`

---

### 🔹 Bước 3: Start Client 1 (Máy A - Windows Host)

**Mở PowerShell:**
```cmd
cd D:\VKU_learning\HK5\NetworkPrograming\BigProject\theEnd\multi-source-downloader\javafx-client
mvn javafx:run
```

**✅ Logs mong đợi:**
```
[INFO] Client Name: Windows-Client-1
[INFO] Local IP Address: 192.168.1.187
[INFO] Tracker URL: http://192.168.1.229:8081
[INFO] Origin Server URL: https://192.168.1.231:8443
```

**✅ UI sẽ hiển thị:**
- Window title: "Multi-Source Downloader - Windows-Client-1 (192.168.1.187)"
- File list dropdown có `2GB.zip`
- Status: "Found 1 files on server"

**⚠️ Lưu ý:** 
- Đợi UI load xong (có thể mất 5-10 giây)
- Nếu không thấy file list, click nút "Refresh"

---

### 🔹 Bước 4: Start Client 2 (Máy C - macOS Host)

**Mở Terminal:**
```bash
cd /path/to/multi-source-downloader/javafx-client
mvn javafx:run
```

**✅ Logs mong đợi:**
```
[INFO] Client Name: Mac-Client-2
[INFO] Local IP Address: 192.168.1.91
[INFO] Tracker URL: http://192.168.1.229:8081
[INFO] Origin Server URL: https://192.168.1.231:8443
```

**✅ UI sẽ hiển thị:**
- Window title: "Multi-Source Downloader - Mac-Client-2 (192.168.1.91)"
- File list dropdown có `2GB.zip`

---

## ✅ BƯỚC 8: DEMO SCENARIOS

### 🎬 Demo 1: Multi-Source Download (Client 1 tải từ Origin)

**Trên Máy A (Windows Host - Client 1):**

1. **Chọn file:** Dropdown → `2GB.zip`
2. **Click "Add Download"**
3. **Quan sát:**
   - Progress bar tổng thể
   - Progress bars riêng: Origin, Mirror, Peers
   - Bytes downloaded từ mỗi nguồn
   - Logs trong console

**✅ Kết quả mong đợi:**
- Download bắt đầu từ Origin Server (192.168.1.231 - Máy B)
- Progress bar "Origin" tăng dần
- Logs hiển thị: `[ORIGIN] GET /files/2GB.zip | Range: bytes=...`

**⏱️ Thời gian:** Với file 2GB, download sẽ mất vài phút tùy tốc độ mạng

---

### 🎬 Demo 2: P2P Peer Exchange (Client 2 tải từ Client 1)

**Trên Máy A (Windows Host - Client 1):**
- Để Client 1 tải một phần file (khoảng 10-20%)

**Trên Máy C (macOS Host - Client 2):**

1. **Chọn file:** Dropdown → `2GB.zip`
2. **Click "Add Download"**
3. **Quan sát:**
   - Client 2 sẽ tự động discover Client 1 qua Tracker
   - Client 2 tải một số pieces từ Client 1 (P2P)
   - Progress bar "Peers" tăng

**✅ Logs mong đợi trên Máy C (Client 2):**
```
[TRACKER] GET /peers | FROM: 192.168.1.91 → TO: 192.168.1.229:8081
[TRACKER] ✓ Retrieved 1 peers | Peers: [192.168.1.187:6881]
[PEER] GET /piece/2GB.zip/5 | FROM: 192.168.1.91 → TO: 192.168.1.187:6881
[DOWNLOAD] ✓ Piece 5 completed | FROM: 192.168.1.187
```

**✅ Logs mong đợi trên Máy A (Client 1):**
```
[PEER] GET /piece/2GB.zip/5 | FROM: 192.168.1.91 → TO: Peer (192.168.1.187:6881)
[PEER] ✓ Piece 5 sent | FROM: 192.168.1.187 → TO: 192.168.1.91
```

**✅ Kết quả:**
- Client 2 tải pieces từ Client 1 (P2P)
- Giảm tải cho Origin Server
- Progress bar "Peers" hiển thị bytes từ peers

---

### 🎬 Demo 3: Hash Verification & Auto-Retry

**Mục tiêu:** Chứng minh hệ thống tự động phát hiện và retry khi hash mismatch

**Thực hiện:**
1. **Trên Máy A (Origin Server):**
   - Dừng server (Ctrl+C)
   - Sửa một phần file `2GB.zip` (làm hỏng dữ liệu)
   - Start lại server

2. **Trên Client:**
   - Bắt đầu download
   - Khi piece bị hỏng được tải:
     - Hệ thống phát hiện hash mismatch
     - Tự động retry từ nguồn khác (Peer)

**✅ Logs mong đợi:**
```
[HASH] ✗ Mismatch for piece 10 | FROM: 192.168.1.101
[RETRY] Retrying piece 10 | FROM: 192.168.1.101 → TO: 192.168.1.103
[DOWNLOAD] ✓ Piece 10 completed | FROM: 192.168.1.103
```

---

### 🎬 Demo 4: Resume Download

**Mục tiêu:** Chứng minh khả năng resume sau khi dừng

**Thực hiện:**
1. **Bắt đầu download** file 2GB
2. **Pause download** (nút Pause trên UI)
3. **Đóng ứng dụng**
4. **Mở lại ứng dụng**
5. **Resume download** (nút Resume)

**✅ Kết quả mong đợi:**
- Download tiếp tục từ điểm đã dừng
- Không tải lại các pieces đã hoàn thành
- State được lưu trong `~/.downloader/state/`

---

## ✅ BƯỚC 9: VERIFICATION CHECKLIST

### Kiểm tra kết nối:

**Từ Máy A (Windows Host - Client 1), test:**
```cmd
# Test Origin Server
curl -k https://192.168.1.231:8443/manifest/2GB.zip

# Test Tracker
curl http://192.168.1.229:8081/tracker/peers?fileId=test

# Test ping
ping 192.168.1.231  # Origin Server (Máy B)
ping 192.168.1.229  # Tracker (Máy D)
ping 192.168.1.91   # Client 2 (Máy C)
```

**Từ Máy C (macOS Host - Client 2), test:**
```bash
# Test Origin Server
curl -k https://192.168.1.231:8443/manifest/2GB.zip | head -20

# Test Tracker
curl http://192.168.1.229:8081/tracker/peers?fileId=test

# Test ping
ping -c 4 192.168.1.231  # Origin Server (Máy B)
ping -c 4 192.168.1.229  # Tracker (Máy D)
ping -c 4 192.168.1.187  # Client 1 (Máy A)
```

---

### Checklist tổng thể:

- [ ] Tất cả 4 máy có IP trong cùng subnet (192.168.1.x)
- [ ] Tất cả máy có thể ping được nhau
- [ ] Firewall đã mở ports (8080, 8081, 6881)
- [ ] VM network mode là Bridged (không phải NAT)
- [ ] Origin Server đã chạy và có file `2GB.zip`
- [ ] P2P Tracker đã chạy
- [ ] Client 1 đã chạy và kết nối được
- [ ] Client 2 đã chạy và kết nối được
- [ ] File list hiển thị `2GB.zip` trên cả 2 clients
- [ ] Download hoạt động từ Origin Server
- [ ] P2P sharing hoạt động giữa 2 clients

---

## 🐛 TROUBLESHOOTING

### Lỗi: VM không ping được host

**Nguyên nhân:** VM dùng NAT thay vì Bridged

**Giải pháp:**
1. Tắt VM
2. VM Settings → Network → Adapter 1
3. Đổi từ NAT sang **Bridged Adapter**
4. Restart VM

---

### Lỗi: "Connection refused" hoặc "Connection timeout"

**Nguyên nhân:** Firewall block hoặc IP sai

**Giải pháp:**
1. Kiểm tra firewall đã mở ports chưa
2. Kiểm tra IP trong config.properties có đúng không
3. Test ping giữa các máy
4. Kiểm tra server đã start chưa

---

### Lỗi: Client không thấy file list

**Nguyên nhân:** Origin Server chưa tạo file hoặc chưa start

**Giải pháp:**
1. Kiểm tra Origin Server đã start chưa
2. Kiểm tra file `2GB.zip` có trong `origin-server/server_files/` chưa
3. Test API: `curl http://ORIGIN_IP:8080/files/list`
4. Click nút "Refresh" trên Client UI

---

### Lỗi: File 2GB.zip không được tạo

**Nguyên nhân:** Thiếu dung lượng hoặc quyền ghi

**Giải pháp:**
1. Kiểm tra dung lượng ổ đĩa (cần ít nhất 2GB trống)
2. Kiểm tra quyền ghi trong thư mục `origin-server/server_files/`
3. Xem logs của Origin Server để biết lỗi cụ thể

**Lưu ý:** File được tạo dạng sparse file, nên dung lượng thực tế trên disk sẽ nhỏ hơn 2GB

---

### Lỗi: P2P không hoạt động

**Nguyên nhân:** Tracker chưa start hoặc firewall block

**Giải pháp:**
1. Kiểm tra Tracker đã start chưa
2. Kiểm tra firewall trên cả 2 clients (port 6881)
3. Kiểm tra logs của Tracker để xem có peers announce không
4. Đảm bảo cả 2 clients đều tải cùng file (`2GB.zip`)

---

### Lỗi: Hash mismatch liên tục

**Nguyên nhân:** File trên server bị hỏng hoặc manifest sai

**Giải pháp:**
1. Xóa file `2GB.zip` và `2GB.zip.manifest.json` trong `origin-server/server_files/`
2. Restart Origin Server (sẽ tự động tạo lại file và manifest)
3. Đảm bảo tất cả sources có file giống nhau

---

## 📊 EXPECTED LOGS

### Origin Server (Máy B - Ubuntu VM):
```
Creating a dummy 2GB file for serving: ...
Generating manifest for: .../2GB.zip
Generated hash for piece 0: ...
...
Started OriginServerApplication
Tomcat started on port(s): 8080 (http)
[ORIGIN] GET /manifest/2GB.zip | FROM: 192.168.1.187 → TO: Origin:8080
[ORIGIN] GET /files/2GB.zip | Range: bytes=0-1048575 | FROM: 192.168.1.187
```

### P2P Tracker (Máy D - Ubuntu VM):
```
Started P2PTrackerApplication
Tomcat started on port(s): 8081 (http)
[TRACKER] POST /announce | FROM: 192.168.1.187 → TO: 192.168.1.229:8081
[TRACKER] GET /peers | FROM: 192.168.1.91 → TO: 192.168.1.229:8081
```

### Client 1 (Máy A - Windows Host):
```
[INFO] Client Name: Windows-Client-1
[INFO] Local IP Address: 192.168.1.187
[STEP 01] Fetching manifest | FROM: 192.168.1.187 → TO: 192.168.1.231:8080
[TRACKER] POST /announce | FROM: 192.168.1.187 → TO: 192.168.1.229:8081
[DOWNLOAD] ✓ Piece 0 completed | FROM: 192.168.1.231
```

### Client 2 (Máy C - macOS Host):
```
[INFO] Client Name: Mac-Client-2
[INFO] Local IP Address: 192.168.1.91
[STEP 01] Fetching manifest | FROM: 192.168.1.91 → TO: 192.168.1.231:8080
[TRACKER] GET /peers | FROM: 192.168.1.91 → TO: 192.168.1.229:8081
[TRACKER] ✓ Retrieved 1 peers | Peers: [192.168.1.187:6881]
[PEER] GET /piece/2GB.zip/5 | FROM: 192.168.1.91 → TO: 192.168.1.187:6881
[DOWNLOAD] ✓ Piece 5 completed | FROM: 192.168.1.187
```

---

## 💡 TIPS QUAN TRỌNG

1. **Luôn start theo thứ tự:** Origin → Tracker → Client 1 → Client 2
2. **Xem logs trên tất cả 4 máy** để thấy flow rõ ràng
3. **P2P demo:** Client 1 tải trước, Client 2 sẽ tải từ Client 1
4. **Network:** Đảm bảo tất cả máy cùng network (có thể ping được nhau)
5. **File 2GB:** Quá trình tạo file có thể mất 1-2 phút, đợi logs "Successfully generated manifest"
6. **VM Network:** Phải dùng Bridged mode, không dùng NAT
7. **Firewall:** Mở ports trên cả host và VM
8. **IP Detection:** Scripts tự động detect IP, nhưng cần verify

---

## 📝 QUICK REFERENCE TABLE

| Máy | IP | Role | Port | Config File |
|-----|-----|------|------|-------------|
| Windows Host | 192.168.1.187 | Client 1 | 6881 | `javafx-client/.../config.properties` |
| Ubuntu VM (Win) | 192.168.1.231 | Origin Server | 8080 | `origin-server/.../config.properties` |
| macOS Host | 192.168.1.91 | Client 2 | 6881 | `javafx-client/.../config.properties` |
| Ubuntu VM (Mac) | 192.168.1.229 | P2P Tracker | 8081 | `p2p-tracker/.../config.properties` |

---

## ✅ KẾT LUẬN

Sau khi hoàn thành tất cả các bước trên, bạn sẽ có:
- ✅ Origin Server chạy với file **2GB.zip**
- ✅ P2P Tracker hoạt động
- ✅ 2 Clients có thể tải file từ Origin
- ✅ P2P sharing giữa 2 clients hoạt động
- ✅ Hash verification và auto-retry hoạt động
- ✅ Resume download hoạt động

**Chúc bạn demo thành công! 🎉**

