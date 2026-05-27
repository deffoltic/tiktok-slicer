# 🎬 TikTok Slicer - Web Video Editor & Slicing Tool

A high-performance, fully client-side web application designed to automatically slice and crop horizontal landscape movies into vertical 9:16 clips optimized for **TikTok**, **YouTube Shorts**, and **Instagram Reels**.

👉 **Live Demo:** [https://deffoltic.github.io/tiktok-slicer/](https://deffoltic.github.io/tiktok-slicer/)

---

## ✨ Features

- **🔒 100% Client-Side & Private**: Your videos are never uploaded to any server. All decoding, rendering, cropping, and encoding happen directly in your browser using the HTML5 Canvas, Web Audio, and MediaRecorder APIs.
- **🎯 Draggable 9:16 Crop Boundary**: Adjust the crop area by dragging the 9:16 box horizontally to focus on the key subject in the video.
- **🎛 Custom Video Controls**: Play, pause, and scrub through the video timeline using the built-in seeking slider to find the perfect starting frame.
- **📂 Local Gallery Store**: Processed clips are automatically saved inside your browser's local database (`IndexedDB`). Watch previews, download clips, or clean up your gallery anytime.
- **💎 Fully Free & Premium-Unlocked**:
  - No watermarks
  - High Definition (720p HD and 1080p Full HD) exports
  - Customizable slice durations (from 10 to 60 seconds)
- **🍏 macOS Playback Support**: Automatic selection of compatible video codecs (`video/mp4;codecs=avc1`) for native preview and QuickTime compatibility on macOS and iOS.

---

## 🛠 Tech Stack

- **Structure:** Semantic HTML5
- **Styling:** Modern Vanilla CSS3 featuring a dark glassmorphism theme, custom scrollbars, and neon glow effects.
- **Logic:** Vanilla JavaScript ES6+ utilizing:
  - **MediaRecorder API** for client-side stream capture
  - **Canvas API** for frame scaling and crop positioning
  - **Web Audio API** for mixing audio tracks
  - **IndexedDB** for local storage persistent gallery

---

## 🚀 How to Run Locally

Since this is a fully static client-side application, you can run it locally with zero build tools or dependencies:

1. Clone the repository:
   ```bash
   git clone https://github.com/Deffoltic/tiktok-slicer.git
   cd tiktok-slicer
   ```
2. Start a simple local server in the `web/` directory:
   ```bash
   npx http-server web
   # or
   python3 -m http.server -d web
   ```
3. Open `http://localhost:8080` in your web browser.
