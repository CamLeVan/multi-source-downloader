# KỊCH BẢN KIỂM THỬ VÀ DEMO (TEST SCENARIOS)

Tài liệu này hướng dẫn chi tiết các bước Demo dự án **Multi-Source P2P Downloader** trên 2 cấu hình phần cứng khác nhau.

---

## 🌟 PHẦN 1: CHUẨN BỊ CHUNG (BẮT BUỘC)

Trước khi bắt đầu bất kỳ kịch bản nào, hãy đảm bảo:
1.  **Mạng:** Tất cả các máy (Thật + Ảo) phải Ping thấy nhau.
2.  **Tường lửa (Firewall):** TẮT Firewall trên máy chạy Server (Windows) để các máy khác kết nối được vào cổng 8080/8081.
3.  **Code:** Đã build ra file `.jar` (trong thư mục `RELEASE`) hoặc đã pull code mới nhất về các máy.
4.  **File Test:** Đã có sẵn file `video_test.mp4` (khoảng 100MB-500MB) trong thư mục `server_files` của Origin Server.

---

## 🏎️ KỊCH BẢN A: CẤU HÌNH CƠ BẢN (2 MÁY)
**Phù hợp:** Test tại nhà hoặc Demo nhanh.
*   **Máy 1 (Windows Host - IP: 192.168.1.10):** Chạy Server + Client A (Seeder).
*   **Máy 2 (Linux VM - IP: 192.168.1.11):** Chạy Client B (Leecher).

### **Giai đoạn 1: Khởi động Hệ thống**
1.  **Trên Windows:** Bật `p2p-tracker.jar` và `origin-server.jar`.
2.  **Trên Windows:** Mở trình duyệt vào `http://localhost:8081` (Admin Dashboard). Đăng nhập.
3.  **Trên Windows:** Mở `javafx-client.jar` (Client A).
4.  **Trên Linux VM:** Mở `javafx-client.jar` (Client B). *Lưu ý: Config của Client B phải trỏ về IP 192.168.1.10*.

### **Giai đoạn 2: Tạo Seeder (Nguồn P2P)**
1.  **Thao tác (Win):** Trên Client A, nhập tên file `video_test.mp4`. Bấm tải.
2.  **Quan sát:** Dashboard hiện 1 Peer.
3.  **Kết quả:** Client A tải xong 100%. Giờ đây Client A đang "Seeding" (Chia sẻ).

### **Giai đoạn 3: Demo P2P & Failover (Màn chính)**
1.  **Thao tác (Linux):** Trên Client B, nhập tên file `video_test.mp4`. Bấm tải.
2.  **Quan sát (Dashboard):** Thấy **2 Peer** đang kết nối. Biểu đồ Traffic tăng lên.
3.  **HÀNH ĐỘNG GAY CẤN:**
    *   Khi Client B tải được khoảng **30-40%**, bạn **TẮT CỬA SỔ ORIGIN SERVER** trên Windows (giả lập sập Server).
4.  **Kỳ vọng:**
    *   Client B có thể khựng lại 1 giây (đang timeout kết nối Server).
    *   Sau đó **THANH TIẾN TRÌNH VẪN CHẠY TIẾP!**
    *   Log Console của Client B hiện: `Downloading piece from Peer...`.
    *   **Kết luận:** Hệ thống tự động chuyển sang hút dữ liệu từ Client A. -> **Demo thành công!**

---

## 🚀 KỊCH BẢN B: CẤU HÌNH NÂNG CAO (4 MÁY - THE FULL SHOW)
**Phù hợp:** Bảo vệ đồ án chính thức, gây ấn tượng mạnh.
*   **Máy 1 (Win Host - .1.10):** MASTER SERVER + Client A (Seeder 1).
*   **Máy 2 (Linux VM 1 - .1.11):** Client B (Seeder 2).
*   **Máy 3 (MacBook - .1.20):** Client C (Streamer).
*   **Máy 4 (Linux VM 2 - .1.21):** Client D (Super Leecher).

### **Bước 1: Thiết lập mạng lưới "Bầy đàn" (Swarm)**
1.  Khởi động Server trên Máy 1 (Win Host).
2.  **Máy 1 (Client A):** Tải file xong 100%. -> **Seeder 1 Ready.**
3.  **Máy 2 (Client B):** Tải file xong 100%. -> **Seeder 2 Ready.**
    *   *Giải thích:* Bây giờ chúng ta có 3 nguồn tải: 1 Server + 2 Seeder (A & B).

### **Bước 2: Demo Streaming (Trên MacBook)**
1.  **Thao tác (Mac):** Mở Client C. Nhập file `video_test.mp4`.
2.  **Hành động:** Thay vì bấm "Download", bấm nút **"▶ PLAY"** (hoặc Download rồi bấm Play ngay).
3.  **Kỳ vọng:**
    *   VLC Player bật lên.
    *   Video phát ngay lập tức (chỉ sau 3-5s buffer).
    *   Kéo tua (Seek) đến giữa video -> Video nhảy vèo cái đến đó và phát tiếp.
    *   **Kết luận:** Tính năng Smart Streaming & On-demand Download hoạt động hoàn hảo trên macOS.

### **Bước 3: Demo Tốc độ Tối đa (Max Speed)**
1.  **Thao tác (Linux VM 2):** Mở Client D. Bấm tải file.
2.  **Quan sát (Dashboard trên Máy 1):**
    *   Số lượng Peers: **4 Active Peers**.
    *   Biểu đồ Traffic: Nhảy vọt lên cao nhất.
3.  **Quan sát (Log Client D):**
    *   Thấy log kết nối tới cả `.1.10`, `.1.11`, `.1.20` và Origin.
    *   Tốc độ tải (Download Speed) của Client D sẽ nhanh hơn hẳn so với lúc chỉ tải từ 1 nguồn.
4.  **Kết luận:** Chứng minh sức mạnh của Tải đa nguồn (Multi-source) trong môi trường phân tán thực tế.

---

## ✅ CHECKLIST TRƯỚC GIỜ G

| Hạng mục | Trạng thái | Ghi chú |
| :--- | :---: | :--- |
| **IP Config** | [ ] | Kiểm tra file `config.properties` của cả 4 Client đã trỏ về đúng IP Máy 1 chưa? |
| **Ping Test** | [ ] | Từ Linux/Mac ping thử về Windows xem có thông không? |
| **Files** | [ ] | File `video_test.mp4` đã nằm trong `server_files` chưa? |
| **Clean Up** | [ ] | Xóa sạch thư mục `Downloads` cũ trên các máy để test tải lại từ đầu cho trung thực. |
| **Tâm lý** | [ ] | Tự tin, nắm chắc kiến thức: HTTP Range, P2P, Failover. |

---
**Chúc bạn có buổi Demo thành công rực rỡ!**
