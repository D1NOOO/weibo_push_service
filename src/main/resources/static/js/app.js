// ==================== Design Tokens ====================
const DESIGN = {
    primary: '#ff6b35',
    danger: '#ff1744',
    success: '#00c853',
    warning: '#ff9100',
    orangeCoral: '#ff6b35',
    orangeDeep: '#e55a2b',
    pinkMagenta: '#e84393',
    purpleNeon: '#6c5ce7',
    textDark: '#1a1a2e',
    textLight: '#8e8ea0'
};

// ==================== API Layer ====================
class APIService {
    constructor() {
        this.baseUrl = '';
        this.token = localStorage.getItem('token') || null;
    }

    async request(method, path, body = null) {
        const headers = { 'Content-Type': 'application/json' };
        if (this.token) headers['Authorization'] = `Bearer ${this.token}`;
        
        const opts = { method, headers };
        if (body) opts.body = JSON.stringify(body);
        
        try {
            const res = await fetch(this.baseUrl + path, opts);
            if (res.status === 401) {
                this.logout(); throw new Error('登录已过期');
            }
            if (!res.ok) {
                const err = await res.json().catch(() => ({ message: '请求失败' }));
                throw new Error(err.message || `HTTP ${res.status}`);
            }
            return res.json();
        } catch (error) {
            console.error(`API ${method} ${path} error:`, error);
            throw error;
        }
    }

    async get(path) { return this.request('GET', path); }
    async post(path, body) { return this.request('POST', path, body); }
    async put(path, body) { return this.request('PUT', path, body); }
    async delete(path) { return this.request('DELETE', path); }

    setToken(token) { 
        this.token = token; 
        if (token) localStorage.setItem('token', token);
        else localStorage.removeItem('token');
    }

    logout() { 
        this.setToken(null); 
        window.location.reload();
    }
}

const API = new APIService();

// ==================== State Management ====================
class AppState {
    constructor() {
        this.config = { dedupeHours: 6 };
        this.allItems = [];
        this.theme = localStorage.getItem('theme') || 'light';
        this.currentTab = 'hotsearch';
        this.autoRefresh = null;
        
        this.appliedShortcuts = new Set();
        this.currentChart = null;
        this.heatChart = null;
    }

    setTheme(theme) {
        this.theme = theme;
        document.documentElement.setAttribute('data-theme', theme);
        localStorage.setItem('theme', theme);
    }

    toggleTheme() {
        const newTheme = this.theme === 'light' ? 'dark' : 'light';
        this.setTheme(newTheme);
        document.getElementById('btn-theme').textContent = newTheme === 'light' ? '🌙' : '☀️';
    }

    startAutoRefresh() {
        if (this.autoRefresh) return;
        this.autoRefresh = setInterval(() => {
            if (document.getElementById('tab-hotsearch').classList.contains('active')) {
                loadHotSearch();
            }
        }, 30000); // 30 seconds
    }

    stopAutoRefresh() {
        if (this.autoRefresh) {
            clearInterval(this.autoRefresh);
            this.autoRefresh = null;
        }
    }
}

const State = new AppState();

// ==================== Utility Functions ====================
const UTILS = {
    escape(s) { 
        if (!s) return ''; 
        const div = document.createElement('div'); 
        div.textContent = s; 
        return div.innerHTML; 
    },
    splitList(s) { 
        return s ? s.split(/[,，]/).map(x => x.trim()).filter(Boolean) : []; 
    },
    formatNum(n) { 
        if (n >= 100000) return (n / 10000).toFixed(1) + '万';
        if (n >= 10000) return (n / 1000).toFixed(1) + 'k';
        return n.toLocaleString();
    },
    formatTime(t) {
        if (!t) return '-';
        const d = new Date(t.replace(' ', 'T'));
        if (isNaN(d.getTime())) return '-';
        const now = new Date();
        const diff = now - d;
        const minutes = Math.floor(diff / 60000);
        if (minutes < 1) return '刚刚';
        if (minutes < 60) return `${minutes}分钟前`;
        const hours = Math.floor(minutes / 60);
        if (hours < 24) return `${hours}小时前`;
        return d.toLocaleDateString('zh-CN') + ' ' + d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
    },
    showToast(message, type = 'info') {
        const toast = document.getElementById('toast');
        toast.textContent = message;
        toast.className = `toast ${type}`;
        toast.classList.remove('hidden');
        setTimeout(() => toast.classList.add('hidden'), 3000);
    },
    showLoading(container) {
        container.innerHTML = `
            <div class="skeleton skeleton-card"></div>
            <div class="skeleton skeleton-card"></div>
            <div class="skeleton skeleton-card"></div>
        `;
    },
    getLabelClass(label) {
        if (label === '爆') return 'label-burst';
        if (label === '热') return 'label-hot';
        if (label === '新') return 'label-new';
        if (label === '荐' || label?.includes('首发')) return 'label-ad';
        return '';
    },
    getHeatClass(value) {
        if (!value) return '';
        if (value >= 5000000) return 'heat-high';
        if (value >= 1000000) return 'heat-mid';
        return '';
    },
    getRankClass(rank) {
        if (!rank) return '';
        if (rank <= 10) return 'mid';
        return '';
    }
};

