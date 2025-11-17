# Hướng Dẫn Setup Chi Tiết

## 📦 Yêu Cầu Hệ Thống

### Minimum Requirements
- **Java**: JDK 21 hoặc cao hơn
- **Maven**: 3.6.0 hoặc cao hơn
- **RAM**: Tối thiểu 2GB (khuyến nghị 4GB+)
- **Disk**: 5GB trống cho build và dependencies

### Platform Specific
- **macOS**: Cần macFUSE cho VirtualFS
- **Linux**: Cần FUSE library
- **Windows**: Không hỗ trợ VirtualFS, các tính năng khác hoạt động bình thường

## 🔧 Cài Đặt Dependencies

### 1. Java 21

**macOS (Homebrew):**
```bash
brew install openjdk@21
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

**Linux (Ubuntu/Debian):**
```bash
sudo apt update
sudo apt install openjdk-21-jdk
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
```

**Windows:**
- Download từ [Adoptium](https://adoptium.net/)
- Cài đặt và set JAVA_HOME

**Verify:**
```bash
java -version
# Should show: openjdk version "21"...
```

### 2. Maven

**macOS (Homebrew):**
```bash
brew install maven
```

**Linux:**
```bash
sudo apt install maven
```

**Windows:**
- Download từ [Maven website](https://maven.apache.org/)
- Add vào PATH

**Verify:**
```bash
mvn -version
```

### 3. macFUSE (chỉ macOS, cho VirtualFS)

```bash
brew install macfuse
```

**Verify:**
```bash
ls /usr/local/lib/libfuse.dylib
```

## 📥 Clone và Build Project

### Bước 1: Clone Repository

```bash
git clone <repository-url>
cd multi-source-downloader
```

### Bước 2: Build Project

```bash
# Build tất cả modules
mvn clean install

# Nếu có lỗi test, skip tests:
mvn clean install -DskipTests
```

**Thời gian build:** ~2-5 phút (lần đầu tiên)

**Output:**
- JAR files trong `target/` của mỗi module
- Dependencies được download vào `~/.m2/repository/`

## 🖥️ Setup Origin Server

### Bước 1: Tạo SSL Certificate

```bash
cd origin-server/src/main/resources

# Tạo keystore
keytool -genkeypair \
  -alias origin-server \
  -keyalg RSA \
  -keysize 2048 \
  -storetype PKCS12 \
  -keystore keystore.p12 \
  -validity 365 \
  -storepass changeit \
  -keypass changeit \
  -dname "CN=localhost, OU=Development, O=Company, L=City, ST=State, C=VN"
```

**Lưu ý:** Password mặc định là `changeit` (có thể đổi trong `application.properties`)

### Bước 2: Chuẩn bị Files

```bash
# Tạo thư mục
mkdir -p origin-server/server_files

# Copy file cần serve vào đây
cp /path/to/your/file.zip origin-server/server_files/
```

**Lưu ý:** Server tự động tạo file test 100MB nếu chưa có

### Bước 3: Chạy Server

```bash
cd origin-server
mvn spring-boot:run
```

**Hoặc build JAR và chạy:**
```bash
mvn clean package
java -jar target/origin-server-1.0-SNAPSHOT.jar
```

**Verify:**
```bash
# Test manifest endpoint
curl -k https://localhost:8443/manifest/100MB.zip

