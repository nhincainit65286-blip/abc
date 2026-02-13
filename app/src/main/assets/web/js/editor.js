/* ============================
   NeuroApp - Code Editor
   ============================ */

let currentFile = { project: '', path: 'untitled.js', content: '', modified: false };
let openFiles = [{ project: '', path: 'untitled.js', content: '' }];
let activeTabIndex = 0;

// ===== Editor Setup =====
document.addEventListener('DOMContentLoaded', () => {
    const editor = document.getElementById('codeEditor');
    if (!editor) return;

    // Update line numbers on input
    editor.addEventListener('input', () => {
        updateLineNumbers();
        updateEditorStatus();
        currentFile.content = editor.value;
        currentFile.modified = true;

        // Auto-save
        if (window.autoSaveTimer) clearTimeout(window.autoSaveTimer);
        window.autoSaveTimer = setTimeout(() => {
            if (currentFile.project && currentFile.modified) {
                autoSaveFile();
            }
        }, 2000);
    });

    // Update cursor position
    editor.addEventListener('click', updateEditorStatus);
    editor.addEventListener('keyup', updateEditorStatus);

    // Tab key handling
    editor.addEventListener('keydown', (e) => {
        if (e.key === 'Tab') {
            e.preventDefault();
            const start = editor.selectionStart;
            const end = editor.selectionEnd;
            editor.value = editor.value.substring(0, start) + '    ' + editor.value.substring(end);
            editor.selectionStart = editor.selectionEnd = start + 4;
            updateLineNumbers();
        }

        // Enter with auto-indent
        if (e.key === 'Enter') {
            e.preventDefault();
            const start = editor.selectionStart;
            const currentLine = editor.value.substring(0, start).split('\n').pop();
            const indent = currentLine.match(/^\s*/)[0];
            const lastChar = currentLine.trim().slice(-1);

            let newIndent = indent;
            if (lastChar === '{' || lastChar === '(' || lastChar === '[' || lastChar === ':') {
                newIndent = indent + '    ';
            }

            editor.value = editor.value.substring(0, start) + '\n' + newIndent + editor.value.substring(editor.selectionEnd);
            editor.selectionStart = editor.selectionEnd = start + 1 + newIndent.length;
            updateLineNumbers();
        }
    });

    // Sync scroll with line numbers
    editor.addEventListener('scroll', () => {
        const lineNums = document.getElementById('lineNumbers');
        if (lineNums) lineNums.scrollTop = editor.scrollTop;
    });

    updateLineNumbers();
});

function updateLineNumbers() {
    const editor = document.getElementById('codeEditor');
    const lineNums = document.getElementById('lineNumbers');
    if (!editor || !lineNums) return;

    const lines = editor.value.split('\n').length;
    const nums = [];
    for (let i = 1; i <= Math.max(lines, 20); i++) {
        nums.push('<span>' + i + '</span>');
    }
    lineNums.innerHTML = nums.join('');
}

function updateEditorStatus() {
    const editor = document.getElementById('codeEditor');
    if (!editor) return;

    const pos = editor.selectionStart;
    const text = editor.value.substring(0, pos);
    const lines = text.split('\n');
    const line = lines.length;
    const col = lines[lines.length - 1].length + 1;

    document.getElementById('editorPos').textContent = `Ln ${line}, Col ${col}`;
    document.getElementById('editorSize').textContent = `${editor.value.length} chars`;
}

// ===== File Operations =====
function loadProjectFiles(projectName) {
    window.currentProject = projectName;

    try {
        if (typeof NeuroApp !== 'undefined') {
            const files = JSON.parse(NeuroApp.listFiles(projectName));
            renderFileTree(files, projectName);

            // Open first file
            if (files.length > 0) {
                const firstFile = files.find(f => !f.isDir);
                if (firstFile) {
                    openFile(projectName, firstFile.path);
                }
            }

            // Show file explorer
            document.getElementById('fileExplorer').classList.remove('collapsed');
        }
    } catch (e) {
        console.log('Load files error:', e);
    }
}