// ==================== Chart Utilities ====================
const ChartManager = {
    initCharts() {
        if (!window.Chart) {
            console.warn('Chart.js not loaded, skipping charts');
            return;
        }
        
        // Initialize charts
        this.labelChart = new Chart(
            document.getElementById('chart-labels').getContext('2d'),
            this.createLabelChartConfig()
        );
        this.heatChart = new Chart(
            document.getElementById('chart-heat').getContext('2d'),
            this.createHeatChartConfig()
        );
    },
    
    createLabelChartConfig() {
        return {
            type: 'doughnut',
            data: {
                labels: [],
                datasets: [{
                    data: [],
                    backgroundColor: [
                        DESIGN.orangeCoral, DESIGN.danger, DESIGN.warning,
                        DESIGN.success, DESIGN.purpleNeon, DESIGN.textLight
                    ],
                    borderWidth: 0,
                    borderRadius: 4
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: { display: false },
                    tooltip: {
                        callbacks: {
                            label: (ctx) => `${ctx.label}: ${ctx.formattedValue}条 (${ctx.parsed.toFixed(1)}%)`
                        }
                    }
                },
                cutout: '55%'
            }
        };
    },
    
    createHeatChartConfig() {
        return {
            type: 'bar',
            data: {
                labels: [],
                datasets: [{
                    label: '热度值',
                    data: [],
                    backgroundColor: DESIGN.purpleNeon,
                    borderRadius: 6,
                    borderWidth: 0
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: { display: false },
                    tooltip: {
                        callbacks: {
                            label: (ctx) => `${ctx.label}: ${UTILS.formatNum(ctx.raw)}`
                        }
                    }
                },
                scales: {
                    x: { grid: { display: false }, ticks: { color: getComputedStyle(document.documentElement).getPropertyValue('--text-light') } },
                    y: { beginAtZero: true, grid: { color: getComputedStyle(document.documentElement).getPropertyValue('--border') }, ticks: { color: getComputedStyle(document.documentElement).getPropertyValue('--text-light') } }
                }
            }
        };
    },
    
    updateLabelData(data) {
        if (!this.labelChart) return;
        
        this.labelChart.data.labels = data.labels;
        this.labelChart.data.datasets[0].data = data.values;
        this.labelChart.update();
    },
    
    updateHeatData(data) {
        if (!this.heatChart) return;
        
        this.heatChart.data.labels = data.labels;
        this.labelChart.data.datasets[0].data = data.values;
        this.heatChart.update();
    }
};

// ==================== Authentication ====================
// Login
document.getElementById('login-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const username = document.getElementById('username').value;
    const password = document.getElementById('password').value;
    const errorEl = document.getElementById('login-error');

    try {
        const result = await fetch('/api/auth/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, password })
        });
        const data = await result.json();
        if (!result.ok) throw new Error(data.message || data.error || '登录失败');

        API.setToken(data.token);
        showDashboard(data.username || 'admin', data.mustChangePassword);
    } catch (error) {
        errorEl.textContent = error.message;
        setTimeout(() => errorEl.textContent = '', 3000);
    }
});

// Change password
document.getElementById('btn-change-pwd').addEventListener('click', () => {
    showModal('pwd-modal');
});

document.getElementById('pwd-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const oldPwd = document.getElementById('pwd-old').value;
    const newPwd = document.getElementById('pwd-new').value;
    const confirmPwd = document.getElementById('pwd-confirm').value;
    
    if (newPwd !== confirmPwd) {
        UTILS.showToast('新密码不匹配', 'error'); return;
    }
    if (newPwd.length < 6) {
        UTILS.showToast('密码至少6位', 'error'); return;
    }
    
    try {
        await API.post('/api/auth/change-password', { oldPassword: oldPwd, newPassword: newPwd });
        hideModal('pwd-modal');
        UTILS.showToast('密码修改成功', 'success');
        document.getElementById('pwd-form').reset();
    } catch (e) { UTILS.showToast(e.message, 'error'); }
});

document.getElementById('btn-cancel-pwd').addEventListener('click', () => {
    hideModal('pwd-modal');
    document.getElementById('pwd-form').reset();
});

// Logout
document.getElementById('btn-logout').addEventListener('click', () => {
    if (confirm('确定退出登录吗？')) {
        API.logout();
    }
});

// ==================== Theme Toggle ====================
document.getElementById('btn-theme').addEventListener('click', () => {
    State.toggleTheme();
});

// ==================== Tab Navigation ====================
document.querySelectorAll('.tab').forEach(tab => {
    tab.addEventListener('click', () => {
        document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
        document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));
        tab.classList.add('active');
        const tabId = tab.dataset.tab;
        document.getElementById(`tab-${tabId}`).classList.add('active');
        State.currentTab = tabId;
        loadTab(tabId);
    });
});

function loadTab(tab) {
    switch (tab) {
        case 'hotsearch':
            loadHotSearch();
            State.startAutoRefresh();
            break;
        case 'subscriptions':
            loadSubscriptions();
            State.stopAutoRefresh();
            break;
        case 'channels':
            loadChannels();
            State.stopAutoRefresh();
            break;
        case 'logs':
            loadLogs();
            State.stopAutoRefresh();
            break;
        case 'config':
            loadConfig();
            State.stopAutoRefresh();
            break;
    }
}

