# 🔧 VM Network Setup Guide

## 📋 Tổng Quan

Khi demo trên 4 máy (2 host + 2 VM), các VM cần được cấu hình network để có thể giao tiếp với host và các máy khác trong cùng network.

## 🎯 Yêu Cầu Network

Tất cả 4 máy phải:
- ✅ Cùng network (có thể ping được nhau)
- ✅ Có IP trong cùng subnet (ví dụ: 192.168.1.x)
- ✅ Firewall cho phép ports (8443, 8081, 6881)

## 🔧 VirtualBox Setup

### Bước 1: Cấu hình VM Network

1. **Mở VM Settings:**
   - Right-click VM → Settings → Network

2. **Adapter 1 - Bridged Adapter (Khuyến nghị):**
   ```
   Enable Network Adapter: ✓
   Attached to: Bridged Adapter
   Name: [Chọn network adapter của host]
   ```
   
   **Lợi ích:**
   - VM có IP trong cùng network với host
   - VM có thể giao tiếp trực tiếp với các máy khác
   - Dễ dàng demo P2P

3. **Adapter 2 - NAT (Optional, cho internet):**
   ```
   Enable Network Adapter: ✓
   Attached to: NAT
   ```
   
   **Lưu ý:** Chỉ cần nếu VM cần internet access

### Bước 2: Verify Network

**Trong VM Ubuntu:**
```bash
# Kiểm tra IP
ip addr show
# hoặc
hostname -I

# Test ping host
ping 192.168.1.101  # Windows Host IP
ping 192.168.1.103  # macOS Host IP

# Test ping từ host
ping 192.168.1.102  # VM IP
```

### Bước 3: Firewall trên Host

**Windows:**
```cmd
# Allow VM network
netsh advfirewall firewall add rule name="VM Network" dir=in action=allow
netsh advfirewall firewall add rule name="Origin Server" dir=in action=allow protocol=TCP localport=8443
```

**macOS:**
- System Preferences → Security → Firewall
- Allow Java và VM network connections

## 🔧 VMware Setup

### Bước 1: Cấu hình VM Network

1. **VM Settings → Network Adapter:**
   - Chọn **Bridged** mode
   - Replicate physical network connection state: ✓

2. **Network Settings:**
   - VMnet0 (Bridged): Auto-bridging

### Bước 2: Verify Network

Tương tự VirtualBox, test ping giữa host và VM.

## 🔧 Parallels Setup (macOS)

### Bước 1: Cấu hình Network

1. **VM Settings → Hardware → Network:**
   - Network Type: **Shared Network** hoặc **Bridged Ethernet**

2. **Shared Network:**
   - VM có IP trong cùng subnet với host
   - Tự động cấu hình

### Bước 2: Verify

```bash
# Trong VM
hostname -I
ping 192.168.1.103  # macOS Host IP
```

## 📊 Network Topology Examples

### Example 1: VirtualBox Bridged

```
Host Network: 192.168.1.0/24
├── Windows Host: 192.168.1.101
├── Ubuntu VM (Windows): 192.168.1.102
├── macOS Host: 192.168.1.103
└── Ubuntu VM (macOS): 192.168.1.104
```

### Example 2: VMware Bridged

Tương tự VirtualBox, tất cả máy trong cùng subnet.

### Example 3: Host-Only Network (Nếu cần network riêng)

**VirtualBox:**
1. File → Host Network Manager
2. Create new adapter: vboxnet0
3. Configure: 192.168.56.1/24
4. VM Settings → Network → Host-Only Adapter

**Lưu ý:** Với Host-Only, VM chỉ giao tiếp được với host, không giao tiếp được với máy khác trong LAN.

## ✅ Verification Checklist

### Trên mỗi máy:

- [ ] IP address được detect đúng
- [ ] Có thể ping được tất cả máy khác
- [ ] Firewall đã mở ports
- [ ] VM network mode là Bridged (không phải NAT)

### Test kết nối:

**Từ Client 1 (macOS), test:**
```bash
# Test Origin Server (Windows)
ping 192.168.1.101
curl -k https://192.168.1.101:8443/manifest/100MB.zip

# Test Tracker (Ubuntu VM on Windows)
ping 192.168.1.102
curl http://192.168.1.102:8081/tracker/peers?fileId=test

# Test Client 2 (Ubuntu VM on macOS)
ping 192.168.1.104
```

**Từ Client 2 (Ubuntu VM), test:**
```bash
# Test tất cả máy khác
ping 192.168.1.101  # Windows Host
ping 192.168.1.102  # Ubuntu VM on Windows
ping 192.168.1.103  # macOS Host
```

## 🐛 Troubleshooting

### Lỗi: VM không có IP hoặc IP khác subnet

**Nguyên nhân:** VM dùng NAT thay vì Bridged

**Giải pháp:**
1. VM Settings → Network
2. Đổi từ NAT sang Bridged Adapter
3. Restart VM

### Lỗi: VM ping được host nhưng không ping được máy khác

**Nguyên nhân:** Firewall hoặc routing

**Giải pháp:**
1. Kiểm tra firewall trên host
2. Kiểm tra router settings
3. Đảm bảo tất cả máy cùng subnet

### Lỗi: VM có IP 169.254.x.x (APIPA)

**Nguyên nhân:** Không nhận được IP từ DHCP

**Giải pháp:**
1. Kiểm tra VM network adapter
2. Restart network trong VM:
   ```bash
   sudo systemctl restart networking
   # hoặc
   sudo dhclient -r
   sudo dhclient
   ```

### Lỗi: "Connection refused" từ VM

**Nguyên nhân:** Firewall trên VM

**Giải pháp:**
```bash
# Trong VM Ubuntu
sudo ufw allow 8081/tcp  # Tracker
sudo ufw allow 6881/tcp  # Peer Server
sudo ufw status
```

## 💡 Best Practices

1. **Luôn dùng Bridged mode** cho demo
2. **Ghi lại IP của tất cả máy** trước khi config
3. **Test ping** trước khi start services
4. **Xem logs** để verify IP đúng
5. **Firewall:** Mở ports trên cả host và VM

## 📝 Quick Commands

### Lấy IP trong VM:
```bash
hostname -I
# hoặc
ip addr show | grep "inet "
```

### Test connectivity:
```bash
# Ping test
ping -c 4 192.168.1.101

# Port test
nc -zv 192.168.1.101 8443
nc -zv 192.168.1.102 8081
```

### Restart network (VM):
```bash
sudo systemctl restart networking
# hoặc
sudo ifdown eth0 && sudo ifup eth0
```

