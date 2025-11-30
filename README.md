# Multi-Source Downloader

Hệ thống tải file phân mảnh (fragmented download) với hỗ trợ đa nguồn, P2P sharing



## 🎯 Tính năng chính

### ✅ Đã triển khai

1. **Multi-Source Download**
   - Tải song song từ nhiều nguồn (Origin Server, Mirror Server, Peers)
   - Tự động fallback khi một nguồn thất bại
   - Retry với exponential backoff
   - Progress tracking riêng cho từng nguồn

2. **P2P Sharing**
   - P2P Tracker để quản lý peers
   - Peer-to-peer piece sharing
   - Tự động discover và kết nối với peers khác
   - Background refresh peer list

3. **Data Integrity**
   - SHA-256 hash verification cho mỗi piece
   - Tự động retry từ nguồn khác khi hash mismatch
   - Đảm bảo tính toàn vẹn dữ liệu

4. **Resume Support**
   - Lưu trạng thái download (JSON)
   - Resume download sau khi dừng
   - Sparse file storage để tiết kiệm dung lượng

5. **Virtual Filesystem (VFS)**
   - Mount file đang tải như một filesystem thật
   - On-demand download khi truy cập file
   - Hỗ trợ macOS/Linux (FUSE)
   - Có thể mở file trong VLC/PDF viewer ngay khi đang tải

6. **JavaFX UI**
   - Multi-download support
   - Progress bars per-source (Origin, Mirror, Peer)
   - Pause/Resume controls
   - Drag & drop reordering

## 📋 Yêu cầu hệ thống

- **Java**: JDK 21+
- **Maven**: 3.6+
- **macOS/Linux**: Để sử dụng VirtualFS (cần macFUSE hoặc FUSE)
- **Windows**: Có thể chạy nhưng không hỗ trợ VirtualFS

## 🚀 Hướng dẫn Setup và Chạy

### ⚡ Quick Start

**Demo Scenarios:**
- **2 Máy:** Xem `SETUP_TWO_MACHINES.md`
- **4 Máy (2 Host + 2 VM):** Xem `SETUP_4_MACHINES.md` ⭐ Khuyến nghị cho demo đầy đủ

**Xem hướng dẫn chi tiết:**
- `QUICK_START.md` - Setup nhanh 5 phút
- `SETUP_TWO_MACHINES.md` - Hướng dẫn demo 2 máy
- `SETUP_4_MACHINES.md` - Hướng dẫn demo 4 máy (2 host + 2 VM)
- `VM_NETWORK_SETUP.md` - Cấu hình VM network
- `IP_SETUP_GUIDE.md` - Cấu hình IP addresses

**Tóm tắt:**
1. Chạy `scripts/get-ip.sh` (Linux/macOS) hoặc `scripts/get-ip.bat` (Windows) để lấy IP
2. Chạy `scripts/setup-network.sh` hoặc `scripts/setup-network.bat` (2 máy)
   - Hoặc `scripts/setup-4-machines.sh` / `setup-4-machines.bat` (4 máy)
3. Chạy services theo hướng dẫn

### Bước 1: Clone repository

```bash
git clone <repository-url>
cd multi-source-downloader
```

### Bước 2: Build toàn bộ project

```bash
mvn clean install
```

### Bước 3: Setup và chạy các servers

#### 3.1. Origin Server (Máy A - Windows)

```bash
cd origin-server
mvn spring-boot:run
```

**Cấu hình:**
- Port: `8443` (HTTPS)
- File location: `origin-server/server_files/`
- Manifest endpoint: `https://localhost:8443/manifest/{fileName}`
- File endpoint: `https://localhost:8443/files/{fileName}`

**Lưu ý:** 
- Cần file keystore cho SSL tại `src/main/resources/keystore.p12`
- Server tự động tạo test file 100MB nếu chưa có
- Server tự động generate manifest JSON

#### 3.2. P2P Tracker (Có thể chạy trên bất kỳ máy nào)

```bash
cd p2p-tracker
mvn spring-boot:run
```

**Cấu hình:**
- Port: `8081` (mặc định Spring Boot)
- Tracker endpoint: `http://localhost:8081/tracker/announce`
- Peers endpoint: `http://localhost:8081/tracker/peers?fileId={fileId}`

#### 3.3. Mirror Server (VM Linux - Optional)

Xem hướng dẫn chi tiết tại: `mirror-server/README.md`

**Các options:**
- **Nginx** (khuyến nghị): Setup đơn giản, hiệu suất cao
- **Spring Boot**: Copy code từ `origin-server`, đổi port 8080
- **Python**: `python3 -m http.server 8080`

### Bước 4: Chạy Client (Máy B - Mac)

```bash
cd javafx-client
mvn javafx:run
```

**Hoặc build JAR và chạy:**
```bash
mvn clean package
java -jar target/javafx-client-1.0-SNAPSHOT.jar
```

**Cấu hình:**
- Tracker URL: `http://localhost:8081` (có thể sửa trong `MainApp.java`)
- Download folder: `~/Downloads/`
- VFS mount point: `~/downloader-vfs/` (macOS/Linux only)

## 🎬 Demo Scenarios

### Demo 1: Multi-Source Download với Progress Tracking

**Mục tiêu:** Chứng minh tốc độ tải tăng nhờ tải song song từ nhiều nguồn

**Thiết lập:**
1. Chạy Origin Server (port 8443)
2. Chạy Mirror Server (port 8080 hoặc Nginx)
3. Chạy Client

