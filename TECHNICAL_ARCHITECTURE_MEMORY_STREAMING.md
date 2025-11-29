# Giải Pháp Kỹ Thuật: Tối Ưu Hóa Bộ Nhớ & Streaming File Lớn

Tài liệu này trình bày chi tiết kiến trúc kỹ thuật giúp **Multi-Source Downloader** có thể tải và phát (streaming) các file kích thước cực lớn (10GB - 100GB) ngay trên các thiết bị có cấu hình hạn chế (RAM 4GB - 8GB) mà không gặp lỗi tràn bộ nhớ (Out of Memory).

---

## 1. Vấn Đề & Thách Thức

### Bài toán:
Làm thế nào để một máy tính có **8GB RAM** có thể:
1.  Tải một file video **20GB**.
2.  Vừa tải vừa xem (Streaming) file đó ngay lập tức.
3.  Hỗ trợ tua (Seek) đến bất kỳ đoạn nào của video.

### Rủi ro tiềm ẩn:
*   Nếu nạp toàn bộ file vào RAM -> **Tràn bộ nhớ (Crash ứng dụng).**
*   Nếu chờ tải xong mới ghi xuống đĩa -> **Rủi ro mất dữ liệu** khi mất điện và không thể xem ngay.

---

## 2. Giải Pháp Kiến Trúc: Disk-Based & Chunking

Hệ thống sử dụng cơ chế **"Chia nhỏ & Ghi trực tiếp" (Chunking & Direct Disk I/O)**.

### 2.1. Quản Lý Bộ Nhớ (Memory Management)

Thay vì coi file là một khối dữ liệu liền mạch 20GB, hệ thống chia file thành hàng nghìn mảnh nhỏ (**Pieces**), mỗi mảnh có kích thước cố định (ví dụ: **1MB**).

*   **Quy trình xử lý:**
    1.  **Tải về:** Worker tải 1 mảnh (1MB) vào RAM.
    2.  **Xác thực:** Tính mã băm SHA-256 để kiểm tra toàn vẹn.
    3.  **Ghi đĩa:** Ghi ngay lập tức mảnh 1MB đó xuống vị trí tương ứng trên ổ cứng (sử dụng `RandomAccessFile`).
    4.  **Giải phóng:** Xóa mảnh đó khỏi RAM (Garbage Collection).

*   **Công thức tính RAM tiêu thụ:**
    $$RAM_{usage} \approx (N_{threads} \times Size_{piece}) + RAM_{base}$$
    
    *Với 4 luồng tải song song:*
    $$RAM_{usage} \approx (4 \times 1MB) + 100MB \approx 104MB$$

    **Kết luận:** Dù file có kích thước **1TB**, ứng dụng vẫn chỉ tiêu tốn khoảng **100MB RAM**.

### 2.2. Cơ Chế Lưu Trữ (Sparse File Storage)

Sử dụng kỹ thuật **Sparse File** (Tập tin thưa) thông qua Java NIO (`FileChannel`).

*   Khi bắt đầu tải file 20GB, hệ thống tạo ngay một "khung" file rỗng 20GB trên đĩa cứng.
*   Việc này diễn ra tức thì và không chiếm dụng RAM để lưu các bit `0`.
*   Dữ liệu thật được ghi đè vào các vị trí cụ thể khi các mảnh được tải về.

---

## 3. Cơ Chế Streaming Thông Minh (On-Demand Priority)

Để hỗ trợ "Vừa xem vừa tải" và "Tua video", hệ thống sử dụng thuật toán điều phối ưu tiên (**Priority Scheduling**).

### Quy trình hoạt động khi người dùng xem video:

1.  **Yêu cầu (Request):** Người dùng mở video hoặc tua đến phút thứ 60. Trình phát (VLC/Browser) gửi yêu cầu HTTP Range: `Range: bytes=10737418240-` (Lấy dữ liệu từ byte thứ 10 Tỷ).
2.  **Ánh xạ (Mapping):** `LocalStreamingServer` tính toán: Byte thứ 10 Tỷ thuộc **Piece #10240**.
3.  **Kiểm tra (Check):** Hệ thống kiểm tra trạng thái Piece #10240 trên đĩa.
    *   *Nếu đã có:* Đọc từ đĩa -> Trả về ngay.
    *   *Nếu chưa có:* Chuyển sang bước 4.
4.  **Điều phối (Scheduling):**
    *   Server gửi tín hiệu **"Urgent" (Khẩn cấp)** tới `Scheduler`.
    *   `Scheduler` tạm dừng các luồng đang tải tuần tự (ví dụ đang tải Piece #50).
    *   Điều chuyển Worker chuyển sang tải ngay **Piece #10240** và các mảnh kế tiếp (#10241, #10242...).
5.  **Phản hồi (Response):** Ngay khi Piece #10240 tải xong, dữ liệu được stream về trình phát video.

---

## 4. Sơ Đồ Luồng Dữ Liệu (Data Flow)

```mermaid
graph LR
    A[Internet / Peers] -->|Download 1MB| B(RAM Buffer)
    B -->|Write| C[Hard Disk (20GB Sparse File)]
    B -.->|Clear| D[Garbage Collector]
    
    E[Video Player] -->|Request Range| F[Streaming Server]
    F -->|Read| C
    F -->|Stream| E
    
    F -.->|Missing Piece?| G[Scheduler]
    G -.->|Priority Download| A
```

## 5. Kết Luận

Kiến trúc này đảm bảo:
1.  **An toàn bộ nhớ:** Không bao giờ xảy ra tràn RAM (OOM), kể cả trên máy cấu hình thấp.
2.  **Trải nghiệm mượt mà:** Video được phát gần như ngay lập tức, hỗ trợ tua tự do.
3.  **Bền vững:** Dữ liệu được lưu liên tục xuống đĩa, cho phép resume (tải tiếp) bất cứ lúc nào.
