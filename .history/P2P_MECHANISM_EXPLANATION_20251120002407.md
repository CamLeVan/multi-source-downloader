# Cơ Chế P2P Trong Dự Án Multi-Source Downloader

## 📋 Tổng Quan

Dự án sử dụng mô hình **P2P (Peer-to-Peer)** kết hợp với **Client-Server** để tải file phân mảnh. P2P giúp:
- **Giảm tải cho Origin Server**: Peers chia sẻ pieces với nhau thay vì tất cả đều tải từ Origin
- **Tăng tốc độ download**: Nhiều nguồn đồng thời (Origin + Mirror + Peers)
- **Tự động fallback**: Nếu Origin/Mirror lỗi, tự động chuyển sang peers

---

## 🔄 Trình Tự Hoạt Động P2P

### **Bước 1: Khởi Động PeerServer** (Khi bắt đầu download)

```java
// MainApp.startDownload() - Dòng 176-199
if (peerServer == null) {
    peerServer = new PeerServer(stateStorage, pieceStorage, manifest, fileId, localFilePath);
    peerServer.start();
    peerPort = peerServer.getActualPort(); // Port 6881 (hoặc port khác nếu bị chiếm)
}
```

**Chức năng:**
- Tạo HTTP server lắng nghe trên port 6881 (mặc định)
- Nếu port bị chiếm, tự động thử các port 6882, 6883... đến 6890
- Xử lý requests từ peers khác tại endpoint `/piece/{fileId}/{pieceId}`
- Health check endpoint tại `/health`

**Kết quả:** Client sẵn sàng **phục vụ** pieces cho peers khác

---

### **Bước 2: Announce với Tracker** (Ngay sau khi PeerServer start)

```java
// MainApp.startDownload() - Dòng 185-193
boolean announced = trackerClient.announce(fileId, peerId, peerPort);
```

**Chức năng:**
- Gửi POST request đến Tracker: `POST /tracker/announce`
- Payload: `{"fileId": "100MB.zip", "peerId": "uuid", "port": 6881}`
- Tracker lưu thông tin: `Map<fileId, Set<ip:port>>`

**Kết quả:** Tracker biết client này đang tải file và có thể chia sẻ

---

### **Bước 3: Lấy Danh Sách Peers từ Tracker**

```java
// MainApp.startDownload() - Dòng 202-203
enrichManifestWithPeers(manifest, fileId);
```

**Chức năng:**
- Gửi GET request: `GET /tracker/peers?fileId=100MB.zip`
- Tracker trả về: `["192.168.1.100:6881", "192.168.1.101:6882", ...]`
- Thêm peer URLs vào **sources** của mỗi piece:
  ```
  Piece 0 sources:
    - http://origin:8080/files/100MB.zip (Origin)
    - http://mirror.vku.udn.vn/100MB.zip (Mirror)
    - http://192.168.1.100:6881/piece/100MB.zip/0 (Peer 1)
    - http://192.168.1.101:6882/piece/100MB.zip/0 (Peer 2)
  ```

**Kết quả:** Mỗi piece có nhiều nguồn để tải, bao gồm cả peers

---

### **Bước 4: Download với Fallback Logic**

```java
// OkHttpDownloadClient.tryPieceFromSource() - Dòng 48-74
private void tryPieceFromSource(PieceModel piece, int sourceIndex, ...) {
    String sourceUrl = piece.getSources().get(sourceIndex);
    // Thử download từ source này
    // Nếu fail → gọi tryPieceFromSource(piece, sourceIndex + 1, ...)
}
```

**Thứ tự thử:**
1. **Origin Server** (`http://origin:8080/files/100MB.zip`)
2. **Mirror Server** (`http://mirror.vku.udn.vn/100MB.zip`)
3. **Peer 1** (`http://192.168.1.100:6881/piece/100MB.zip/0`)
4. **Peer 2** (`http://192.168.1.101:6882/piece/100MB.zip/0`)
5. ... (các peers khác)

**Fallback logic:**
- Nếu Origin timeout/500 → thử Mirror
- Nếu Mirror 404 → thử Peer 1
- Nếu Peer 1 không có piece → thử Peer 2
- ... cho đến khi hết sources hoặc thành công

**Kết quả:** Download tự động chuyển sang peer nếu Origin/Mirror lỗi

---

### **Bước 5: Serve Pieces cho Peers Khác** (Luôn chạy)

```java
// PieceHandler.handle() - Dòng 35-111
public void handle(HttpExchange exchange) {
    // Parse: /piece/{fileId}/{pieceId}
    // Kiểm tra: File ID khớp? Piece đã download?
    if (state.isPieceCompleted(pieceIndex)) {
        byte[] data = pieceStorage.readPiece(localFilePath, offset, length);
        // Gửi piece cho peer
    }
}
```

**Khi nào serve:**
- Peer khác gửi request: `GET http://192.168.1.98:6881/piece/100MB.zip/5`
- `PieceHandler` kiểm tra:
  - ✅ File ID khớp
  - ✅ Piece đã được download (trong `DownloadState`)
