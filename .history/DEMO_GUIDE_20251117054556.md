# Hướng Dẫn Demo Chi Tiết

## 📋 Checklist Trước Khi Demo

### Yêu cầu phần cứng
- [ ] Máy A (Windows): Origin Server
- [ ] Máy B (Mac): Client với VirtualFS
- [ ] VM Linux (Optional): Mirror Server
- [ ] Tất cả máy trong cùng mạng LAN

### Yêu cầu phần mềm
- [ ] Java 21+ đã cài đặt trên tất cả máy
- [ ] Maven 3.6+ đã cài đặt
- [ ] macFUSE đã cài đặt trên Mac (cho VirtualFS)
- [ ] Git đã cài đặt

## 🚀 Setup Nhanh (5 phút)

### Bước 1: Clone và Build

```bash
# Trên tất cả máy
git clone <repository-url>
cd multi-source-downloader
mvn clean install -DskipTests
```

### Bước 2: Chạy Servers

**Máy A (Windows) - Origin Server:**
```bash
cd origin-server
mvn spring-boot:run
```
✅ Server chạy tại: `https://localhost:8443`

**Máy bất kỳ - P2P Tracker:**
```bash
cd p2p-tracker
mvn spring-boot:run
```
✅ Tracker chạy tại: `http://localhost:8081`

**VM Linux (Optional) - Mirror Server:**
```bash
# Option 1: Nginx
sudo apt install nginx
sudo cp origin-server/server_files/100MB.zip /var/www/html/
sudo systemctl start nginx

# Option 2: Python
cd origin-server/server_files
python3 -m http.server 8080
```

### Bước 3: Chạy Client

**Máy B (Mac) - Client:**
```bash
cd javafx-client
mvn javafx:run
```

## 🎬 Demo Scenarios

### Demo 1: Multi-Source Download ⚡

**Mục tiêu:** Chứng minh tốc độ tải tăng nhờ tải song song

**Setup:**
1. ✅ Origin Server đang chạy (port 8443)
2. ✅ Mirror Server đang chạy (port 8080 hoặc Nginx)
3. ✅ Client đang chạy

**Thực hiện:**
1. Mở Client, bắt đầu download file `100MB.zip`
2. Quan sát UI:
   - **Progress Bar Tổng thể**: Tăng dần
   - **Origin Progress**: Hiển thị bytes từ Origin Server
   - **Mirror Progress**: Hiển thị bytes từ Mirror Server
   - **Peers Progress**: 0 (chưa có peers)

**Kết quả mong đợi:**
- Tốc độ tải ~2x so với chỉ tải từ 1 nguồn
- UI hiển thị rõ ràng lưu lượng từ mỗi nguồn
- Console log: "Downloaded piece X from origin/mirror"

**Điểm nhấn:**
> "Như các bạn thấy, hệ thống đang tải song song từ cả Origin và Mirror, tốc độ tải tăng gấp đôi so với chỉ tải từ một nguồn duy nhất."

---

### Demo 2: Hash Mismatch & Auto-Retry 🔒

**Mục tiêu:** Chứng minh tính toàn vẹn dữ liệu và tự động phục hồi

**Setup:**
1. ✅ Origin Server đang chạy
2. ✅ Mirror Server đang chạy (với file đúng)
3. ✅ Client đang chạy

**Thực hiện:**
1. **Trước khi demo:** Sửa một vài bytes trong file trên Origin Server:
   ```bash
   # Trên Origin Server
   cd origin-server/server_files
   # Sửa file 100MB.zip (làm hỏng piece #5)
   # Hoặc sửa hash trong manifest JSON
   ```

2. Bắt đầu download trên Client

3. Quan sát Console:
   ```
   Hash mismatch for piece 5 from source index 0
   Retrying piece 5 with remaining sources (removed first source)
   Re-queuing piece 5 with remaining sources
   Downloaded piece 5 from mirror
   ```

**Kết quả mong đợi:**
- Hệ thống phát hiện hash mismatch
- Tự động retry từ Mirror Server
- Download tiếp tục thành công
- Không cần can thiệp thủ công

**Điểm nhấn:**
> "Hệ thống tự động phát hiện dữ liệu bị hỏng và chuyển sang nguồn khác, đảm bảo tính toàn vẹn dữ liệu 100%."

---

### Demo 3: Virtual Filesystem On-Demand 🎬

**Mục tiêu:** Chứng minh có thể truy cập file ngay khi đang tải

**Setup:**
1. ✅ Client đang chạy trên Mac/Linux
2. ✅ macFUSE/FUSE đã cài đặt
3. ✅ VLC Media Player đã cài đặt