**Thực hiện:**
1. Mở Client, bắt đầu download file lớn (ví dụ: 1GB)
2. Quan sát UI:
   - Progress bar tổng thể
   - Progress bars riêng cho Origin, Mirror, Peers
   - Bytes downloaded từ mỗi nguồn
3. So sánh tốc độ với việc chỉ tải từ một nguồn

**Kết quả mong đợi:**
- Tốc độ tải tăng ~2x khi có 2 nguồn (Origin + Mirror)
- UI hiển thị rõ ràng lưu lượng từ mỗi nguồn

### Demo 2: Hash Mismatch Detection và Auto-Retry

**Mục tiêu:** Chứng minh hệ thống tự động phát hiện và phục hồi khi dữ liệu bị hỏng

**Thiết lập:**
1. Chạy Origin Server
2. Sửa một piece trong file trên server (làm hỏng dữ liệu)
3. Hoặc sửa hash trong manifest JSON

**Thực hiện:**
1. Bắt đầu download
2. Khi piece bị hỏng được tải:
   - Hệ thống phát hiện hash mismatch
   - Tự động retry từ nguồn khác (Mirror hoặc Peer)
   - Log hiển thị: "Hash mismatch for piece X, retrying from next source"

**Kết quả mong đợi:**
- Download tiếp tục thành công từ nguồn khác
- Không cần can thiệp thủ công

### Demo 3: Virtual Filesystem On-Demand Access

**Mục tiêu:** Chứng minh có thể truy cập file ngay khi đang tải

**Thiết lập:**
1. Chạy Client trên macOS/Linux
2. Đảm bảo macFUSE/FUSE đã được cài đặt

**Thực hiện:**
1. Bắt đầu download file lớn (ví dụ: 20GB video)
2. Chỉ tải một vài pieces đầu (không tải hết)
3. Mở VLC Media Player
4. Mở file từ mount point: `~/downloader-vfs/{fileName}`
5. Seek đến giữa file (ví dụ: 10GB)

**Kết quả mong đợi:**
- VLC có thể mở file ngay lập tức
- Khi seek, hệ thống tự động tải pieces cần thiết
- Progress bar tăng đột ngột khi pieces được tải on-demand

### Demo 4: P2P Peer Exchange

**Mục tiêu:** Chứng minh khả năng chia sẻ pieces giữa peers

**Thiết lập:**
1. Chạy P2P Tracker
2. Chạy Client A (đã tải một phần file)
3. Chạy Client B (bắt đầu tải cùng file)

**Thực hiện:**
1. Client A: Bắt đầu download, tải một số pieces
2. Client B: Bắt đầu download cùng file
3. Quan sát:
   - Client B tự động discover Client A qua Tracker
   - Client B tải một số pieces từ Client A (Peer)
   - UI của Client B hiển thị progress từ Peer

**Kết quả mong đợi:**
- Client B có thể tải từ Client A
- Progress bar "Peers" trên UI hiển thị bytes từ peers
- Giảm tải cho Origin Server

### Demo 5: Resume Download

**Mục tiêu:** Chứng minh khả năng resume sau khi dừng

**Thiết lập:**
1. Chạy Client
2. Bắt đầu download file lớn

**Thực hiện:**
1. Bắt đầu download
2. Pause download (nút Pause)
3. Đóng ứng dụng
4. Mở lại ứng dụng và resume

**Kết quả mong đợi:**
- Download tiếp tục từ điểm đã dừng
- Không tải lại các pieces đã hoàn thành
- State được lưu trong `~/.downloader/state/`

## 📁 Cấu trúc Project

```
multi-source-downloader/
├── shared-model/          # Models và interfaces dùng chung
├── networking-core/       # OkHttpDownloadClient với fallback logic
├── origin-server/         # Spring Boot server phục vụ files
├── p2p-tracker/           # P2P Tracker service
├── javafx-client/         # JavaFX client application
└── mirror-server/         # Hướng dẫn setup mirror server
```

## 🔧 Cấu hình

### Tracker URL

Sửa trong `javafx-client/src/main/java/com/fragmented/download/javafx/MainApp.java`:
```java
private static final String TRACKER_URL = "http://YOUR_TRACKER_IP:8081";
```

### SSL Certificate (Origin Server)

Tạo keystore cho HTTPS:
```bash
keytool -genkeypair -alias origin-server -keyalg RSA -keysize 2048 \
  -storetype PKCS12 -keystore src/main/resources/keystore.p12 \
  -validity 365 -storepass changeit
```

## 🐛 Troubleshooting

### VirtualFS không mount được (macOS)

```bash
# Cài đặt macFUSE
brew install macfuse
```

### Tracker không kết nối được

- Kiểm tra firewall
- Đảm bảo Tracker đang chạy trên port 8081
- Kiểm tra IP address trong TRACKER_URL

### Hash mismatch liên tục

- Kiểm tra file trên server có bị hỏng không
- Kiểm tra manifest JSON có đúng không
- Đảm bảo tất cả sources đều có file giống nhau

## 📝 Notes

- **Windows**: VirtualFS không được hỗ trợ, nhưng các tính năng khác hoạt động bình thường
- **File size**: Hệ thống hỗ trợ file lớn (đã test với 20GB+)
- **Network**: Tốt nhất chạy trong mạng LAN để demo P2P

## 👥 Contributors

- Multi-source download logic
- P2P tracker integration
- VirtualFS implementation
- UI với progress tracking per-source


