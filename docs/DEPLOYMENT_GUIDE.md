# CẨM NANG TRIỂN KHAI HỆ THỐNG (DEPLOYMENT GUIDE)
**Dự án:** Multi-Source P2P Downloader  
**Mục tiêu:** Demo tính năng tải đa nguồn và P2P giữa nhiều máy tính thực và máy ảo.

---

## I. YÊU CẦU CƠ BẢN (PREREQUISITES)

### 1. Mạng (Networking) - QUAN TRỌNG NHẤT
Để các máy (Máy thật và Máy ảo) nhìn thấy nhau như các thiết bị độc lập trong cùng một mạng LAN, bạn **BẮT BUỘC** phải cấu hình Card mạng máy ảo ở chế độ **Bridged Client (Cầu nối)**.

*   **VirtualBox / VMware:**
    *   Vào `Settings` của máy ảo -> `Network`.
    *   Chọn `Attached to`: **Bridged Adapter**.
    *   Chọn đúng tên Card Wifi/LAN mà máy thật đang dùng để vào mạng.
*   **Kiểm tra thành công:**
    *   Máy thật IP: `192.168.1.10`
    *   Máy ảo IP: `192.168.1.11` (Cùng dải `192.168.1.x` là OK).
    *   Thử ping qua lại: Từ máy ảo gõ `ping 192.168.1.10`. Nếu thấy reply là thành công.

### 2. Phần mềm
*   **Java 17+ (JDK):** Phải cài trên TẤT CẢ các máy (Windows, Mac, Linux VM).
*   **Tường lửa (Firewall):**
    *   Tốt nhất là **TẮT Tường lửa** trên máy chạy Server (Windows Host) trong lúc Demo để tránh bị chặn port 8080/8081.
    *   Nếu không tắt, phải mở port: `8080`, `8081`, `6881`.

---

## II. QUY TRÌNH ĐÓNG GÓI (BUILD)
Thay vì chạy code source, chúng ta đóng gói thành file `.jar` chạy cho ổn định.

1.  Tại thư mục gốc dự án trên máy Windows chính, mở CMD:
    ```powershell
    mvn clean package -DskipTests
    ```
2.  Sau khi chạy xong, hãy gom các file sau vào 1 thư mục tên `DEMO_RELEASE`:
    *   `origin-server/target/origin-server-0.0.1-SNAPSHOT.jar`
    *   `p2p-tracker/target/p2p-tracker-0.0.1-SNAPSHOT.jar`
    *   `javafx-client/target/javafx-client-0.0.1-SNAPSHOT.jar`
    *   Copy thư mục `origin-server/server_files` để cạnh file jar server.

---

## III. KỊCH BẢN 1: TEST TẠI NHÀ (1 WINDOWS + 1 LINUX VM)

**Mô hình:**
*   **Máy Windows (Host):** Đóng vai trò TRÙM (Chạy Origin Server, Tracker, Admin Dashboard) và chạy Client A (Seeder).
*   **Máy Linux (VM):** Đóng vai trò Client B (Leecher).

### Bước 1: Lấy IP Máy Host
*   Trên Windows, mở CMD gõ: `ipconfig` -> Lấy IPv4 Wireless LAN (Ví dụ: **192.168.1.5**).
*   *Ghi nhớ IP này là SEVER_IP*.

### Bước 2: Cấu hình
*   Vì chúng ta đã đóng gói JAR, nên cách nhanh nhất để cấu hình lại IP là dùng tham số dòng lệnh khi chạy, HOẶC sửa file `config.properties` **TRƯỚC KHI BUILD**.
*   *Khuyến nghị:* Sửa file `config.properties` trong source code trỏ về `192.168.1.5` rồi Build lại JAR.

### Bước 3: Chạy Server (Trên Windows Host)
Mở 2 cửa sổ CMD:
1.  **Tracker:** `java -jar p2p-tracker-0.0.1-SNAPSHOT.jar`
2.  **Origin:** `java -jar origin-server-0.0.1-SNAPSHOT.jar`

