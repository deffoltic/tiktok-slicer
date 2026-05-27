// Global Variables
let currentVideoFile = null;
let subscriptionActive = true;
let db = null;
let currentRenderProcess = null; // To track active rendering for cancellations
let cropPercent = 0.5; // Drag position of the crop boundary (0 = leftmost, 0.5 = centered, 1 = rightmost)

// Audio nodes (must be initialized once to avoid browser errors)
let audioCtx = null;
let videoAudioSource = null;
let audioDestination = null;

// IndexedDB Helper
const DB_NAME = "TikTokSlicerDB";
const DB_VERSION = 1;

function initDB() {
    return new Promise((resolve, reject) => {
        const request = indexedDB.open(DB_NAME, DB_VERSION);
        request.onupgradeneeded = (e) => {
            const database = e.target.result;
            if (!database.objectStoreNames.contains("clips")) {
                database.createObjectStore("clips", { keyPath: "id", autoIncrement: true });
            }
        };
        request.onsuccess = (e) => {
            db = e.target.result;
            resolve(db);
        };
        request.onerror = (e) => reject(e.target.error);
    });
}

function addClipToDB(name, blob) {
    return new Promise((resolve, reject) => {
        const transaction = db.transaction(["clips"], "readwrite");
        const store = transaction.objectStore("clips");
        const clip = {
            name: name,
            blob: blob,
            date: new Date().toLocaleDateString()
        };
        const request = store.add(clip);
        request.onsuccess = () => resolve();
        request.onerror = (e) => reject(e.target.error);
    });
}

function getClipsFromDB() {
    return new Promise((resolve, reject) => {
        const transaction = db.transaction(["clips"], "readonly");
        const store = transaction.objectStore("clips");
        const request = store.getAll();
        request.onsuccess = (e) => resolve(e.target.result);
        request.onerror = (e) => reject(e.target.error);
    });
}

function deleteClipFromDB(id) {
    return new Promise((resolve, reject) => {
        const transaction = db.transaction(["clips"], "readwrite");
        const store = transaction.objectStore("clips");
        const request = store.delete(id);
        request.onsuccess = () => resolve();
        request.onerror = (e) => reject(e.target.error);
    });
}

