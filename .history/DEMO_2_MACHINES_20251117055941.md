# 🎬 Demo Trên 2 Máy - Hướng Dẫn Nhanh

## ⚡ 3 Bước Setup

### Bước 1: Lấy IP của mỗi máy

**Windows:**
```cmd
scripts\get-ip.bat
```

**Linux/macOS:**
```bash
chmod +x scripts/get-ip.sh
./scripts/get-ip.sh
```

**Ghi lại:** Máy A = `192.168.1.XXX`, Máy B = `192.168.1.YYY`

### Bước 2: Tự động cấu hình

**Windows:**
```cmd
cd scripts
setup-network.bat
```

**Linux/macOS:**
```bash
cd scripts
chmod +x setup-network.sh
./setup-network.sh
```

**Chọn role:**
- `1` = Client
- `2` = Origin Server  
- `3` = P2P Tracker

### Bước 3: Chạy services

## 📋 Scenarios

### Scenario 1: Windows (Origin) + Ubuntu (Client + Tracker)

**Máy A - Windows:**
```cmd
cd origin-server
mvn spring-boot:run
```

**Máy B - Ubuntu:**
```bash
# Terminal 1
cd p2p-tracker
mvn spring-boot:run

# Terminal 2
cd javafx-client
mvn javafx:run
```

**Config cần sửa:**
- Ubuntu Client: `tracker.url=http://IP_UBUNTU:8081`, `origin.server.url=https://IP_WINDOWS:8443`
- Ubuntu Tracker: `server.host=IP_UBUNTU`
- Windows Origin: `server.host=IP_WINDOWS`

### Scenario 2: macOS (Client) + Ubuntu (Origin + Tracker)

**Máy A - macOS:**
```bash
cd javafx-client
mvn javafx:run
```

**Máy B - Ubuntu:**
```bash
# Terminal 1
cd origin-server
mvn spring-boot:run

# Terminal 2
cd p2p-tracker
mvn spring-boot:run
```

**Config cần sửa:**
- macOS Client: `tracker.url=http://IP_UBUNTU:8081`, `origin.server.url=https://IP_UBUNTU:8443`
- Ubuntu Origin: `server.host=IP_UBUNTU`
- Ubuntu Tracker: `server.host=IP_UBUNTU`

## ✅ Kiểm Tra

**Logs sẽ hiển thị IP:**
```
[INFO] Local IP Address: 192.168.1.102
[INFO] Tracker URL: http://192.168.1.100:8081
[ORIGIN] GET /manifest/100MB.zip | FROM: 192.168.1.102 → TO: 192.168.1.101:8443
```

**Nếu thấy "localhost" trong logs → IP chưa đúng!**

## 🔥 Firewall

**Windows:**
```cmd
netsh advfirewall firewall add rule name="Origin" dir=in action=allow protocol=TCP localport=8443
netsh advfirewall firewall add rule name="Tracker" dir=in action=allow protocol=TCP localport=8081
```

**Linux:**
```bash
sudo ufw allow 8443/tcp 8081/tcp 6881/tcp
```

## 📝 Lưu Ý

- ✅ **PHẢI** dùng IP thực (192.168.x.x), không dùng localhost
- ✅ **PHẢI** mở firewall ports
- ✅ **PHẢI** cùng network (LAN)
- ✅ Xem logs để verify IP đúng

## 🆘 Nếu Lỗi

1. Kiểm tra IP trong config.properties
2. Test ping: `ping IP_MÁY_KHÁC`
3. Test endpoint: `curl http://IP:8081/tracker/peers?fileId=test`
4. Xem logs để tìm lỗi

**Xem chi tiết:** `SETUP_TWO_MACHINES.md` hoặc `IP_SETUP_GUIDE.md`

