/* ============================
   NeuroApp - Proxy Dashboard
   ============================ */

let proxyPollInterval = null;
let lastLogId = null;

function initProxyDashboard() {
    loadProxyStatus();
    startProxyPolling();
}

function loadProxyStatus() {
    if (typeof NeuroApp !== 'undefined') {
        const isRunning = NeuroApp.isProxyServerRunning();
        updateProxyStatusUI(isRunning);
    }
}

function updateProxyStatusUI(isRunning) {
    const statusText = document.getElementById('proxyStatusText');
    const toggle = document.getElementById('proxyServerToggle');
    const indicator = document.getElementById('proxyStatusIndicator');

    if (statusText) statusText.textContent = isRunning ? 'Running' : 'Stopped';
    if (toggle) toggle.checked = isRunning;
    if (indicator) indicator.className = isRunning ? 'status-indicator running' : 'status-indicator stopped';
}

function toggleProxyServer() {
    if (typeof NeuroApp !== 'undefined') {
        const toggle = document.getElementById('proxyServerToggle');
        if (toggle.checked) {
            if (NeuroApp.startProxyServer()) {
                showToast('Proxy Server Started 🟢', 'success');
                updateProxyStatusUI(true);
            } else {
                showToast('Failed to start server', 'error');
                toggle.checked = false;
                updateProxyStatusUI(false);
            }
        } else {
            NeuroApp.stopProxyServer();
            showToast('Proxy Server Stopped 🔴', 'info');
            updateProxyStatusUI(false);
        }
    }
}

function startProxyPolling() {
    if (proxyPollInterval) clearInterval(proxyPollInterval);
    fetchProxyLogs(); // Initial fetch
    proxyPollInterval = setInterval(fetchProxyLogs, 2000); // Poll every 2s
}

function stopProxyPolling() {
    if (proxyPollInterval) clearInterval(proxyPollInterval);
    proxyPollInterval = null;
}

function fetchProxyLogs() {
    if (typeof NeuroApp === 'undefined') return;

    try {
        const logsJson = NeuroApp.getProxyLogs();
        const logs = JSON.parse(logsJson);
        updateProxyStats(logs);
        updateProxyLogsTable(logs);
    } catch (e) {
        console.error("Error fetching proxy logs", e);
    }
}

function updateProxyStats(logs) {
    const totalReqs = logs.length;
    let errors = 0;
    let totalLatency = 0;
    let tokens = 0;

    logs.forEach(log => {
        if (log.status >= 400) errors++;
        totalLatency += log.durationMs;
        tokens += (log.promptTokens + log.completionTokens);
    });

    const avgLatency = totalReqs > 0 ? Math.round(totalLatency / totalReqs) : 0;
    const errorRate = totalReqs > 0 ? Math.round((errors / totalReqs) * 100) : 0;

    document.getElementById('statTotalReqs').textContent = totalReqs;
    document.getElementById('statAvgLatency').textContent = avgLatency + 'ms';
    document.getElementById('statErrorRate').textContent = errorRate + '%';
    document.getElementById('statTokens').textContent = tokens; // Estimated
}

function updateProxyLogsTable(logs) {
    const tbody = document.getElementById('proxyLogsBody');
    if (!tbody) return;

    // Check if new logs arrived by comparing ID of first log
    if (logs.length > 0 && logs[0].id === lastLogId) {
        return; // No change
    }
    if (logs.length > 0) lastLogId = logs[0].id;

    tbody.innerHTML = '';

    if (logs.length === 0) {
        tbody.innerHTML = '<tr><td colspan="5" style="text-align:center; color:var(--text-secondary); padding:20px;">No requests yet</td></tr>';
        return;
    }

    logs.forEach(log => {
        const row = document.createElement('tr');
        row.onclick = () => showLogDetails(log);

        const time = new Date(log.timestamp).toLocaleTimeString();
        const statusClass = log.status >= 200 && log.status < 300 ? 'status-success' : 'status-error';

        row.innerHTML = `
            <td>${time}</td>
            <td><span class="method-badge">${log.method}</span></td>
            <td>${log.model}</td>
            <td><span class="${statusClass}">${log.status}</span></td>
            <td>${log.durationMs}ms</td>
        `;
        tbody.appendChild(row);
    });
}

function showLogDetails(log) {
    const modal = document.getElementById('logDetailModal');
    const content = document.getElementById('logDetailContent');

    // Format JSON bodies
    let reqBody = log.requestBody;
    let resBody = log.responseBody;
    try {
        if (reqBody.startsWith('{')) reqBody = JSON.stringify(JSON.parse(reqBody), null, 2);
    } catch (e) { }
    try {
        if (resBody.startsWith('{')) resBody = JSON.stringify(JSON.parse(resBody), null, 2);
    } catch (e) { }

    content.innerHTML = `
        <div class="log-detail-item">
            <span class="log-detail-label">ID:</span> ${log.id}
        </div>
        <div class="log-detail-item">
            <span class="log-detail-label">Endpoint:</span> ${log.endpoint}
        </div>
         <div class="log-detail-item">
            <span class="log-detail-label">Provider:</span> ${log.provider}
        </div>
        
        <div class="log-detail-section">Request Body</div>
        <pre class="log-code-block">${escapeHtml(reqBody)}</pre>
        
        <div class="log-detail-section">Response Body</div>
        <pre class="log-code-block">${escapeHtml(resBody)}</pre>
    `;

    modal.style.display = 'flex';
}

function closeLogModal() {
    document.getElementById('logDetailModal').style.display = 'none';
}

function clearLogs() {
    if (typeof NeuroApp !== 'undefined') {
        NeuroApp.clearProxyLogs();
        fetchProxyLogs(); // Refresh immediately
        showToast('Logs cleared', 'info');
    }
}

function escapeHtml(text) {
    if (!text) return '';
    return text
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}
