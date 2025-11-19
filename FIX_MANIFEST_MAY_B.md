# 🔧 SỬA LỖI MANIFEST TRÊN MÁY B (Origin Server)

## ❌ Vấn đề hiện tại

Manifest trả về:
```json
{"fileSize":0,"pieceSize":1048576,"pieces":[],"totalPieces":0}
```

## ✅ Giải pháp: Restart Origin Server

### Bước 1: Dừng Origin Server hiện tại

**Trên Máy B (Ubuntu VM), trong terminal đang chạy server:**
- Nhấn `Ctrl+C` để dừng server

### Bước 2: Xóa file và manifest cũ (nếu cần)

```bash
cd /path/to/multi-source-downloader/origin-server

# Xóa file 2GB.zip cũ (nếu size = 0)
rm -f server_files/2GB.zip

# Xóa manifest cũ
rm -f server_files/2GB.zip.manifest.json
```

### Bước 3: Restart Origin Server

```bash
cd /path/to/multi-source-downloader/origin-server
mvn spring-boot:run
```

**✅ Logs mong đợi:**
```
Creating a dummy 2GB file for serving: .../server_files/2GB.zip
File created successfully: 2147483648 bytes
Generating manifest for: .../2GB.zip (size: 2147483648 bytes)
Generated hash for piece 0: ...
Generated hash for piece 1: ...
...
Successfully generated manifest file: .../2GB.zip.manifest.json
Started OriginServerApplication
Tomcat started on port(s): 8080 (http)
```

### Bước 4: Kiểm tra manifest

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

**✅ Nếu thấy `fileSize: 2147483648` và `totalPieces: 2048` → Thành công!**

## ⚠️ Lưu ý

- Quá trình tạo file 2GB có thể mất 1-2 phút
- Quá trình generate manifest cho 2048 pieces có thể mất vài phút
- Đợi đến khi thấy log "Successfully generated manifest file" mới test