// ==================== Hot Search Main Logic ====================
async function loadHotSearch() {
    const container = document.getElementById('hotsearch-list');
    UTILS.showLoading(container);
    
    try {
        const result = await API.get('/api/hotsearch');
        const items = Array.isArray(result) ? result : (result.items || []);
        const fetchedAt = result.fetchedAt || null;
        State.allItems = items;
        
        if (!items.length) {
            container.innerHTML = '<div class="empty"><div class="empty-icon">😴</div><p>暂无热搜数据</p></div>';
            return;
        }
        
        // Update stats
        updateStats(items);
        // Update charts
        updateCharts(items);
        
        // Get search filter
        const search = document.getElementById('hotsearch-search').value.toLowerCase().trim();
        const filteredItems = search 
            ? items.filter(item => item.keyword && item.keyword.toLowerCase().includes(search))
            : items;
        
        if (!filteredItems.length) {
            container.innerHTML = `<div class="empty"><div class="empty-icon">🔍</div><p>未找到匹配 "${search}" 的热搜</p></div>`;
            return;
        }
        
        // Render list with skeleton removal animation
        setTimeout(() => {
            container.innerHTML = filteredItems.map((item, i) => renderHotItem(item, i)).join('');
        }, 200);
        
        // Show freshness banner
        if (fetchedAt && document.querySelector('.freshness-banner')) {
            document.querySelector('.freshness-banner .freshness-time').textContent = UTILS.formatTime(fetchedAt);
        }
    } catch (e) {
        container.innerHTML = '<div class="empty"><div class="empty-icon">️⚠️</div><p>加载失败: ' + UTILS.escape(e.message) + '</p></div>';
        UTILS.showToast('热搜加载失败', 'error');
    }
}

function renderHotItem(item, i) {
    const rank = item.rank || i + 1;
    return `
        <div class="card hotsearch-card">
            <span class="hot-rank ${i < 3 ? 'top' : UTILS.getRankClass(rank)}">${rank}</span>
            <div class="hot-info">
                <div class="hot-keyword">
                    ${item.url ? `<a href="${UTILS.escape(item.url)}" target="_blank" rel="noopener" class="hot-link">${UTILS.escape(item.keyword)}</a>` : UTILS.escape(item.keyword)}${item.isAd ? ' &nbsp;📌' : ''}
                </div>
                <div class="hot-meta">${item.label ? UTILS.escape(item.label) : ''} ${item.isAd ? '广告推广' : ''}</div>
            </div>
            <button class="btn-trend" onclick="showTrend('${UTILS.escape(item.keyword).replace(/'/g, "\\'")}')" title="查看历史趋势">📈</button>
            ${item.label ? `<span class="hot-label ${UTILS.getLabelClass(item.label)}">${UTILS.escape(item.label)}</span>` : ''}
            ${item.hotValue ? `<span class="hot-value ${UTILS.getHeatClass(item.hotValue)}">${UTILS.formatNum(item.hotValue)}</span>` : ''}
        </div>
    `;
}

function updateStats(items) {
    const total = items.length;
    const burst = items.filter(x => x.label === '爆').length;
    const newItems = items.filter(x => x.label === '新').length;
    const ads = items.filter(x => x.isAd).length;
    
    document.getElementById('stat-total').textContent = total;
    document.getElementById('stat-burst').textContent = burst;
    document.getElementById('stat-new').textContent = newItems;
    document.getElementById('stat-ads').textContent = ads;
}

function updateCharts(items) {
    // Label distribution
    const labels = ['爆', '热', '新', '其他有效', '广告', '无标签'];
    const counts = [0, 0, 0, 0, 0, 0];
    items.forEach(item => {
        if (item.label === '爆') counts[0]++;
        else if (item.label === '热') counts[1]++;
        else if (item.label === '新') counts[2]++;
        else if (item.label && !['荐'].includes(item.label)) counts[3]++;
        else if (item.isAd || item.label === '荐') counts[4]++;
        else counts[5]++;
    });
    
    // Only show non-zero labels
    const validLabels = labels.filter((_, i) => counts[i] > 0);
    const validCounts = counts.filter(c => c > 0);
    
    if (ChartManager.updateLabelData && ChartManager.initCharts) {
        ChartManager.updateLabelData({ labels: validLabels, values: validCounts });
    }
    
    // Heat top 10
    const sortedByHeat = items.filter(x => x.hotValue).sort((a,b) => b.hotValue - a.hotValue).slice(0, 10);
    if (sortedByHeat.length && ChartManager.updateHeatData) {
        const heatLabels = sortedByHeat.map(x => x.keyword.length > 6 ? x.keyword.substring(0,6)+'...' : x.keyword);
        const heatValues = sortedByHeat.map(x => x.hotValue);
        ChartManager.updateHeatData({ labels: heatLabels, values: heatValues });
    }
}

// Search
document.getElementById('hotsearch-search').addEventListener('input', debounce(() => {
    loadHotSearch();
}, 300));

// Trigger
document.getElementById('btn-trigger').addEventListener('click', async () => {
    try {
        await API.post('/api/hotsearch/trigger', {});
        UTILS.showToast('推送管线已触发，即将刷新数据...', 'success');
        setTimeout(() => {
            loadHotSearch();
            loadLogs();
        }, 3000);
    } catch (e) { UTILS.showToast(e.message, 'error'); }
});

