/* ============================
   NeuroApp - Main App Logic
   ============================ */

// State
let currentPage = 'dashboard';
let appVersion = '1.0.0';

// ===== App Initialization =====
function onAppReady(version) {
    appVersion = version;
    document.getElementById('versionBadge').textContent = 'v' + version;
    document.getElementById('aboutVersion').textContent = 'Version ' + version;
    loadDashboard();
    updateHeaderStatus();
}

// Splash screen auto-hide
setTimeout(() => {
    const splash = document.getElementById('splash');
    const app = document.getElementById('app');
    if (splash) splash.style.display = 'none';
    if (app) app.style.display = 'flex';

    // Load data after splash
    setTimeout(() => {
        loadDashboard();
        loadSettings();
    }, 200);
}, 3000);

// ===== Navigation =====
function navigateTo(page) {
    currentPage = page;

    // Update pages
    document.querySelectorAll('.page').forEach(p => p.classList.remove('active'));
    const targetPage = document.getElementById('page-' + page);
    if (targetPage) targetPage.classList.add('active');

    // Update nav
    document.querySelectorAll('.nav-item').forEach(n => n.classList.remove('active'));
    const navItem = document.querySelector('.nav-item[data-page="' + page + '"]');
    if (navItem) navItem.classList.add('active');

    // Page-specific load
    switch (page) {
        case 'dashboard': loadDashboard(); break;
        case 'plugins': loadPlugins(); break;
        case 'settings': loadSettings(); break;
    }
}

function onBackPressed() {
    if (currentPage !== 'dashboard') {
        navigateTo('dashboard');
    }
}

// ===== Dashboard =====
function loadDashboard() {
    updateGreeting();
    loadStats();
    loadProjects();
}

function updateGreeting() {
    const hour = new Date().getHours();
    let greeting;
    if (hour < 12) greeting = 'Good Morning! ☀️';
    else if (hour < 18) greeting = 'Good Afternoon! 🌤️';
    else greeting = 'Good Evening! 🌙';
    document.getElementById('greeting').textContent = greeting;
}

function loadStats() {
    try {
        if (typeof NeuroApp !== 'undefined') {
            const stats = JSON.parse(NeuroApp.getDashboardStats());
            document.getElementById('statProjects').textContent = stats.totalProjects || 0;
            document.getElementById('statFiles').textContent = stats.totalFiles || 0;
            document.getElementById('statLines').textContent = formatNumber(stats.linesGenerated || 0);
            document.getElementById('statRequests').textContent = stats.aiRequests || 0;
        }
    } catch (e) {
        console.log('Stats load error:', e);
    }
}

function loadProjects() {
    try {
        if (typeof NeuroApp !== 'undefined') {
            const projects = JSON.parse(NeuroApp.listProjects());
            const container = document.getElementById('projectsList');

            if (!projects || projects.length === 0) {
                container.innerHTML = `
                    <div class="empty-state">
                        <span class="empty-icon">🚀</span>
                        <p>No projects yet. Create one to start!</p>
                    </div>`;
                return;
            }

            container.innerHTML = projects.map(p => `
                <div class="project-item" onclick="openProject('${escapeHtml(p.name)}')">
                    <div class="project-icon">📁</div>
                    <div class="project-info">
                        <div class="project-name">${escapeHtml(p.name)}</div>
                        <div class="project-meta">${p.files} files • ${formatDate(p.lastModified)}</div>
                    </div>
                    <span class="project-arrow">›</span>
                </div>
            `).join('');
        }
    } catch (e) {
        console.log('Projects load error:', e);
    }
}

function openProject(name) {
    // Switch to editor and load project files
    window.currentProject = name;
    navigateTo('editor');
    loadProjectFiles(name);
    addActivity('Opened project: ' + name, 'blue');
}

function showNewProjectDialog() {
    showModal('New Project', `
        <input type="text" class="modal-input" id="newProjectName" placeholder="Project name..." autofocus>
        <button class="modal-btn" onclick="createNewProject()">Create Project</button>
    `);
    setTimeout(() => {
        const input = document.getElementById('newProjectName');
        if (input) input.focus();
    }, 300);
}