// Initialize Application
document.addEventListener("DOMContentLoaded", async () => {
    // 1. Init Database
    try {
        await initDB();
        loadGallery();
    } catch (err) {
        console.error("Failed to initialize IndexedDB:", err);
    }

    // 2. Load Subscription state (always active/unlocked)

    // 3. Tab Navigation Routing
    const navButtons = document.querySelectorAll(".nav-btn");
    const tabs = document.querySelectorAll(".tab-content");

    navButtons.forEach(btn => {
        btn.addEventListener("click", () => {
            const targetId = btn.getAttribute("data-target");
            
            navButtons.forEach(b => b.classList.remove("active"));
            tabs.forEach(t => t.classList.remove("active"));

            btn.classList.add("active");
            document.getElementById(targetId).classList.add("active");

            if (targetId === "tab-gallery") {
                loadGallery();
            }
        });
    });

    // 4. File Drag and Drop / Selection listeners
    const uploadZone = document.getElementById("upload-zone");
    const videoInput = document.getElementById("video-input");

    uploadZone.addEventListener("click", () => {
        if (!currentRenderProcess) {
            videoInput.click();
        }
    });

    uploadZone.addEventListener("dragover", (e) => {
        e.preventDefault();
        uploadZone.classList.add("dragover");
    });

    uploadZone.addEventListener("dragleave", () => {
        uploadZone.classList.remove("dragover");
    });

    uploadZone.addEventListener("drop", (e) => {
        e.preventDefault();
        uploadZone.classList.remove("dragover");
        if (e.dataTransfer.files.length > 0 && !currentRenderProcess) {
            handleVideoSelection(e.dataTransfer.files[0]);
        }
    });

    videoInput.addEventListener("change", (e) => {
        if (e.target.files.length > 0) {
            handleVideoSelection(e.target.files[0]);
        }
    });

    // 5. Config Control Bindings
    const slider = document.getElementById("clip-duration");
    const sliderVal = document.getElementById("duration-val");

    slider.addEventListener("input", (e) => {
        let val = parseInt(e.target.value);
        sliderVal.innerText = val + "s";
    });

    // Layout configuration toggle
    const layoutCrop = document.getElementById("layout-crop");
    const layoutFit = document.getElementById("layout-fit");
    const boundingBox = document.getElementById("bounding-box-9-16");

    layoutCrop.addEventListener("click", () => {
        layoutCrop.classList.add("active");
        layoutFit.classList.remove("active");
        boundingBox.style.aspectRatio = "9/16";
        boundingBox.style.height = "100%";
        boundingBox.style.width = "auto";
        setTimeout(updateCropBoxDOM, 50);
    });

    layoutFit.addEventListener("click", () => {
        layoutFit.classList.add("active");
        layoutCrop.classList.remove("active");
        boundingBox.style.aspectRatio = "16/9";
        boundingBox.style.width = "100%";
        boundingBox.style.height = "auto";
        setTimeout(updateCropBoxDOM, 50);
    });

    // Resolution selector toggling
    const radioOptions = document.querySelectorAll(".radio-option");
    radioOptions.forEach(option => {
        option.addEventListener("click", () => {
            const input = option.querySelector("input");
            if (input.disabled) return;
            
            // Toggle active visual state
            const name = input.getAttribute("name");
            document.querySelectorAll(`input[name="${name}"]`).forEach(r => {
                r.checked = false;
                r.closest(".radio-option").classList.remove("active");
            });
            
            input.checked = true;
            option.classList.add("active");
        });
    });

    // Preview video modal close
    const previewModal = document.getElementById("preview-modal");
    const closePreview = () => {
        const previewPlayer = document.getElementById("preview-player");
        previewPlayer.pause();
        previewModal.classList.remove("active");
    };

    document.getElementById("btn-close-preview").addEventListener("click", closePreview);

    // Close modal when clicking outside (on the overlay background)
    previewModal.addEventListener("click", (e) => {
        if (e.target === previewModal) {
            closePreview();
        }
    });

    // Slicing Trigger
    const btnSlice = document.getElementById("btn-slice");
    btnSlice.addEventListener("click", startSlicingFlow);

    // Drag-and-drop crop box logic
    boundingBox.style.pointerEvents = "auto";
    boundingBox.style.cursor = "grab";

    let isDragging = false;
    let startX = 0;
    let startLeft = 0;

    boundingBox.addEventListener("mousedown", (e) => {
        isDragging = true;
        boundingBox.style.cursor = "grabbing";
        startX = e.clientX;
        const rect = boundingBox.getBoundingClientRect();
        const containerRect = document.querySelector(".video-container").getBoundingClientRect();
        startLeft = rect.left - containerRect.left;
        e.preventDefault();
    });

    document.addEventListener("mousemove", (e) => {
        if (!isDragging) return;
        const container = previewModal.parentNode.querySelector(".video-container");
        const deltaX = e.clientX - startX;
        let newLeft = startLeft + deltaX;

        const containerWidth = container.clientWidth;
        const boxWidth = boundingBox.clientWidth;
        const maxLeft = containerWidth - boxWidth;

        if (newLeft < 0) newLeft = 0;
        if (newLeft > maxLeft) newLeft = maxLeft;

        boundingBox.style.left = newLeft + "px";
        boundingBox.style.margin = "0";

        cropPercent = maxLeft > 0 ? (newLeft / maxLeft) : 0.5;
    });

    document.addEventListener("mouseup", () => {
        if (isDragging) {
            isDragging = false;
            boundingBox.style.cursor = "grab";
        }
    });

    // Touch support for dragging
    boundingBox.addEventListener("touchstart", (e) => {
        if (e.touches.length === 1) {
            isDragging = true;
            startX = e.touches[0].clientX;
            const rect = boundingBox.getBoundingClientRect();
            const containerRect = document.querySelector(".video-container").getBoundingClientRect();
            startLeft = rect.left - containerRect.left;
        }
    });

    document.addEventListener("touchmove", (e) => {
        if (!isDragging || e.touches.length !== 1) return;
        const container = previewModal.parentNode.querySelector(".video-container");
        const deltaX = e.touches[0].clientX - startX;
        let newLeft = startLeft + deltaX;

        const containerWidth = container.clientWidth;
        const boxWidth = boundingBox.clientWidth;
        const maxLeft = containerWidth - boxWidth;

        if (newLeft < 0) newLeft = 0;
        if (newLeft > maxLeft) newLeft = maxLeft;

        boundingBox.style.left = newLeft + "px";
        boundingBox.style.margin = "0";

        cropPercent = maxLeft > 0 ? (newLeft / maxLeft) : 0.5;
    });

    document.addEventListener("touchend", () => {
        isDragging = false;
    });

    // Cancel slicing
    document.getElementById("btn-cancel-slice").addEventListener("click", () => {
        if (currentRenderProcess) {
            currentRenderProcess.cancelled = true;
        }
    });

    // Custom Video controls
    const playPauseBtn = document.getElementById("btn-play-pause");
    const videoTimeDisplay = document.getElementById("video-time-display");
    const sourceVideo = document.getElementById("source-video");

    if (playPauseBtn && sourceVideo) {
        playPauseBtn.addEventListener("click", () => {
            if (sourceVideo.paused || sourceVideo.ended) {
                sourceVideo.play();
                playPauseBtn.innerText = "⏸ Pause";
            } else {
                sourceVideo.pause();
                playPauseBtn.innerText = "▶ Play";
            }
        });

        sourceVideo.addEventListener("timeupdate", () => {
            if (videoTimeDisplay) {
                const current = formatTime(sourceVideo.currentTime);
                const total = formatTime(sourceVideo.duration || 0);
                videoTimeDisplay.innerText = `${current} / ${total}`;
            }
        });

        sourceVideo.addEventListener("play", () => {
            playPauseBtn.innerText = "⏸ Pause";
        });

        sourceVideo.addEventListener("pause", () => {
            playPauseBtn.innerText = "▶ Play";
        });
    }

    // Window resize handler to keep crop box alignment
    window.addEventListener("resize", updateCropBoxDOM);
});

