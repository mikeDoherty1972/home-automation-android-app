import time
import os
import firebase_admin
from firebase_admin import credentials, firestore

os.system("title Firebase Gauge Status - Weather & Power Monitoring") # ✅ Window title

# --- CONFIGURATION ---
PATH_TO_YOUR_SERVICE_ACCOUNT_JSON = r"C:\website\firebase\security-33809-firebase-adminsdk-fbsvc-5bbc47b722.json"
SENSOR_FILE_PATH = r"C:\Users\miked\My Drive\python\current\sensors"

GAUGE_SENSORS = {
    'WaterTemp': "water_temp.txt",
    'WaterPressure': "water_pressure.txt",
    'DVRHeartbeat': "dvr_heartbeat.txt",
    'AlarmActive': "alarm_active.txt",
    'MikeRSSI': "mike_RSSI.txt",
    'AllenRSSI': "allen_RSSI.txt",
    'WMBUSClean': "wmbus_clean.txt",
    'MikeWaterReading': "wmbus_clean.txt",
    'AllenWaterReading': "allen.txt",
    # Weather Station & Power Monitoring from book5.csv
    'WeatherStation': "book5.csv"
}
# ---------------------

try:
    cred = credentials.Certificate(PATH_TO_YOUR_SERVICE_ACCOUNT_JSON)
    firebase_admin.initialize_app(cred, name='gauge_app') # Use a unique name
except Exception as e:
    print(f"Error initializing Firebase Gauge App: {e}")
    exit()

db = firestore.client(app=firebase_admin.get_app('gauge_app'))

def read_gauge_status(filename, sensor_name=None):
    """Reads gauge status (handles both numeric and text values)."""
    full_path = os.path.join(SENSOR_FILE_PATH, filename)
    try:
        with open(full_path, 'r') as f:
            # Handle water meter readings specially
            if sensor_name == 'MikeWaterReading':
                # For wmbus_clean.txt, get the last line and extract the reading
                lines = f.readlines()
                if lines:
                    last_line = lines[-1].strip()
                    # Format: "394.624,2025-09-30 14:09:51"
                    if ',' in last_line:
                        reading = last_line.split(',')[0]
                        print(f"Reading Mike's water meter from {full_path}: '{reading}' liters")
                        return float(reading)
                return -999.0
            elif sensor_name == 'AllenWaterReading':
                # For allen.txt, get the last line and extract the reading
                lines = f.readlines()
                if lines:
                    last_line = lines[-1].strip()
                    # Format: "2025-09-30 14:39:51,314.368"
                    if ',' in last_line:
                        reading = last_line.split(',')[1]
                        print(f"Reading Allen's water meter from {full_path}: '{reading}' liters")
                        return float(reading)
                return -999.0
            elif sensor_name in ['MikeRSSI', 'AllenRSSI']:
                # RSSI files need to read the last line
                lines = f.readlines()
                if lines:
                    last_line = lines[-1].strip()
                    print(f"Reading {sensor_name} from {full_path}: '{last_line}'")
                    # Format: "123     2025-09-30 16:09:58"
                    if '\t' in last_line or '  ' in last_line:
                        rssi_value = last_line.split()[0]  # Get first part (RSSI value)
                        return float(rssi_value)
                return -999.0
            elif sensor_name == 'WeatherStation':
                # Parse book5.csv for weather and power data
                lines = f.readlines()
                if len(lines) >= 2:  # Need header + at least one data line
                    header = lines[0].strip()
                    data_line = lines[-1].strip()  # Get last data line
                    print(f"Reading {sensor_name} from {full_path}: '{data_line}'")
                    
                    # Expected columns: TheTime,temp in,temp out, humidity,wind speed,geyser temp,water pressure,kw,amps,kw daily,water,water daily,wind direction,dvr temp
                    if ',' in data_line:
                        values = data_line.split(',')
                        if len(values) >= 14:  # Ensure we have all expected columns
                            # Return a dictionary with all weather/power data
                            return {
                                'temp_in': float(values[1]) if values[1] else 0.0,
                                'temp_out': float(values[2]) if values[2] else 0.0,
                                'humidity': float(values[3]) if values[3] else 0.0,
                                'wind_speed': float(values[4]) if values[4] else 0.0,
                                'geyser_temp': float(values[5]) if values[5] else 0.0,
                                'water_pressure': float(values[6]) if values[6] else 0.0,
                                'power_kw': float(values[7]) if values[7] else 0.0,
                                'power_amps': float(values[8]) if values[8] else 0.0,
                                'power_kw_daily': float(values[9]) if values[9] else 0.0,
                                'water_level': float(values[10]) if values[10] else 0.0,
                                'water_daily': float(values[11]) if values[11] else 0.0,
                                'wind_direction': float(values[12]) if values[12] else 0.0,
                                'dvr_temp': float(values[13]) if values[13] else 0.0
                            }
                return -999.0
            else:
                # Regular sensor reading (single line files)
                line = f.readline().strip()
                print(f"Reading {full_path}: '{line}'")
                
                # Handle WMBUS status specially
                if sensor_name == 'WMBUSClean' and ',' in line:
                    # Return 1.0 to indicate WMBUS is active (has data)
                    return 1.0
                
                # Try to parse as float first
                try:
                    return float(line)
                except ValueError:
                    # Handle text/status data
                    if 'DVR_ONLINE=1' in line or line == '1':
                        return 1.0  # Online/Active
                    elif 'DVR_ONLINE=0' in line or line == '0':
                        return 0.0  # Offline/Inactive
                    else:
                        # For other text, return a status code
                        return 1.0 if line else 0.0
                    
    except Exception as e:
        print(f"Error reading {full_path}: {e}")
        return -999.0 # Error value

def send_gauge_data():
    data = {'timestamp': firestore.SERVER_TIMESTAMP}
    errors = False

    for sensor_name, filename in GAUGE_SENSORS.items():
        value = read_gauge_status(filename, sensor_name)
        
        if value == -999.0: 
            errors = True
        elif sensor_name == 'WeatherStation' and isinstance(value, dict):
            # Flatten weather station data into main data dictionary
            for weather_key, weather_value in value.items():
                data[weather_key] = weather_value
        else:
            data[sensor_name] = value

    if errors:
        print("Skipping gauge data send due to file error.")
        return

    # Overwrite the single 'gauge_status' document
    db.collection('security sensors').document('gauge_status').set(data)

    # Create summary but limit length for readability
    status_items = []
    for k, v in data.items():
        if k != 'timestamp':
            if isinstance(v, float):
                status_items.append(f"{k}={v:.2f}")
            else:
                status_items.append(f"{k}={v}")
    
    status_summary = ', '.join(status_items[:8]) # Limit to first 8 items for console
    if len(status_items) > 8:
        status_summary += f" +{len(status_items)-8} more"
        
    print(f"[{time.strftime('%H:%M:%S')}] GAUGE Status Sent (5m): {status_summary}")

print("Starting GAUGE Status push (5-minute interval). Press Ctrl+C to stop.")

while True:
    try:
        send_gauge_data()
        time.sleep(300) # 300 seconds (5 minutes) update interval

    except KeyboardInterrupt:
        print("\nGauge Status push stopped by user.")
        break
    except Exception as e:
        print(f"Unexpected error in gauge script: {e}")
        time.sleep(30)
