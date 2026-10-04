/**
 * MediCareX Emergency Health Card QR Scanner Component
 * Supports desktop webcams, mobile cameras (front/rear), dynamic frame decoding,
 * permission handling, manual token fallback, authenticated API scan, and authorized emergency display.
 */
(function () {
    let html5QrCode = null;
    let currentRole = 'DOCTOR';
    let currentOnSuccessCallback = null;
    let isProcessing = false;

    // Inject CSS styles for the scanner reticle and animations
    function injectScannerStyles() {
        if (document.getElementById('emg-scanner-custom-styles')) return;
        const style = document.createElement('style');
        style.id = 'emg-scanner-custom-styles';
        style.textContent = `
            @keyframes emgLaserSweep {
                0% { top: 12%; opacity: 0.4; }
                50% { opacity: 1; }
                100% { top: 88%; opacity: 0.4; }
            }
            @keyframes emgPulse {
                0% { transform: scale(0.95); opacity: 0.7; }
                50% { transform: scale(1.15); opacity: 1; }
                100% { transform: scale(0.95); opacity: 0.7; }
            }
            .emg-laser-line {
                position: absolute;
                left: 12%;
                right: 12%;
                height: 2px;
                background: linear-gradient(90deg, rgba(239,68,68,0) 0%, rgba(239,68,68,0.95) 50%, rgba(239,68,68,0) 100%);
                box-shadow: 0 0 8px 1px rgba(239, 68, 68, 0.85);
                animation: emgLaserSweep 2s ease-in-out infinite alternate;
                pointer-events: none;
                z-index: 10;
            }
            .emg-corner-bracket {
                position: absolute;
                width: 24px;
                height: 24px;
                border-color: #ef4444;
                border-style: solid;
                pointer-events: none;
                z-index: 10;
            }
            .emg-bracket-tl { top: 18px; left: 18px; border-width: 3px 0 0 3px; border-top-left-radius: 6px; }
            .emg-bracket-tr { top: 18px; right: 18px; border-width: 3px 3px 0 0; border-top-right-radius: 6px; }
            .emg-bracket-bl { bottom: 18px; left: 18px; border-width: 0 0 3px 3px; border-bottom-left-radius: 6px; }
            .emg-bracket-br { bottom: 18px; right: 18px; border-width: 0 3px 3px 0; border-bottom-right-radius: 6px; }
            #emg-qr-reader video {
                width: 100% !important;
                height: 100% !important;
                object-fit: cover !important;
                border-radius: 10px !important;
            }
            #emg-qr-reader__scan_region {
                background: transparent !important;
            }
            #emg-qr-reader__dashboard {
                display: none !important;
            }
        `;
        document.head.appendChild(style);
    }

    const SCANNER_MODAL_HTML = `
    <div id="emg-scanner-modal" class="modal-backdrop" style="display: none; position: fixed; inset: 0; background: rgba(15, 23, 42, 0.85); backdrop-filter: blur(6px); z-index: 9999; align-items: center; justify-content: center; padding: 1rem;">
        <div class="modal-card" style="background: #ffffff; border-radius: 16px; width: 100%; max-width: 480px; box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5); overflow: hidden; border: 1px solid #cbd5e1;">
            <div style="background: linear-gradient(135deg, #1e293b 0%, #0f172a 100%); color: #ffffff; padding: 1.15rem 1.5rem; display: flex; justify-content: space-between; align-items: center;">
                <div style="display: flex; align-items: center; gap: 0.75rem;">
                    <div style="width: 38px; height: 38px; background: rgba(239, 68, 68, 0.2); border: 1px solid #ef4444; border-radius: 8px; display: flex; align-items: center; justify-content: center; font-size: 18px;">
                        📷
                    </div>
                    <div>
                        <h3 style="margin: 0; font-size: 16px; font-weight: 700; color: #ffffff;">Scan Emergency QR Code</h3>
                        <p style="margin: 0; font-size: 11px; color: #94a3b8;">Real-time camera scanner for patient emergency cards</p>
                    </div>
                </div>
                <button type="button" onclick="window.EmergencyScanner.closeScanner()" style="background: transparent; border: none; color: #94a3b8; font-size: 24px; cursor: pointer; line-height: 1; padding: 0 4px;" title="Close Scanner">&times;</button>
            </div>

            <div style="padding: 1.25rem;">
                <!-- Camera Viewport with Unobstructed Laser Reticle -->
                <div id="emg-reader-wrapper" style="position: relative; background: #090d16; border-radius: 12px; overflow: hidden; min-height: 270px; display: flex; align-items: center; justify-content: center; border: 1px solid #334155;">
                    <div id="emg-qr-reader" style="width: 100%; min-height: 270px;"></div>
                    
                    <!-- Reticle Frame (Corners + Sweep Laser - Centrally Clear) -->
                    <div id="emg-scan-reticle" style="position: absolute; inset: 0; pointer-events: none;">
                        <div class="emg-corner-bracket emg-bracket-tl"></div>
                        <div class="emg-corner-bracket emg-bracket-tr"></div>
                        <div class="emg-corner-bracket emg-bracket-bl"></div>
                        <div class="emg-corner-bracket emg-bracket-br"></div>
                        <div class="emg-laser-line"></div>
                    </div>

                    <div id="emg-camera-loading" style="position: absolute; color: #ffffff; font-size: 13px; text-align: center; display: none; z-index: 20;">
                        <div class="spinner" style="margin: 0 auto 8px auto; width: 28px; height: 28px; border: 3px solid rgba(255,255,255,0.2); border-top-color: #ef4444; border-radius: 50%; animation: spin 0.8s linear infinite;"></div>
                        <span style="font-weight: 600;">Initializing Camera Stream...</span>
                    </div>
                </div>

                <!-- Scanner Live Status Card -->
                <div id="emg-scan-status-card" style="margin-top: 0.75rem; background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 0.6rem 0.85rem; display: flex; align-items: center; justify-content: space-between; font-size: 12px;">
                    <div style="display: flex; align-items: center; gap: 0.5rem;">
                        <span id="emg-scan-status-dot" style="width: 8px; height: 8px; border-radius: 50%; background: #22c55e; display: inline-block; animation: emgPulse 1.5s infinite;"></span>
                        <span id="emg-scan-status-text" style="color: #334155; font-weight: 600;">Searching for QR code...</span>
                    </div>
                    <span style="font-size: 11px; color: #64748b;">Point camera at patient's QR</span>
                </div>

                <!-- Camera Controls -->
                <div style="margin-top: 0.75rem; display: flex; gap: 0.5rem; align-items: center; justify-content: space-between;">
                    <select id="emg-camera-select" class="form-control" style="font-size: 12px; padding: 6px 10px; flex: 1; display: none;" onchange="window.EmergencyScanner.switchCamera(this.value)">
                        <option value="">Select Camera Device</option>
                    </select>
                    <button type="button" id="emg-btn-restart-cam" class="btn btn-sm btn-secondary" onclick="window.EmergencyScanner.startCamera()" style="font-size: 12px; display: none;">
                        🔄 Restart Camera
                    </button>
                </div>

                <!-- Error Banner -->
                <div id="emg-camera-error-banner" style="display: none; background: #fef2f2; border: 1px solid #fecaca; color: #991b1b; padding: 0.75rem; border-radius: 8px; font-size: 12px; margin-top: 0.75rem;">
                    <strong>Notice:</strong> <span id="emg-camera-error-text">Unable to access camera.</span>
                </div>

                <!-- Manual Token Fallback Section -->
                <div style="margin-top: 1rem; padding-top: 0.85rem; border-top: 1px solid #e2e8f0;">
                    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.35rem;">
                        <label for="emg-manual-token-input" style="font-size: 12px; font-weight: 600; color: #475569;">Manual Token Fallback</label>
                        <span style="font-size: 10.5px; color: #64748b;">If camera is unavailable</span>
                    </div>
                    <div style="display: flex; gap: 0.5rem;">
                        <input type="text" id="emg-manual-token-input" class="form-control" placeholder="e.g. EMG-12a1055... or paste emergency URL" style="font-size: 12px; font-family: monospace;" />
                        <button type="button" class="btn btn-primary btn-sm" onclick="window.EmergencyScanner.submitManualToken()" style="white-space: nowrap; font-size: 12px; font-weight: 700;">
                            Verify Token
                        </button>
                    </div>
                </div>
            </div>

            <div style="background: #f8fafc; padding: 0.85rem 1.5rem; display: flex; justify-content: space-between; align-items: center; border-top: 1px solid #e2e8f0;">
                <span style="font-size: 11px; color: #64748b;">🔒 Protected by MediCareX Role Authorization & Access Audit</span>
                <button type="button" class="btn btn-secondary btn-sm" onclick="window.EmergencyScanner.closeScanner()">Close</button>
            </div>
        </div>
    </div>

    <!-- Result Display Modal -->
    <div id="emg-result-modal" class="modal-backdrop" style="display: none; position: fixed; inset: 0; background: rgba(15, 23, 42, 0.85); backdrop-filter: blur(6px); z-index: 10000; align-items: center; justify-content: center; padding: 1rem;">
        <div class="modal-card" style="background: #ffffff; border-radius: 16px; width: 100%; max-width: 560px; box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5); overflow: hidden; border: 1px solid #cbd5e1; max-height: 90vh; display: flex; flex-direction: column;">
            <!-- Header with Blood Group Badge -->
            <div style="background: linear-gradient(135deg, #dc2626 0%, #991b1b 100%); color: #ffffff; padding: 1.25rem 1.5rem; display: flex; justify-content: space-between; align-items: center;">
                <div style="display: flex; align-items: center; gap: 1rem;">
                    <div id="emg-res-blood-badge" style="width: 54px; height: 54px; border-radius: 50%; background: #ffffff; color: #dc2626; display: flex; flex-direction: column; align-items: center; justify-content: center; font-weight: 900; font-size: 1.35rem; line-height: 1; box-shadow: 0 4px 12px rgba(0,0,0,0.3); border: 2px solid #fecaca; flex-shrink: 0;">
                        <span id="emg-res-blood">O+</span>
                    </div>
                    <div>
                        <div style="font-size: 11px; font-weight: 800; text-transform: uppercase; letter-spacing: 0.05em; color: #fecaca; display: flex; align-items: center; gap: 0.35rem;">
                            <span>🚨 EMERGENCY MEDICAL INFORMATION</span>
                        </div>
                        <h2 id="emg-res-name" style="margin: 0.15rem 0 0 0; font-size: 1.35rem; font-weight: 800; color: #ffffff;">Patient Name</h2>
                        <div id="emg-res-phone" style="font-size: 12px; color: #fee2e2;">Phone: +91 98765 43210</div>
                    </div>
                </div>
                <button type="button" onclick="window.EmergencyScanner.closeResultModal()" style="background: transparent; border: none; color: #fee2e2; font-size: 26px; cursor: pointer; line-height: 1;" title="Close">&times;</button>
            </div>

            <!-- Body Details -->
            <div style="padding: 1.25rem; overflow-y: auto; display: flex; flex-direction: column; gap: 0.85rem;">
                <!-- Critical Allergies -->
                <div style="background: #fef2f2; border: 1.5px solid #fca5a5; border-radius: 10px; padding: 0.85rem 1rem;">
                    <div style="font-size: 11.5px; font-weight: 800; text-transform: uppercase; color: #b91c1c; display: flex; align-items: center; gap: 0.35rem; margin-bottom: 0.25rem;">
                        <span>🚨 CRITICAL ALLERGIES</span>
                    </div>
                    <div id="emg-res-allergies" style="font-size: 14px; font-weight: 700; color: #7f1d1d;">None declared</div>
                </div>

                <!-- Chronic Conditions -->
                <div style="background: #f0f9ff; border: 1px solid #bae6fd; border-radius: 10px; padding: 0.85rem 1rem;">
                    <div style="font-size: 11.5px; font-weight: 800; text-transform: uppercase; color: #0369a1; display: flex; align-items: center; gap: 0.35rem; margin-bottom: 0.25rem;">
                        <span>🏥 CHRONIC MEDICAL CONDITIONS</span>
                    </div>
                    <div id="emg-res-conditions" style="font-size: 13.5px; font-weight: 600; color: #0c4a6e;">None declared</div>
                </div>

                <!-- Ongoing Medications Summary -->
                <div style="background: #faf5ff; border: 1px solid #e9d5ff; border-radius: 10px; padding: 0.85rem 1rem;">
                    <div style="font-size: 11.5px; font-weight: 800; text-transform: uppercase; color: #7e22ce; display: flex; align-items: center; gap: 0.35rem; margin-bottom: 0.25rem;">
                        <span>💊 CURRENT ONGOING MEDICATIONS</span>
                    </div>
                    <div id="emg-res-meds" style="font-size: 13px; font-weight: 500; color: #581c87;">None declared</div>
                </div>

                <!-- Emergency Contact -->
                <div style="background: #ecfdf5; border: 1px solid #a7f3d0; border-radius: 10px; padding: 0.85rem 1rem;">
                    <div style="font-size: 11.5px; font-weight: 800; text-transform: uppercase; color: #047857; display: flex; align-items: center; gap: 0.35rem; margin-bottom: 0.25rem;">
                        <span>📞 DESIGNATED EMERGENCY CONTACT</span>
                    </div>
                    <div style="display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 0.5rem;">
                        <div>
                            <div id="emg-res-contact-name" style="font-size: 14px; font-weight: 700; color: #064e3b;">Jane Doe (Spouse)</div>
                            <div id="emg-res-contact-phone" style="font-size: 12px; color: #047857;">+91 98765 00000</div>
                        </div>
                        <a id="emg-res-contact-call" href="#" class="btn btn-sm" style="background: #10b981; color: #ffffff; font-size: 12px; font-weight: 700; text-decoration: none; padding: 6px 12px; border-radius: 6px; display: inline-flex; align-items: center; gap: 4px;">
                            📞 Call Now
                        </a>
                    </div>
                </div>

                <!-- Organ Donor & Insurance Grid -->
                <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 0.75rem;">
                    <div style="background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 0.75rem;">
                        <span style="font-size: 10.5px; font-weight: 700; color: #64748b; text-transform: uppercase; display: block;">Organ Donor</span>
                        <span id="emg-res-donor" style="font-size: 13px; font-weight: 700; color: #10b981; display: block; margin-top: 2px;">YES (Pledged)</span>
                    </div>
                    <div style="background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 0.75rem;">
                        <span style="font-size: 10.5px; font-weight: 700; color: #64748b; text-transform: uppercase; display: block;">Insurance</span>
                        <span id="emg-res-insurance" style="font-size: 12px; font-weight: 600; color: #1e293b; display: block; margin-top: 2px;">Star Health</span>
                    </div>
                </div>

                <div id="emg-res-updated" style="font-size: 11px; color: #94a3b8; text-align: center;">Verified MySQL Emergency Record</div>
            </div>

            <!-- Footer Actions -->
            <div style="background: #f8fafc; padding: 1rem 1.5rem; display: flex; justify-content: space-between; align-items: center; border-top: 1px solid #e2e8f0; gap: 0.75rem;">
                <div id="emg-doctor-ehr-action" style="display: none; flex: 1;">
                    <a id="emg-btn-view-ehr" href="#" class="btn btn-primary btn-sm w-full justify-center" style="font-size: 13px; font-weight: 700;">
                        📂 View Full Patient Medical EHR
                    </a>
                </div>
                <button type="button" class="btn btn-secondary btn-sm" onclick="window.EmergencyScanner.closeResultModal()">Done</button>
            </div>
        </div>
    </div>
    `;

    function ensureModalsInDom() {
        injectScannerStyles();
        if (!document.getElementById('emg-scanner-modal')) {
            const div = document.createElement('div');
            div.innerHTML = SCANNER_MODAL_HTML;
            document.body.appendChild(div);
        }
    }

    function extractToken(raw) {
        if (!raw) return '';
        raw = raw.trim();
        try {
            // Handle URL with query params (?token=... or &token=...)
            if (raw.includes('token=')) {
                // Parse full URL or relative URL
                const url = new URL(raw, window.location.origin);
                const t = url.searchParams.get('token');
                if (t) return t.trim();
            }
            // Handle path-based tokens e.g. /api/emergency/card/EMG-xxxx
            const pathMatch = raw.match(/\/emergency\/card\/([a-zA-Z0-9_-]+)/i);
            if (pathMatch && pathMatch[1] && pathMatch[1] !== 'public' && pathMatch[1] !== 'scan') {
                return pathMatch[1].trim();
            }
        } catch (e) {
            // Regex fallback
            const match = raw.match(/[?&]token=([^&#]+)/);
            if (match && match[1]) return decodeURIComponent(match[1]).trim();
        }
        return raw;
    }

    function updateScanStatus(text, statusType = 'searching') {
        const statusText = document.getElementById('emg-scan-status-text');
        const statusDot = document.getElementById('emg-scan-status-dot');
        if (!statusText || !statusDot) return;

        statusText.textContent = text;
        if (statusType === 'searching') {
            statusDot.style.background = '#22c55e';
            statusDot.style.display = 'inline-block';
            statusText.style.color = '#334155';
        } else if (statusType === 'detected') {
            statusDot.style.background = '#f59e0b';
            statusDot.style.display = 'inline-block';
            statusText.style.color = '#d97706';
        } else if (statusType === 'verified') {
            statusDot.style.background = '#10b981';
            statusDot.style.display = 'inline-block';
            statusText.style.color = '#047857';
        } else if (statusType === 'error') {
            statusDot.style.background = '#ef4444';
            statusDot.style.display = 'inline-block';
            statusText.style.color = '#b91c1c';
        }
    }

    window.EmergencyScanner = {
        openScanner: async function (options = {}) {
            ensureModalsInDom();
            isProcessing = false;
            currentRole = (options.role || (window.api.getCurrentUser()?.role || 'DOCTOR')).toUpperCase();
            currentOnSuccessCallback = options.onScanSuccess || null;

            document.getElementById('emg-manual-token-input').value = '';
            document.getElementById('emg-camera-error-banner').style.display = 'none';
            document.getElementById('emg-btn-restart-cam').style.display = 'none';
            updateScanStatus('Searching for QR code...', 'searching');

            document.getElementById('emg-scanner-modal').style.display = 'flex';

            await this.startCamera();
        },

        startCamera: async function (selectedCameraId = null) {
            const errorBanner = document.getElementById('emg-camera-error-banner');
            const errorText = document.getElementById('emg-camera-error-text');
            const cameraSelect = document.getElementById('emg-camera-select');
            const restartBtn = document.getElementById('emg-btn-restart-cam');
            const loadingIndicator = document.getElementById('emg-camera-loading');

            errorBanner.style.display = 'none';
            restartBtn.style.display = 'none';
            loadingIndicator.style.display = 'block';
            isProcessing = false;
            updateScanStatus('Initializing camera...', 'searching');

            // Gracefully stop previous instance
            if (html5QrCode) {
                try {
                    if (html5QrCode.isScanning) {
                        await html5QrCode.stop();
                    }
                    html5QrCode.clear();
                } catch (e) {}
                html5QrCode = null;
            }

            if (typeof Html5Qrcode === 'undefined') {
                loadingIndicator.style.display = 'none';
                errorText.textContent = 'Camera scanner library not yet loaded. Please use manual token entry or verify network connection.';
                errorBanner.style.display = 'block';
                updateScanStatus('Scanner library unavailable', 'error');
                return;
            }

            try {
                // Initialize Html5Qrcode with QR code format only & native BarcodeDetector optimization
                const formats = (typeof Html5QrcodeSupportedFormats !== 'undefined' && Html5QrcodeSupportedFormats.QR_CODE)
                    ? [Html5QrcodeSupportedFormats.QR_CODE]
                    : undefined;

                html5QrCode = new Html5Qrcode("emg-qr-reader", {
                    formatsToSupport: formats,
                    verbose: false,
                    experimentalFeatures: {
                        useBarCodeDetectorIfSupported: true
                    }
                });

                // Enumerate cameras
                const devices = await Html5Qrcode.getCameras();
                if (devices && devices.length > 0) {
                    cameraSelect.innerHTML = '';
                    devices.forEach((dev, idx) => {
                        const opt = document.createElement('option');
                        opt.value = dev.id;
                        opt.textContent = dev.label || `Camera ${idx + 1}`;
                        cameraSelect.appendChild(opt);
                    });
                    cameraSelect.style.display = 'block';

                    // Pick rear camera by default on mobile, or first camera on desktop/laptop
                    let chosenCameraId = selectedCameraId;
                    if (!chosenCameraId) {
                        const backCam = devices.find(d => {
                            const lbl = (d.label || '').toLowerCase();
                            return lbl.includes('back') || lbl.includes('rear') || lbl.includes('environment');
                        });
                        chosenCameraId = backCam ? backCam.id : devices[0].id;
                    }
                    cameraSelect.value = chosenCameraId;

                    // Scanner configuration: generous responsive qrbox, 15 fps scanning rate
                    const scanConfig = {
                        fps: 15,
                        qrbox: function (viewfinderWidth, viewfinderHeight) {
                            const minEdge = Math.min(viewfinderWidth, viewfinderHeight);
                            const size = Math.floor(minEdge * 0.85);
                            return {
                                width: Math.max(size, 200),
                                height: Math.max(size, 200)
                            };
                        }
                    };

                    const cameraConfig = chosenCameraId ? chosenCameraId : { facingMode: "environment" };

                    await html5QrCode.start(
                        cameraConfig,
                        scanConfig,
                        (decodedText) => {
                            window.EmergencyScanner.handleDecodedText(decodedText);
                        },
                        (errorMessage) => {
                            // Frame scan miss (frame did not contain decodable QR)
                        }
                    );

                    loadingIndicator.style.display = 'none';
                    updateScanStatus('Searching for QR code...', 'searching');
                } else {
                    loadingIndicator.style.display = 'none';
                    errorText.textContent = 'No compatible camera was found on this device. Please use the manual token fallback below.';
                    errorBanner.style.display = 'block';
                    updateScanStatus('No camera found', 'error');
                }
            } catch (err) {
                console.error('Camera init error:', err);
                loadingIndicator.style.display = 'none';
                restartBtn.style.display = 'inline-block';
                updateScanStatus('Camera unavailable', 'error');

                let msg = 'Camera is currently unavailable.';
                if (err && (err.name === 'NotAllowedError' || err.name === 'PermissionDeniedError')) {
                    msg = 'No camera permission. Please allow camera access in your browser or enter the token manually.';
                } else if (err && err.name === 'NotFoundError') {
                    msg = 'No compatible camera was found.';
                } else if (err && err.name === 'NotReadableError') {
                    msg = 'Camera is currently unavailable (may be in use by another application).';
                } else if (err && err.message) {
                    msg = err.message;
                }
                errorText.textContent = msg;
                errorBanner.style.display = 'block';
            }
        },

        switchCamera: async function (cameraId) {
            if (cameraId) {
                await this.startCamera(cameraId);
            }
        },

        handleDecodedText: async function (rawText) {
            if (isProcessing) return;
            isProcessing = true;

            const token = extractToken(rawText);
            if (!token) {
                isProcessing = false;
                window.toast.error('This QR code is not a MediCareX Emergency QR.');
                updateScanStatus('Invalid QR format', 'error');
                return;
            }

            updateScanStatus('QR detected. Verifying emergency card...', 'detected');

            // Audio confirmation beep
            try {
                const AudioCtx = window.AudioContext || window.webkitAudioContext;
                if (AudioCtx) {
                    const ctx = new AudioCtx();
                    const osc = ctx.createOscillator();
                    const gain = ctx.createGain();
                    osc.type = 'sine';
                    osc.frequency.setValueAtTime(880, ctx.currentTime);
                    gain.gain.setValueAtTime(0.2, ctx.currentTime);
                    osc.connect(gain);
                    gain.connect(ctx.destination);
                    osc.start();
                    osc.stop(ctx.currentTime + 0.15);
                }
            } catch (e) {}

            await this.closeScanner();
            await this.processToken(token);
        },

        submitManualToken: async function () {
            if (isProcessing) return;
            const input = document.getElementById('emg-manual-token-input');
            const raw = input ? input.value : '';
            const token = extractToken(raw);

            if (!token) {
                window.toast.error('Please enter a valid Emergency Token.');
                return;
            }

            isProcessing = true;
            updateScanStatus('Verifying emergency token...', 'detected');
            await this.closeScanner();
            await this.processToken(token);
        },

        processToken: async function (token) {
            try {
                window.toast.info('Verifying emergency card with MySQL...');
                const res = await window.api.scanEmergencyCard(token);

                if (res && res.success && res.data) {
                    const card = res.data;
                    window.toast.success(`Emergency card verified for ${card.patientName || 'Patient'}`);
                    
                    if (currentOnSuccessCallback) {
                        currentOnSuccessCallback(card);
                    }
                    
                    this.showResultModal(card, currentRole);
                } else {
                    const msg = (res && res.message) ? res.message : 'Invalid or expired MediCareX Emergency QR.';
                    window.toast.error(msg);
                }
            } catch (err) {
                console.error('QR Scan error:', err);
                const msg = (err && err.message) ? err.message : 'Unable to verify the emergency card. Check your connection and try again.';
                window.toast.error(msg);
            } finally {
                isProcessing = false;
            }
        },

        showResultModal: function (card, role = 'DOCTOR') {
            ensureModalsInDom();

            document.getElementById('emg-res-name').textContent = card.patientName || 'Patient Profile';
            document.getElementById('emg-res-phone').textContent = card.phone ? 'Phone: ' + card.phone : 'Phone: N/A';
            document.getElementById('emg-res-blood').textContent = card.bloodGroup || 'O+';
            
            document.getElementById('emg-res-allergies').textContent = (card.allergies && card.allergies.trim()) ? card.allergies : 'None declared (No known drug/food allergies)';
            document.getElementById('emg-res-conditions').textContent = (card.chronicConditions && card.chronicConditions.trim()) ? card.chronicConditions : 'None declared';
            document.getElementById('emg-res-meds').textContent = (card.currentMedicationsSummary && card.currentMedicationsSummary.trim()) ? card.currentMedicationsSummary : 'None listed';

            const cName = card.emergencyContactName || 'None declared';
            const cRel = card.emergencyContactRelation ? ` (${card.emergencyContactRelation})` : '';
            const cPhone = card.emergencyContactPhone || '';

            document.getElementById('emg-res-contact-name').textContent = cName + cRel;
            document.getElementById('emg-res-contact-phone').textContent = cPhone || 'No phone provided';

            const callBtn = document.getElementById('emg-res-contact-call');
            if (cPhone) {
                callBtn.href = `tel:${cPhone}`;
                callBtn.style.display = 'inline-flex';
            } else {
                callBtn.style.display = 'none';
            }

            document.getElementById('emg-res-donor').textContent = card.isOrganDonor ? 'YES (Registered Pledge)' : 'No Pledge';
            if (!card.isOrganDonor) {
                document.getElementById('emg-res-donor').style.color = '#64748b';
            } else {
                document.getElementById('emg-res-donor').style.color = '#10b981';
            }

            const insText = (card.insuranceProvider ? card.insuranceProvider : 'None') + (card.insurancePolicyNumber ? ` (${card.insurancePolicyNumber})` : '');
            document.getElementById('emg-res-insurance').textContent = insText;

            if (card.lastUpdated) {
                document.getElementById('emg-res-updated').textContent = `Verified MySQL Emergency Record • Updated: ${card.lastUpdated}`;
            }

            // Doctor-specific EHR link
            const ehrActionDiv = document.getElementById('emg-doctor-ehr-action');
            const ehrLinkBtn = document.getElementById('emg-btn-view-ehr');
            if (role === 'DOCTOR' && card.patientId) {
                ehrLinkBtn.href = `/doctor-patient-overview.html?id=${card.patientId}`;
                ehrActionDiv.style.display = 'block';
            } else {
                ehrActionDiv.style.display = 'none';
            }

            document.getElementById('emg-result-modal').style.display = 'flex';
        },

        closeScanner: async function () {
            if (html5QrCode) {
                try {
                    if (html5QrCode.isScanning) {
                        await html5QrCode.stop();
                    }
                    html5QrCode.clear();
                } catch (e) {}
                html5QrCode = null;
            }
            isProcessing = false;
            const modal = document.getElementById('emg-scanner-modal');
            if (modal) modal.style.display = 'none';
        },

        closeResultModal: function () {
            const modal = document.getElementById('emg-result-modal');
            if (modal) modal.style.display = 'none';
        }
    };
})();
