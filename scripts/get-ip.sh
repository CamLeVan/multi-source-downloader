#!/bin/bash
# Script để lấy IP address trên Linux/macOS

echo "═══════════════════════════════════════════════════════════════"
echo "Network Configuration Helper"
echo "═══════════════════════════════════════════════════════════════"
echo ""

# Detect OS
OS="$(uname -s)"
case "${OS}" in
    Linux*)     MACHINE=Linux;;
    Darwin*)    MACHINE=Mac;;
    *)          MACHINE="UNKNOWN:${OS}"
esac

echo "Operating System: $MACHINE"
echo ""

# Get IP addresses
echo "Available Network Interfaces:"
echo "───────────────────────────────────────────────────────────────"

if [ "$MACHINE" = "Mac" ]; then
    # macOS
    ifconfig | grep -E "^[a-z]|inet " | grep -v "127.0.0.1" | while read line; do
        if [[ $line =~ ^[a-z] ]]; then
            interface=$(echo $line | cut -d: -f1)
            echo ""
            echo "Interface: $interface"
        elif [[ $line =~ inet ]]; then
            ip=$(echo $line | awk '{print $2}')
            if [ "$ip" != "127.0.0.1" ]; then
                echo "  IP Address: $ip"
            fi
        fi
    done
else
    # Linux
    ip -4 addr show | grep -E "^[0-9]|inet " | while read line; do
        if [[ $line =~ ^[0-9] ]]; then
            interface=$(echo $line | awk '{print $2}' | sed 's/:$//')
            echo ""
            echo "Interface: $interface"
        elif [[ $line =~ inet ]]; then
            ip=$(echo $line | awk '{print $2}' | cut -d/ -f1)
            if [ "$ip" != "127.0.0.1" ]; then
                echo "  IP Address: $ip"
            fi
        fi
    done
fi

echo ""
echo "═══════════════════════════════════════════════════════════════"
echo "Recommended IP (first non-loopback):"
echo "───────────────────────────────────────────────────────────────"

if [ "$MACHINE" = "Mac" ]; then
    IP=$(ifconfig | grep "inet " | grep -v "127.0.0.1" | head -1 | awk '{print $2}')
else
    IP=$(hostname -I | awk '{print $1}')
fi

if [ -z "$IP" ]; then
    IP=$(ip route get 8.8.8.8 2>/dev/null | awk '{print $7}' | head -1)
fi

echo "IP Address: $IP"
echo ""
echo "Use this IP in your config.properties files:"
echo "  tracker.url=http://$IP:8081"
echo "  origin.server.url=https://$IP:8443"
echo "═══════════════════════════════════════════════════════════════"