function renderFileTree(files, projectName) {
    const tree = document.getElementById('fileTree');
    if (!tree) return;

    tree.innerHTML = files.map(f => {
        const icon = f.isDir ? '📁' : getFileIcon(f.name);
        const indent = (f.path.split('/').length - 1) * 16;

        if (f.isDir) {
            return `<div class="file-tree-item" style="padding-left:${8 + indent}px">
                <span class="ft-icon">${icon}</span>
                <span>${escapeHtml(f.name)}</span>
            </div>`;
        }

        return `<div class="file-tree-item" onclick="openFile('${escapeHtml(projectName)}','${escapeHtml(f.path)}')" 
                style="padding-left:${8 + indent}px">
            <span class="ft-icon">${icon}</span>
            <span>${escapeHtml(f.name)}</span>
        </div>`;
    }).join('');
}

function getFileIcon(name) {
    const ext = name.split('.').pop().toLowerCase();
    const icons = {
        'js': '🟨', 'ts': '🔷', 'py': '🐍', 'java': '☕',
        'html': '🌐', 'css': '🎨', 'json': '📋', 'md': '📝',
        'txt': '📄', 'xml': '📦', 'yaml': '📑', 'yml': '📑',
        'sh': '⚙️', 'bat': '⚙️', 'rb': '💎', 'go': '🔵',
        'rs': '🦀', 'c': '🔧', 'cpp': '🔧', 'h': '📎',
        'php': '🐘', 'swift': '🕊️', 'kt': '🟪'
    };
    return icons[ext] || '📄';
}

function getLanguage(name) {
    const ext = name.split('.').pop().toLowerCase();
    const langs = {
        'js': 'JavaScript', 'ts': 'TypeScript', 'py': 'Python',
        'java': 'Java', 'html': 'HTML', 'css': 'CSS',
        'json': 'JSON', 'md': 'Markdown', 'xml': 'XML',
        'sh': 'Shell', 'rb': 'Ruby', 'go': 'Go',
        'rs': 'Rust', 'c': 'C', 'cpp': 'C++',
        'php': 'PHP', 'swift': 'Swift', 'kt': 'Kotlin'
    };
    return langs[ext] || 'Plain Text';
}

function openFile(projectName, filePath) {
    try {
        if (typeof NeuroApp !== 'undefined') {
            const content = NeuroApp.readFile(projectName, filePath);
            const editor = document.getElementById('codeEditor');

            // Save current file first
            if (currentFile.modified && currentFile.project) {
                saveCurrentFile(true);
            }

            currentFile = { project: projectName, path: filePath, content: content, modified: false };
            editor.value = content;
            updateLineNumbers();
            updateEditorStatus();

            // Update language indicator
            document.getElementById('editorLang').textContent = getLanguage(filePath);

            // Update tabs
            addTab(filePath);

            // Highlight in file tree
            document.querySelectorAll('.file-tree-item').forEach(item => item.classList.remove('active'));

            if (typeof addActivity === 'function') {
                addActivity('Opened: ' + filePath, 'blue');
            }
        }
    } catch (e) {
        console.log('Open file error:', e);
    }
}

function saveCurrentFile(silent) {
    if (!currentFile.project) {
        if (!silent) showToast('No project open. Create one first!', 'error');
        return;
    }

    try {
        if (typeof NeuroApp !== 'undefined') {
            const editor = document.getElementById('codeEditor');
            const result = JSON.parse(NeuroApp.writeFile(currentFile.project, currentFile.path, editor.value));
            currentFile.modified = false;

            if (!silent) {
                showToast('File saved! 💾', 'success');
                if (typeof addActivity === 'function') {
                    addActivity('Saved: ' + currentFile.path, 'green');
                }
            }
        }
    } catch (e) {
        if (!silent) showToast('Save error: ' + e.message, 'error');
    }
}

function autoSaveFile() {
    saveCurrentFile(true);
}

