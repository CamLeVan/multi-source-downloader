# Hướng Dẫn Setup Demo Trên 2 Máy

## 🖥️ Scenarios Hỗ Trợ

### Scenario 1: Windows + Ubuntu Linux
- **Máy A (Windows)**: Origin Server
- **Máy B (Ubuntu)**: Client + P2P Tracker

### Scenario 2: macOS + Ubuntu Linux  
- **Máy A (macOS)**: Client
- **Máy B (Ubuntu)**: Origin Server + P2P Tracker

## 🚀 Quick Setup Guide

### Bước 1: Lấy IP Address của mỗi máy

#### Trên Windows:
```cmd
# Chạy script helper
scripts\get-ip.bat

# Hoặc manual
ipconfig
# Tìm "IPv4 Address" (không phải 127.0.0.1)
```

#### Trên Linux/macOS:
```bash
# Chạy script helper
chmod +x scripts/get-ip.sh
./scripts/get-ip.sh

# Hoặc manual
# Linux
hostname -I
# hoặc
ip addr show

# macOS
ifconfig | grep "inet "
```

**Ghi lại IP của mỗi máy:**
- Máy A (Windows/macOS): `192.168.1.XXX`
- Máy B (Ubuntu): `192.168.1.YYY`

### Bước 2: Cấu hình tự động (Khuyến nghị)

#### Trên Windows:
```cmd
cd scripts
setup-network.bat
# Chọn role (1=Client, 2=Origin Server, 3=Tracker)
```

#### Trên Linux/macOS:
```bash
cd scripts
chmod +x setup-network.sh
./setup-network.sh
# Chọn role (1=Client, 2=Origin Server, 3=Tracker)
```

### Bước 3: Cấu hình thủ công (Nếu cần)

#### Scenario 1: Windows (Origin) + Ubuntu (Client + Tracker)

**Máy A - Windows (Origin Server):**
1. Sửa `origin-server/src/main/resources/config.properties`:
```properties
server.host=192.168.1.101  # IP của máy Windows
server.port=8443
```

2. Chạy Origin Server:
```cmd
cd origin-server
mvn spring-boot:run
```

**Máy B - Ubuntu (Client + Tracker):**
1. Sửa `p2p-tracker/src/main/resources/config.properties`:
```properties
server.host=192.168.1.102  # IP của máy Ubuntu
server.port=8081
```

2. Sửa `javafx-client/src/main/resources/config.properties`:
```properties
tracker.url=http://192.168.1.102:8081  # IP của máy Ubuntu (Tracker)
origin.server.url=https://192.168.1.101:8443  # IP của máy Windows (Origin)
client.name=Ubuntu-Client
```

3. Chạy P2P Tracker:
```bash
cd p2p-tracker
mvn spring-boot:run
```

4. Chạy Client (terminal khác):
```bash
cd javafx-client
mvn javafx:run
```

#### Scenario 2: macOS (Client) + Ubuntu (Origin + Tracker)

**Máy A - macOS (Client):**
1. Sửa `javafx-client/src/main/resources/config.properties`:
```properties
tracker.url=http://192.168.1.102:8081  # IP của máy Ubuntu (Tracker)
origin.server.url=https://192.168.1.102:8443  # IP của máy Ubuntu (Origin)
client.name=Mac-Client
```

2. Chạy Client:
```bash
cd javafx-client
mvn javafx:run
```

**Máy B - Ubuntu (Origin + Tracker):**
1. Sửa `origin-server/src/main/resources/config.properties`:
```properties
server.host=192.168.1.102  # IP của máy Ubuntu
server.port=8443
```

2. Sửa `p2p-tracker/src/main/resources/config.properties`:
```properties
server.host=192.168.1.102  # IP của máy Ubuntu
server.port=8081
```

3. Chạy Origin Server (terminal 1):
```bash
cd origin-server
mvn spring-boot:run
```

4. Chạy P2P Tracker (terminal 2):
```bash
cd p2p-tracker
mvn spring-boot:run
```

## ✅ Verification Checklist

### Kiểm tra kết nối giữa 2 máy:

**Từ máy A, ping máy B:**
```bash
# Windows
ping 192.168.1.102

# macOS/Linux
ping 192.168.1.102
```

**Từ máy B, ping máy A:**
```bash
ping 192.168.1.101
```

### Kiểm tra ports:

**Từ máy Client, test Tracker:**
```bash
curl http://TRACKER_IP:8081/tracker/peers?fileId=test
# Should return: []
```

**Từ máy Client, test Origin Server:**
```bash
curl -k https://ORIGIN_IP:8443/manifest/100MB.zip
# Should return JSON manifest
```

## 🔥 Firewall Configuration