// ==================== Export Functions ====================
document.getElementById('btn-export-csv').addEventListener('click', () => exportData('csv'));
document.getElementById('btn-export-json').addEventListener('click', () => exportData('json'));

function exportData(format) {
    const items = State.allItems;
    if (!items.length) {
        UTILS.showToast('没有数据可导出', 'warning');
        return;
    }
    
    if (format === 'csv') {
        const headers = ['排名', '关键词', '标签', '热度值', '广告', 'URL'];
        const rows = items.map(item => [
            item.rank || '',
            item.keyword || '',
            item.label || '',
            item.hotValue || '',
            item.isAd ? '是' : '否',
            item.url || ''
        ]);
        const csvContent = [headers.join(','), ...rows.map(row => row.join(','))].join('\n');
        downloadFile(csvContent, 'hotsearch.csv', 'text/csv');
    } else {
        const json = JSON.stringify(items, null, 2);
        downloadFile(json, 'hotsearch.json', 'application/json');
    }
    UTILS.showToast(`已导出${items.length}条数据`, 'success');
}

function downloadFile(content, filename, mimeType) {
    const blob = new Blob([content], { type: mimeType });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
}

// ==================== Trend History ====================
window.showTrend = async function(keyword) {
    showModal('history-modal');
    const content = document.getElementById('history-content');
    content.innerHTML = '<p style="font-size:13px;color:var(--text-light);text-align:center;padding:20px">加载中...</p>';
    
    try {
        const trend = await API.get(`/api/hotsearch/trend?keyword=${encodeURIComponent(keyword)}&hours=48`);
        if (!trend.length) {
            content.innerHTML = `<p style="text-align:center;color:var(--text-light);padding:20px;">"${UTILS.escape(keyword)}" 近48小时内无数据</p>`;
            return;
        }
        
        let html = `<h4 style="margin-bottom:16px;">"${UTILS.escape(keyword)}" 📈 排名趋势</h4>`;
        html += '<div class="trend-chart">';
        
        const maxRank = Math.max(...trend.map(t => t.rank || 1));
        trend.forEach(point => {
            const barWidth = Math.max(5, 100 - ((point.rank / maxRank) * 90));
            const heatText = point.hotValue ? UTILS.formatNum(point.hotValue) : '';
            const labelTag = point.label ? `<span class="hot-label ${UTILS.getLabelClass(point.label)}" style="font-size:10px;padding:1px 5px;">${UTILS.escape(point.label)}</span>` : '';
            html += `
                <div class="trend-row">
                    <span class="trend-time">${UTILS.formatTime(point.fetchedAt)}</span>
                    <span class="trend-rank">#${point.rank}</span>
                    <div class="trend-bar" style="width: ${barWidth}%;"></div>
                    <span class="trend-heat">${heatText}</span>
                    ${labelTag}
                </div>`;
        });
        
        html += '</div>';
        content.innerHTML = html;
    } catch (e) {
        content.innerHTML = `<p style="text-align:center;color:var(--danger);">加载失败: ${UTILS.escape(e.message)}</p>`;
    }
};

document.getElementById('btn-close-history').addEventListener('click', () => {
    hideModal('history-modal');
});

// ==================== Configuration ====================
async function loadConfig() {
    try {
        const config = await API.get('/api/config');
        const currentHours = config.dedupeWindowHours || 6;
        document.getElementById('current-dedupe-hours').textContent = currentHours;
        document.getElementById('dedupe-hours').value = currentHours;
        document.getElementById('btn-save-config').disabled = true;
        
        document.getElementById('dedupe-hours').onchange = () => {
            const newVal = document.getElementById('dedupe-hours').value;
            document.getElementById('btn-save-config').disabled = (parseInt(newVal) === currentHours);
        };
    } catch (e) { console.warn('加载配置失败:', e.message); }
}

document.getElementById('btn-save-config').addEventListener('click', () => {
    const hours = document.getElementById('dedupe-hours').value;
    if (confirm('修改系统配置需要重启应用才能生效，确定继续？')) {
        UTILS.showToast(`已记录配置变更（新去重窗口: ${hours}小时），请手动重启服务应用新配置`, 'warning');
        document.getElementById('btn-save-config').disabled = true;
    }
});

// ==================== Keyboard Shortcuts ====================
document.addEventListener('keydown', (e) => {
    const isMac = navigator.platform.toUpperCase().indexOf('MAC') >= 0;
    const cmdKey = isMac ? e.metaKey : e.ctrlKey;
    const key = e.key.toLowerCase();
    
    // Global shortcuts
    if (cmdKey && key === 'k') {
        e.preventDefault();
        showModal('search-modal');
        document.getElementById('global-search-input').focus();
        return;
    }
    
    if (cmdKey && key === 't') {
        e.preventDefault();
        State.toggleTheme();
        return;
    }
    
    // Tab switching with 1-5
    if (!cmdKey && !e.altKey && !e.shiftKey && key >= '1' && key <= '5') {
        const tabIndex = parseInt(key) - 1;
        const tabs = Array.from(document.querySelectorAll('.tab'));
        if (tabs[tabIndex]) {
            tabs[tabIndex].click();
        }
    }
    
    // R = refresh
    if (key === 'r' && document.activeElement.tagName !== 'INPUT' && document.activeElement.tagName !== 'TEXTAREA') {
        loadHotSearch();
    }
    
    // Escape = close modals
    if (key === 'escape') {
        const modals = Array.from(document.querySelectorAll('.modal:not(.hidden)'));
        if (modals.length > 0) {
            hideModal(modals[0].id);
        }
    }
});

