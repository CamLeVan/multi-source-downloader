# 🚀 Bắt Đầu Tại Đây

## 📋 Chọn Scenario Demo

### 🎯 Demo 2 Máy
**Phù hợp cho:** Test nhanh, demo cơ bản

**Files:**
- `SETUP_TWO_MACHINES.md` - Hướng dẫn chi tiết
- `DEMO_2_MACHINES.md` - Quick guide

**Setup:**
```bash
# Mỗi máy
scripts/get-ip.sh  # hoặc get-ip.bat
scripts/setup-network.sh  # hoặc setup-network.bat
```

### 🎯 Demo 4 Máy (2 Host + 2 VM) ⭐ Khuyến nghị
**Phù hợp cho:** Demo đầy đủ tính năng, P2P exchange

**Architecture:**
- Windows Host → Origin Server
- Ubuntu VM (Windows) → P2P Tracker
- macOS Host → Client 1
- Ubuntu VM (macOS) → Client 2

**Files:**
- `SETUP_4_MACHINES.md` - Hướng dẫn chi tiết
- `DEMO_4_MACHINES_QUICK.md` - Quick guide
- `VM_NETWORK_SETUP.md` - Cấu hình VM network

**Setup:**
```bash
# Mỗi máy
scripts/get-ip.sh  # hoặc get-ip.bat
scripts/setup-4-machines.sh  # hoặc setup-4-machines.bat
```

## ⚡ Quick Start

### Bước 1: Lấy IP
```bash
# Linux/macOS
./scripts/get-ip.sh

# Windows
scripts\get-ip.bat
```

### Bước 2: Auto Config
```bash
# 2 máy
./scripts/setup-network.sh

# 4 máy
./scripts/setup-4-machines.sh
```

### Bước 3: Chạy Services
Xem hướng dẫn trong file tương ứng.

## 📚 Tài Liệu

### Setup Guides
- `QUICK_START.md` - Setup nhanh 5 phút
- `SETUP_TWO_MACHINES.md` - Demo 2 máy
- `SETUP_4_MACHINES.md` - Demo 4 máy
- `VM_NETWORK_SETUP.md` - VM network config
- `IP_SETUP_GUIDE.md` - IP configuration

### Demo Guides
- `DEMO_GUIDE.md` - Các scenarios demo
- `DEMO_2_MACHINES.md` - Quick guide 2 máy
- `DEMO_4_MACHINES_QUICK.md` - Quick guide 4 máy

### Configuration
- `NETWORK_CONFIG.md` - Network config chi tiết
- `SETUP_GUIDE.md` - Setup từng component

## 🎬 Demo Flow

### 4 Máy (Khuyến nghị):
1. **Start Origin Server** (Windows Host)
2. **Start P2P Tracker** (Ubuntu VM)
3. **Start Client 1** (macOS) → Tải từ Origin
4. **Start Client 2** (Ubuntu VM) → Tải từ Origin + P2P từ Client 1

### 2 Máy:
1. **Start Origin Server**
2. **Start P2P Tracker**
3. **Start Client**

## ✅ Checklist

- [ ] Đã clone repository
- [ ] Đã build: `mvn clean install`
- [ ] Đã lấy IP của tất cả máy
- [ ] Đã cấu hình config.properties
- [ ] Đã mở firewall ports (8443, 8081, 6881)
- [ ] VM network là Bridged mode (nếu dùng VM)
- [ ] Có thể ping được giữa các máy
- [ ] Đã start services theo thứ tự

## 🆘 Cần Giúp?

1. **Lỗi kết nối:** Xem `IP_SETUP_GUIDE.md` → Troubleshooting
2. **VM network:** Xem `VM_NETWORK_SETUP.md`
3. **Config IP:** Xem `IP_SETUP_GUIDE.md`
4. **Demo scenarios:** Xem `DEMO_GUIDE.md`

## 📊 Network Topology

### 4 Máy:
```
192.168.1.101 (Windows) → Origin Server
192.168.1.102 (Ubuntu VM) → P2P Tracker
192.168.1.103 (macOS) → Client 1
192.168.1.104 (Ubuntu VM) → Client 2
```

### 2 Máy:
```
192.168.1.101 → Origin Server
192.168.1.102 → P2P Tracker + Client
```

## 🎯 Next Steps

1. Chọn scenario (2 máy hoặc 4 máy)
2. Đọc hướng dẫn tương ứng
3. Setup và chạy
4. Xem logs để verify
5. Demo các tính năng!

**Chúc bạn demo thành công! 🎉**

