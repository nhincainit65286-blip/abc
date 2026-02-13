/* ============================
   NeuroApp - Plugin System
   ============================ */

function loadPlugins() {
    loadAvailablePlugins();
    loadInstalledPlugins();
}

function showPluginSection(section) {
    document.querySelectorAll('.plugin-tab').forEach(t => t.classList.remove('active'));
    document.querySelector(`.plugin-tab[onclick*="${section}"]`).classList.add('active');

    document.getElementById('pluginsAvailable').style.display = section === 'available' ? 'grid' : 'none';
    document.getElementById('pluginsInstalled').style.display = section === 'installed' ? 'grid' : 'none';
}

function loadAvailablePlugins() {
    try {
        if (typeof NeuroApp !== 'undefined') {
            const plugins = JSON.parse(NeuroApp.getAvailablePlugins());
            const container = document.getElementById('pluginsAvailable');

            container.innerHTML = plugins.map(p => {
                const icon = getCategoryIcon(p.category);
                const actionBtn = p.installed
                    ? `<button class="plugin-action-btn uninstall" onclick="uninstallPlugin('${p.id}')">Remove</button>`
                    : `<button class="plugin-action-btn install" onclick="installPluginFromCatalog('${p.id}','${escapeHtml(p.name)}','${p.version}','${escapeHtml(p.description)}')">Install</button>`;

                return `
                    <div class="plugin-card">
                        <div class="plugin-icon">${icon}</div>
                        <div class="plugin-info">
                            <div class="plugin-name">${escapeHtml(p.name)}</div>
                            <div class="plugin-desc">${escapeHtml(p.description)}</div>
                        </div>
                        ${actionBtn}
                    </div>
                `;
            }).join('');
        } else {
            // Demo data
            document.getElementById('pluginsAvailable').innerHTML = getDemoPlugins();
        }
    } catch (e) {
        console.log('Load plugins error:', e);
    }
}

function loadInstalledPlugins() {
    try {
        if (typeof NeuroApp !== 'undefined') {
            const plugins = JSON.parse(NeuroApp.getInstalledPlugins());
            const container = document.getElementById('pluginsInstalled');

            if (!plugins || plugins.length === 0) {
                container.innerHTML = `
                    <div class="empty-state">
                        <span class="empty-icon">🧩</span>
                        <p>No plugins installed yet</p>
                    </div>
                `;
                return;
            }

            container.innerHTML = plugins.map(p => `
                <div class="plugin-card">
                    <div class="plugin-icon">🧩</div>
                    <div class="plugin-info">
                        <div class="plugin-name">${escapeHtml(p.name)}</div>
                        <div class="plugin-desc">v${p.version} • ${p.enabled ? '✅ Active' : '⏸️ Disabled'}</div>
                    </div>
                    <button class="plugin-action-btn uninstall" onclick="uninstallPlugin('${p.id}')">Remove</button>
                </div>
            `).join('');
        }
    } catch (e) {
        console.log('Load installed plugins error:', e);
    }
}

function installPluginFromCatalog(id, name, version, description) {
    // Generate a basic plugin scaffold using AI if available
    const code = generatePluginCode(id, name);

    try {
        if (typeof NeuroApp !== 'undefined') {
            const result = JSON.parse(NeuroApp.installPlugin(id, name, version, code, description));
            if (result.success) {
                showToast('Plugin installed: ' + name + ' 🧩', 'success');
                loadPlugins();
                if (typeof addActivity === 'function') {
                    addActivity('Installed plugin: ' + name, 'green');
                }
            } else {
                showToast(result.message, 'error');
            }
        }
    } catch (e) {
        showToast('Install error: ' + e.message, 'error');
    }
}

function uninstallPlugin(id) {
    try {
        if (typeof NeuroApp !== 'undefined') {
            const result = JSON.parse(NeuroApp.uninstallPlugin(id));
            if (result.success) {
                showToast('Plugin removed', 'success');
                loadPlugins();
            } else {
                showToast(result.message, 'error');
            }
        }
    } catch (e) {
        showToast('Uninstall error: ' + e.message, 'error');
    }
}

function generatePluginCode(id, name) {
    return `// NeuroApp Plugin: ${name}
// ID: ${id}
// Auto-generated plugin scaffold

(function() {
    'use strict';
    
    const plugin = {
        id: '${id}',
        name: '${name}',
        
        init: function() {
            console.log('[${name}] Plugin initialized');
        },
        
        execute: function(context) {
            // Plugin logic here
            console.log('[${name}] Executing...');
            return { success: true };
        },
        
        destroy: function() {
            console.log('[${name}] Plugin destroyed');
        }
    };
    
    // Register plugin
    if (typeof window.neuroPlugins === 'undefined') {
        window.neuroPlugins = {};
    }
    window.neuroPlugins['${id}'] = plugin;
    plugin.init();
})();`;
}

function getCategoryIcon(category) {
    const icons = {
        'editor': '✏️',
        'tools': '🔧',
        'productivity': '⚡',
        'ai': '🤖',
        'theme': '🎨',
        'social': '💬'
    };
    return icons[category] || '🧩';
}

function getDemoPlugins() {
    const demos = [
        { icon: '✏️', name: 'Syntax Themes', desc: 'Additional syntax highlighting themes' },
        { icon: '🔧', name: 'Git Integration', desc: 'Basic Git operations' },
        { icon: '⚡', name: 'Code Snippets', desc: 'Reusable code snippets library' },
        { icon: '✏️', name: 'Markdown Preview', desc: 'Live preview for Markdown' },
        { icon: '🔧', name: 'Terminal Emulator', desc: 'Built-in terminal' },
        { icon: '⚡', name: 'Auto Formatter', desc: 'Auto-format code on save' },
        { icon: '🤖', name: 'AI Documentation', desc: 'Auto-generate docs from code' },
        { icon: '⚡', name: 'Project Templates', desc: 'Quick-start templates' }
    ];

    return demos.map(p => `
        <div class="plugin-card">
            <div class="plugin-icon">${p.icon}</div>
            <div class="plugin-info">
                <div class="plugin-name">${p.name}</div>
                <div class="plugin-desc">${p.desc}</div>
            </div>
            <button class="plugin-action-btn install" onclick="showToast('Build and run on Android to install plugins', 'info')">Install</button>
        </div>
    `).join('');
}