// Update Subscription status in the app
// Subscription features are fully unlocked by default

// Handle video loading
function handleVideoSelection(file) {
    currentVideoFile = file;
    
    const displayName = document.getElementById("display-name");
    const displayDuration = document.getElementById("display-duration");
    const loadedInfo = document.getElementById("loaded-info");
    const uploadPrompt = document.querySelector(".upload-prompt");

    displayName.innerText = file.name;
    loadedInfo.style.display = "flex";
    uploadPrompt.style.display = "none";

    const video = document.getElementById("source-video");
    const fileUrl = URL.createObjectURL(file);
    video.src = fileUrl;

    video.onloadedmetadata = () => {
        displayDuration.innerText = "Duration: " + formatTime(video.duration);
        
        // Unlock panel configs
        document.getElementById("editor-configs").classList.remove("disabled-state");
        document.getElementById("clip-duration").disabled = false;
        
        const resolutions = document.getElementsByName("export-res");
        resolutions.forEach(r => r.disabled = false);
        
        document.getElementById("layout-crop").disabled = false;
        document.getElementById("layout-fit").disabled = false;
        document.getElementById("btn-slice").disabled = false;

        document.getElementById("placeholder-viewport").style.display = "none";
        document.getElementById("crop-overlay-container").style.display = "flex";

        // Show source video player and custom controls
        video.style.display = "block";
        const customControls = document.getElementById("custom-video-controls");
        if (customControls) customControls.style.display = "flex";
        
        const videoTimeDisplay = document.getElementById("video-time-display");
        if (videoTimeDisplay) {
            videoTimeDisplay.innerText = `00:00 / ${formatTime(video.duration)}`;
        }

        // Initialize/reset crop box placement to center
        cropPercent = 0.5;
        setTimeout(updateCropBoxDOM, 100);
    };
}

// Start Slicing Pipeline
async function startSlicingFlow() {
    const video = document.getElementById("source-video");
    const canvas = document.getElementById("render-canvas");
    const durationLimit = subscriptionActive ? parseInt(document.getElementById("clip-duration").value) : 15;
    const cropStyle = document.getElementById("layout-crop").classList.contains("active") ? "CENTER_CROP" : "FIT";
    
    const resolutionChoice = document.querySelector('input[name="export-res"]:checked').value;
    const targetWidth = parseInt(resolutionChoice) === 1080 ? 1080 : 720;
    const targetHeight = parseInt(resolutionChoice) === 1080 ? 1920 : 1280;

    canvas.width = targetWidth;
    canvas.height = targetHeight;

    const totalDuration = video.duration;
    const segmentDuration = durationLimit;
    const totalClips = Math.ceil(totalDuration / segmentDuration);

    // Initial audio components
    initAudioNodes(video);

    // Show Progress Output Panel
    const monitorCard = document.getElementById("export-monitor-card");
    const progressOverlay = document.getElementById("render-progress-overlay");
    monitorCard.style.display = "block";
    progressOverlay.style.display = "flex";
    
    // Disable inputs during processing
    document.getElementById("editor-configs").classList.add("disabled-state");

    currentRenderProcess = { cancelled: false };

    for (let i = 0; i < totalClips; i++) {
        if (currentRenderProcess.cancelled) break;

        const start = i * segmentDuration;
        const end = Math.min((i + 1) * segmentDuration, totalDuration);

        // Update Progress UI
        document.getElementById("progress-header").innerText = `Generating Clip ${i + 1} of ${totalClips}...`;
        
        try {
            const blob = await renderSegment(video, canvas, start, end, cropStyle, i + 1, totalClips);
            if (blob && !currentRenderProcess.cancelled) {
                // Determine file extension based on MIME type (MP4 vs WebM fallback)
                const ext = blob.type.includes('mp4') ? 'mp4' : 'webm';
                const name = `clip_${i + 1}_of_${totalClips}_${Date.now()}.${ext}`;
                
                // 1. Save to database (gallery)
                await addClipToDB(name, blob);
            }
        } catch (err) {
            console.error("Error exporting clip:", err);
        }
    }

    // Done
    monitorCard.style.display = "none";
    progressOverlay.style.display = "none";
    document.getElementById("editor-configs").classList.remove("disabled-state");
    currentRenderProcess = null;

    // Load gallery update and automatically switch to Gallery tab
    loadGallery();
    document.getElementById("btn-gallery").click();
}

