#!/bin/bash
# soak_time.sh - Time-based memory soak test for DayDreamer

echo "Starting DayDreamer time-based memory soak test..."
PACKAGE="im.skn.daydreamerquoth"
LOGFILE="memory_soak_time_test_$(date +%Y%m%d_%H%M%S).csv"
ADB_PATH="/Users/srijith/tools/android/platform-tools"
DEVICE=$1
TIME_MINUTES=$2

if [ -z "$DEVICE" ] || [ -z "$TIME_MINUTES" ]; then
    echo "Usage: $0 <device_id> <time_in_minutes>"
    echo "Example: $0 emulator-5554 30"
    exit 1
fi

echo "Device: $DEVICE"
echo "Test duration: $TIME_MINUTES minutes"

# Calculate end time
START_TIME=$(date +%s)
END_TIME=$((START_TIME + TIME_MINUTES * 60))

# CSV header
echo "timestamp,pss_kb,private_dirty_kb,heap_alloc_kb,elapsed_minutes" > $LOGFILE

# Function to get memory stats
get_memory() {
    $ADB_PATH/adb -s $DEVICE shell dumpsys meminfo $PACKAGE 2>/dev/null | grep -E "TOTAL PSS:" | awk '{print $3}' | tr -d ','
}

# Function to start screensaver
start_screensaver() {
    echo "Starting screensaver..."
    $ADB_PATH/adb -s $DEVICE shell am start -n "com.android.systemui/.Somnambulator"
}

# Function to check if DayDreamer is running
check_dream_running() {
    PID=$($ADB_PATH/adb -s $DEVICE shell pidof $PACKAGE)
    if [ ! -z "$PID" ]; then
        return 0
    else
        return 1
    fi
}

# Setup: Ensure DayDreamer is set as default screensaver
echo "Setting DayDreamer as default screensaver..."
$ADB_PATH/adb -s $DEVICE shell settings put secure screensaver_components $PACKAGE/.DayDreamerQuoth
$ADB_PATH/adb -s $DEVICE shell settings put secure screensaver_enabled 1

# Verify settings
CURRENT_SCREENSAVER=$($ADB_PATH/adb -s $DEVICE shell settings get secure screensaver_components)
echo "Current screensaver: $CURRENT_SCREENSAVER"

# Start screensaver and keep it running
echo "Starting continuous screensaver test..."
start_screensaver
sleep 5

if check_dream_running; then
    echo "✅ SUCCESS: DayDreamer launched successfully"
else
    echo "❌ ERROR: DayDreamer failed to launch"
    exit 1
fi

echo "Test running for $TIME_MINUTES minutes..."
echo "Memory sampling every 30 seconds..."

# Main monitoring loop
SAMPLE_COUNT=0
while [ $(date +%s) -lt $END_TIME ]; do
    CURRENT_TIME=$(date +%s)
    ELAPSED_MINUTES=$(((CURRENT_TIME - START_TIME) / 60))
    
    # Check if still running
    if check_dream_running; then
        MEMORY=$(get_memory)
        TIMESTAMP=$(date +%s)
        echo "$TIMESTAMP,$MEMORY,,$ELAPSED_MINUTES" >> $LOGFILE
        
        SAMPLE_COUNT=$((SAMPLE_COUNT + 1))
        echo "$(date '+%H:%M:%S') - Sample $SAMPLE_COUNT (${ELAPSED_MINUTES}m): ${MEMORY}KB"
        
        # Progress update every 5 minutes
        if [ $((ELAPSED_MINUTES % 5)) -eq 0 ] && [ $ELAPSED_MINUTES -gt 0 ]; then
            REMAINING_MINUTES=$((TIME_MINUTES - ELAPSED_MINUTES))
            echo "--- $ELAPSED_MINUTES/$TIME_MINUTES minutes elapsed ($REMAINING_MINUTES minutes remaining) ---"
        fi
    else
        echo "$(date '+%H:%M:%S') - WARNING: DayDreamer stopped running, restarting..."
        start_screensaver
        sleep 5
        
        if ! check_dream_running; then
            echo "❌ ERROR: Failed to restart DayDreamer"
            break
        fi
    fi
    
    # Sample every 30 seconds
    sleep 30
done

# Stop screensaver
echo "Test duration completed, stopping screensaver..."
$ADB_PATH/adb -s $DEVICE shell input keyevent KEYCODE_HOME
sleep 2

echo "✅ Test complete!"
echo "Results saved to: $LOGFILE"
echo "Total samples collected: $SAMPLE_COUNT"
echo ""

# Analysis
echo "Quick analysis:"
echo "Memory usage over time (last 10 samples):"
grep -v "NOTRUNNING" $LOGFILE | tail -10 | while read line; do
    MEMORY=$(echo $line | cut -d',' -f2)
    ELAPSED=$(echo $line | cut -d',' -f5)
    echo "  ${ELAPSED}m: ${MEMORY}KB"
done

# Calculate memory trend
FIRST_MEMORY=$(grep -v "NOTRUNNING" $LOGFILE | head -2 | tail -1 | cut -d',' -f2)
LAST_MEMORY=$(grep -v "NOTRUNNING" $LOGFILE | tail -1 | cut -d',' -f2)

if [ ! -z "$FIRST_MEMORY" ] && [ ! -z "$LAST_MEMORY" ]; then
    MEMORY_CHANGE=$((LAST_MEMORY - FIRST_MEMORY))
    echo ""
    echo "Memory analysis:"
    echo "  First measurement: ${FIRST_MEMORY}KB"
    echo "  Last measurement:  ${LAST_MEMORY}KB"
    echo "  Total change:      ${MEMORY_CHANGE}KB"
    echo "  Test duration:     ${TIME_MINUTES} minutes"
    
    # Calculate rate of change per hour
    CHANGE_PER_HOUR=$((MEMORY_CHANGE * 60 / TIME_MINUTES))
    echo "  Rate of change:    ${CHANGE_PER_HOUR}KB/hour"

    if [ $MEMORY_CHANGE -gt 10000 ]; then
        echo "  ⚠️  WARNING: Memory increased by >10MB - significant leak detected"
    elif [ $MEMORY_CHANGE -gt 5000 ]; then
        echo "  ⚠️  CAUTION: Memory increased by >5MB - possible leak"
    elif [ $MEMORY_CHANGE -gt 1000 ]; then
        echo "  ⚠️  MONITOR: Memory increased by >1MB - watch closely"
    else
        echo "  ✅ GOOD: Memory change within acceptable range"
    fi
    
    # Hourly projection
    if [ $TIME_MINUTES -ge 60 ]; then
        echo "  📊 Projected daily increase: $((CHANGE_PER_HOUR * 24))KB"
    fi
fi