- Nếu đủ điều kiện → đọc piece từ disk và gửi

**Kết quả:** Client chia sẻ pieces đã tải với peers khác

---

### **Bước 6: Background Peer Refresh** (Mỗi 30 giây)

```java
// MainApp.startPeerRefreshThread() - Dòng 389-410
Thread refreshThread = new Thread(() -> {
    while (!Thread.currentThread().isInterrupted()) {
        Thread.sleep(30000); // 30 giây
        trackerClient.announce(fileId, peerId, peerServer.getActualPort());
    }
});
```

**Chức năng:**
- Re-announce với Tracker mỗi 30 giây
- Đảm bảo Tracker biết client vẫn đang online
- Cập nhật danh sách peers mới (nếu có)

**Kết quả:** Danh sách peers luôn được cập nhật

---

## 🎯 Tóm Tắt: P2P Hoạt Động Khi Nào?

| **Hành Động** | **Khi Nào** | **Mục Đích** |
|---------------|------------|--------------|
| **Khởi động PeerServer** | Khi bắt đầu download | Sẵn sàng phục vụ pieces cho peers |
| **Announce với Tracker** | Khi bắt đầu download + mỗi 30s | Thông báo đang tải file |
| **Lấy danh sách peers** | Khi bắt đầu download | Thêm peers vào sources của pieces |
| **Download từ peers** | Khi Origin/Mirror lỗi hoặc không khả dụng | Fallback sang peers |
| **Serve pieces cho peers** | Khi peer khác request (luôn chạy) | Chia sẻ pieces đã tải |
| **Refresh peer list** | Mỗi 30 giây (background) | Cập nhật danh sách peers mới |

---

## 🔍 Chi Tiết Kỹ Thuật

### **1. Peer URL Format**
```
http://{peerIP}:{peerPort}/piece/{fileId}/{pieceId}
```
Ví dụ: `http://192.168.1.100:6881/piece/100MB.zip/5`

### **2. Tracker API**
- **Announce**: `POST /tracker/announce`
  ```json
  {
    "fileId": "100MB.zip",
    "peerId": "uuid-here",
    "port": 6881
  }
  ```
- **Get Peers**: `GET /tracker/peers?fileId=100MB.zip`
  ```json
  ["192.168.1.100:6881", "192.168.1.101:6882"]
  ```

### **3. PieceHandler Logic**
```java
if (state.isPieceCompleted(pieceIndex)) {
    // Serve piece
} else {
    // Return 404 - Piece chưa được download
}
```

### **4. Fallback trong OkHttpDownloadClient**
- **Retry với exponential backoff** cho cùng một source (1s, 2s, 4s, 8s, 16s)
- **Fallback sang source tiếp theo** nếu hết retries hoặc lỗi không thể retry (404, 416)

---

## 💡 Lợi Ích của P2P

1. **Giảm tải Origin Server**: Peers chia sẻ với nhau thay vì tất cả đều tải từ Origin
2. **Tăng tốc độ**: Nhiều nguồn đồng thời → download nhanh hơn
3. **Tự động failover**: Nếu Origin down, vẫn có thể tải từ peers
4. **Scalability**: Càng nhiều peers → càng nhiều nguồn → download càng nhanh

---

## ⚠️ Lưu Ý

1. **Peer chỉ serve pieces đã download**: Nếu piece chưa tải xong, peer sẽ trả về 404
2. **File ID phải khớp**: Peer chỉ serve pieces của cùng một file
3. **Port conflict**: Nếu port 6881 bị chiếm, tự động thử port khác (6882-6890)
4. **Network connectivity**: Peers phải có thể kết nối với nhau (cùng mạng hoặc public IP)

---

## 📊 Flow Diagram

```
Client A                    Tracker                    Client B
   |                          |                          |
   |---[1. Announce]--------->|                          |
   |                          |                          |
   |<--[2. Get Peers]---------|                          |
   |                          |                          |
   |                          |<---[3. Announce]---------|
   |                          |                          |
   |---[4. Download Piece 0]--------------------------->|
   |                          |                          |
   |<--[5. Piece 0 Data]--------------------------------|
   |                          |                          |
   |---[6. Download Piece 1 from Origin]               |
   |                          |                          |
   |---[7. Download Piece 2 from Peer B]--------------->|
   |                          |                          |
   |<--[8. Piece 2 Data]--------------------------------|
```

**Chú thích:**
- [1-2]: Client A announce và lấy danh sách peers
- [3]: Client B cũng announce
- [4-5]: Client A tải piece từ Client B
- [6]: Client A tải piece từ Origin
- [7-8]: Client A tải piece khác từ Client B

---

## 🎬 Kết Luận

**P2P hoạt động ngay từ khi bắt đầu download**, không phải đợi đến khi có lỗi. Cơ chế này đảm bảo:
- ✅ Peers được discover ngay từ đầu
- ✅ Fallback tự động khi Origin/Mirror lỗi
- ✅ Chia sẻ pieces với peers khác ngay khi có thể
- ✅ Danh sách peers được refresh định kỳ

