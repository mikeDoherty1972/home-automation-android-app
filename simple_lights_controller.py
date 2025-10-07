#!/usr/bin/env python3
"""
Simplified Lights Controller - File Bridge Approach
Android App → Firebase → File → (DAQFactory reads file and controls PLC)
"""

import time
import os
import firebase_admin
from firebase_admin import credentials, firestore

os.system("title Firebase Lights Controller - SCADA Integration") # ✅ Window title

# Configuration
PATH_TO_YOUR_SERVICE_ACCOUNT_JSON = r"C:\website\firebase\security-33809-firebase-adminsdk-fbsvc-5bbc47b722.json"
SENSOR_FILE_PATH = r"C:\Users\miked\My Drive\python\current\sensors"
LIGHTS_COMMAND_FILE = "lights_command.txt"
LIGHTS_STATUS_FILE = "lights_status.txt"

# Initialize Firebase
try:
    cred = credentials.Certificate(PATH_TO_YOUR_SERVICE_ACCOUNT_JSON)
    firebase_admin.initialize_app(cred, name='simple_lights_app')
    db = firestore.client(app=firebase_admin.get_app('simple_lights_app'))
    print("✅ Firebase initialized - Simple Lights Controller")
except Exception as e:
    print(f"❌ Firebase error: {e}")
    exit()

def write_lights_command(command):
    """Write lights command to file for DAQFactory to read"""
    try:
        file_path = os.path.join(SENSOR_FILE_PATH, LIGHTS_COMMAND_FILE)
        value = "1" if command.lower() == "on" else "0"
        with open(file_path, 'w') as f:
            f.write(value)
        print(f"📝 Command written to file: {command} -> {value}")
        return True
    except Exception as e:
        print(f"❌ Error writing command file: {e}")
        return False

def read_lights_status():
    """Read lights status from file (DAQFactory writes this)"""
    try:
        file_path = os.path.join(SENSOR_FILE_PATH, LIGHTS_STATUS_FILE)
        if os.path.exists(file_path):
            with open(file_path, 'r') as f:
                status = f.read().strip()
                return int(status) if status.isdigit() else 0
        else:
            # Create default status file
            with open(file_path, 'w') as f:
                f.write("0")
            return 0
    except Exception as e:
        print(f"❌ Error reading status file: {e}")
        return -1

def monitor_firebase_commands():
    """Monitor Firebase for lights commands from Android app"""
    print("🔥 Monitoring Firebase for lights commands...")
    
    def on_snapshot(doc_snapshot, changes, read_time):
        for doc in doc_snapshot:
            if doc.exists:
                data = doc.to_dict()
                command = data.get('lights_command')
                if command:
                    print(f"📱 Received from Android: {command}")
                    success = write_lights_command(command)
                    
                    # Read status after short delay
                    time.sleep(1)
                    actual_status = read_lights_status()
                    
                    # Update Firebase with results
                    update_firebase_status(command, success, actual_status)
    
    db.collection('scada_controls').document('lights').on_snapshot(on_snapshot)

def update_firebase_status(command, success, actual_status):
    """Update Firebase with current lights status"""
    doc_data = {
        'timestamp': firestore.SERVER_TIMESTAMP,
        'last_command': command,
        'command_success': success,
        'M22_write_sent': 1 if command.lower() == "on" else 0,
        'Y21_read_status': actual_status,
        'lights_on': actual_status == 1,
        'connection_method': 'file_bridge',
        'plc_type': 'Delta_SV2_via_files'
    }
    
    db.collection('scada_controls').document('lights_status').set(doc_data)
    print(f"🔥 Firebase updated: {command} → File → Status={actual_status}")

def main():
    """Main controller loop"""
    print("💡 Simple File-Based Lights Controller")
    print("📱 Android App → Firebase → Files → (Your DAQFactory setup)")
    
    # Test file access
    initial_status = read_lights_status()
    print(f"🔍 Initial lights status: {initial_status}")
    
    # Start monitoring
    monitor_firebase_commands()
    
    print("🔄 Controller ready. Press Ctrl+C to stop.")
    try:
        while True:
            # Periodic status update
            status = read_lights_status()
            if status != -1:  # Only update if file read successful
                update_firebase_status("monitor", True, status)
            time.sleep(10)  # Check every 10 seconds
            
    except KeyboardInterrupt:
        print("\n🛑 Controller stopped")

if __name__ == "__main__":
    main()