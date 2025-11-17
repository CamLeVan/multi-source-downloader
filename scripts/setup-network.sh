#!/bin/bash
# Script tự động setup network config cho Linux/macOS

echo "═══════════════════════════════════════════════════════════════"
echo "Auto Network Configuration Setup"
echo "═══════════════════════════════════════════════════════════════"
echo ""

# Get local IP
if [[ "$OSTYPE" == "darwin"* ]]; then
    # macOS
    LOCAL_IP=$(ifconfig | grep "inet " | grep -v "127.0.0.1" | head -1 | awk '{print $2}')
else
    # Linux
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

# Ask for role
echo "What is this machine's role?"
echo "1) Client (JavaFX Client)"
echo "2) Origin Server"
echo "3) P2P Tracker"
echo "4) Mirror Server"
read -p "Enter choice [1-4]: " role

case $role in
    1)
        echo ""
        echo "⚠️  IMPORTANT: Enter the IP addresses of OTHER machines"
        echo "   This machine's IP: $LOCAL_IP"
        echo ""
        read -p "Enter Tracker IP [default: $LOCAL_IP]: " TRACKER_IP
        TRACKER_IP=${TRACKER_IP:-$LOCAL_IP}
        
        read -p "Enter Origin Server IP [default: $LOCAL_IP]: " ORIGIN_IP
        ORIGIN_IP=${ORIGIN_IP:-$LOCAL_IP}
        
        read -p "Enter Client Name [default: Client-1]: " CLIENT_NAME
        CLIENT_NAME=${CLIENT_NAME:-Client-1}
        
        echo ""
        echo "Configuration:"
        echo "  Tracker: http://$TRACKER_IP:8081"
        echo "  Origin:  https://$ORIGIN_IP:8443"
        echo "  Client:  $CLIENT_NAME ($LOCAL_IP)"
        read -p "Continue? [y/N]: " confirm
        if [ "$confirm" != "y" ] && [ "$confirm" != "Y" ]; then
            echo "Cancelled"
            exit 0
        fi
        
        CONFIG_FILE="../javafx-client/src/main/resources/config.properties"
        cat > "$CONFIG_FILE" << EOF
# Client Configuration
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
        ;;
    2)
        CONFIG_FILE="../origin-server/src/main/resources/config.properties"
        cat > "$CONFIG_FILE" << EOF
# Origin Server Configuration
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
        ;;
    3)
        CONFIG_FILE="../p2p-tracker/src/main/resources/config.properties"
        cat > "$CONFIG_FILE" << EOF
# P2P Tracker Configuration
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
        ;;
    4)
        echo ""
        echo "Mirror Server setup - see mirror-server/README.md"
        echo "For quick setup with Python:"
        echo "  cd origin-server/server_files"
        echo "  python3 -m http.server 8080"
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