// Global search
document.getElementById('global-search-input').addEventListener('input', (e) => {
    const term = e.target.value.toLowerCase().trim();
    const results = document.getElementById('global-search-results');
    
    if (!term) {
        results.innerHTML = '<p style="text-align:center;color:var(--text-light);padding:20px;">输入关键词搜索</p>';
        return;
    }
    
    const items = State.allItems;
    const matches = items.filter(item => 
        item.keyword && item.keyword.toLowerCase().includes(term)
    ).slice(0, 10);
    
    if (!matches.length) {
        results.innerHTML = '<p style="text-align:center;color:var(--text-light);padding:20px;">未找到匹配结果</p>';
        return;
    }
    
    results.innerHTML = matches.map(item => `
        <div class="card" style="margin-bottom:6px;cursor:pointer;padding:12px;" onclick="window.scrollToItem('${UTILS.escape(item.keyword)}')">
            <span class="hot-rank" style="margin-right:8px;">${item.rank || '?'}</span>
            <span>${UTILS.escape(item.keyword)}</span>
            ${item.label ? `<span class="hot-label ${UTILS.getLabelClass(item.label)}" style="margin-left:auto;">${UTILS.escape(item.label)}</span>` : ''}
        </div>
    `).join('');
});

window.scrollToItem = function(keyword) {
    hideModal('search-modal');
    // Scroll to matching item in hotsearch list
    const items = State.allItems;
    const index = items.findIndex(item => item.keyword === keyword);
    if (index !== -1) {
        document.querySelectorAll('.tab')[0].click(); // Switch to hotsearch tab
        setTimeout(() => {
            const container = document.getElementById('hotsearch-list');
            const cards = container.querySelectorAll('.card');
            if (cards[index]) {
                cards[index].scrollIntoView({ behavior: 'smooth', block: 'center' });
                cards[index].animate([{ backgroundColor: 'rgba(255,107,53,0.1)' }, { backgroundColor: 'transparent' }], { duration: 1500 });
            }
        }, 300);
    }
};

// ==================== Subscription Functions ==================== 
let editingSubscriptionId = null;

function loadSubscriptions() {
    const container = document.getElementById('sub-list');
    const search = document.getElementById('sub-search').value.toLowerCase().trim();
    
    UTILS.showLoading(container);
    
    API.get('/api/subscriptions').then(subscriptions => {
        if (!subscriptions.length) {
            container.innerHTML = `
                <div class="empty onboarding">
                    <div class="empty-icon">📋</div>
                    <h3>订阅管理</h3>
                    <p>创建订阅规则，匹配的热搜会自动推送到指定通道</p>
                    <p>支持关键词匹配、正则表达式、标签过滤</p>
                    <button class="btn btn-primary" onclick="showSubscriptionModal()" style="margin-top: 16px;">创建第一条订阅</button>
                </div>
            `;
            return;
        }
        
        const filtered = search 
            ? subscriptions.filter(sub => 
                (sub.name && sub.name.toLowerCase().includes(search)) ||
                (sub.keywords && sub.keywords.some(k => k.toLowerCase().includes(search)))
            )
            : subscriptions;
        
        if (!filtered.length) {
            container.innerHTML = `
                <div class="empty">
                    <div class="empty-icon">🔍</div>
                    <p>未找到匹配 "${search}" 的订阅</p>
                </div>
            `;
            return;
        }
        
        setTimeout(() => {
            container.innerHTML = filtered.map(sub => `
                <div class="item-card">
                    <div class="item-header">
                        <div class="item-name">${UTILS.escape(sub.name)}</div>
                        <div style="display:flex;align-items:center;gap:6px;">
                            <span class="status-dot ${sub.enabled ? 'on' : 'off'}"></span>
                            <div style="display:flex;gap:4px;">
                                <button class="btn btn-sm btn-ghost" onclick="editSubscription(${sub.id})" title="编辑">✏️</button>
                                <button class="btn btn-sm btn-ghost btn-danger" onclick="deleteSubscription(${sub.id})" title="删除">🗑️</button>
                            </div>
                        </div>
                    </div>
                    ${sub.keywords?.length ? `
                        <div class="item-tags">${sub.keywords.map(k => 
                            `<span class="tag">${UTILS.escape(k)}</span>`).join('')
                        }</div>
                    ` : ''}
                    ${sub.excludeKeywords?.length ? `
                        <div class="item-tags" style="margin-top:4px;">${sub.excludeKeywords.map(k => 
                            `<span class="tag exclude">🚫 ${UTILS.escape(k)}</span>`).join('')
                        }</div>
                    ` : ''}
                    <div class="item-meta">
                        标签过滤: ${sub.labels?.join(', ') || '无'} • 最低热度: ${sub.minHotValue || '不限'} • 创建: ${UTILS.formatTime(sub.createdAt)}
                    </div>
                    <div class="item-actions">
                        <button class="btn btn-sm ${sub.enabled ? 'btn-danger' : 'btn-primary'}" 
                                onclick="toggleSubscription(${sub.id}, ${!sub.enabled})">
                            ${sub.enabled ? '禁用' : '启用'}
                        </button>
                    </div>
                </div>
            `).join('');
        }, 200);
    }).catch(err => {
        container.innerHTML = `<div class="empty"><div class="empty-icon">️⚠️</div><p>加载失败: ${UTILS.escape(err.message)}</p></div>`;
    });
}

