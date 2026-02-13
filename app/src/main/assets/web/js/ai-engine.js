/* ============================
   NeuroApp - AI Engine (Frontend)
   ============================ */

let aiMode = 'chat';
let isAIProcessing = false;

function setAIMode(mode) {
    aiMode = mode;
    document.querySelectorAll('.ai-mode').forEach(b => b.classList.remove('active'));
    document.querySelector(`.ai-mode[data-mode="${mode}"]`).classList.add('active');

    const input = document.getElementById('aiInput');
    switch (mode) {
        case 'chat':
            input.placeholder = 'Nhập prompt để generate code...';
            break;
        case 'generate':
            input.placeholder = 'Mô tả code bạn muốn tạo...';
            break;
        case 'evolve':
            input.placeholder = 'Paste code và mục tiêu cải tiến...';
            break;
    }
}

function quickPrompt(text) {
    document.getElementById('aiInput').value = text;
    sendAIMessage();
}

function sendAIMessage() {
    const input = document.getElementById('aiInput');
    const message = input.value.trim();

    if (!message || isAIProcessing) return;

    isAIProcessing = true;
    input.value = '';
    autoResizeInput();

    // Add user message to chat
    addChatMessage(message, 'user');

    // Show typing indicator
    showTypingIndicator();

    // Send to AI
    if (typeof NeuroApp !== 'undefined') {
        let prompt = message;

        if (aiMode === 'generate') {
            prompt = `Generate complete, production-ready code for: ${message}\n\nInclude:
- Clean, commented code
- Error handling
- Best practices
Return the code in a markdown code block.`;
        } else if (aiMode === 'evolve') {
            prompt = `Evolve and improve this code/concept: ${message}\n\nMake it:
- More efficient
- Better structured
- With enhanced features
- Production-ready
Explain changes and return improved code.`;
        }

        const cbId = registerCallback(
            (result) => {
                hideTypingIndicator();
                addChatMessage(result, 'ai');
                isAIProcessing = false;

                // Track stats
                NeuroApp.incrementStat('ai_requests', 1);
                const lines = (result.match(/\n/g) || []).length;
                NeuroApp.incrementStat('lines_generated', lines);
            },
            (error) => {
                hideTypingIndicator();
                addChatMessage('❌ Error: ' + error, 'ai');
                isAIProcessing = false;
            }
        );
        NeuroApp.generateCode(prompt, cbId);
    } else {
        // Demo mode (no native bridge)
        setTimeout(() => {
            hideTypingIndicator();
            addChatMessage('🔌 AI Engine is not connected. Please build and run on Android device to use AI features.\n\nMake sure you have set your API key in Settings.', 'ai');
            isAIProcessing = false;
        }, 1500);
    }
}

function addChatMessage(text, type) {
    const chat = document.getElementById('aiChat');

    // Remove welcome message if present
    const welcome = chat.querySelector('.ai-welcome');
    if (welcome) welcome.remove();

    const msg = document.createElement('div');
    msg.className = 'chat-message ' + type;

    const avatar = type === 'ai' ? '🧠' : '👤';

    let content = '';
    if (type === 'ai') {
        content = formatAIResponseForChat(text);
    } else {
        content = escapeHtml(text);
    }

    msg.innerHTML = `
        <div class="chat-avatar">${avatar}</div>
        <div class="chat-bubble">${content}</div>
    `;

    chat.appendChild(msg);
    chat.scrollTop = chat.scrollHeight;
}

function formatAIResponseForChat(text) {
    if (!text) return '';

    let formatted = escapeHtml(text);

    // Code blocks with copy/apply buttons
    formatted = formatted.replace(/```(\w*)\n([\s\S]*?)```/g, (match, lang, code) => {
        const encodedCode = encodeURIComponent(code.trim());
        return `<pre>${code}</pre>
            <div class="code-actions">
                <button class="code-action-btn" onclick="copyCodeToClipboard('${encodedCode}')">📋 Copy</button>
                <button class="code-action-btn" onclick="sendCodeToEditor('${encodedCode}')">📝 To Editor</button>
                <button class="code-action-btn" onclick="evolveThisCode('${encodedCode}')">🧬 Evolve</button>
            </div>`;
    });

    // Inline code
    formatted = formatted.replace(/`([^`]+)`/g, '<code>$1</code>');

    // Headers
    formatted = formatted.replace(/## (.+)/g, '<h4 style="color:var(--accent-purple);margin:8px 0;">$1</h4>');
    formatted = formatted.replace(/# (.+)/g, '<h3 style="color:var(--accent-purple);margin:8px 0;">$1</h3>');

    // Bold
    formatted = formatted.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');

    // Line breaks
    formatted = formatted.replace(/\n/g, '<br>');

    return formatted;
}

function copyCodeToClipboard(encodedCode) {
    const code = decodeURIComponent(encodedCode);
    if (navigator.clipboard) {
        navigator.clipboard.writeText(code).then(() => {
            showToast('Code copied! 📋', 'success');
        });
    } else {
        // Fallback
        const textarea = document.createElement('textarea');
        textarea.value = code;
        document.body.appendChild(textarea);
        textarea.select();
        document.execCommand('copy');
        document.body.removeChild(textarea);
        showToast('Code copied! 📋', 'success');
    }
}

function sendCodeToEditor(encodedCode) {
    const code = decodeURIComponent(encodedCode);
    const editor = document.getElementById('codeEditor');
    editor.value = code;
    updateLineNumbers();
    currentFile.modified = true;
    navigateTo('editor');
    showToast('Code sent to editor! 📝', 'success');
}

function evolveThisCode(encodedCode) {
    const code = decodeURIComponent(encodedCode);
    document.getElementById('aiInput').value = code + '\n\nPlease evolve and improve this code.';
    setAIMode('evolve');
    sendAIMessage();
}

function showTypingIndicator() {
    const chat = document.getElementById('aiChat');
    const indicator = document.createElement('div');
    indicator.className = 'chat-message ai';
    indicator.id = 'typingIndicator';
    indicator.innerHTML = `
        <div class="chat-avatar">🧠</div>
        <div class="chat-bubble">
            <div class="typing-indicator">
                <div class="typing-dot"></div>
                <div class="typing-dot"></div>
                <div class="typing-dot"></div>
            </div>
        </div>
    `;
    chat.appendChild(indicator);
    chat.scrollTop = chat.scrollHeight;
}

function hideTypingIndicator() {
    const indicator = document.getElementById('typingIndicator');
    if (indicator) indicator.remove();
}

// Auto-resize input
const aiInput = document.getElementById('aiInput');
if (aiInput) {
    aiInput.addEventListener('input', autoResizeInput);
    aiInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            sendAIMessage();
        }
    });
}

function autoResizeInput() {
    const input = document.getElementById('aiInput');
    if (!input) return;
    input.style.height = 'auto';
    input.style.height = Math.min(input.scrollHeight, 100) + 'px';
}
