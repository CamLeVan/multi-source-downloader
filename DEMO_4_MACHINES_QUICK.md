# 🎬 Demo 4 Máy - Quick Guide

## 📋 Architecture

```
Máy A: Windows Host (192.168.1.101)
  └─ Origin Server

Máy B: Ubuntu VM trên Windows (192.168.1.102)
  └─ P2P Tracker

Máy C: macOS Host (192.168.1.103)
  └─ Client 1 (JavaFX)

Máy D: Ubuntu VM trên macOS (192.168.1.104)
  └─ Client 2 (JavaFX)
```

## ⚡ 3 Bước Setup

### Bước 1: Lấy IP mỗi máy

**Windows:**
```cmd
scripts\get-ip.bat
```

**Linux/macOS:**
```bash
./scripts/get-ip.sh
```

**Ghi lại:** A=192.168.1.101, B=192.168.1.102, C=192.168.1.103, D=192.168.1.104

### Bước 2: Auto Config

**Mỗi máy chạy:**
```bash
# Linux/macOS
./scripts/setup-4-machines.sh

# Windows
scripts\setup-4-machines.bat
```

**Chọn role:**
- Máy A: `2` (Origin Server)
- Máy B: `3` (P2P Tracker)
- Máy C: `1` (Client) → Nhập: Tracker=192.168.1.102, Origin=192.168.1.101
- Máy D: `1` (Client) → Nhập: Tracker=192.168.1.102, Origin=192.168.1.101

### Bước 3: Chạy Services

**Máy A (Windows):**
```cmd
cd origin-server
mvn spring-boot:run
```

**Máy B (Ubuntu VM):**
```bash
cd p2p-tracker
mvn spring-boot:run
```

**Máy C (macOS):**
```bash
cd javafx-client
mvn javafx:run
```

**Máy D (Ubuntu VM):**
```bash
cd javafx-client
mvn javafx:run
```

## ✅ Verification

**Test từ Client 1 (macOS):**
```bash
ping 192.168.1.101  # Origin
ping 192.168.1.102  # Tracker
ping 192.168.1.104  # Client 2
```

**Test từ Client 2 (Ubuntu VM):**
```bash
ping 192.168.1.101  # Origin
ping 192.168.1.102  # Tracker
ping 192.168.1.103  # Client 1
```

## 🎯 Demo Flow

1. **Start Origin** (Máy A) → Logs: `Origin Server started on 192.168.1.101:8443`
2. **Start Tracker** (Máy B) → Logs: `P2P Tracker started on 192.168.1.102:8081`
3. **Start Client 1** (Máy C) → Bắt đầu tải từ Origin
4. **Start Client 2** (Máy D) → Tải từ Origin + P2P từ Client 1

## 🔥 VM Network Setup

**VirtualBox:**
- VM Settings → Network → Adapter 1
- Chọn: **Bridged Adapter**
- Restart VM

**VMware:**
- VM Settings → Network Adapter
- Chọn: **Bridged**
- Restart VM

**Xem chi tiết:** `VM_NETWORK_SETUP.md`

## 📊 Expected Logs

**Client 1 (macOS):**
```
[INFO] Local IP Address: 192.168.1.103
[STEP 01] Fetching manifest | FROM: 192.168.1.103 → TO: 192.168.1.101:8443
[TRACKER] POST /announce | FROM: 192.168.1.103 → TO: 192.168.1.102:8081
```

**Client 2 (Ubuntu VM):**
```
[INFO] Local IP Address: 192.168.1.104
[STEP 01] Fetching manifest | FROM: 192.168.1.104 → TO: 192.168.1.101:8443
[TRACKER] GET /peers | FROM: 192.168.1.104 → TO: 192.168.1.102:8081
[TRACKER] ✓ Retrieved 1 peers | Peers: [192.168.1.103:6881]
[PEER] GET /piece/100MB.zip/5 | FROM: 192.168.1.104 → TO: 192.168.1.103:6881
[DOWNLOAD] ✓ Piece 5 completed | FROM: 192.168.1.103
```

## 🐛 Troubleshooting

### VM không ping được host
→ Đổi VM network từ NAT sang **Bridged**

### Connection refused
→ Mở firewall ports (8443, 8081, 6881)

### IP khác subnet
→ Đảm bảo VM dùng Bridged mode

**Xem chi tiết:** `SETUP_4_MACHINES.md`