### Windows Firewall:
```cmd
# Mở ports
netsh advfirewall firewall add rule name="Origin Server" dir=in action=allow protocol=TCP localport=8443
netsh advfirewall firewall add rule name="P2P Tracker" dir=in action=allow protocol=TCP localport=8081
netsh advfirewall firewall add rule name="Peer Server" dir=in action=allow protocol=TCP localport=6881
```

### Linux Firewall (ufw):
```bash
sudo ufw allow 8443/tcp  # Origin Server
sudo ufw allow 8081/tcp  # P2P Tracker
sudo ufw allow 6881/tcp  # Peer Server
sudo ufw status
```

### macOS Firewall:
- System Preferences → Security & Privacy → Firewall
- Click "Firewall Options"
- Allow incoming connections for Java

## 📊 Expected Log Output

Khi chạy đúng, bạn sẽ thấy logs như sau:

**Trên Origin Server (Windows):**
```
[14:30:16.200] [ORIGIN] GET /manifest/100MB.zip | FROM: 192.168.1.102 → TO: Origin:8443
[14:30:16.250] [ORIGIN] ✓ Manifest sent | FROM: Origin:8443 → TO: 192.168.1.102 | Pieces: 100
```

**Trên Tracker (Ubuntu):**
```
[14:30:16.500] [TRACKER] POST /tracker/announce | FROM: 192.168.1.102 → TO: Tracker:8081
[14:30:16.550] [TRACKER] ✓ Announce registered | FROM: Tracker:8081 → TO: 192.168.1.102
```

**Trên Client (macOS/Ubuntu):**
```
[14:30:16.100] [STEP 01] Fetching manifest | FROM: 192.168.1.102 → TO: 192.168.1.101:8443
[14:30:16.200] [ORIGIN] GET /manifest/100MB.zip | FROM: 192.168.1.102 → TO: 192.168.1.101:8443
[14:30:16.250] [ORIGIN] ✓ Manifest received | FROM: 192.168.1.101:8443 → TO: 192.168.1.102
```

## 🐛 Troubleshooting

### Lỗi: "Connection refused" hoặc "Connection timeout"

1. **Kiểm tra IP addresses:**
   ```bash
   # Đảm bảo IP trong config.properties đúng với IP thực tế
   # Không dùng localhost, phải dùng IP thực (192.168.x.x)
   ```

2. **Kiểm tra firewall:**
   ```bash
   # Windows
   netsh advfirewall firewall show rule name=all | findstr 8443
   
   # Linux
   sudo ufw status
   ```

3. **Kiểm tra services đang chạy:**
   ```bash
   # Windows
   netstat -an | findstr 8443
   netstat -an | findstr 8081
   
   # Linux/macOS
   netstat -an | grep 8443
   netstat -an | grep 8081
   ```

### Lỗi: "SSL certificate error"

Origin Server dùng self-signed certificate. Client sẽ tự động bỏ qua (OkHttp mặc định).

Nếu vẫn lỗi, thêm vào `MainApp.java`:
```java
OkHttpClient httpClient = new OkHttpClient.Builder()
    .hostnameVerifier((hostname, session) -> true)
    .build();
```

### Lỗi: "Tracker not found"

- Đảm bảo Tracker đang chạy trên máy B
- Đảm bảo IP trong `tracker.url` đúng
- Test với: `curl http://TRACKER_IP:8081/tracker/peers?fileId=test`

## 📝 Quick Reference

### IP Configuration Template

**Máy A (Windows - Origin Server):**
```properties
# origin-server/src/main/resources/config.properties
server.host=192.168.1.101
```

**Máy B (Ubuntu - Client + Tracker):**
```properties
# p2p-tracker/src/main/resources/config.properties
server.host=192.168.1.102

# javafx-client/src/main/resources/config.properties
tracker.url=http://192.168.1.102:8081
origin.server.url=https://192.168.1.101:8443
```

### Ports Summary
- **8443**: Origin Server (HTTPS)
- **8081**: P2P Tracker (HTTP)
- **6881**: Peer Server (HTTP) - tự động detect

### Network Flow
```
Client (192.168.1.102)
  ↓ GET /manifest
Origin Server (192.168.1.101:8443)
  ↓ POST /announce
P2P Tracker (192.168.1.102:8081)
  ↓ GET /peers
P2P Tracker → Client (peer list)
  ↓ GET /piece/fileId/pieceId
Peer Server (192.168.1.102:6881)
```

## 🎯 Demo Tips

1. **Chạy servers trước:** Origin Server và Tracker phải chạy trước Client
2. **Kiểm tra logs:** Xem logs để đảm bảo IP addresses đúng
3. **Test từng bước:** Test manifest, test tracker, rồi mới start download
4. **P2P demo:** Cần 2 clients chạy cùng lúc để demo P2P

