# Mirror Server Setup Guide

## Mục đích
Mirror Server là một bản sao của Origin Server, phục vụ file qua HTTP Range để hỗ trợ multi-source download.

## Cách Setup

### Option 1: Sử dụng Nginx (Khuyến nghị cho production)

1. Cài đặt Nginx trên VM Linux:
```bash
sudo apt update
sudo apt install nginx
```

2. Copy file cần mirror vào thư mục Nginx:
```bash
sudo cp /path/to/your/file.zip /var/www/html/
```

3. Cấu hình Nginx để hỗ trợ HTTP Range:
Tạo file `/etc/nginx/sites-available/mirror`:
```nginx
server {
    listen 80;
    server_name mirror.vku.udn.vn;

    root /var/www/html;
    index index.html;

    location / {
        # Enable HTTP Range requests
        add_header Accept-Ranges bytes;
        
        # CORS headers (nếu cần)
        add_header Access-Control-Allow-Origin *;
        
        # Serve files
        try_files $uri $uri/ =404;
    }
}
```

4. Enable site và restart:
```bash
sudo ln -s /etc/nginx/sites-available/mirror /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl restart nginx
```

### Option 2: Sử dụng Spring Boot (Giống Origin Server)

1. Copy toàn bộ code từ `origin-server` module
2. Sửa port trong `application.properties`:
```properties
server.port=8080
```
3. Copy file vào thư mục `server_files/`
4. Chạy: `mvn spring-boot:run`

### Option 3: Sử dụng Python SimpleHTTPServer (Cho demo nhanh)

```bash
cd /path/to/files
python3 -m http.server 8080
```

**Lưu ý**: Python SimpleHTTPServer hỗ trợ HTTP Range requests từ Python 3.7+

## Kiểm tra Mirror Server

```bash
curl -I -H "Range: bytes=0-1023" http://mirror.vku.udn.vn/100MB.zip
```

Response phải có:
- Status: `206 Partial Content`
- Header: `Content-Range: bytes 0-1023/104857600`

## Cấu hình Client

Đảm bảo manifest JSON có mirror URL:
```json
{
  "sources": [
    "http://localhost:8443/files/100MB.zip",
    "http://mirror.vku.udn.vn/100MB.zip"
  ]
}
```

