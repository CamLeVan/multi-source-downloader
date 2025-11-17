# 🖥️ Hướng Dẫn Demo Trên 4 Máy (2 Host + 2 VM)

## 📋 Kiến Trúc Demo

### Máy Setup:
1. **Máy A - Windows Host** (192.168.1.101)
   - Origin Server

2. **Máy B - Ubuntu VM trên Windows** (192.168.1.102)
   - P2P Tracker
   - Mirror Server (optional)

3. **Máy C - macOS Host** (192.168.1.103)
   - Client 1 (JavaFX)

4. **Máy D - Ubuntu VM trên macOS** (192.168.1.104)
   - Client 2 (JavaFX) - để demo P2P

## 🔧 Bước 1: Lấy IP của Tất Cả 4 Máy

### Trên Windows Host:
```cmd
scripts\get-ip.bat
# Ghi lại IP: 192.168.1.101
```

### Trên Ubuntu VM (Windows):
```bash
# Trong VM Ubuntu
./scripts/get-ip.sh
# Ghi lại IP: 192.168.1.102
```

### Trên macOS Host:
```bash
./scripts/get-ip.sh
# Ghi lại IP: 192.168.1.103
```

### Trên Ubuntu VM (macOS):
```bash
# Trong VM Ubuntu
./scripts/get-ip.sh
# Ghi lại IP: 192.168.1.104
```

**Lưu ý:** VM thường có IP trong cùng subnet với host, ví dụ:
- Windows Host: 192.168.1.101
- Ubuntu VM (Windows): 192.168.1.102
- macOS Host: 192.168.1.103
- Ubuntu VM (macOS): 192.168.1.104

## 🚀 Bước 2: Cấu Hình Từng Máy

### Máy A - Windows Host (Origin Server)

```cmd
cd scripts
setup-network.bat
# Chọn: 2 (Origin Server)
# IP sẽ tự động: 192.168.1.101
```

**Hoặc thủ công:**
Sửa `origin-server/src/main/resources/config.properties`:
```properties
server.host=192.168.1.101
server.port=8443
```

**Chạy:**
```cmd
cd origin-server
mvn spring-boot:run
```

### Máy B - Ubuntu VM trên Windows (P2P Tracker)

```bash
cd scripts
./setup-network.sh
# Chọn: 3 (P2P Tracker)
# IP sẽ tự động: 192.168.1.102
```

**Hoặc thủ công:**
Sửa `p2p-tracker/src/main/resources/config.properties`:
```properties
server.host=192.168.1.102
server.port=8081
```

**Chạy:**
```bash
cd p2p-tracker
mvn spring-boot:run
```

### Máy C - macOS Host (Client 1)

```bash
cd scripts
./setup-network.sh
# Chọn: 1 (Client)
# Nhập:
#   Tracker IP: 192.168.1.102 (Máy B)
#   Origin IP: 192.168.1.101 (Máy A)
#   Client Name: Mac-Client-1
```

**Hoặc thủ công:**
Sửa `javafx-client/src/main/resources/config.properties`:
```properties
tracker.url=http://192.168.1.102:8081
origin.server.url=https://192.168.1.101:8443
client.name=Mac-Client-1
```

**Chạy:**
```bash
cd javafx-client
mvn javafx:run
```

### Máy D - Ubuntu VM trên macOS (Client 2)

```bash
cd scripts
./setup-network.sh
# Chọn: 1 (Client)
# Nhập:
#   Tracker IP: 192.168.1.102 (Máy B)
#   Origin IP: 192.168.1.101 (Máy A)
#   Client Name: Ubuntu-Client-2
```

**Hoặc thủ công:**
Sửa `javafx-client/src/main/resources/config.properties`:
```properties
tracker.url=http://192.168.1.102:8081
origin.server.url=https://192.168.1.101:8443
client.name=Ubuntu-Client-2
```

**Chạy:**
```bash
cd javafx-client
mvn javafx:run
```

## 📊 Network Topology

```
┌─────────────────────────────────────────────────────────────┐
│                    Network: 192.168.1.0/24                  │
└─────────────────────────────────────────────────────────────┘

┌──────────────────┐         ┌──────────────────┐
│  Windows Host    │         │   macOS Host     │
│  192.168.1.101   │         │  192.168.1.103   │
│                  │         │                  │
│  Origin Server   │         │  Client 1        │
│  Port: 8443      │         │  (JavaFX)        │
└────────┬─────────┘         └────────┬─────────┘
         │                            │
         │ VM Network                 │ VM Network
         │                            │
┌────────▼─────────┐         ┌────────▼─────────┐
│  Ubuntu VM       │         │  Ubuntu VM        │
│  (Windows)       │         │  (macOS)          │
│  192.168.1.102   │         │  192.168.1.104    │
│                  │         │                   │
│  P2P Tracker     │         │  Client 2         │
│  Port: 8081      │         │  (JavaFX)         │
└──────────────────┘         └───────────────────┘
```

## 🎬 Demo Flow

### 1. Start Services (theo thứ tự)

**Bước 1: Start Origin Server (Máy A - Windows)**
```cmd
cd origin-server
mvn spring-boot:run
```
✅ Logs: `Origin Server started on 192.168.1.101:8443`

**Bước 2: Start P2P Tracker (Máy B - Ubuntu VM)**
```bash
cd p2p-tracker
mvn spring-boot:run
```
✅ Logs: `P2P Tracker started on 192.168.1.102:8081`

