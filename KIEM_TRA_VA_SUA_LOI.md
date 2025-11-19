# 🔧 KIỂM TRA VÀ SỬA LỖI - DEMO 4 MÁY

## ✅ ĐÃ SỬA TRONG CODE

### 1. FileServerInitializer.java
- ✅ Sửa cách tạo file 2GB để tương thích với Windows
- ✅ Thử nhiều phương pháp: setLength() → seek+write → FileChannel.truncate()
- ✅ Validate file size trước khi generate manifest
- ✅ Báo lỗi rõ ràng nếu file không được tạo đúng

### 2. ManifestGeneratorUtil.java
- ✅ Tự động regenerate manifest nếu file size thay đổi
- ✅ Validate file size > 0 trước khi generate
- ✅ Validate number of pieces > 0

### 3. ManifestController.java
- ✅ Sửa log để hiển thị đúng port (8080 thay vì hardcode 8443)

### 4. MainApp.java (Client)
- ✅ Thêm method extractPortFromUrl() để tự động detect port
- ✅ Sửa log để hiển thị đúng port

### 5. DownloadCell.fxml
- ✅ Xóa fx:controller để tránh conflict

---

## 🔍 KIỂM TRA TRÊN MÁY B (Origin Server - Ubuntu VM)

### Bước 1: Xóa file và manifest cũ

```bash
cd /path/to/multi-source-downloader/origin-server

# Xóa file cũ (nếu size = 0)
rm -f server_files/2GB.zip
rm -f server_files/2GB.zip.manifest.json
```

### Bước 2: Rebuild origin-server

```bash
cd /path/to/multi-source-downloader
mvn clean install -DskipTests
```

### Bước 3: Restart Origin Server

```bash
cd origin-server
mvn spring-boot:run
```

**✅ Logs mong đợi:**
```
Creating a dummy 2GB file for serving: .../server_files/2GB.zip
Creating sparse file (writing only start and end markers)...
File length set successfully using setLength()
File created successfully: 2147483648 bytes (2048 MB)
File size verified: 2147483648 bytes. Generating manifest...
Generating manifest for: .../2GB.zip (size: 2147483648 bytes)
Calculated number of pieces: 2048 (piece size: 1048576 bytes)
Generated hash for piece 0: ...
Generated hash for piece 1: ...
...
Successfully generated manifest file: .../2GB.zip.manifest.json
Manifest generation completed successfully.
Started OriginServerApplication
Tomcat started on port(s): 8080 (http)
```

**⚠️ Nếu thấy lỗi:**
- `Invalid argument` → Code mới sẽ tự động dùng phương pháp thay thế
- `File size is incorrect` → Kiểm tra dung lượng ổ đĩa (cần ít nhất 2GB trống)
- `Failed to generate manifest` → Kiểm tra file có tồn tại và size đúng không

### Bước 4: Kiểm tra file và manifest

```bash
# Kiểm tra file size
ls -lh server_files/2GB.zip
# Kết quả mong đợi: 2.0G (hoặc tương tự)

# Kiểm tra manifest
cat server_files/2GB.zip.manifest.json | head -20
# Kết quả mong đợi: "fileSize": 2147483648, "totalPieces": 2048
```

### Bước 5: Test API từ Máy A

**Từ Máy A (Windows), test:**
```cmd
curl http://192.168.1.231:8080/manifest/2GB.zip
```

**Kết quả mong đợi:**
```json
{
  "fileSize": 2147483648,
  "pieceSize": 1048576,
  "pieces": [
    {
      "id": 0,
      "sha256": "...",
      "sources": [...]
    },
    ...
  ],
  "totalPieces": 2048
}
```

---

## 🔍 KIỂM TRA TRÊN MÁY A (Windows Host - Client 1)

### Config đã đúng:
```properties
tracker.url=http://192.168.1.229:8081
origin.server.url=http://192.168.1.231:8080
client.name=Windowns-Client
```

### Chạy client:
```cmd
cd javafx-client
mvn javafx:run
```

**✅ Logs mong đợi:**
```
[INFO] Origin Server URL: http://192.168.1.231:8080
[STEP 01] Fetching manifest | FROM: ... → TO: 192.168.1.231:8080
[ORIGIN] ✓ Manifest received | FROM: 192.168.1.231:8080 → TO: ... | Pieces: 2048
[INFO] Manifest received: 2048 pieces, 2048 MB
```

