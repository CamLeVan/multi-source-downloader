# 🔹 HƯỚNG DẪN CẤU HÌNH MÁY C (macOS Host - Client 2)

## ✅ Máy C cần cấu hình giống Máy A

### Bước 1: Cấu hình Client

**Mở Terminal trên macOS:**
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

### Bước 2: Kiểm tra config

```bash
cat ../javafx-client/src/main/resources/config.properties
```

**Kết quả mong đợi:**
```properties
tracker.url=http://192.168.1.229:8081
origin.server.url=http://192.168.1.231:8080
client.name=Mac-Client-2
```

**⚠️ QUAN TRỌNG:** 
- Phải dùng `http://` (không phải `https://`)
- Port phải là `8080` (không phải `8443`)

### Bước 3: Build và chạy

```bash
cd /path/to/multi-source-downloader
mvn clean install -DskipTests

cd javafx-client
mvn javafx:run
```

## ✅ Không cần thay đổi gì khác!

Config của Máy C giống hệt Máy A, chỉ khác:
- `client.name` (tên client để phân biệt)
- IP local của máy (tự động detect)

