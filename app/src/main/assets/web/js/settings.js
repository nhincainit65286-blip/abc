/* ============================
   NeuroApp - Settings
   ============================ */

function loadSettings() {
    try {
        if (typeof NeuroApp !== 'undefined') {
            const settings = JSON.parse(NeuroApp.getSettings());

            document.getElementById('settingProvider').value = settings.aiProvider || 'gemini';
            document.getElementById('settingAutoUpdate').checked = settings.autoUpdate || false;
            document.getElementById('settingAutoSave').checked = settings.autoSave !== false;
            document.getElementById('settingFontSize').value = settings.fontSize || 14;
            document.getElementById('fontSizeValue').textContent = (settings.fontSize || 14) + 'px';
            document.getElementById('settingProxyPalUrl').value = settings.proxyPalUrl || '';
            document.getElementById('settingProxyPalModel').value = settings.proxyPalModel || 'claude-sonnet-4-20250514';
            document.getElementById('settingUpdateUrl').value = settings.updateUrl || '';

            // Show/hide ProxyPal section
            toggleProxyPalSettings();

            // Apply font size
            updateFontSize(settings.fontSize || 14);
        }
    } catch (e) {
        console.log('Load settings error:', e);
    }
}

function saveSetting(key, value) {
    try {
        if (typeof NeuroApp !== 'undefined') {
            NeuroApp.saveSetting(key, String(value));
            showToast('Setting saved ✅', 'success');
        }
    } catch (e) {
        showToast('Error saving setting', 'error');
    }
}

function saveApiKey(provider) {
    const inputId = provider === 'openai' ? 'settingKeyOpenai' : 'settingKeyGemini';
    const key = document.getElementById(inputId).value.trim();

    if (!key) {
        showToast('Enter an API key', 'error');
        return;
    }

    try {
        if (typeof NeuroApp !== 'undefined') {
            const settingKey = provider === 'openai' ? 'apiKeyOpenai' : 'apiKeyGemini';
            NeuroApp.saveSetting(settingKey, key);
            document.getElementById(inputId).value = '';
            showToast(provider.toUpperCase() + ' API key saved! 🔑', 'success');
            if (typeof addActivity === 'function') {
                addActivity('Updated ' + provider + ' API key', 'green');
            }
        }
    } catch (e) {
        showToast('Error saving API key', 'error');
    }
}

function saveProxyPalUrl() {
    const url = document.getElementById('settingProxyPalUrl').value.trim();
    if (!url) {
        showToast('Enter a Proxy URL', 'error');
        return;
    }
    saveSetting('proxyPalUrl', url);
}

function toggleProxyPalSettings() {
    const provider = document.getElementById('settingProvider').value;
    const section = document.getElementById('proxyPalSection');
    if (section) {
        section.style.display = provider === 'proxypal' ? 'block' : 'none';
    }
}

function testProxyPalConnection() {
    showToast('Testing ProxyPal connection... 🔌', 'info');
    // Simple test by trying to generate a short greeting
    if (typeof NeuroApp !== 'undefined') {
        const cbId = registerCallback(
            (result) => {
                showModal('Connection Successful 🔌', `
                    <div style="text-align:center; padding:12px 0;">
                        <div style="font-size:48px; margin-bottom:12px;">✅</div>
                        <p style="margin-bottom:12px;">ProxyPal is working!</p>
                        <div style="background:#1e1e1e; padding:8px; border-radius:6px; font-family:monospace; font-size:12px; text-align:left; color:#aaffaa;">
                            ${escapeHtml(result)}
                        </div>
                    </div>
                `);
            },
            (error) => {
                showModal('Connection Failed ❌', `
                    <div style="text-align:center; padding:12px 0;">
                        <div style="font-size:48px; margin-bottom:12px;">⚠️</div>
                        <p style="margin-bottom:12px;">Could not connect to ProxyPal.</p>
                        <div style="background:#2d1a1a; padding:8px; border-radius:6px; font-family:monospace; font-size:12px; text-align:left; color:#ffaaaa;">
                            ${escapeHtml(error)}
                        </div>
                        <p style="margin-top:12px; font-size:12px; color:var(--text-secondary);">
                            Make sure ProxyPal app is running and the URL is correct.<br>
                            (Default: http://localhost:8317/v1/chat/completions)
                        </p>
                    </div>
                `);
            }
        );
        NeuroApp.generateCode('Say "Connection successful!" in 3 words.', cbId);
    }
}

function updateFontSize(size) {
    document.getElementById('fontSizeValue').textContent = size + 'px';
    const editor = document.getElementById('codeEditor');
    const lineNums = document.getElementById('lineNumbers');
    if (editor) editor.style.fontSize = size + 'px';
    if (lineNums) lineNums.style.fontSize = size + 'px';
    saveSetting('fontSize', size);
}

function checkForAppUpdate() {
    showToast('Checking for updates... 🔄', 'info');

    try {
        if (typeof NeuroApp !== 'undefined') {
            const cbId = registerCallback(
                (result) => {
                    if (result.status === 'available') {
                        showModal('Update Available', `
                            <div style="text-align:center; padding:12px 0;">
                                <div style="font-size:48px; margin-bottom:12px;">🎉</div>
                                <h3>Version ${result.version}</h3>
                                <p style="color:var(--text-secondary); margin:8px 0 16px; font-size:13px; line-height:1.5;">
                                    ${escapeHtml(result.changelog || 'New version available!')}
                                </p>
                                <button class="modal-btn" onclick="downloadAppUpdate()">
                                    ⬇️ Download Update
                                </button>
                            </div>
                        `);
                    } else if (result.status === 'up_to_date') {
                        showToast('App is up to date! ✅', 'success');
                    } else {
                        showToast('Update check failed', 'error');
                    }
                },
                (error) => {
                    showToast('Update error: ' + error, 'error');
                }
            );
            NeuroApp.checkForUpdate(cbId);
        } else {
            showToast('Build and run on Android to check updates', 'info');
        }
    } catch (e) {
        showToast('Update check error', 'error');
    }
}

function downloadAppUpdate() {
    closeModal();
    showToast('Downloading update... ⬇️', 'info');

    if (typeof NeuroApp !== 'undefined') {
        const cbId = registerCallback(
            (result) => { },
            (error) => showToast('Download error: ' + error, 'error')
        );
        NeuroApp.downloadUpdate(cbId);
    }
}

function onDownloadProgress(callbackId, percent) {
    showToast('Downloading: ' + percent + '%', 'info');
}

function onDownloadComplete(callbackId, filePath) {
    showModal('Update Ready', `
        <div style="text-align:center; padding:12px 0;">
            <div style="font-size:48px; margin-bottom:12px;">✅</div>
            <h3>Download Complete</h3>
            <p style="color:var(--text-secondary); margin:8px 0 16px;">
                Tap Install to update NeuroApp.
            </p>
            <button class="modal-btn" onclick="installAppUpdate('${filePath}')">
                📦 Install Update
            </button>
        </div>
    `);
}

function onDownloadError(callbackId, error) {
    showToast('Download failed: ' + error, 'error');
}

function installAppUpdate(filePath) {
    if (typeof NeuroApp !== 'undefined') {
        NeuroApp.installUpdate(filePath);
    }
}