# Test file endpoint
curl -k -H "Range: bytes=0-1023" https://localhost:8443/files/100MB.zip
```

## 🎯 Setup P2P Tracker

### Chạy Tracker

```bash
cd p2p-tracker
mvn spring-boot:run
```

**Hoặc build JAR:**
```bash
mvn clean package
java -jar target/p2p-tracker-1.0-SNAPSHOT.jar
```

**Verify:**
```bash
# Test tracker
curl http://localhost:8081/tracker/peers?fileId=test
# Should return: []
```

**Cấu hình Port:**
Sửa `p2p-tracker/src/main/resources/application.properties`:
```properties
server.port=8081
```

## 🔄 Setup Mirror Server (Optional)

Xem chi tiết tại: `mirror-server/README.md`

**Quick setup với Python:**
```bash
cd origin-server/server_files
python3 -m http.server 8080
```

**Quick setup với Nginx:**
```bash
sudo apt install nginx
sudo cp origin-server/server_files/* /var/www/html/
sudo systemctl start nginx
```

## 💻 Setup Client

### Bước 1: Cấu hình Tracker URL

Sửa `javafx-client/src/main/java/com/fragmented/download/javafx/MainApp.java`:

```java
// Nếu Tracker chạy trên máy khác
private static final String TRACKER_URL = "http://192.168.1.50:8081";
```

### Bước 2: Chạy Client

```bash
cd javafx-client
mvn javafx:run
```

**Hoặc build JAR:**
```bash
mvn clean package
java -jar target/javafx-client-1.0-SNAPSHOT.jar
```

**Lưu ý:** 
- Trên macOS/Linux, VirtualFS sẽ tự động mount tại `~/downloader-vfs/`
- Trên Windows, VirtualFS sẽ không hoạt động (warning message)

## 🌐 Network Configuration

### LAN Setup

**Để các máy giao tiếp được:**

1. **Kiểm tra IP addresses:**
   ```bash
   # macOS/Linux
   ifconfig
   
   # Windows
   ipconfig
   ```

2. **Cấu hình Tracker URL trên Client:**
   ```java
   // Thay localhost bằng IP của máy chạy Tracker
   private static final String TRACKER_URL = "http://192.168.1.50:8081";
   ```

3. **Cấu hình Origin Server URL trong Manifest:**
   - Sửa `origin-server/src/main/java/com/fragmented/download/backend/util/ManifestGeneratorUtil.java`
   - Thay `localhost` bằng IP thực tế

4. **Firewall:**
   ```bash
   # Linux
   sudo ufw allow 8081/tcp  # Tracker
   sudo ufw allow 8443/tcp  # Origin Server
   sudo ufw allow 6881/tcp  # Peer Server
   
   # macOS
   # Mở System Preferences → Security → Firewall
   # Allow incoming connections cho Java
   ```

## ✅ Verification Checklist

Sau khi setup, kiểm tra:

- [ ] Origin Server chạy và respond tại `https://localhost:8443`
- [ ] P2P Tracker chạy và respond tại `http://localhost:8081`
- [ ] Client có thể kết nối đến Tracker
- [ ] Client có thể download manifest từ Origin Server
- [ ] VirtualFS mount thành công (macOS/Linux)
- [ ] Tất cả máy trong cùng network có thể ping nhau

## 🐛 Troubleshooting

### Lỗi: "Port already in use"

```bash
# Tìm process đang dùng port
lsof -i :8081  # macOS/Linux
netstat -ano | findstr :8081  # Windows

# Kill process
kill -9 <PID>
```

### Lỗi: "Cannot connect to tracker"

- Kiểm tra Tracker đang chạy
- Kiểm tra firewall
- Kiểm tra IP address trong TRACKER_URL
- Test với curl: `curl http://TRACKER_IP:8081/tracker/peers?fileId=test`

### Lỗi: "VirtualFS mount failed"

**macOS:**
```bash
# Cài đặt macFUSE
brew install macfuse

# Restart máy sau khi cài
```

**Linux:**
```bash
sudo apt install fuse libfuse2
```

### Lỗi: "SSL certificate error"

- Origin Server dùng self-signed certificate
- Client cần trust certificate hoặc disable SSL verification
- Hoặc tạo certificate hợp lệ

## 📝 Notes

- **First run:** Maven sẽ download dependencies (~200MB), mất vài phút
- **Build time:** ~2-5 phút cho lần đầu, ~30s cho các lần sau
- **Memory:** Mỗi server cần ~200-500MB RAM
- **Network:** Tốt nhất chạy trong LAN để demo P2P

