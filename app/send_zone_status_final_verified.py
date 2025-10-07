import time
import os
import firebase_admin
from firebase_admin import credentials, firestore

os.system("title Firebase Zone Status - Security Monitoring") # ✅ Window title
from fcm_v1_send import send_fcm_v1_notification, fetch_fcm_tokens

# --- CONFIGURATION ---
PATH_TO_YOUR_SERVICE_ACCOUNT_JSON = r"C:\website\firebase\security-33809-firebase-adminsdk-fbsvc-5bbc47b722.json"
SENSOR_FILE_PATH = r"C:\Users\miked\My Drive\python\current\sensors"

ZONE_SENSOR_FILES = {
    'garage_motion': "garage_motion.txt",
    'garage_sensor': "garage_sensor.txt",
    'garage_side_motion': "garage_side_motion.txt",
    'garage_side_sensor': "garage_side_sensor.txt",
    'south_motion': "south_motion.txt",
    'south_sensor': "south_sensor.txt",
    'back_motion': "back_motion.txt",
    'back_sensor': "back_sensor.txt",
    'north_motion': "north_motion.txt",
    'north_sensor': "north_sensor.txt",
    'front_motion': "front_motion.txt",
    'front_sensor': "front_sensor.txt"
}

# Initialize Firebase App
try:
    cred = credentials.Certificate(PATH_TO_YOUR_SERVICE_ACCOUNT_JSON)
    firebase_admin.initialize_app(cred)
except Exception as e:
    print(f"Error initializing Firebase: {e}")
    exit()

db = firestore.client()

def read_sensor_status(filename):
    """Opens a file, reads the first line, and returns the integer status (0 or 1)."""
    full_path = f"{SENSOR_FILE_PATH}\\{filename}"
    try:
        with open(full_path, 'r') as f:
            content = f.readline().strip() 
            return int(content)
    except FileNotFoundError:
        print(f"Error: Sensor file not found at {full_path}")
        return -1
    except ValueError:
        print(f"Error: Data in {filename} is not a valid integer.")
        return -1



def send_security_data():
    """Reads all sensor files, sends data to Firestore, and sends FCM notifications if both sensors in a zone are active."""
    data = {'timestamp': firestore.SERVER_TIMESTAMP}
    errors = False

    for sensor_name, filename in ZONE_SENSOR_FILES.items():
        status = read_sensor_status(filename)
        if status == -1:
            errors = True
        data[sensor_name] = status

    if errors:
        print("Skipping data send due to file read error.")
        return

    # Check for zones where both sensors are active
    ZONES = [
        {"name": "Garage", "sensors": ["garage_motion", "garage_sensor"]},
        {"name": "Garage Side", "sensors": ["garage_side_motion", "garage_side_sensor"]},
        {"name": "South", "sensors": ["south_motion", "south_sensor"]},
        {"name": "Back", "sensors": ["back_motion", "back_sensor"]},
        {"name": "North", "sensors": ["north_motion", "north_sensor"]},
        {"name": "Front", "sensors": ["front_motion", "front_sensor"]}
    ]
    for zone in ZONES:
        s1, s2 = zone["sensors"]
        if data.get(s1) == 1 and data.get(s2) == 1:
            # Always fetch latest tokens and send notification
            send_fcm_v1_notification(f"{zone['name']} Alert", f"Both sensors in zone {zone['name']} are ACTIVE!")

    db.collection('security sensors').document('live_status').set(data)
    status_summary = ', '.join([f"{k}={v}" for k, v in data.items() if k != 'timestamp'])
    print(f"[{time.strftime('%H:%M:%S')}] FINAL VERIFIED Status Sent: {status_summary}")

print("Starting FINAL VERIFIED Status push (15-second interval). Press Ctrl+C to stop.")
while True:
    try:
        send_security_data()
        time.sleep(15)
    except KeyboardInterrupt:
        print("\nSecurity sensor push stopped by user.")
        break
    except Exception as e:
        print(f"An unexpected error occurred: {e}")
        time.sleep(30)
