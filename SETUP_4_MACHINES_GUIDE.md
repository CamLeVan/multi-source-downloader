# 🚀 **HƯỚNG DẪN SETUP 4 MÁY (2 MÁY THẬT + 2 MÁY ẢO)**

## 📋 **KIẾN TRÚC ĐỀ XUẤT**

```
┌─────────────────────────────────────────────────────────┐
│                    NETWORK TOPOLOGY                      │
└─────────────────────────────────────────────────────────┘

Machine 1 (Windows Real)     → Origin Server (port 8080)
Machine 2 (macOS Real)        → Client 1
Machine 3 (Windows VM)        → P2P Tracker (port 8081)
Machine 4 (macOS VM)          → Client 2
```

---

## 🎯 **PHÂN CÔNG MÁY**

### **Option 1: Recommended**
| Máy | Vai trò | IP | Ports |
|-----|---------|----|----|
| **Windows Real** | Origin Server | `192.168.1.214` | `8080` |
| **macOS Real** | Client 1 | `192.168.1.83` | `6881` (Peer) |
| **Windows VM** | P2P Tracker | `192.168.1.XXX` | `8081` |
| **macOS VM** | Client 2 | `192.168.1.XXX` | `6881` (Peer) |

### **Option 2: Alternative**
| Máy | Vai trò | IP | Ports |
|-----|---------|----|----|
| **Windows Real** | Origin Server | `192.168.1.214` | `8080` |
| **macOS Real** | P2P Tracker | `192.168.1.83` | `8081` |
| **Windows VM** | Client 1 | `192.168.1.XXX` | `6881` (Peer) |
| **macOS VM** | Client 2 | `192.168.1.XXX` | `6881` (Peer) |

---

## 📝 **BƯỚC 1: LẤY IP ADDRESSES**

### **Trên mỗi máy, chạy:**

**Windows:**
```cmd
ipconfig
```
→ Tìm `IPv4 Address` (ví dụ: `192.168.1.214`)

**macOS/Linux:**
```bash
ifconfig | grep "inet " | grep -v 127.0.0.1
```
→ Tìm IP address (ví dụ: `192.168.1.83`)

**Ghi lại IP của 4 máy:**
- Windows Real: `192.168.1.214`
- macOS Real: `192.168.1.83`
- Windows VM: `192.168.1.XXX` (thay bằng IP thực tế)
- macOS VM: `192.168.1.XXX` (thay bằng IP thực tế)

---

## 🔧 **BƯỚC 2: SETUP ORIGIN SERVER (Windows Real)**

### **1. Cấu hình `origin-server/src/main/resources/config.properties`:**
```properties
server.host=192.168.1.214
server.port=8080
server.name=Origin-Server
```

### **2. Đảm bảo có files trong `origin-server/server_files/`:**
```bash
cd origin-server/server_files
ls -lh
# Phải có: 100MB.zip, 100MB.zip.manifest.json
# (và có thể có 2GB.zip nếu server đã chạy)
```

### **3. Chạy Origin Server:**
```bash
cd origin-server
mvn spring-boot:run
```

**Kiểm tra:**
- Server chạy trên `http://192.168.1.214:8080`
- API `/files/info` trả về danh sách files

---

## 🔧 **BƯỚC 3: SETUP P2P TRACKER (Windows VM hoặc macOS Real)**

### **1. Cấu hình `p2p-tracker/src/main/resources/application.properties`:**
```properties
server.port=8081
server.address=0.0.0.0
```

### **2. Chạy P2P Tracker:**
```bash
cd p2p-tracker
mvn spring-boot:run
```

**Kiểm tra:**
- Tracker chạy trên `http://192.168.1.XXX:8081`
- API `/tracker/health` trả về `200 OK`

---

## 🔧 **BƯỚC 4: SETUP CLIENT 1 (macOS Real)**

### **1. Cấu hình `javafx-client/src/main/resources/config.properties`:**
```properties
tracker.url=http://192.168.1.XXX:8081    # IP của P2P Tracker
origin.server.url=http://192.168.1.214:8080  # IP của Origin Server
client.name=Mac-Client-1
```

### **2. Chạy Client:**
```bash
cd javafx-client
mvn javafx:run
```

---

## 🔧 **BƯỚC 5: SETUP CLIENT 2 (macOS VM)**

### **1. Cấu hình `javafx-client/src/main/resources/config.properties`:**
```properties
tracker.url=http://192.168.1.XXX:8081    # IP của P2P Tracker
origin.server.url=http://192.168.1.214:8080  # IP của Origin Server
client.name=Mac-Client-2
```