**Bước 3: Start Client 1 (Máy C - macOS)**
```bash
cd javafx-client
mvn javafx:run
```
✅ Logs: `Client Name: Mac-Client-1 | IP: 192.168.1.103`

**Bước 4: Start Client 2 (Máy D - Ubuntu VM)**
```bash
cd javafx-client
mvn javafx:run
```
✅ Logs: `Client Name: Ubuntu-Client-2 | IP: 192.168.1.104`

### 2. Demo Scenarios

#### Demo 1: Multi-Source Download
- Client 1 và Client 2 cùng tải file
- Quan sát progress bars per-source
- Xem logs: pieces được tải từ Origin (192.168.1.101)

#### Demo 2: P2P Peer Exchange
- Client 1 tải trước, có một số pieces
- Client 2 bắt đầu tải cùng file
- Quan sát logs:
  ```
  [TRACKER] ✓ Retrieved 1 peers | FROM: 192.168.1.102:8081
  [PEER] GET /piece/100MB.zip/5 | FROM: 192.168.1.104 → TO: Peer (192.168.1.103:6881)
  [DOWNLOAD] ✓ Piece 5 completed | FROM: 192.168.1.103
  ```
- Client 2 tải pieces từ Client 1 (P2P)

#### Demo 3: Hash Mismatch & Retry
- Làm hỏng một piece trên Origin Server
- Quan sát logs:
  ```
  [HASH] ✗ Mismatch for piece 10 | FROM: 192.168.1.101
  [RETRY] Retrying piece 10 | FROM: 192.168.1.101 → TO: 192.168.1.103
  [DOWNLOAD] ✓ Piece 10 completed | FROM: 192.168.1.103
  ```

## ✅ Verification Checklist

### Kiểm tra kết nối:

**Từ Client 1 (macOS), test:**
```bash
# Test Origin Server
curl -k https://192.168.1.101:8443/manifest/100MB.zip

# Test Tracker
curl http://192.168.1.102:8081/tracker/peers?fileId=test
```

**Từ Client 2 (Ubuntu VM), test:**
```bash
# Test Origin Server
curl -k https://192.168.1.101:8443/manifest/100MB.zip

# Test Tracker
curl http://192.168.1.102:8081/tracker/peers?fileId=test
```

### Kiểm tra VM Network:

**Trên Windows Host:**
```cmd
# Xem VM network adapter
ipconfig
# Tìm adapter của VM (thường là "VirtualBox Host-Only" hoặc "VMware")
```

**Trên macOS Host:**
```bash
# Xem VM network
ifconfig | grep -A 5 "vmnet"
# Hoặc
ifconfig | grep -A 5 "vboxnet"
```

## 🔥 VM Network Configuration

### VirtualBox:
1. VM Settings → Network
2. Adapter 1: **Bridged Adapter** (khuyến nghị)
   - Cho phép VM có IP trong cùng network với host
3. Hoặc **NAT Network** nếu cần network riêng

### VMware:
1. VM Settings → Network Adapter
2. Chọn **Bridged** mode
3. Replicate physical network connection state

### Kiểm tra VM có thể ping host:
```bash
# Trong VM Ubuntu
ping 192.168.1.101  # Windows Host
ping 192.168.1.103  # macOS Host
```

## 🐛 Troubleshooting VM Network

### Lỗi: VM không ping được host

**VirtualBox:**
1. VM Settings → Network → Adapter 1
2. Chọn "Bridged Adapter"
3. Chọn network adapter của host
4. Restart VM

**VMware:**
1. VM Settings → Network Adapter
2. Chọn "Bridged"
3. Restart VM

### Lỗi: VM có IP khác subnet

**Giải pháp:**
- Đảm bảo VM dùng Bridged mode
- Hoặc cấu hình static IP trong VM
- Hoặc dùng host-only network và cấu hình routing

### Lỗi: Firewall block VM

**Windows:**
```cmd
# Allow VM network
netsh advfirewall firewall add rule name="VM Network" dir=in action=allow
```

**macOS:**
- System Preferences → Security → Firewall
- Allow Java và VM network

## 📝 Quick Reference Table

| Máy | IP | Role | Config File |
|-----|-----|------|-------------|
| Windows Host | 192.168.1.101 | Origin Server | `origin-server/.../config.properties` |
| Ubuntu VM (Win) | 192.168.1.102 | P2P Tracker | `p2p-tracker/.../config.properties` |
| macOS Host | 192.168.1.103 | Client 1 | `javafx-client/.../config.properties` |
| Ubuntu VM (Mac) | 192.168.1.104 | Client 2 | `javafx-client/.../config.properties` |

## 🎯 Demo Tips

1. **Start theo thứ tự:** Origin → Tracker → Client 1 → Client 2
2. **Xem logs trên tất cả máy** để thấy flow rõ ràng
3. **P2P demo:** Client 1 tải trước, Client 2 sẽ tải từ Client 1
4. **Network:** Đảm bảo tất cả máy cùng network (có thể ping được nhau)

## 💡 Lưu Ý Đặc Biệt

- **VM Network Mode:** Phải dùng **Bridged** để VM có IP trong cùng network
- **Firewall:** Mở ports trên cả host và VM
- **IP Detection:** Scripts tự động detect IP, nhưng cần verify
- **Logs:** Xem logs trên tất cả 4 máy để thấy flow đầy đủ

