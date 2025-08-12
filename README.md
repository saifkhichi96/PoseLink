# PoseLink

PoseLink is a real-time mobile capture app for **wireless pose estimation and motion-tracking systems**, built as a fork of [VIRec](https://a3dv.github.io/autvi#virec).  
It streams **synchronized camera video and sensor data** to a host computer over the network, enabling flexible single- or multi-camera setups without USB tethering.

---

## Features (Planned)

- 📹 **Live camera streaming** (MJPEG/HTTP, RTSP, or WebRTC)
- 📡 **Sensor data streaming** (accelerometer, gyroscope, magnetometer)
- ⏱ **Time-synchronized frames & IMU** for accurate motion analysis
- 🔧 **Remote camera control** (exposure, focus, resolution) via API
- ⚡ **Low-latency modes** for real-time tracking
- 🔄 **USB fallback** when Wi-Fi performance is insufficient
- 📂 **Optional local recording** of raw or encoded streams
- 🛠 **Configurable output formats** for integration with research pipelines

---

## Origin

This project is based on the open-source [VIRec](https://a3dv.github.io/autvi#virec) application, originally designed for visual-inertial recording with dual camera support.  
PoseLink adapts and extends VIRec’s architecture to support **live IP streaming and multi-sensor data delivery** tailored for pose estimation workflows.

---

## Planned Architecture

- **Android (Camera2 + SensorManager)** →  
  Encoded video + sensor JSON packets →  
  **Network Transport (HTTP/RTSP/WebRTC)** →  
  **Python/C++ client** for processing in pose estimation or motion tracking systems.

---

## Development Checklist

### Core Networking
- [ ] Implement MJPEG/HTTP streaming from camera frames
- [ ] Implement `/sensors.json` endpoint for live IMU data
- [ ] Add synchronized timestamps to video & sensor streams
- [ ] Implement optional RTSP streaming of H.264 bitstream

### Sensor Integration
- [ ] Poll accelerometer, gyroscope, magnetometer at high rate
- [ ] Fuse orientation (quaternion) from raw IMU data
- [ ] Ensure timestamp sync with camera frames

### Remote Control API
- [ ] Implement commands for exposure, focus, resolution
- [ ] Add camera switch (front/rear) control
- [ ] Add flashlight control

### Client Tools
- [ ] Python reference client (OpenCV + Requests)
- [ ] 3-D visualization of live camera pose
- [ ] CSV/JSON logging for offline analysis

### Performance & Stability
- [ ] Wi-Fi latency measurement & optimization
- [ ] Battery usage profiling
- [ ] Error handling for dropped frames/connections
- [ ] USB fallback mode

---

## License

PoseLink follows the original VIRec license. See [LICENSE](LICENSE) for details.

---