function showSubscriptionModal(subscription = null) {
    editingSubscriptionId = subscription?.id || null;
    const modal = document.getElementById('sub-modal');
    const title = document.getElementById('sub-modal-title');
    const form = document.getElementById('sub-form');
    
    title.textContent = subscription ? '编辑订阅' : '新增订阅';
    
    if (subscription) {
        document.getElementById('sub-id').value = subscription.id;
        document.getElementById('sub-name').value = subscription.name;
        document.getElementById('sub-keywords').value = subscription.keywords?.join(', ') || '';
        document.getElementById('sub-exclude').value = subscription.excludeKeywords?.join(', ') || '';
        document.getElementById('sub-labels').value = subscription.labels?.join(', ') || '';
        document.getElementById('sub-min-hot').value = subscription.minHotValue || 0;
        document.getElementById('sub-enabled').checked = subscription.enabled !== false;
    } else {
        form.reset();
        document.getElementById('sub-min-hot').value = 0;
        document.getElementById('sub-enabled').checked = true;
    }
    
    showModal('sub-modal');
}

document.getElementById('btn-add-sub').addEventListener('click', () => showSubscriptionModal());

document.getElementById('sub-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    
    const subscription = {
        name: document.getElementById('sub-name').value,
        keywords: UTILS.splitList(document.getElementById('sub-keywords').value),
        excludeKeywords: UTILS.splitList(document.getElementById('sub-exclude').value),
        labels: UTILS.splitList(document.getElementById('sub-labels').value),
        minHotValue: parseInt(document.getElementById('sub-min-hot').value) || null,
        enabled: document.getElementById('sub-enabled').checked
    };
    
    const id = document.getElementById('sub-id').value;
    
    try {
        if (id) {
            await API.put(`/api/subscriptions/${id}`, subscription);
            UTILS.showToast('订阅更新成功', 'success');
        } else {
            await API.post('/api/subscriptions', subscription);
            UTILS.showToast('订阅创建成功', 'success');
        }
        hideModal('sub-modal');
        loadSubscriptions();
    } catch (err) {
        UTILS.showToast(err.message, 'error');
    }
});

document.getElementById('btn-cancel-sub').addEventListener('click', () => {
    hideModal('sub-modal');
});

function editSubscription(id) {
    API.get(`/api/subscriptions/${id}`).then(data => {
        showSubscriptionModal(data);
    }).catch(err => UTILS.showToast(err.message, 'error'));
}

function toggleSubscription(id, enabled) {
    API.put(`/api/subscriptions/${id}`, { enabled }).then(() => {
        UTILS.showToast(`订阅已${enabled ? '启用' : '禁用'}`, 'success');
        loadSubscriptions();
    }).catch(err => UTILS.showToast(err.message, 'error'));
}

function deleteSubscription(id) {
    if (!confirm('确定删除这条订阅吗？')) return;
    
    API.delete(`/api/subscriptions/${id}`).then(() => {
        UTILS.showToast('订阅删除成功', 'success');
        loadSubscriptions();
    }).catch(err => UTILS.showToast(err.message, 'error'));
}

// Search subscriptions
document.getElementById('sub-search').addEventListener('input', debounce(() => {
    loadSubscriptions();
}, 300));

// ==================== Channel Functions ==================== 
let editingChannelId = null;

function loadChannels() {
    const container = document.getElementById('ch-list');
    
    UTILS.showLoading(container);
    
    API.get('/api/channels').then(channels => {
        if (!channels.length) {
            container.innerHTML = `
                <div class="empty onboarding">
                    <div class="empty-icon">📤</div>
                    <h3>推送通道</h3>
                    <p>添加飞书、钉钉、企微、Telegram等推送通道</p>
                    <p>每条通道可以绑定多个订阅规则</p>
                    <button class="btn btn-primary" onclick="showChannelModal()" style="margin-top: 16px;">创建第一条通道</button>
                </div>
            `;
            return;
        }
        
        setTimeout(() => {
            container.innerHTML = channels.map(ch => `
                <div class="item-card">
                    <div class="item-header">
                        <div class="item-name">${getProviderLabel(ch.provider)}</div>
                        <div style="display:flex;align-items:center;gap:6px;">
                            <span class="status-dot ${ch.enabled ? 'on' : 'off'}"></span>
                            <div style="display:flex;gap:4px;">
                                <button class="btn btn-sm btn-ghost" onclick="editChannel(${ch.id})" title="编辑">✏️</button>
                                <button class="btn btn-sm btn-ghost btn-danger" onclick="deleteChannel(${ch.id})" title="删除">🗑️</button>
                            </div>
                        </div>
                    </div>
                    <div class="item-meta">Webhook: ${maskWebhookUrl(ch.config?.webhookUrl || ch.config?.webhook_url || '')}</div>
                    <div class="item-meta">创建时间: ${UTILS.formatTime(ch.createdAt)}</div>
                    <div class="item-actions">
                        <button class="btn btn-sm" onclick="sendTestMessage(${ch.id})" title="发送测试消息">测试</button>
                        <button class="btn btn-sm ${ch.enabled ? 'btn-danger' : 'btn-primary'}" 
                                onclick="toggleChannel(${ch.id}, ${!ch.enabled})">
                            ${ch.enabled ? '禁用' : '启用'}
                        </button>
                    </div>
                </div>
            `).join('');
        }, 200);
    }).catch(err => {
        container.innerHTML = `<div class="empty"><div class="empty-icon">️⚠️</div><p>加载失败: ${UTILS.escape(err.message)}</p></div>`;
    });
}