// Global Audio initializations
function initAudioNodes(video) {
    if (!audioCtx) {
        audioCtx = new (window.AudioContext || window.webkitAudioContext)();
        videoAudioSource = audioCtx.createMediaElementSource(video);
        audioDestination = audioCtx.createMediaStreamDestination();
        videoAudioSource.connect(audioDestination);
    }
}

// Core Rendering Loop for individual slices
function renderSegment(video, canvas, start, end, cropStyle, index, total) {
    return new Promise((resolve, reject) => {
        const ctx = canvas.getContext("2d");
        
        // Seek video to start
        video.currentTime = start;
        video.muted = false; // We connect to AudioContext, which handles output silencing automatically

        const onSeeked = () => {
            video.removeEventListener("seeked", onSeeked);

            // Establish Stream
            const fps = 30;
            const canvasStream = canvas.captureStream(fps);
            
            // Mix Audio destination with canvas stream
            const combinedStream = new MediaStream();
            canvasStream.getVideoTracks().forEach(t => combinedStream.addTrack(t));
            audioDestination.stream.getAudioTracks().forEach(t => combinedStream.addTrack(t));

            // Select supported encoding mime-type
            let selectedMimeType = 'video/mp4;codecs=avc1';
            
            if (!MediaRecorder.isTypeSupported(selectedMimeType)) {
                selectedMimeType = 'video/mp4';
            }
            if (!MediaRecorder.isTypeSupported(selectedMimeType)) {
                selectedMimeType = 'video/webm;codecs=vp9,opus';
            }
            if (!MediaRecorder.isTypeSupported(selectedMimeType)) {
                selectedMimeType = 'video/webm;codecs=vp8,opus';
            }
            if (!MediaRecorder.isTypeSupported(selectedMimeType)) {
                selectedMimeType = 'video/webm';
            }

            let options = { mimeType: selectedMimeType };
            let recorder;
            try {
                recorder = new MediaRecorder(combinedStream, options);
            } catch (err) {
                // Final fallback
                recorder = new MediaRecorder(combinedStream);
            }

            const chunks = [];
            recorder.ondataavailable = (e) => {
                if (e.data && e.data.size > 0) {
                    chunks.push(e.data);
                }
            };

            recorder.onstop = () => {
                clearInterval(drawInterval);
                video.pause();
                
                const blob = new Blob(chunks, { type: recorder.mimeType || selectedMimeType });
                resolve(blob);
            };

            // Start processing
            recorder.start();
            video.play();

            const barFill = document.getElementById("progress-bar-fill");
            const percentText = document.getElementById("progress-percent-text");

            // Animation Loop
            const drawInterval = setInterval(() => {
                if (currentRenderProcess.cancelled) {
                    clearInterval(drawInterval);
                    recorder.stop();
                    video.pause();
                    reject(new Error("Render cancelled"));
                    return;
                }

                // Draw frames on Canvas
                drawVideoFrame(video, canvas, ctx, cropStyle);

                // Update individual progress bar percentage
                const currentDuration = video.currentTime - start;
                const totalTarget = end - start;
                let percent = Math.min((currentDuration / totalTarget) * 100, 100);
                if (percent < 0) percent = 0;
                barFill.style.width = percent + "%";
                percentText.innerText = Math.round(percent) + "%";

                // Check termination condition
                if (video.currentTime >= end || video.ended) {
                    clearInterval(drawInterval);
                    recorder.stop();
                }
            }, 1000 / fps);
        };

        video.addEventListener("seeked", onSeeked);
    });
}