### Bước 4: Chạy Client A (Trên Windows Host)
Mở CMD:
*   `java -jar javafx-client-0.0.1-SNAPSHOT.jar`
*   Vào giao diện, tải file `100MB.zip`. Đợi 100%. -> **Đã có Seeder 1**.

### Bước 5: Chạy Client B (Trên Linux VM)
*   Copy file `javafx-client-0.0.1-SNAPSHOT.jar` sang Linux.
*   Mở Terminal Linux:
    ```bash
    # Chạy client, lưu ý đổi Client Name và Bind Address về IP của máy Linux
    java -jar javafx-client-0.0.1-SNAPSHOT.jar
    ```
    *(Lưu ý: Nếu app JavaFX không lên hình trên Linux VM do thiếu thư viện đồ họa, bạn có thể cần cài `openjfx` hoặc demo Client B trên máy Windows thứ 2 nếu có).*

---

## IV. KỊCH BẢN 2: FINAL DEMO (2 MÁY THẬT + VMs)

**Mô hình:**
*   **Máy 1 (Windows - Của bạn):** SERVER MASTER. Chạy Origin, Tracker, Client A.
*   **Máy 2 (Mac - Của bạn bè):** CLIENT B.

### Bước 1: Xác định IP Server Master
*   Kết nối cả 2 máy vào cùng mạng Wifi (hoặc cắm dây LAN qua Switch).
*   Lấy IP máy Windows (Ví dụ: **192.168.1.100**).

### Bước 2: Chuẩn bị App cho máy Mac
*   Copy file `javafx-client-0.0.1-SNAPSHOT.jar` sang máy Mac.
*   **Quan trọng:** File Jar này phải được build với cấu hình `tracker.url=http://192.168.1.100:8081` (IP máy Windows).

### Bước 3: Triển khai
1.  **Trên máy Windows:**
    *   Bật Tracker Server & Origin Server.
    *   Bật Dashboard (`http://localhost:8081`).
    *   Bật Client A -> Tải file -> Để đó làm Seeder.

2.  **Trên máy Mac:**
    *   Mở Terminal.
    *   Chạy: `java -jar ipv4_prefer=true javafx-client-0.0.1-SNAPSHOT.jar`
    *   Nhập tên file và Tải.

3.  **Hành động Demo:**
    *   Quan sát Dashboard trên máy Windows: Thấy 2 Peer kết nối.
    *   Tốc độ tải trên máy Mac tăng lên.
    *   **Ngắt Origin Server trên Windows:** Máy Mac vẫn tải tiếp -> Vỗ tay!

---

## V. CHECKLIST XỬ LÝ SỰ CỐ (TROUBLESHOOTING)

1.  **Lỗi: Client B không kết nối được Tracker?**
    *   Check 1: IP Server trong config đúng chưa?
    *   Check 2: Máy Server (Windows) có tắt Firewall chưa?
    *   Check 3: Ping từ Client B sang Server có thông không?

2.  **Lỗi: P2P không chạy (Dashboard không thấy Peer, hoặc thấy mà không tải được)?**
    *   Nguyên nhân: Do NAT hoặc sai IP Bind.
    *   Khắc phục: Đảm bảo trong `config.properties` của Client, dòng `client.bind.address` phải được set đúng là IP LAN của máy đó (hoặc để `0.0.0.0` nếu mạng ổn).
    *   Với máy ảo: Bắt buộc chọn chế độ mạng **Bridged**.

3.  **Lỗi: JavaFX lỗi trên Linux?**
    *   Nếu máy ảo Linux chỉ là dòng lệnh (Server), app Client sẽ không chạy được giao diện.
    *   Giải pháp: Chỉ chạy Client trên máy thật (Windows/Mac) cho phần giao diện đẹp. Dùng máy ảo Linux để chạy Server (nếu muốn thể hiện trình độ Linux) hoặc bỏ qua máy ảo nếu máy thật đã đủ.

---
**Lời khuyên cuối:** Hãy in tờ hướng dẫn này ra giấy hoặc thuộc lòng IP của máy chủ. Cấm kỵ việc đến lớp mới lúi húi chỉnh file config!
