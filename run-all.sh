#!/bin/bash

# Script to run all services for multi-source-downloader

echo "🚀 Starting Multi-Source Downloader System..."
echo ""

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Get the project directory
PROJECT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$PROJECT_DIR"

# Function to kill processes on exit
cleanup() {
    echo ""
    echo "${YELLOW}Shutting down all services...${NC}"
    kill $(jobs -p) 2>/dev/null
    exit
}

trap cleanup SIGINT SIGTERM

# Start Origin Server
echo "${BLUE}[1/3] Starting Origin Server (port 8443)...${NC}"
mvn spring-boot:run -pl origin-server > logs/origin-server.log 2>&1 &
ORIGIN_PID=$!
echo "  ✓ Origin Server PID: $ORIGIN_PID"

# Wait for Origin Server to start
sleep 5

# Start P2P Tracker
echo "${BLUE}[2/3] Starting P2P Tracker (port 8080)...${NC}"
mvn spring-boot:run -pl p2p-tracker > logs/p2p-tracker.log 2>&1 &
TRACKER_PID=$!
echo "  ✓ P2P Tracker PID: $TRACKER_PID"

# Wait for Tracker to start
sleep 3

# Start JavaFX Client
echo "${BLUE}[3/3] Starting JavaFX Client...${NC}"
mvn javafx:run -pl javafx-client &
CLIENT_PID=$!
echo "  ✓ JavaFX Client PID: $CLIENT_PID"

echo ""
echo "${GREEN}✅ All services started successfully!${NC}"
echo ""
echo "Services running:"
echo "  • Origin Server: https://localhost:8443"
echo "  • P2P Tracker:   http://localhost:8080"
echo "  • JavaFX Client: GUI Window"
echo ""
echo "Logs are saved in: $PROJECT_DIR/logs/"
echo ""
echo "Press Ctrl+C to stop all services"

# Wait for all background jobs
wait