function getProviderLabel(provider) {
    const labels = {
        feishu: '飞书 Webhook',
        dingtalk: '钉钉 Webhook',
        wecom: '企业微信',
        telegram: 'Telegram Bot',
        generic: '通用 Webhook'
    };
    return labels[provider] || provider;
}

function maskWebhookUrl(url) {
    if (!url) return '未配置';
    const parts = url.split('/');
    if (parts.length > 4) {
        return `${parts[0]}//${parts[2]}/.../${parts[parts.length-1].substring(0, 8)}...`;
    }
    return url.substring(0, 40) + (url.length > 40 ? '...' : '');
}

function updateProviderHint() {
    const provider = document.getElementById('ch-provider').value;
    const hintEl = document.getElementById('ch-provider-hint');
    const urlLabel = document.getElementById('ch-url-label');
    
    const hints = {
        feishu: '💡 飞书群机器人 → 添加机器人 → 复制 Webhook 地址',
        dingtalk: '💡 钉钉群机器人 → 安全设置 → Webhook → 复制地址',
        wecom: '💡 企业微信群机器人 → 右键添加机器人 → 获取 Webhook',
        telegram: '💡 与 @BotFather 对话创建机器人 → 获取 bot token → /setwebhook',
        generic: '💡 任意支持 POST JSON 的 HTTP 端点'
    };
    
    const labels = {
        feishu: 'Webhook URL',
        dingtalk: 'Webhook URL',
        wecom: 'Webhook URL',
        telegram: 'Bot Token',
        generic: 'Webhook URL'
    };
    
    hintEl.textContent = hints[provider] || '请填写通道配置';
    urlLabel.textContent = labels[provider] || '配置';
}

function showChannelModal(channel = null) {
    editingChannelId = channel?.id || null;
    const modal = document.getElementById('ch-modal');
    const title = document.getElementById('ch-modal-title');
    
    title.textContent = channel ? '编辑通道' : '新增通道';
    
    if (channel) {
        document.getElementById('ch-id').value = channel.id;
        document.getElementById('ch-provider').value = channel.provider;
        document.getElementById('ch-webhook').value = channel.config?.webhookUrl || channel.config?.webhook_url || '';
        document.getElementById('ch-enabled').checked = channel.enabled !== false;
    } else {
        document.getElementById('ch-form').reset();
        document.getElementById('ch-enabled').checked = true;
    }
    
    updateProviderHint();
    showModal('ch-modal');
}

document.getElementById('btn-add-ch').addEventListener('click', () => showChannelModal());

document.getElementById('ch-provider').addEventListener('change', updateProviderHint);

document.getElementById('ch-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    
    const provider = document.getElementById('ch-provider').value;
    const webhook = document.getElementById('ch-webhook').value.trim();
    const enabled = document.getElementById('ch-enabled').checked;
    
    const channel = {
        provider,
        config: { webhookUrl: webhook },
        enabled
    };
    
    const id = document.getElementById('ch-id').value;
    
    try {
        if (id) {
            await API.put(`/api/channels/${id}`, channel);
            UTILS.showToast('通道更新成功', 'success');
        } else {
            await API.post('/api/channels', channel);
            UTILS.showToast('通道创建成功', 'success');
        }
        hideModal('ch-modal');
        loadChannels();
    } catch (err) {
        UTILS.showToast(err.message, 'error');
    }
});

document.getElementById('btn-cancel-ch').addEventListener('click', () => {
    hideModal('ch-modal');
});

function editChannel(id) {
    API.get(`/api/channels/${id}`).then(data => {
        showChannelModal(data);
    }).catch(err => UTILS.showToast(err.message, 'error'));
}

function toggleChannel(id, enabled) {
    API.put(`/api/channels/${id}`, { enabled }).then(() => {
        UTILS.showToast(`通道已${enabled ? '启用' : '禁用'}`, 'success');
        loadChannels();
    }).catch(err => UTILS.showToast(err.message, 'error'));
}

function deleteChannel(id) {
    if (!confirm('确定删除这条通道吗？')) return;
    
    API.delete(`/api/channels/${id}`).then(() => {
        UTILS.showToast('通道删除成功', 'success');
        loadChannels();
    }).catch(err => UTILS.showToast(err.message, 'error'));
}