**Thực hiện:**
1. Bắt đầu download file lớn (ví dụ: 1GB video)
2. **Pause download** sau khi tải được ~10%
3. Mở Terminal:
   ```bash
   ls -lh ~/downloader-vfs/
   # Sẽ thấy file ảo với size đầy đủ nhưng dung lượng thực tế nhỏ
   ```
4. Mở VLC:
   - File → Open File
   - Chọn: `~/downloader-vfs/100MB.zip`
5. **Seek** đến giữa file (50%)
6. Quan sát:
   - Console: "VirtualFS: Piece X not available, downloading on-demand..."
   - Progress bar tăng đột ngột
   - VLC bắt đầu phát sau khi pieces được tải

**Kết quả mong đợi:**
- File xuất hiện trong VFS ngay lập tức
- VLC có thể mở file ngay
- Khi seek, hệ thống tự động tải pieces cần thiết
- Dung lượng thực tế chỉ bằng các pieces đã tải

**Điểm nhấn:**
> "Người dùng có thể mở và sử dụng file ngay lập tức mà không cần đợi tải hết. Hệ thống sẽ tự động tải các phần cần thiết khi người dùng truy cập."

---

### Demo 4: P2P Peer Exchange 👥

**Mục tiêu:** Chứng minh khả năng chia sẻ pieces giữa peers

**Setup:**
1. ✅ P2P Tracker đang chạy
2. ✅ Client A đang chạy (đã tải một phần file)
3. ✅ Client B đang chạy (bắt đầu tải cùng file)

**Thực hiện:**
1. **Client A:**
   - Bắt đầu download file `100MB.zip`
   - Đợi tải được ~30% pieces
   - **Không pause**, để chạy background

2. **Client B:**
   - Bắt đầu download cùng file `100MB.zip`
   - Quan sát Console:
     ```
     Announced to tracker: fileId=100MB.zip, port=6881
     Found 1 peers for fileId: 100MB.zip
     Enriched manifest with peer sources
     ```
   - Quan sát UI:
     - **Peers Progress Bar**: Bắt đầu tăng
     - **Peer Label**: Hiển thị bytes từ peer

3. **Kiểm tra Tracker:**
   ```bash
   curl http://localhost:8081/tracker/peers?fileId=100MB.zip
   # Sẽ thấy: ["192.168.1.100:6881"]
   ```

**Kết quả mong đợi:**
- Client B tự động discover Client A
- Client B tải một số pieces từ Client A
- UI hiển thị progress từ Peer
- Giảm tải cho Origin Server

**Điểm nhấn:**
> "Hệ thống P2P cho phép các client chia sẻ pieces với nhau, giảm tải cho server trung tâm và tăng tốc độ tải tổng thể."

---

### Demo 5: Resume Download 🔄

**Mục tiêu:** Chứng minh khả năng resume sau khi dừng

**Setup:**
1. ✅ Client đang chạy

**Thực hiện:**
1. Bắt đầu download file lớn
2. Đợi tải được ~50%
3. Click **Pause** button
4. Quan sát:
   - Status: "Paused"
   - Progress bar dừng lại
5. **Đóng ứng dụng** (hoặc để pause)
6. **Mở lại ứng dụng**
7. Download sẽ tự động resume từ điểm đã dừng

**Kiểm tra State:**
```bash
cat ~/.downloader/state/100MB.zip.state.json
# Sẽ thấy JSON với thông tin pieces đã tải
```

**Kết quả mong đợi:**
- Download tiếp tục từ điểm đã dừng
- Không tải lại các pieces đã hoàn thành
- State được lưu tự động

**Điểm nhấn:**
> "Hệ thống tự động lưu trạng thái download, cho phép resume bất cứ lúc nào mà không mất dữ liệu đã tải."

---

## 🎯 Tips cho Demo

### Trước khi demo:
1. **Test trước** tất cả scenarios
2. **Chuẩn bị file lớn** (1GB+) để demo rõ ràng hơn
3. **Kiểm tra network** giữa các máy
4. **Chuẩn bị backup plan** nếu một component fail

### Trong khi demo:
1. **Giải thích từng bước** rõ ràng
2. **Highlight các tính năng** quan trọng
3. **So sánh** với cách tải truyền thống
4. **Trả lời câu hỏi** về technical details

### Troubleshooting nhanh:
- **Tracker không kết nối**: Kiểm tra IP và firewall
- **VirtualFS không mount**: Kiểm tra macFUSE
- **Hash mismatch liên tục**: Kiểm tra file trên server
- **Peer không discover**: Kiểm tra Tracker đang chạy

## 📊 Metrics để Highlight

- **Tốc độ tải**: 2x khi có 2 nguồn
- **Data integrity**: 100% với SHA-256 verification
- **Resume**: 0% data loss
- **On-demand**: Truy cập file ngay lập tức
- **P2P**: Giảm 50% tải cho Origin Server