### **2. Chạy Client:**
```bash
cd javafx-client
mvn javafx:run
```

---

## 🔥 **BƯỚC 6: TEST MULTI-SOURCE DOWNLOAD**

### **Scenario 1: Download từ Origin Server**
1. Client 1 chọn file và bắt đầu download
2. File sẽ được tải từ Origin Server
3. Client 1 announce với Tracker

### **Scenario 2: P2P Sharing**
1. Client 1 đang download file
2. Client 2 chọn cùng file và bắt đầu download
3. Client 2 sẽ:
   - Tải một số pieces từ Origin Server
   - Tải một số pieces từ Client 1 (P2P)
   - Tải một số pieces từ Mirror (nếu có)

### **Scenario 3: Multi-Source Download**
- Client 2 sẽ tải từ nhiều nguồn cùng lúc:
  - Origin Server
  - Client 1 (P2P)
  - Mirror (nếu có)

---

## 🔍 **KIỂM TRA KẾT NỐI**

### **Từ mỗi máy, test ping:**
```bash
# Windows
ping 192.168.1.214
ping 192.168.1.83
ping 192.168.1.XXX  # IP của VM

# macOS
ping -c 4 192.168.1.214
ping -c 4 192.168.1.83
ping -c 4 192.168.1.XXX  # IP của VM
```

### **Test HTTP connections:**
```bash
# Test Origin Server
curl http://192.168.1.214:8080/files/info

# Test P2P Tracker
curl http://192.168.1.XXX:8081/tracker/health
```

---

## 🛡️ **FIREWALL CONFIGURATION**

### **Windows (Origin Server):**
```cmd
# Mở port 8080
netsh advfirewall firewall add rule name="Origin Server" dir=in action=allow protocol=TCP localport=8080
```

### **Windows (P2P Tracker):**
```cmd
# Mở port 8081
netsh advfirewall firewall add rule name="P2P Tracker" dir=in action=allow protocol=TCP localport=8081
```

### **macOS (Clients):**
```bash
# Mở port 6881 (Peer Server)
sudo pfctl -f /etc/pf.conf
# Thêm rule vào /etc/pf.conf nếu cần
```

---

## 📊 **MONITORING**

### **Xem logs trên mỗi máy:**

**Origin Server:**
- Console logs hiển thị requests từ clients
- Xem: `[ORIGIN] GET /files/info | FROM: ...`

**P2P Tracker:**
- Console logs hiển thị announce/peer requests
- Xem: `[TRACKER] POST /tracker/announce`

**Clients:**
- Console logs hiển thị download progress
- UI hiển thị progress bars và source tracking

---

## 🐛 **TROUBLESHOOTING**

### **Vấn đề 1: Clients không thấy files**
- **Nguyên nhân:** Origin Server chưa chạy hoặc IP sai
- **Giải pháp:** Kiểm tra Origin Server đang chạy và IP đúng

### **Vấn đề 2: P2P không hoạt động**
- **Nguyên nhân:** Tracker chưa chạy hoặc firewall block
- **Giải pháp:** Kiểm tra Tracker và firewall rules

### **Vấn đề 3: Download chậm**
- **Nguyên nhân:** Network latency hoặc bandwidth
- **Giải pháp:** Kiểm tra network connection giữa các máy

### **Vấn đề 4: VMs không kết nối được**
- **Nguyên nhân:** VM network mode (NAT/Bridged)
- **Giải pháp:** Đổi VM network mode sang Bridged

---

## ✅ **CHECKLIST**

- [ ] 4 máy đều có IP addresses trong cùng network
- [ ] Origin Server chạy trên Windows Real (port 8080)
- [ ] P2P Tracker chạy trên VM (port 8081)
- [ ] Client 1 chạy trên macOS Real
- [ ] Client 2 chạy trên macOS VM
- [ ] Firewall rules đã mở ports cần thiết
- [ ] Tất cả máy có thể ping nhau
- [ ] Clients có thể fetch file list từ Origin Server
- [ ] Clients có thể announce với Tracker
- [ ] P2P sharing hoạt động giữa 2 clients

---

## 🎉 **KẾT QUẢ MONG ĐỢI**

Khi test thành công, bạn sẽ thấy:
- ✅ Client 1 download file từ Origin Server
- ✅ Client 2 download file từ Origin Server + Client 1 (P2P)
- ✅ Download speed tăng khi có nhiều sources
- ✅ Progress bars hiển thị progress từ từng source
- ✅ Console logs hiển thị P2P sharing

**Chúc bạn test thành công!** 🚀