function sendTestMessage(channelId) {
    if (!confirm('发送测试消息到这条通道吗？')) return;
    
    API.post(`/api/channels/${channelId}/test`, {})
        .then(() => UTILS.showToast('测试消息发送成功', 'success'))
        .catch(err => UTILS.showToast('测试失败: ' + err.message, 'error'));
}

// ==================== Delivery Log Functions ==================== 
function loadLogs() {
    const container = document.getElementById('log-list');
    const hours = document.getElementById('log-hours').value;
    
    UTILS.showLoading(container);
    
    API.get(`/api/delivery-logs?hours=${hours}`).then(logs => {
        if (!logs.length) {
            container.innerHTML = `
                <div class="empty onboarding">
                    <div class="empty-icon">📨</div>
                    <h3>推送日志</h3>
                    <p>查看所有已发生的推送记录</p>
                    <p>包括成功/失败状态、推送时间、错误信息</p>
                </div>
            `;
            return;
        }
        
        setTimeout(() => {
            container.innerHTML = logs.map(log => `
                <div class="log-card" title="${getLogTooltip(log)}">
                    <span class="log-status ${log.status === 'SUCCESS' ? 'success' : 'failed'}">${log.status}</span>
                    <div style="flex:1;min-width:0;">
                        <div style="font-weight:600;margin-bottom:2px;">${UTILS.escape(log.keyword)}</div>
                        <div style="font-size:11px;color:var(--text-light);">
                            ${log.label ? `[${UTILS.escape(log.label)}] ` : ''}
                            ${log.hotValue ? `热度: ${UTILS.formatNum(log.hotValue)} • ` : ''}
                            ${UTILS.formatTime(log.deliveredAt)}
                        </div>
                    </div>
                </div>
            `).join('');
        }, 200);
    }).catch(err => {
        container.innerHTML = `<div class="empty"><div class="empty-icon">️⚠️</div><p>加载失败: ${UTILS.escape(err.message)}</p></div>`;
    });
}

function getLogTooltip(log) {
    let tooltip = `关键词: ${log.keyword}\n`;
    tooltip += `状态: ${log.status}\n`;
    tooltip += `时间: ${log.deliveredAt}\n`;
    if (log.label) tooltip += `标签: ${log.label}\n`;
    if (log.hotValue) tooltip += `热度: ${log.hotValue}\n`;
    if (log.error) tooltip += `错误: ${log.error}\n`;
    return tooltip.trim();
}

document.getElementById('log-hours').addEventListener('change', () => {
    loadLogs();
});

// ==================== Modal Utilities ====================
let lastFocusedElement = null;

function showModal(modalId) {
    const modal = document.getElementById(modalId);
    if (!modal) return;
    lastFocusedElement = document.activeElement;
    modal.classList.remove('hidden');
    document.getElementById('global-search-input')?.focus();
}

function hideModal(modalId) {
    const modal = document.getElementById(modalId);
    if (!modal) return;
    modal.classList.add('hidden');
    if (lastFocusedElement) {
        lastFocusedElement.focus();
        lastFocusedElement = null;
    }
}

// ==================== Debounce Utility ====================
function debounce(func, wait) {
    let timeout;
    return function executedFunction(...args) {
        const later = () => {
            clearTimeout(timeout);
            func(...args);
        };
        clearTimeout(timeout);
        timeout = setTimeout(later, wait);
    };
}

// ==================== Init ====================
function showDashboard(username, mustChangePassword) {
    document.getElementById('user-info').textContent = `@${username}`;
    document.getElementById('login-page').classList.add('hidden');
    document.getElementById('dashboard-page').classList.remove('hidden');

    // Set theme
    State.setTheme(State.theme);
    document.getElementById('btn-theme').textContent = State.theme === 'light' ? '🌙' : '☀️';

    // Initialize charts
    setTimeout(() => {
        try {
            ChartManager.initCharts();
        } catch (e) { console.warn('Chart initialization failed:', e); }
    }, 100);

    // Force password change on first login
    if (mustChangePassword) {
        setTimeout(() => {
            UTILS.showToast('首次登录，请修改默认密码', 'warning');
            showModal('pwd-modal');
        }, 500);
    }

    // Load initial tab
    loadTab('hotsearch');
}

// Auto login if token exists
if (API.token) {
    (async () => {
        try {
            await API.get('/api/health');
            const payload = JSON.parse(atob(API.token.split('.')[1]));
            showDashboard(payload.sub || 'admin');
        } catch (e) {
            API.logout();
        }
    })();
}

// Add freshness banner
setTimeout(() => {
    const hotsearchTab = document.getElementById('tab-hotsearch');
    if (!hotsearchTab.querySelector('.freshness-banner')) {
        hotsearchTab.insertAdjacentHTML('afterbegin', `
            <div class="freshness-banner">
                <div class="freshness-title">🎯 数据时效性说明</div>
                <p>数据为定时抓取快照，非实时更新。最近更新：<span class="freshness-time">—</span></p>
                <p style="font-size:11px;color:var(--text-light);margin-top:4px;">系统每小时自动获取微博热搜榜最新数据</p>
            </div>
        `);
    }
}, 100);

// Export for debugging
window.State = State;
window.API = API;
window.UTILS = UTILS;