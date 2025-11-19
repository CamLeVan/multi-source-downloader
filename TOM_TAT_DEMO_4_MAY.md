# 📋 TÓM TẮT NHANH - DEMO 4 MÁY

## 🎯 Kiến trúc (MỚI - 2 Host chạy Client, 2 VM chạy Server)

- **Máy A (Windows Host):** Client 1 - Port 6881
- **Máy B (Ubuntu VM trên Windows):** Origin Server - Port 8080 - File: **2GB.zip**
- **Máy C (macOS Host):** Client 2 - Port 6881
- **Máy D (Ubuntu VM trên macOS):** P2P Tracker - Port 8081

## ⚡ Quick Start (5 bước)

### 1. Lấy IP mỗi máy
```bash
# Windows
scripts\get-ip.bat

# Linux/macOS
./scripts/get-ip.sh
```

### 2. Cấu hình tự động
```bash
# Mỗi máy chạy
scripts\setup-4-machines.bat  # Windows
./scripts/setup-4-machines.sh  # Linux/macOS
```

**Chọn role:**
- Máy A: `1` (Client) → Nhập: Tracker=192.168.1.229, Origin=192.168.1.231
- Máy B: `2` (Origin Server)
- Máy C: `1` (Client) → Nhập: Tracker=192.168.1.229, Origin=192.168.1.231
- Máy D: `3` (P2P Tracker)

### 3. Build project (tất cả máy)
```bash
mvn clean install -DskipTests
```

### 4. Chạy services (theo thứ tự)

**Máy A (Windows Host - Client 1):**
```cmd
cd javafx-client
mvn javafx:run
```

**Máy B (Ubuntu VM - Origin Server):**
```bash
cd origin-server
mvn spring-boot:run
```

**Máy C (macOS Host - Client 2):**
```bash
cd javafx-client
mvn javafx:run
```

**Máy D (Ubuntu VM - P2P Tracker):**
```bash
cd p2p-tracker
mvn spring-boot:run
```

### 5. Demo
- Client 1: Chọn `2GB.zip` → Add Download
- Client 2: Chọn `2GB.zip` → Add Download
- Quan sát P2P sharing giữa 2 clients

## ⚠️ Lưu ý quan trọng

1. **VM Network:** Phải dùng **Bridged** mode (không phải NAT)
2. **Firewall:** Mở ports 8080, 8081, 6881
3. **Thứ tự start:** Origin Server (Máy B) → Tracker (Máy D) → Client 1 (Máy A) → Client 2 (Máy C)
4. **File 2GB:** Server tự động tạo, có thể mất 1-2 phút

## 📖 Xem chi tiết

Xem file **`HUONG_DAN_DEMO_4_MAY_CHI_TIET.md`** để có hướng dẫn đầy đủ từng bước.

