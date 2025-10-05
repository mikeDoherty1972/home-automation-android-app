# 🏠 Home Automation & SCADA Monitoring App

A comprehensive Android application for monitoring home automation systems, security sensors, weather data, and power consumption using real-time data from Google Sheets and Firebase.

## 📱 Features

### 🔒 Security Dashboard
- Real-time motion detection monitoring
- 6-zone security system with alarm states
- Visual status indicators with color-coded alerts
- Push notifications for security events

### ⚡ SCADA Monitoring
- **Power Consumption**: Real-time amps (prominent display) and kW monitoring
- **Weather Station**: Indoor/outdoor temperature, humidity, wind speed & direction
- **Water Heating**: Geyser temperature and pressure monitoring
- **DVR Monitoring**: Equipment temperature tracking

### 📊 Data Analytics
- Live data usage tracking from multiple sensors
- Active sensor count and data rate monitoring
- Source breakdown by category (Temperature, Humidity, Wind, Power, DVR)
- Real-time analytics from Google Sheets integration

### 📈 Historical Graphs
- **Weather Trends**: Temperature, humidity, and wind patterns
- **Power Analysis**: Consumption trends, daily totals, amp usage
- **Geyser Performance**: Temperature and pressure over time
- **DVR Monitoring**: Equipment health tracking

## 🛠 Technical Stack

### Android Development
- **Language**: Kotlin
- **UI Framework**: Android Views with CardView layouts
- **Charts**: MPAndroidChart library for data visualization
- **Async Processing**: Coroutines for smooth data loading

### Data Sources
- **Firebase Firestore**: Security sensor data and real-time notifications
- **Google Sheets**: Weather, power, and geyser data via CSV export
- **Real-time Updates**: 30-second refresh intervals for live monitoring

### Build Configuration
- **Java**: OpenJDK 17
- **Android Gradle Plugin**: 8.2.0
- **Minimum SDK**: 24 (Android 7.0)
- **Target SDK**: 34 (Android 14)

## 🏗 Project Structure

```
app/
├── src/main/java/com/security/app/
│   ├── MainActivity.kt              # Main dashboard with data analytics
│   ├── ScadaActivity.kt            # SCADA monitoring (amps prominent)
│   ├── SecurityActivity.kt         # Security system monitoring
│   ├── IPERLActivity.kt            # Water meter monitoring
│   ├── WeatherGraphsActivity.kt    # Weather trend graphs
│   ├── PowerGraphsActivity.kt      # Power consumption graphs
│   ├── GeyserGraphsActivity.kt     # Water heating graphs
│   ├── DvrGraphsActivity.kt        # DVR monitoring graphs
│   └── GoogleSheetsReader.kt       # Google Sheets CSV integration
├── src/main/res/layout/            # UI layouts
└── src/main/res/values/            # Strings, colors, themes
```

## 🔧 Setup & Installation

### Prerequisites
- Android Studio with Java 17
- Android SDK (API level 24+)
- Firebase project with Firestore enabled
- Google Sheets with sensor data in CSV export format

### Build Instructions
1. Clone the repository
2. Configure `local.properties` with Android SDK path
3. Set `ANDROID_HOME` environment variable
4. Build with: `./gradlew assembleDebug`

### Configuration
- **Firebase**: Add `google-services.json` to app directory
- **Google Sheets**: Update CSV URL in `GoogleSheetsReader.kt`
- **Column Mapping**: Verify data column assignments in GoogleSheetsReader

## 📋 Data Column Mapping

The app reads from Google Sheets with the following column structure:
- **Column A**: Timestamp
- **Column B**: Indoor Temperature
- **Column C**: Outdoor Temperature  
- **Column D**: Humidity
- **Column E**: Wind Speed
- **Column F**: Geyser Temperature
- **Column G**: Geyser Pressure
- **Column H**: kW Today
- **Column I**: Current Amps (prominent display)
- **Column J**: Total kW Yesterday
- **Column M**: Wind Direction
- **Column N**: DVR Temperature

## 🎯 Key Features Implemented

### ✅ User Request: "Make amps prominent, kW smaller"
- Amps displayed prominently in larger text
- kW shown in smaller side display
- Real-time data from Google Sheets Column I (amps) and Column H (kW)

### ✅ Weather Station Integration
- **Card 1**: Indoor temperature (Column B)
- **Card 2**: Outdoor temperature (Column C) + humidity (Column D)  
- **Card 3**: Wind speed (Column E) + direction arrows (Column M)

### ✅ Data Usage Analytics
- Total data points across all sensors
- Active sensor count
- Data rate (points/minute)
- Breakdown by sensor type with live counting

### ✅ Graph Functionality
- Click any SCADA card to view historical trends
- Data sourced from Google Sheets for consistent historical analysis
- Professional charts with MPAndroidChart library

## 🔔 Notifications & Alerts

- Firebase Cloud Messaging for real-time security alerts
- Color-coded alarm states (Red = Alert, Green = Normal)
- Alarm sound configuration with system bypass
- Long-press cards to test alarm states

## 📱 User Interface

- **Dark theme** optimized for monitoring environments
- **Card-based layout** for easy touch interaction
- **Real-time updates** with visual indicators
- **Responsive design** for various screen sizes

## 🚀 Future Enhancements

- [ ] IDS (Intrusion Detection System) integration
- [ ] Additional sensor types and data sources
- [ ] Historical data export functionality
- [ ] Advanced alerting rules and thresholds
- [ ] Multi-user access controls

## 📄 License

This project is for personal home automation use.

## 👨‍💻 Developer

Created for comprehensive home monitoring and automation control.

---
*Last Updated: October 2025*