// Crop and draw frame logic
function drawVideoFrame(video, canvas, ctx, cropStyle) {
    const vW = video.videoWidth;
    const vH = video.videoHeight;
    const cW = canvas.width;
    const cH = canvas.height;

    ctx.fillStyle = "#000";
    ctx.fillRect(0, 0, cW, cH);

    if (cropStyle === "CENTER_CROP") {
        // Landscape to Vertical Crop (uses custom horizontal dragging position offset)
        const scale = cH / vH;
        const scaledWidth = vW * scale;
        const xOffset = -cropPercent * (scaledWidth - cW);
        ctx.drawImage(video, xOffset, 0, scaledWidth, cH);
    } else {
        // Fit Landscape (adds letterboxes on top and bottom)
        const scale = cW / vW;
        const scaledHeight = vH * scale;
        const yOffset = (cH - scaledHeight) / 2;
        ctx.drawImage(video, 0, yOffset, cW, scaledHeight);
    }

    // Watermark removed
}

// Draggable Crop Box UI updating helper
function updateCropBoxDOM() {
    const boundingBox = document.getElementById("bounding-box-9-16");
    const container = document.querySelector(".video-container");
    
    if (!boundingBox || !container) return;
    
    const containerWidth = container.clientWidth;
    const boxWidth = boundingBox.clientWidth;
    const maxLeft = containerWidth - boxWidth;
    
    if (maxLeft > 0) {
        boundingBox.style.left = (cropPercent * maxLeft) + "px";
        boundingBox.style.margin = "0";
    } else {
        boundingBox.style.left = "0px";
        boundingBox.style.margin = "";
    }
}

// Download Trigger
function triggerFileDownload(blob, name) {
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = name;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
}

// Gallery rendering from DB
async function loadGallery() {
    const grid = document.getElementById("gallery-video-grid");
    grid.innerHTML = "";

    let clips = [];
    try {
        clips = await getClipsFromDB();
    } catch (err) {
        console.error("Failed to fetch gallery clips:", err);
    }

    if (clips.length === 0) {
        grid.innerHTML = `
            <div class="empty-gallery-msg" style="grid-column: 1/-1; text-align: center; padding: 48px; color: var(--text-secondary);">
                <p>No video clips found in storage yet.</p>
            </div>
        `;
        return;
    }

    clips.forEach(clip => {
        const card = document.createElement("div");
        card.className = "clip-card";
        
        card.innerHTML = `
            <div class="clip-thumbnail-fallback">
                <div class="play-badge-icon">▶</div>
            </div>
            <div class="clip-info-overlay">
                <h4>${clip.name}</h4>
                <div class="clip-meta-row">
                    <span class="clip-duration-tag">${clip.date}</span>
                    <div class="clip-actions">
                        <button class="gallery-icon-btn download-action" title="Download">📥</button>
                        <button class="gallery-icon-btn delete-action" title="Delete">🗑</button>
                    </div>
                </div>
            </div>
        `;

        // Click on body plays video
        card.addEventListener("click", (e) => {
            if (e.target.classList.contains("gallery-icon-btn")) return;
            playClipPreview(clip.blob);
        });

        // Click download
        card.querySelector(".download-action").addEventListener("click", (e) => {
            e.stopPropagation();
            triggerFileDownload(clip.blob, clip.name);
        });

        // Click delete
        card.querySelector(".delete-action").addEventListener("click", async (e) => {
            e.stopPropagation();
            if (confirm("Delete this video clip?")) {
                try {
                    await deleteClipFromDB(clip.id);
                    loadGallery();
                } catch (err) {
                    console.error("Delete error:", err);
                }
            }
        });

        grid.appendChild(card);
    });
}

// Video Player Modal open
function playClipPreview(blob) {
    const previewModal = document.getElementById("preview-modal");
    const previewPlayer = document.getElementById("preview-player");
    
    const blobUrl = URL.createObjectURL(blob);
    previewPlayer.src = blobUrl;
    
    previewModal.classList.add("active");
    previewPlayer.play();
}

// Format utilities
function formatTime(seconds) {
    const minutes = Math.floor(seconds / 60);
    const secs = Math.floor(seconds % 60);
    return `${minutes.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
}
