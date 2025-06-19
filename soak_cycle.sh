# soak_test_somnambulator.sh

  echo "Starting DayDreamer memory soak test using Somnambulator..."
  PACKAGE="im.skn.daydreamerquoth"
  LOGFILE="memory_soak_test_$(date +%Y%m%d_%H%M%S).csv"
  ADB_PATH="/Users/srijith/tools/android/platform-tools"
  DEVICE=$1

  echo "Device: $DEVICE"


  # CSV header
  echo "timestamp,pss_kb,private_dirty_kb,heap_alloc_kb,cycle" > $LOGFILE

  # Function to get memory stats
  get_memory() {
      $ADB_PATH/adb -s $DEVICE shell dumpsys meminfo $PACKAGE 2>/dev/null | grep -E "TOTAL PSS:" | awk '{print $3}' | tr -d ','
  }

  # Function to start screensaver
  start_screensaver() {
      echo "Starting screensaver..."
      $ADB_PATH/adb -s $DEVICE shell am start -n "com.android.systemui/.Somnambulator"
  }

  # Function to stop screensaver
  stop_screensaver() {
      echo "Stopping screensaver..."
      $ADB_PATH/adb -s $DEVICE shell input keyevent KEYCODE_HOME
      # Alternative: adb shell input keyevent KEYCODE_BACK
  }

  # Function to check if DayDreamer is running
  check_dream_running() {
      PID=$($ADB_PATH/adb -s $DEVICE shell pidof $PACKAGE)
      if [ ! -z "$PID" ]; then
          echo "DayDreamer running (PID: $PID)"
          return 0
      else
          echo "DayDreamer not running"
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

  # Test launch once
  echo "Testing screensaver launch..."
  start_screensaver
  sleep 5

  if check_dream_running; then
      echo "✅ SUCCESS: DayDreamer launched successfully"
  else
      echo "❌ WARNING: DayDreamer not detected, but continuing test..."
  fi

  stop_screensaver
  sleep 3

  echo "Starting memory soak test..."
  #echo "Will run for $(($1 > 0 ? $1 : 100)) cycles"

  # Main test loop
  CYCLES=${2:-1000}  # Default 100 cycles, or pass as argument
  for i in $(seq 1 $CYCLES); do
      echo "=== Cycle $i/$CYCLES ==="

      # Start screensaver
      start_screensaver
      sleep 5  # Let it fully initialize and load quotes

      # Collect memory stats if service is running
      TIMESTAMP=$(date +%s)
      if check_dream_running; then
          MEMORY=$(get_memory)
          echo "$TIMESTAMP,$MEMORY,,$i" >> $LOGFILE
          echo "Memory: ${MEMORY}KB"

          # Also log to console with human-readable time
          echo "$(date '+%H:%M:%S') - Cycle $i: ${MEMORY}KB"
      else
          echo "$TIMESTAMP,0,NOTRUNNING,$i" >> $LOGFILE
          echo "Service not running"
      fi

      # Stop screensaver
      stop_screensaver
      sleep 3  # Let it fully stop and cleanup

      # Progress indicator
      if [ $((i % 10)) -eq 0 ]; then
          echo "--- Completed $i/$CYCLES cycles ---"
      fi
  done

  echo "✅ Test complete!"
  echo "Results saved to: $LOGFILE"
  echo ""
  echo "Quick analysis:"
  echo "Memory usage over time:"
  grep -v "NOTRUNNING" $LOGFILE | tail -10 | cut -d',' -f2 | while read mem; do
      echo "  ${mem}KB"
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

      if [ $MEMORY_CHANGE -gt 5000 ]; then
          echo "  ⚠️  WARNING: Memory increased by >5MB - possible leak"
      elif [ $MEMORY_CHANGE -gt 1000 ]; then
          echo "  ⚠️  CAUTION: Memory increased by >1MB - monitor closely"
      else
          echo "  ✅ GOOD: Memory change within acceptable range"
      fi
  fi