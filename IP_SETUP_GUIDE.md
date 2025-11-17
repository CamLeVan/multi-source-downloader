# 🔧 Hướng Dẫn Cấu Hình IP Cho Demo 2 Máy

## 📋 Tổng Quan

Khi demo trên 2 máy khác nhau, bạn **CẦN** thay đổi IP addresses trong config files để các máy có thể giao tiếp với nhau.

## 🎯 2 Cách Setup IP

### Cách 1: Tự Động (Khuyến nghị) ⚡

**Bước 1: Lấy IP của mỗi máy**

**Windows:**
```cmd
scripts\get-ip.bat
```

**Linux/macOS:**
```bash
chmod +x scripts/get-ip.sh
./scripts/get-ip.sh
```

**Bước 2: Tự động cấu hình**

**Windows:**
```cmd
cd scripts
setup-network.bat
# Chọn role: 1=Client, 2=Origin Server, 3=Tracker
```

**Linux/macOS:**
```bash
cd scripts
chmod +x setup-network.sh
./setup-network.sh
# Chọn role: 1=Client, 2=Origin Server, 3=Tracker
```

Script sẽ tự động:
- Detect IP của máy
- Tạo/sửa config.properties với IP đúng
- Hiển thị thông tin cấu hình

### Cách 2: Thủ Công ✏️

**Bước 1: Lấy IP của mỗi máy**

**Windows:**
```cmd
ipconfig
# Tìm "IPv4 Address" (không phải 127.0.0.1)
# Ví dụ: 192.168.1.101
```

**Linux:**
```bash
hostname -I
# hoặc
ip addr show | grep "inet "
# Ví dụ: 192.168.1.102
```

**macOS:**
```bash
ifconfig | grep "inet "
# Tìm IP không phải 127.0.0.1
# Ví dụ: 192.168.1.103
```

**Bước 2: Sửa config files**

#### Scenario 1: Windows (Origin) + Ubuntu (Client + Tracker)

**Máy A - Windows:**
Sửa `origin-server/src/main/resources/config.properties`:
```properties
server.host=192.168.1.101  # ← IP của máy Windows này
```

**Máy B - Ubuntu:**
1. Sửa `p2p-tracker/src/main/resources/config.properties`:
```properties
server.host=192.168.1.102  # ← IP của máy Ubuntu này
```

2. Sửa `javafx-client/src/main/resources/config.properties`:
```properties
tracker.url=http://192.168.1.102:8081  # ← IP của máy Ubuntu (Tracker)
origin.server.url=https://192.168.1.101:8443  # ← IP của máy Windows (Origin)
```

#### Scenario 2: macOS (Client) + Ubuntu (Origin + Tracker)

**Máy A - macOS:**
Sửa `javafx-client/src/main/resources/config.properties`:
```properties
tracker.url=http://192.168.1.102:8081  # ← IP của máy Ubuntu (Tracker)
origin.server.url=https://192.168.1.102:8443  # ← IP của máy Ubuntu (Origin)
```

**Máy B - Ubuntu:**
1. Sửa `origin-server/src/main/resources/config.properties`:
```properties
server.host=192.168.1.102  # ← IP của máy Ubuntu này
```

2. Sửa `p2p-tracker/src/main/resources/config.properties`:
```properties
server.host=192.168.1.102  # ← IP của máy Ubuntu này
```

## ✅ Verification

### Kiểm tra IP đã đúng:

**Từ máy Client, test kết nối:**
```bash
# Test Tracker
curl http://TRACKER_IP:8081/tracker/peers?fileId=test
# Should return: []

# Test Origin Server
curl -k https://ORIGIN_IP:8443/manifest/100MB.zip
# Should return JSON
```

**Từ máy Server, kiểm tra logs:**
Khi Client connect, logs sẽ hiển thị:
```
[ORIGIN] GET /manifest/100MB.zip | FROM: 192.168.1.102 → TO: Origin:8443
```

### Checklist:
- [ ] IP trong config.properties **KHÔNG phải** localhost (127.0.0.1)
- [ ] IP trong config.properties **ĐÚNG** với IP thực tế của máy
- [ ] Có thể ping được giữa 2 máy
- [ ] Firewall đã mở ports (8443, 8081, 6881)

## 🔥 Firewall Setup

### Windows:
```cmd
# Mở Command Prompt as Administrator
netsh advfirewall firewall add rule name="Origin Server" dir=in action=allow protocol=TCP localport=8443
netsh advfirewall firewall add rule name="P2P Tracker" dir=in action=allow protocol=TCP localport=8081
netsh advfirewall firewall add rule name="Peer Server" dir=in action=allow protocol=TCP localport=6881
```

### Linux (Ubuntu):
```bash
sudo ufw allow 8443/tcp
sudo ufw allow 8081/tcp
sudo ufw allow 6881/tcp
sudo ufw status
```

### macOS:
- System Preferences → Security & Privacy → Firewall
- Click "Firewall Options"
- Allow incoming connections for Java

## 📊 Ví Dụ Cấu Hình Thực Tế

### Ví dụ: Windows (192.168.1.101) + Ubuntu (192.168.1.102)

**Windows - Origin Server:**
```properties
# origin-server/src/main/resources/config.properties
server.host=192.168.1.101
server.port=8443
```

**Ubuntu - Tracker:**
```properties
# p2p-tracker/src/main/resources/config.properties
server.host=192.168.1.102
server.port=8081
```

**Ubuntu - Client:**
```properties
# javafx-client/src/main/resources/config.properties
tracker.url=http://192.168.1.102:8081
origin.server.url=https://192.168.1.101:8443
client.name=Ubuntu-Client
```

## 🐛 Troubleshooting

### Lỗi: "Connection refused"

**Nguyên nhân:** IP sai hoặc firewall block

**Giải pháp:**
1. Kiểm tra IP trong config.properties
2. Kiểm tra firewall
3. Test ping: `ping IP_MÁY_KHÁC`

### Lỗi: "Connection timeout"

**Nguyên nhân:** Không cùng network hoặc IP sai

**Giải pháp:**
1. Đảm bảo 2 máy cùng LAN
2. Kiểm tra IP addresses
3. Kiểm tra router/firewall

### Lỗi: Logs hiển thị "localhost" thay vì IP thực

**Nguyên nhân:** Config chưa được load hoặc IP detect sai

**Giải pháp:**
1. Rebuild project: `mvn clean install`
2. Kiểm tra config.properties có đúng không
3. Xem logs khi start để confirm IP

## 💡 Tips

1. **Luôn dùng IP thực** (192.168.x.x), không dùng localhost
2. **Ghi lại IP** của mỗi máy trước khi config
3. **Test từng bước**: ping → test endpoint → start services
4. **Xem logs** để verify IP đúng
5. **Script tự động** sẽ giúp bạn tránh lỗi typo

## 📝 Quick Reference

| Component | Config File | IP Field |
|-----------|-------------|----------|
| Client | `javafx-client/.../config.properties` | `tracker.url`, `origin.server.url` |
| Origin Server | `origin-server/.../config.properties` | `server.host` |
| P2P Tracker | `p2p-tracker/.../config.properties` | `server.host` |

**Lưu ý:** Client tự động detect local IP, không cần config.