function createNewFile() {
    showModal('New File', `
        <input type="text" class="modal-input" id="newFileName" placeholder="filename.js" autofocus>
        <button class="modal-btn" onclick="doCreateFile()">Create</button>
    `);
}

function doCreateFile() {
    const name = document.getElementById('newFileName').value.trim();
    if (!name) {
        showToast('Enter a file name', 'error');
        return;
    }

    const project = window.currentProject;
    if (!project) {
        showToast('Open a project first', 'error');
        return;
    }

    try {
        if (typeof NeuroApp !== 'undefined') {
            const result = JSON.parse(NeuroApp.createFile(project, name));
            if (result.success) {
                closeModal();
                loadProjectFiles(project);
                openFile(project, name);
                showToast('File created! 📝', 'success');
            } else {
                showToast(result.message, 'error');
            }
        }
    } catch (e) {
        showToast('Error: ' + e.message, 'error');
    }
}

// ===== Tabs =====
function addTab(filePath) {
    const tabs = document.getElementById('editorTabs');
    const name = filePath.split('/').pop();

    // Check if tab exists
    const existing = tabs.querySelector(`[data-file="${filePath}"]`);
    if (existing) {
        // Activate existing tab
        tabs.querySelectorAll('.editor-tab').forEach(t => t.classList.remove('active'));
        existing.classList.add('active');
        return;
    }

    // Deactivate all tabs
    tabs.querySelectorAll('.editor-tab').forEach(t => t.classList.remove('active'));

    const tab = document.createElement('div');
    tab.className = 'editor-tab active';
    tab.dataset.file = filePath;
    tab.innerHTML = `
        <span class="tab-name">${escapeHtml(name)}</span>
        <button class="tab-close" onclick="event.stopPropagation(); closeTab(this)">×</button>
    `;
    tab.addEventListener('click', () => {
        tabs.querySelectorAll('.editor-tab').forEach(t => t.classList.remove('active'));
        tab.classList.add('active');
        openFile(window.currentProject, filePath);
    });
    tabs.appendChild(tab);
}

function closeTab(btn) {
    const tab = btn.parentElement;
    const tabs = document.getElementById('editorTabs');
    tab.remove();

    // If active tab closed, open the last remaining tab
    if (tab.classList.contains('active')) {
        const remaining = tabs.querySelectorAll('.editor-tab');
        if (remaining.length > 0) {
            const lastTab = remaining[remaining.length - 1];
            lastTab.classList.add('active');
            openFile(window.currentProject, lastTab.dataset.file);
        }
    }
}

function toggleFileExplorer() {
    document.getElementById('fileExplorer').classList.toggle('collapsed');
}

// ===== Code Execution =====
function runCode() {
    const editor = document.getElementById('codeEditor');
    const code = editor.value;
    const lang = document.getElementById('editorLang').textContent;

    if (lang === 'JavaScript') {
        try {
            // Run in sandboxed context
            const result = new Function('"use strict";\n' + code)();
            showToast('Code executed successfully! ✅', 'success');
            if (typeof addActivity === 'function') {
                addActivity('Ran code: ' + currentFile.path, 'green');
            }
        } catch (e) {
            showToast('Error: ' + e.message, 'error');
            if (typeof addActivity === 'function') {
                addActivity('Code error: ' + e.message, 'orange');
            }
        }
    } else {
        showToast('Direct execution for ' + lang + ' coming soon!', 'info');
    }
}

// ===== AI Integration =====
function aiAnalyzeCurrentCode() {
    const editor = document.getElementById('codeEditor');
    const code = editor.value.trim();

    if (!code) {
        showToast('Write some code first!', 'error');
        return;
    }

    showToast('AI analyzing code... 🔍', 'info');

    if (typeof NeuroApp !== 'undefined') {
        const cbId = registerCallback(
            (result) => {
                showModal('AI Analysis', `
                    <div style="white-space:pre-wrap; font-size:13px; line-height:1.6; color:var(--text-secondary);">
                        ${formatAIResponse(result)}
                    </div>
                    <div style="margin-top:12px; display:flex; gap:8px;">
                        <button class="modal-btn" onclick="applyAICode('${encodeURIComponent(result)}')">Apply Suggestions</button>
                    </div>
                `);
                if (typeof addActivity === 'function') {
                    addActivity('AI analyzed: ' + currentFile.path, 'purple');
                }
            },
            (error) => showToast('AI Error: ' + error, 'error')
        );
        NeuroApp.analyzeCode(code, cbId);
    }
}

