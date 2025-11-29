# Hướng Dẫn Demo Dự Án Multi-Source Downloader

Tài liệu này hướng dẫn chi tiết cách thiết lập và thực hiện demo dự án trên 2 máy tính trong cùng mạng LAN.

## 1. Chuẩn Bị

*   **Thiết bị:** 2 Máy tính (Laptop/PC) kết nối cùng một mạng WiFi hoặc dây LAN.
    *   **Máy 1 (Host):** Sẽ chạy Server (Origin, Tracker) và Client A.
    *   **Máy 2 (Guest):** Sẽ chạy Client B.
*   **Phần mềm:**
    *   Java JDK 21 trở lên đã được cài đặt trên cả 2 máy.
    *   Phần mềm xem video VLC (để demo tính năng Streaming).
    *   Source code dự án (hoặc file JAR đã build).

---

## 2. Thiết Lập Môi Trường (Cấu Hình IP)

Đây là bước quan trọng nhất. Nếu sai IP, các máy sẽ không thấy nhau.

### Bước 2.1: Lấy địa chỉ IP của Máy 1 (Host)
1.  Trên **Máy 1**, mở **Command Prompt (CMD)** hoặc Terminal.
2.  Gõ lệnh: `ipconfig` (Windows) hoặc `ifconfig` (Mac/Linux).
3.  Tìm dòng **IPv4 Address** (hoặc `inet`).
    *   *Ví dụ:* `192.168.1.10` (Hãy ghi nhớ số này).

### Bước 2.2: Cấu hình file `config.properties`

Mở file `javafx-client/src/main/resources/config.properties` và chỉnh sửa nội dung.

**Cấu hình cho Máy 1 (Host):**
```properties
# Thay 192.168.1.10 bằng IP thật của Máy 1
tracker.url=http://192.168.1.10:8081
origin.server.url=http://192.168.1.10:8080
client.name=Host-Client
```

**Cấu hình cho Máy 2 (Guest):**
```properties
# Vẫn trỏ về IP của Máy 1
tracker.url=http://192.168.1.10:8081
origin.server.url=http://192.168.1.10:8080
client.name=Guest-Client
```

---

## 3. Kịch Bản Demo (Từng Bước)

### Giai đoạn 1: Khởi động Hệ thống (Trên Máy 1)

1.  **Chạy Origin Server:**
    *   Mở project `origin-server`.
    *   Chạy class `OriginServerApplication`.
    *   *Kiểm tra:* Mở trình duyệt, vào `http://localhost:8080/health` -> Thấy "OK".

2.  **Chạy Tracker Server:**
    *   Mở project `p2p-tracker`.
    *   Chạy class `TrackerApplication`.
    *   *Kiểm tra:* Mở trình duyệt, vào `http://localhost:8081/peers` -> Thấy danh sách trống `[]`.

3.  **Chạy Client A (Host):**
    *   Mở project `javafx-client`.
    *   Chạy class `MainApp`.
    *   Giao diện ứng dụng hiện lên với tên "Host-Client".

### Giai đoạn 2: Demo Streaming & Seeding (Trên Máy 1)

1.  **Bắt đầu tải:**
    *   Trên giao diện Client A, chọn file (ví dụ `video_100MB.mp4`) và bấm **Download**.
2.  **Demo Streaming:**
    *   Khi nút **"▶ Play"** sáng lên (màu xanh), bấm vào đó.
    *   Thông báo hiện ra: "URL Copied to Clipboard".
    *   Mở **VLC Player** -> Menu **Media** -> **Open Network Stream** (Ctrl+N).
    *   Dán link (Ctrl+V) và bấm Play.
    *   *Kết quả:* Video chạy ngay lập tức. -> **Thành công tính năng Portable Streaming.**
3.  **Chuẩn bị Seeding:**
    *   Để Client A tải được khoảng **30-50%** rồi tạm dừng hoặc để chạy tiếp. Lúc này Client A đã có dữ liệu để chia sẻ cho người khác.

### Giai đoạn 3: Demo P2P Multi-Source (Trên Máy 2)

1.  **Chạy Client B (Guest):**
    *   Chạy class `MainApp` trên Máy 2.
2.  **Bắt đầu tải:**
    *   Chọn **cùng file** mà Máy 1 đang tải. Bấm **Download**.
3.  **Quan sát & Giải thích:**
    *   Nhìn vào thanh tiến độ (Progress Bar) của Client B.
    *   Chỉ cho người xem thấy các thanh màu nhỏ bên dưới:
        *   **Màu Xanh (Origin):** Đang tải từ Server Máy 1.
        *   **Màu Tím/Khác (Peer):** Đang tải từ Client A (Máy 1).
    *   *Điểm nhấn:* "Mọi người thấy không, Client B đang tải từ 2 nguồn cùng lúc. Tốc độ nhanh hơn hẳn so với tải đơn lẻ."

### Giai đoạn 4: Demo Tự Phục Hồi (Nâng cao - Tùy chọn)

1.  Khi Máy 2 đang tải, hãy thử **Tắt Origin Server** trên Máy 1 (Stop process).
2.  Quan sát Máy 2:
    *   Tốc độ từ Origin về 0.
    *   Nhưng quá trình tải **KHÔNG DỪNG LẠI**. Nó vẫn tiếp tục chạy nhờ dữ liệu từ Client A (P2P).
    *   -> **Thành công tính năng Reliability.**

---

## 4. Khắc Phục Sự Cố (Troubleshooting)

*   **Lỗi "Connection Refused":**
    *   Kiểm tra lại IP trong `config.properties`.
    *   Tắt Tường lửa (Firewall) trên Máy 1 hoặc cho phép Java đi qua Firewall.
*   **Máy 2 không thấy Máy 1:**
    *   Đảm bảo 2 máy chung mạng WiFi. Thử ping từ Máy 2 sang Máy 1: `ping 192.168.1.10`.
*   **Video không chạy:**
    *   Đảm bảo đã cài VLC. Windows Media Player mặc định có thể không hỗ trợ stream tốt.

**Chúc bạn có buổi Demo thành công!**