function createNewProject() {
    const name = document.getElementById('newProjectName').value.trim();
    if (!name) {
        showToast('Please enter a project name', 'error');
        return;
    }

    try {
        if (typeof NeuroApp !== 'undefined') {
            const result = JSON.parse(NeuroApp.createProject(name));
            if (result.success) {
                showToast('Project created! 🎉', 'success');
                closeModal();
                loadProjects();
                loadStats();
                addActivity('Created project: ' + name, 'green');
                openProject(name);
            } else {
                showToast(result.message, 'error');
            }
        }
    } catch (e) {
        showToast('Error: ' + e.message, 'error');
    }
}

// ===== Activity Feed =====
function addActivity(text, color) {
    const feed = document.getElementById('activityFeed');
    const item = document.createElement('div');
    item.className = 'activity-item';
    item.innerHTML = `
        <span class="activity-dot dot-${color || 'green'}"></span>
        <span class="activity-text">${escapeHtml(text)}</span>
        <span class="activity-time">Just now</span>
    `;
    feed.insertBefore(item, feed.firstChild);

    // Keep only last 20
    while (feed.children.length > 20) {
        feed.removeChild(feed.lastChild);
    }
}

// ===== Modal =====
function showModal(title, body) {
    document.getElementById('modalTitle').textContent = title;
    document.getElementById('modalBody').innerHTML = body;
    document.getElementById('modalOverlay').style.display = 'flex';
}

function closeModal() {
    document.getElementById('modalOverlay').style.display = 'none';
}

// ===== Toast =====
let toastTimeout;
function showToast(message, type) {
    const toast = document.getElementById('toast');
    toast.textContent = message;
    toast.className = 'toast ' + (type || '') + ' show';

    clearTimeout(toastTimeout);
    toastTimeout = setTimeout(() => {
        toast.classList.remove('show');
    }, 2500);
}

// ===== Notifications =====
function toggleNotifications() {
    showToast('No new notifications', 'info');
}

// ===== Utilities =====
function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function formatNumber(n) {
    if (n >= 1000000) return (n / 1000000).toFixed(1) + 'M';
    if (n >= 1000) return (n / 1000).toFixed(1) + 'K';
    return n.toString();
}

function formatDate(timestamp) {
    if (!timestamp) return 'Unknown';
    const date = new Date(timestamp);
    const now = new Date();
    const diff = now - date;

    if (diff < 60000) return 'Just now';
    if (diff < 3600000) return Math.floor(diff / 60000) + 'm ago';
    if (diff < 86400000) return Math.floor(diff / 3600000) + 'h ago';
    return date.toLocaleDateString();
}

// AI callback registry
const aiCallbacks = {};
let callbackCounter = 0;

function registerCallback(onSuccess, onError) {
    const id = 'cb_' + (++callbackCounter);
    aiCallbacks[id] = { onSuccess, onError };
    return id;
}

function onAIResponse(callbackId, result, error) {
    const cb = aiCallbacks[callbackId];
    if (cb) {
        if (error) {
            cb.onError(error);
        } else {
            cb.onSuccess(result);
        }
        delete aiCallbacks[callbackId];
    }
}

function onUpdateCheck(callbackId, version, changelog, status) {
    const cb = aiCallbacks[callbackId];
    if (cb) {
        cb.onSuccess({ version, changelog, status });
        delete aiCallbacks[callbackId];
    }
}

console.log('NeuroApp initialized');

function updateHeaderStatus() {
    // Check AI Provider status
    if (typeof NeuroApp !== 'undefined') {
        try {
            const settings = JSON.parse(NeuroApp.getSettings());
            const badge = document.getElementById('versionBadge');

            if (settings.aiProvider === 'proxypal') {
                badge.innerHTML = '<span style="color:#4caf50;font-size:10px;">●</span> ProxyPal';
                badge.style.borderColor = '#4caf50';
                badge.style.color = '#a5d6a7';
                badge.onclick = () => navigateTo('settings');
            } else if (settings.aiProvider === 'openai') {
                badge.innerHTML = 'GPT';
                badge.style.borderColor = 'rgba(255, 255, 255, 0.1)';
                badge.style.color = 'var(--text-secondary)';
            } else {
                badge.innerHTML = 'Gemini';
                badge.style.borderColor = 'rgba(255, 255, 255, 0.1)';
                badge.style.color = 'var(--text-secondary)';
            }
        } catch (e) { }
    }
}