function aiEvolveCurrentCode() {
    const editor = document.getElementById('codeEditor');
    const code = editor.value.trim();

    if (!code) {
        showToast('Write some code first!', 'error');
        return;
    }

    showModal('Self-Evolve Goals', `
        <p style="font-size:13px; color:var(--text-secondary); margin-bottom:12px;">
            Describe what you want the evolved code to achieve:
        </p>
        <textarea class="modal-input" id="evolveGoals" rows="3" placeholder="e.g., Better performance, add error handling, add new features..."></textarea>
        <button class="modal-btn" onclick="doEvolve()">🧬 Evolve Code</button>
    `);
}

function doEvolve() {
    const goals = document.getElementById('evolveGoals').value.trim();
    if (!goals) {
        showToast('Describe your goals!', 'error');
        return;
    }

    closeModal();
    showToast('AI evolving code... 🧬', 'info');

    const editor = document.getElementById('codeEditor');
    const code = editor.value;

    if (typeof NeuroApp !== 'undefined') {
        const cbId = registerCallback(
            (result) => {
                // Extract code from response
                const evolved = extractCode(result);
                if (evolved) {
                    editor.value = evolved;
                    updateLineNumbers();
                    currentFile.modified = true;
                    showToast('Code evolved! 🧬✨', 'success');
                } else {
                    showModal('Evolution Result', `
                        <div style="white-space:pre-wrap; font-size:13px; line-height:1.6;">
                            ${formatAIResponse(result)}
                        </div>
                    `);
                }
                if (typeof addActivity === 'function') {
                    addActivity('Code evolved: ' + currentFile.path, 'purple');
                }
                if (typeof NeuroApp !== 'undefined') {
                    NeuroApp.incrementStat('lines_generated', editor.value.split('\n').length);
                }
            },
            (error) => showToast('Evolve error: ' + error, 'error')
        );
        NeuroApp.selfEvolve(code, goals, cbId);
    }
}

function applyAICode(encodedResult) {
    const result = decodeURIComponent(encodedResult);
    const code = extractCode(result);
    if (code) {
        const editor = document.getElementById('codeEditor');
        editor.value = code;
        updateLineNumbers();
        currentFile.modified = true;
        closeModal();
        showToast('AI suggestions applied! ✅', 'success');
    } else {
        showToast('No code block found in response', 'info');
    }
}

function extractCode(text) {
    // Try to extract code from markdown code blocks
    const codeBlockRegex = /```[\w]*\n([\s\S]*?)```/g;
    const matches = [];
    let match;
    while ((match = codeBlockRegex.exec(text)) !== null) {
        matches.push(match[1].trim());
    }

    // Return the longest code block (likely the main code)
    if (matches.length > 0) {
        return matches.sort((a, b) => b.length - a.length)[0];
    }
    return null;
}

function formatAIResponse(text) {
    if (!text) return '';
    return escapeHtml(text)
        .replace(/```(\w*)\n([\s\S]*?)```/g, '<pre style="background:var(--bg-primary);padding:10px;border-radius:8px;overflow-x:auto;border:1px solid var(--border-color);">$2</pre>')
        .replace(/`([^`]+)`/g, '<code style="background:var(--bg-tertiary);padding:2px 4px;border-radius:4px;">$1</code>')
        .replace(/## (.+)/g, '<h4 style="color:var(--accent-purple);margin:8px 0;">$1</h4>')
        .replace(/\*\*(.+?)\*\*/g, '<strong style="color:var(--text-primary);">$1</strong>')
        .replace(/\n/g, '<br>');
}
