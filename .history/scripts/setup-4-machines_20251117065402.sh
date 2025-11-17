#!/bin/bash
# Script setup cho demo 4 máy (2 host + 2 VM)

echo "═══════════════════════════════════════════════════════════════"
echo "4-Machine Demo Setup"
echo "═══════════════════════════════════════════════════════════════"
echo ""

# Get local IP
if [[ "$OSTYPE" == "darwin"* ]]; then
    LOCAL_IP=$(ifconfig | grep "inet " | grep -v "127.0.0.1" | head -1 | awk '{print $2}')
else
    LOCAL_IP=$(hostname -I | awk '{print $1}')
fi

if [ -z "$LOCAL_IP" ]; then
    LOCAL_IP=$(ip route get 8.8.8.8 2>/dev/null | awk '{print $7}' | head -1)
fi

if [ -z "$LOCAL_IP" ]; then
    echo "ERROR: Could not detect IP address"
    exit 1
fi

echo "Detected Local IP: $LOCAL_IP"
echo ""
echo "═══════════════════════════════════════════════════════════════"
echo "4-Machine Demo Architecture:"
echo "  Machine A: Windows Host (Origin Server)"
echo "  Machine B: Ubuntu VM on Windows (P2P Tracker)"
echo "  Machine C: macOS Host (Client 1)"
echo "  Machine D: Ubuntu VM on macOS (Client 2)"
echo "═══════════════════════════════════════════════════════════════"
echo ""

# Ask for role
echo "What is this machine's role?"
echo "1) Client (JavaFX Client)"
echo "2) Origin Server (Windows Host)"
echo "3) P2P Tracker (Ubuntu VM on Windows)"
echo "4) Mirror Server"
read -p "Enter choice [1-4]: " role

case $role in
    1)
        echo ""
        echo "⚠️  Enter IP addresses of OTHER machines:"
        echo "   This machine's IP: $LOCAL_IP"
        echo ""
        echo "Expected setup:"
        echo "  - Windows Host (Origin): Usually 192.168.1.101"
        echo "  - Ubuntu VM on Windows (Tracker): Usually 192.168.1.102"
        echo ""
        read -p "Enter Tracker IP [default: 192.168.1.102]: " TRACKER_IP
        TRACKER_IP=${TRACKER_IP:-192.168.1.102}
        
        read -p "Enter Origin Server IP [default: 192.168.1.101]: " ORIGIN_IP
        ORIGIN_IP=${ORIGIN_IP:-192.168.1.101}
        
        read -p "Enter Client Name [default: Client-1]: " CLIENT_NAME
        CLIENT_NAME=${CLIENT_NAME:-Client-1}
        
        echo ""
        echo "Configuration:"
        echo "  This machine: $CLIENT_NAME ($LOCAL_IP)"
        echo "  Tracker: http://$TRACKER_IP:8081"
        echo "  Origin:  https://$ORIGIN_IP:8443"
        read -p "Continue? [y/N]: " confirm
        if [ "$confirm" != "y" ] && [ "$confirm" != "Y" ]; then
            echo "Cancelled"
            exit 0
        fi
        
        CONFIG_FILE="../javafx-client/src/main/resources/config.properties"
        cat > "$CONFIG_FILE" << EOF
# Client Configuration - 4-Machine Demo
tracker.url=http://$TRACKER_IP:8081
origin.server.url=https://$ORIGIN_IP:8443
client.name=$CLIENT_NAME

# Network Configuration
client.bind.address=0.0.0.0
peer.server.port=6881

# Logging
log.level=INFO
log.show.ip=true
log.show.flow=true
EOF
        echo ""
        echo "✓ Client config saved to: $CONFIG_FILE"
        echo ""
        echo "Next steps:"
        echo "  1. Start Origin Server on Windows Host ($ORIGIN_IP)"
        echo "  2. Start P2P Tracker on Ubuntu VM ($TRACKER_IP)"
        echo "  3. Run: cd javafx-client && mvn javafx:run"
        ;;
    2)
        CONFIG_FILE="../origin-server/src/main/resources/config.properties"
        cat > "$CONFIG_FILE" << EOF
# Origin Server Configuration - 4-Machine Demo
server.host=$LOCAL_IP
server.port=8443
server.name=Origin-Server

# File Server
file.directory=server_files

# Logging
log.level=INFO
log.show.ip=true
log.show.flow=true
EOF
        echo ""
        echo "✓ Origin Server config saved to: $CONFIG_FILE"
        echo "  Server will run on: https://$LOCAL_IP:8443"
        echo ""
        echo "Next steps:"
        echo "  1. Run: cd origin-server && mvn spring-boot:run"
        echo "  2. Make sure firewall allows port 8443"
        ;;
    3)
        CONFIG_FILE="../p2p-tracker/src/main/resources/config.properties"
        cat > "$CONFIG_FILE" << EOF
# P2P Tracker Configuration - 4-Machine Demo
server.host=$LOCAL_IP
server.port=8081
tracker.name=P2P-Tracker

# Logging
log.level=INFO
log.show.ip=true
log.show.flow=true
EOF
        echo ""
        echo "✓ P2P Tracker config saved to: $CONFIG_FILE"
        echo "  Tracker will run on: http://$LOCAL_IP:8081"
        echo ""
        echo "Next steps:"
        echo "  1. Run: cd p2p-tracker && mvn spring-boot:run"
        echo "  2. Make sure firewall allows port 8081"
        echo "  3. Verify VM network is in Bridged mode"
        ;;
    4)
        echo ""
        echo "Mirror Server setup - see mirror-server/README.md"
        ;;
    *)
        echo "Invalid choice"
        exit 1
        ;;
esac

echo ""
echo "═══════════════════════════════════════════════════════════════"
echo "Configuration complete!"
echo "═══════════════════════════════════════════════════════════════"