---

## 🔍 KIỂM TRA TRÊN MÁY C (macOS Host - Client 2)

### Config cần đúng:
```properties
tracker.url=http://192.168.1.229:8081
origin.server.url=http://192.168.1.231:8080  # ⚠️ Phải là http:// và port 8080
client.name=Mac-Client-2
```

**Xem chi tiết:** `HUONG_DAN_MAY_C.md`

---

## 🔍 KIỂM TRA TRÊN MÁY D (Ubuntu VM - P2P Tracker)

### Config đã đúng:
```properties
server.host=192.168.1.229
server.port=8081
```

### Chạy tracker:
```bash
cd p2p-tracker
mvn spring-boot:run
```

**✅ Logs mong đợi:**
```
Started P2PTrackerApplication
Tomcat started on port(s): 8081 (http)
```

---

## 🐛 TROUBLESHOOTING

### Lỗi: "Invalid argument" khi tạo file

**Nguyên nhân:** Windows có giới hạn với sparse file lớn

**Giải pháp:** Code mới đã tự động xử lý:
1. Thử `setLength()` trước
2. Nếu fail, dùng `seek()` + `write()` vào đầu và cuối file
3. Sau đó thử `setLength()` lại
4. Nếu vẫn fail, dùng `FileChannel.truncate()`

**Nếu vẫn lỗi:**
- Kiểm tra dung lượng ổ đĩa (cần ít nhất 2GB trống)
- Kiểm tra quyền ghi trong thư mục `server_files/`
- Thử tạo file thủ công bằng PowerShell:
  ```powershell
  $file = [System.IO.File]::Create("server_files\2GB.zip")
  $file.SetLength(2GB)
  $file.Close()
  ```

---

### Lỗi: Manifest có fileSize = 0

**Nguyên nhân:** File chưa được tạo đúng hoặc manifest cũ chưa được xóa

**Giải pháp:**
1. Xóa file và manifest cũ:
   ```bash
   rm -f server_files/2GB.zip
   rm -f server_files/2GB.zip.manifest.json
   ```
2. Restart Origin Server
3. Kiểm tra logs để đảm bảo file được tạo đúng size

---

### Lỗi: "Cannot generate manifest: number of pieces is 0"

**Nguyên nhân:** File size = 0 hoặc quá nhỏ

**Giải pháp:**
1. Kiểm tra file size: `ls -lh server_files/2GB.zip`
2. Nếu size = 0, xóa và restart server
3. Kiểm tra logs khi server start để xem lỗi cụ thể

---

## ✅ CHECKLIST TỔNG THỂ

### Máy B (Origin Server):
- [ ] File `2GB.zip` có size = 2147483648 bytes (2GB)
- [ ] Manifest có `fileSize: 2147483648` và `totalPieces: 2048`
- [ ] Server chạy trên port 8080 (HTTP)
- [ ] API `/files/list` trả về `["2GB.zip"]`
- [ ] API `/manifest/2GB.zip` trả về manifest đầy đủ

### Máy D (P2P Tracker):
- [ ] Tracker chạy trên port 8081
- [ ] API `/tracker/peers?fileId=test` trả về `[]`

### Máy A (Client 1):
- [ ] Config dùng `http://192.168.1.231:8080` (không phải https://8443)
- [ ] Client có thể fetch manifest thành công
- [ ] Manifest có 2048 pieces

### Máy C (Client 2):
- [ ] Config dùng `http://192.168.1.231:8080` (không phải https://8443)
- [ ] Client có thể fetch manifest thành công

---

## 📝 LƯU Ý QUAN TRỌNG

1. **Port:** Tất cả phải dùng HTTP port 8080 (không phải HTTPS 8443)
2. **File size:** File 2GB.zip phải có size đúng 2147483648 bytes
3. **Manifest:** Phải có 2048 pieces (2GB / 1MB per piece)
4. **Network:** Tất cả máy phải ping được nhau
5. **Firewall:** Mở ports 8080, 8081, 6881

---

## 🎯 SAU KHI SỬA XONG

1. **Máy B:** Restart Origin Server → Kiểm tra manifest có 2048 pieces
2. **Máy A:** Chạy client → Test download
3. **Máy C:** Chạy client → Test download và P2P
4. **Máy D:** Đảm bảo Tracker đang chạy

**Chúc bạn thành công! 🎉**

