package com.fragmented.download.javafx.util;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

/**
 * Utility class để lấy local IP address
 */
public class NetworkUtil {

    /**
     * Lấy local IP address (không phải localhost)
     * 
     * @return IP address dạng string, hoặc "localhost" nếu không tìm thấy
     */
    public static String getLocalIPAddress() {
        try {
            // Vòng 1: Ưu tiên SiteLocalAddress (192.168.x.x, 10.x.x.x, 172.16-31.x.x)
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();

                // Bỏ qua loopback và disabled interfaces
                // CẬP NHẬT: Bỏ qua các interface ảo của VirtualBox/VMware/Docker
                if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                    continue;
                }

                String name = networkInterface.getDisplayName().toLowerCase();
                // Skip virtual adapters to find real LAN IP
                if (name.contains("virtual") || name.contains("wsl") || name.contains("docker")
                        || name.contains("vmware") || name.contains("vbox") || name.contains("pseudo")) {
                    continue;
                }

                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();

                    // Chỉ lấy IPv4 addresses
                    if (!address.isLoopbackAddress() &&
                            address.getAddress().length == 4) {

                        // Ưu tiên SiteLocalAddress cho mạng LAN
                        if (address.isSiteLocalAddress()) {
                            return address.getHostAddress();
                        }
                    }
                }
            }

            // Vòng 2: Fallback (nếu không tìm thấy SiteLocal, lấy IP đầu tiên hợp lệ)
            // Lưu ý: Vòng này vẫn nên tránh virtual nến possible, nhưng giữ simple như cũ
            // để đảm bảo có IP
            interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (networkInterface.isLoopback() || !networkInterface.isUp())
                    continue;

                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (!address.isLoopbackAddress() && address.getAddress().length == 4) {
                        return address.getHostAddress();
                    }
                }
            }

        } catch (SocketException e) {
            System.err.println("Error getting local IP: " + e.getMessage());
        }

        return "localhost";
    }

    /**
     * Lấy hostname của máy
     */
    public static String getHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
