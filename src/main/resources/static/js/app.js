// ==================== Design Tokens ====================
const DESIGN = {
    primary: '#e7352f',
    danger: '#d92d42',
    success: '#0f9f5f',
    warning: '#d97706',
    orangeCoral: '#ff7a45',
    orangeDeep: '#b91f24',
    pinkMagenta: '#e7352f',
    purpleNeon: '#6e56cf',
    accent: '#186bff',
    textDark: '#151924',
    textLight: '#8b94a4',
    labelPalette: {
        burst: '#b4232e',
        hot: '#c9822a',
        boil: '#6e56cf',
        fresh: '#16a36f',
        other: '#3f6f8f',
        ad: '#9a7a50',
        none: '#9aa1ac'
    }
};

// ==================== API Layer ====================
class APIService {
    constructor() {
        this.baseUrl = '';
        this.token = localStorage.getItem('token') || null;
    }

    async request(method, path, body = null) {
        const headers = {};
        if (body != null) headers['Content-Type'] = 'application/json';
        if (this.token) headers['Authorization'] = `Bearer ${this.token}`;

        const opts = { method, headers };
        if (body != null) opts.body = JSON.stringify(body);
        
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
    async patch(path, body) { return this.request('PATCH', path, body); }
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
        this.hotSearchFetchedAt = null;
        this.hotSearchCache = { data: null, loadedAt: 0 };
        this.channelsCache = { data: null, loadedAt: 0 };
        this.theme = localStorage.getItem('theme') || 'light';
        this.currentTab = 'hotsearch';
        this.autoRefresh = null;
        
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
                loadHotSearch({ force: true, showSkeleton: false });
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
        if (!s) return [];
        const parts = [];
        let current = '';
        let escapeNext = false;
        let parenDepth = 0;
        let bracketDepth = 0;
        let braceDepth = 0;

        for (const ch of s) {
            if (escapeNext) {
                current += ch;
                escapeNext = false;
                continue;
            }

            if (ch === '\\') {
                current += ch;
                escapeNext = true;
                continue;
            }

            if (ch === '(') parenDepth++;
            else if (ch === ')' && parenDepth > 0) parenDepth--;
            else if (ch === '[') bracketDepth++;
            else if (ch === ']' && bracketDepth > 0) bracketDepth--;
            else if (ch === '{') braceDepth++;
            else if (ch === '}' && braceDepth > 0) braceDepth--;

            if ((ch === ',' || ch === '，') && parenDepth === 0 && bracketDepth === 0 && braceDepth === 0) {
                const value = current.trim();
                if (value) parts.push(value);
                current = '';
            } else {
                current += ch;
            }
        }

        const value = current.trim();
        if (value) parts.push(value);
        return parts;
    },
    formatNum(n) { 
        if (n >= 100000) return (n / 10000).toFixed(1) + '万';
        if (n >= 10000) return (n / 1000).toFixed(1) + 'k';
        return n.toLocaleString();
    },
    formatTime(t) {
        if (!t) return '-';
        if (typeof t === 'number') t = new Date(t).toISOString();
        const d = new Date(t.replace(' ', 'T') + (t.includes('Z') || t.includes('+') ? '' : 'Z'));
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
        if (label === '沸') return 'label-boil';
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

        // Force canvas to respect device pixel ratio for sharp rendering
        Chart.defaults.devicePixelRatio = window.devicePixelRatio || 1;

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
                    backgroundColor: [],
                    borderColor: getComputedStyle(document.documentElement).getPropertyValue('--card').trim() || '#ffffff',
                    borderWidth: 3,
                    borderRadius: 4
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: true,
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
                            title: (ctx) => ctx[0].label,
                            label: (ctx) => `热度: ${UTILS.formatNum(ctx.raw)}`
                        }
                    }
                },
                scales: {
                    x: {
                        grid: { display: false },
                        ticks: {
                            autoSkip: false,
                            maxRotation: 45,
                            minRotation: 0,
                            font: { size: 11 },
                            color: getComputedStyle(document.documentElement).getPropertyValue('--text-light')
                        }
                    },
                    y: {
                        beginAtZero: true,
                        grid: { color: getComputedStyle(document.documentElement).getPropertyValue('--border') },
                        ticks: {
                            color: getComputedStyle(document.documentElement).getPropertyValue('--text-light'),
                            callback: (v) => UTILS.formatNum(v)
                        }
                    }
                }
            }
        };
    },

    updateLabelData(data) {
        if (!this.labelChart) return;

        this.labelChart.data.labels = data.labels;
        this.labelChart.data.datasets[0].data = data.values;
        this.labelChart.data.datasets[0].backgroundColor = data.colors || [];
        this.labelChart.update();
    },

    updateHeatData(data) {
        if (!this.heatChart) return;

        this.heatChart.data.labels = data.labels;
        this.heatChart.data.datasets[0].data = data.values;
        this.heatChart.update();
    }
};

window.addEventListener('chartjs-ready', () => {
    if (ChartManager.labelChart || document.getElementById('dashboard-page').classList.contains('hidden')) {
        return;
    }
    try {
        ChartManager.initCharts();
        if (State.allItems.length) updateCharts(State.allItems);
    } catch (e) {
        console.warn('Chart initialization failed:', e);
    }
});
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
async function getHotSearchResult(force = false) {
    const cacheTtlMs = 30000;
    const now = Date.now();
    if (!force && State.hotSearchCache.data && (now - State.hotSearchCache.loadedAt) < cacheTtlMs) {
        return State.hotSearchCache.data;
    }

    const result = await API.get('/api/hotsearch');
    State.hotSearchCache = { data: result, loadedAt: now };
    return result;
}

function renderHotSearch(result = null) {
    const container = document.getElementById('hotsearch-list');
    let items = result ? (Array.isArray(result) ? result : (result.items || [])) : State.allItems;
    const fetchedAt = result ? (result.fetchedAt || null) : State.hotSearchFetchedAt;

    items = items.filter(item => item.keyword);
    State.allItems = items;
    State.hotSearchFetchedAt = fetchedAt;

    if (!items.length) {
        container.innerHTML = '<div class="empty"><div class="empty-icon">😔</div><p>暂无热搜数据</p></div>';
        return;
    }

    updateStats(items);
    updateCharts(items);

    const search = document.getElementById('hotsearch-search').value.toLowerCase().trim();
    const filteredItems = search
        ? items.filter(item => item.keyword && item.keyword.toLowerCase().includes(search))
        : items;

    if (!filteredItems.length) {
        container.innerHTML = `<div class="empty"><div class="empty-icon">—</div><p>未找到匹配 "${UTILS.escape(search)}" 的热搜</p></div>`;
        return;
    }

    container.innerHTML = filteredItems.map((item, i) => renderHotItem(item, i)).join('');

    if (fetchedAt && document.querySelector('.freshness-banner')) {
        document.querySelector('.freshness-banner .freshness-time').textContent = UTILS.formatTime(fetchedAt);
    }
}

async function loadHotSearch(options = {}) {
    const container = document.getElementById('hotsearch-list');
    const force = options.force === true;
    const showSkeleton = options.showSkeleton !== false;

    if (showSkeleton && !State.allItems.length) {
        UTILS.showLoading(container);
    }

    try {
        const result = await getHotSearchResult(force);
        renderHotSearch(result);
    } catch (e) {
        container.innerHTML = '<div class="empty"><div class="empty-icon">!</div><p>加载失败: ' + UTILS.escape(e.message) + '</p></div>';
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
                    ${item.url ? `<a href="${UTILS.escape(item.url)}" target="_blank" rel="noopener" class="hot-link">${UTILS.escape(item.keyword)}</a>` : UTILS.escape(item.keyword)}${item.isAd ? ' · 广告' : ''}
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
    const boil = items.filter(x => x.label === '沸').length;

    document.getElementById('stat-total').textContent = total;
    document.getElementById('stat-burst').textContent = burst;
    document.getElementById('stat-new').textContent = newItems;
    document.getElementById('stat-ads').textContent = boil;
}

function updateCharts(items) {
    // Label distribution
    const labels = ['爆', '热', '沸', '新', '其他有效', '广告', '无标签'];
    const counts = [0, 0, 0, 0, 0, 0, 0];
    const colors = [
        DESIGN.labelPalette.burst,
        DESIGN.labelPalette.hot,
        DESIGN.labelPalette.boil,
        DESIGN.labelPalette.fresh,
        DESIGN.labelPalette.other,
        DESIGN.labelPalette.ad,
        DESIGN.labelPalette.none
    ];
    items.forEach(item => {
        if (item.label === '爆') counts[0]++;
        else if (item.label === '热') counts[1]++;
        else if (item.label === '沸') counts[2]++;
        else if (item.label === '新') counts[3]++;
        else if (item.label && !['荐'].includes(item.label)) counts[4]++;
        else if (item.isAd || item.label === '荐') counts[5]++;
        else counts[6]++;
    });
    
    // Only show non-zero labels
    const validLabels = labels.filter((_, i) => counts[i] > 0);
    const validCounts = counts.filter(c => c > 0);
    const validColors = colors.filter((_, i) => counts[i] > 0);
    
    if (ChartManager.labelChart) {
        ChartManager.updateLabelData({ labels: validLabels, values: validCounts, colors: validColors });
    }

    // Heat top 10
    const sortedByHeat = items.filter(x => x.hotValue).sort((a,b) => b.hotValue - a.hotValue).slice(0, 10);
    if (sortedByHeat.length && ChartManager.heatChart) {
        const heatLabels = sortedByHeat.map(x => x.keyword);
        const heatValues = sortedByHeat.map(x => x.hotValue);
        ChartManager.updateHeatData({ labels: heatLabels, values: heatValues });
    }
}

// Search
document.getElementById('hotsearch-search').addEventListener('input', debounce(() => {
    if (State.allItems.length) renderHotSearch();
    else loadHotSearch({ showSkeleton: false });
}, 150));

// Trigger
document.getElementById('btn-trigger').addEventListener('click', async () => {
    try {
        await API.post('/api/hotsearch/trigger', {});
        UTILS.showToast('推送管线已触发，即将刷新数据...', 'success');
        setTimeout(() => {
            loadHotSearch({ force: true });
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
        const currentInterval = config.fetchIntervalMinutes || 10;
        document.getElementById('current-dedupe-hours').textContent = currentHours;
        document.getElementById('dedupe-hours').value = currentHours;
        document.getElementById('btn-save-dedupe-config').disabled = true;
        document.getElementById('fetch-interval-minutes').value = currentInterval;
        document.getElementById('current-fetch-interval').textContent = currentInterval;
        document.getElementById('btn-save-fetch-config').disabled = true;
        document.getElementById('sink-base-url').value = config.sinkBaseUrl || '';
        document.getElementById('sink-site-token').value = config.sinkToken || '';
        State.config = config;

        document.getElementById('dedupe-hours').onchange = () => {
            const newVal = document.getElementById('dedupe-hours').value;
            document.getElementById('btn-save-dedupe-config').disabled = (parseInt(newVal) === currentHours);
        };
        document.getElementById('fetch-interval-minutes').oninput = () => {
            const newVal = parseInt(document.getElementById('fetch-interval-minutes').value);
            document.getElementById('btn-save-fetch-config').disabled = (newVal === currentInterval);
        };
    } catch (e) { console.warn('加载配置失败:', e.message); }
}

document.getElementById('btn-save-dedupe-config').addEventListener('click', async () => {
    const hours = parseInt(document.getElementById('dedupe-hours').value);
    try {
        await API.put('/api/config', { dedupeWindowHours: hours });
        document.getElementById('current-dedupe-hours').textContent = hours;
        document.getElementById('btn-save-dedupe-config').disabled = true;
        UTILS.showToast(`去重窗口已更新为 ${hours} 小时`, 'success');
    } catch (err) {
        UTILS.showToast('保存失败: ' + err.message, 'error');
    }
});

document.getElementById('btn-save-fetch-config').addEventListener('click', async () => {
    const minutes = parseInt(document.getElementById('fetch-interval-minutes').value);
    try {
        await API.put('/api/config', { fetchIntervalMinutes: minutes });
        document.getElementById('current-fetch-interval').textContent = minutes;
        document.getElementById('btn-save-fetch-config').disabled = true;
        UTILS.showToast(`数据抓取频率已更新为 ${minutes} 分钟/次`, 'success');
    } catch (err) {
        UTILS.showToast('保存失败: ' + err.message, 'error');
    }
});

document.getElementById('btn-save-sink-config').addEventListener('click', async () => {
    const sinkBaseUrl = document.getElementById('sink-base-url').value.trim();
    const sinkToken = document.getElementById('sink-site-token').value.trim();
    try {
        const config = await API.put('/api/config', { sinkBaseUrl, sinkToken });
        document.getElementById('sink-base-url').value = config.sinkBaseUrl || '';
        document.getElementById('sink-site-token').value = config.sinkToken || '';
        State.config = config;
        UTILS.showToast(config.sinkConfigured ? 'Sink 短链服务配置已保存' : 'Sink 短链服务配置已清除', 'success');
    } catch (err) {
        UTILS.showToast('保存失败: ' + err.message, 'error');
    }
});

// ==================== Cached Data Helpers ====================
async function getChannels(force = false) {
    const cacheTtlMs = 30000;
    const now = Date.now();
    if (!force && State.channelsCache.data && (now - State.channelsCache.loadedAt) < cacheTtlMs) {
        return State.channelsCache.data;
    }

    const channels = await API.get('/api/channels');
    State.channelsCache = { data: channels, loadedAt: now };
    return channels;
}

function invalidateChannelsCache() {
    State.channelsCache = { data: null, loadedAt: 0 };
}
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
                    <div class="empty-icon">—</div>
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
                    <div class="empty-icon">—</div>
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
                                <button class="btn btn-sm btn-ghost" onclick="editSubscription(${sub.id})" title="编辑">编辑</button>
                                <button class="btn btn-sm btn-ghost btn-danger" onclick="deleteSubscription(${sub.id})" title="删除">删除</button>
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
                        标签过滤: ${sub.labels?.join(', ') || '无'} • 最低热度: ${sub.minHotValue || '不限'} • 推送: ${sub.channelIds?.length ? `已选${sub.channelIds.length}个通道` : '全部通道'} • 创建: ${UTILS.formatTime(sub.createdAt)}
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
        container.innerHTML = `<div class="empty"><div class="empty-icon">!</div><p>加载失败: ${UTILS.escape(err.message)}</p></div>`;
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

    // Load channels for checkbox selection
    getChannels().then(channels => {
        const container = document.getElementById('sub-channel-list');
        const selectedIds = subscription?.channelIds || [];
        if (channels.length) {
            container.innerHTML = channels.map(ch => `
                <label class="checkbox-label" style="font-size:13px;">
                    <input type="checkbox" class="sub-ch-cb" value="${ch.id}"
                        ${selectedIds.includes(ch.id) ? 'checked' : ''}>
                    ${getProviderLabel(ch.provider)} ${ch.provider === 'wechat' ? '→ ' + UTILS.escape(ch.config?.chat || '未配置') : ''}
                </label>
            `).join('');
        } else {
            container.innerHTML = '<span style="font-size:12px;color:var(--text-light);">暂无可用通道</span>';
        }
    }).catch(() => {
        document.getElementById('sub-channel-list').innerHTML = '<span style="font-size:12px;color:var(--text-light);">加载通道失败</span>';
    });

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
        channelIds: Array.from(document.querySelectorAll('.sub-ch-cb:checked')).map(cb => parseInt(cb.value)),
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
    API.patch(`/api/subscriptions/${id}/enabled`, { enabled }).then(() => {
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
    
    getChannels().then(channels => {
        if (!channels.length) {
            container.innerHTML = `
                <div class="empty onboarding">
                    <div class="empty-icon">—</div>
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
                                <button class="btn btn-sm btn-ghost" onclick="editChannel(${ch.id})" title="编辑">编辑</button>
                                <button class="btn btn-sm btn-ghost btn-danger" onclick="deleteChannel(${ch.id})" title="删除">删除</button>
                            </div>
                        </div>
                    </div>
                    <div class="item-meta">${describeChannelConfig(ch)}</div>
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
        container.innerHTML = `<div class="empty"><div class="empty-icon">!</div><p>加载失败: ${UTILS.escape(err.message)}</p></div>`;
    });
}

function getProviderLabel(provider) {
    const labels = {
        feishu: '飞书 Webhook',
        dingtalk: '钉钉 Webhook',
        wecom: '企业微信',
        wechat: '微信机器人',
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

function describeChannelConfig(ch) {
    const config = ch.config || {};
    const shortLink = config.shortLinkEnabled ? ' · 短链: 已启用' : '';
    if (ch.provider === 'wechat') {
        return `目标聊天: ${UTILS.escape(config.chat || '未配置')} · API: ${UTILS.escape(config.apiBaseUrl || 'http://localhost:5001')}${shortLink}`;
    }
    if (ch.provider === 'feishu' && config.mode === 'app') {
        return `自建应用: ${UTILS.escape(config.appId || '未配置')} · 接收: ${UTILS.escape(config.receiveId || config.token || '未配置')}${shortLink}`;
    }
    if (ch.provider === 'telegram') {
        return `Bot Token: ${UTILS.escape(config.token || '未配置')} · Chat ID: ${UTILS.escape(config.chatId || '未配置')}${shortLink}`;
    }
    return `Webhook: ${maskWebhookUrl(config.webhookUrl || config.webhook_url || '')}${shortLink}`;
}

function updateProviderHint() {
    const provider = document.getElementById('ch-provider').value;
    const hintEl = document.getElementById('ch-provider-hint');
    const urlLabel = document.getElementById('ch-url-label');
    const webhookInput = document.getElementById('ch-webhook');
    const fieldFeishuMode = document.getElementById('ch-field-feishu-mode');
    const feishuMode = document.getElementById('ch-feishu-mode').value;
    const fieldWebhook = document.getElementById('ch-field-webhook');
    const fieldFeishuApp = document.getElementById('ch-field-feishu-app');
    const fieldTelegram = document.getElementById('ch-field-telegram');
    const fieldWechat = document.getElementById('ch-field-wechat');

    const hints = {
        feishu: '提示：飞书群机器人 → 添加机器人 → 复制自定义机器人 Webhook 地址',
        dingtalk: '提示：钉钉群机器人 → 安全设置 → Webhook → 复制带 access_token 的地址',
        wecom: '提示：企业微信群 → 添加群机器人 → 复制 key 参数形式的 Webhook 地址',
        wechat: '提示：通过 WeChatBot RESTful API 发送微信消息',
        telegram: '提示：Telegram 需要 Bot Token 和 Chat ID，本服务会直接调用 sendMessage API',
        generic: '提示：任意支持 POST JSON 的 HTTP/HTTPS 端点'
    };

    const labels = {
        feishu: 'Webhook URL',
        dingtalk: 'Webhook URL',
        wecom: 'Webhook URL',
        telegram: 'Bot Token',
        generic: 'Webhook URL'
    };

    const placeholders = {
        feishu: 'https://open.feishu.cn/open-apis/bot/v2/hook/xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx',
        dingtalk: 'https://oapi.dingtalk.com/robot/send?access_token=xxxxxxxxxxxxxxxx',
        wecom: 'https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx',
        generic: 'https://example.com/webhook/weibo-hotsearch'
    };

    fieldFeishuMode.style.display = provider === 'feishu' ? '' : 'none';

    if (provider === 'feishu' && feishuMode === 'app') {
        fieldWebhook.style.display = 'none';
        fieldFeishuApp.style.display = '';
        fieldTelegram.style.display = 'none';
        fieldWechat.style.display = 'none';
        hintEl.textContent = '提示：自建应用机器人：App ID + App Secret 获取 tenant_access_token，再向接收 ID 发送消息';
    } else if (provider === 'feishu') {
        fieldWebhook.style.display = '';
        fieldFeishuApp.style.display = 'none';
        fieldTelegram.style.display = 'none';
        fieldWechat.style.display = 'none';
        hintEl.textContent = hints.feishu;
        urlLabel.textContent = labels.feishu;
        webhookInput.placeholder = placeholders.feishu;
    } else
    if (provider === 'wechat') {
        fieldWebhook.style.display = 'none';
        fieldFeishuApp.style.display = 'none';
        fieldTelegram.style.display = 'none';
        fieldWechat.style.display = '';
        hintEl.textContent = hints.wechat;
    } else if (provider === 'telegram') {
        fieldWebhook.style.display = 'none';
        fieldFeishuApp.style.display = 'none';
        fieldTelegram.style.display = '';
        fieldWechat.style.display = 'none';
        hintEl.textContent = hints.telegram;
    } else {
        fieldWebhook.style.display = '';
        fieldFeishuApp.style.display = 'none';
        fieldTelegram.style.display = 'none';
        fieldWechat.style.display = 'none';
        hintEl.textContent = hints[provider] || '请填写通道配置';
        urlLabel.textContent = labels[provider] || '配置';
        webhookInput.placeholder = placeholders[provider] || placeholders.generic;
    }

}

function showChannelModal(channel = null) {
    editingChannelId = channel?.id || null;
    const modal = document.getElementById('ch-modal');
    const title = document.getElementById('ch-modal-title');
    document.getElementById('ch-shortlink-enabled').checked = false;
    
    title.textContent = channel ? '编辑通道' : '新增通道';
    
    if (channel) {
        document.getElementById('ch-id').value = channel.id;
        document.getElementById('ch-provider').value = channel.provider;
        if (channel.provider === 'feishu') {
            const mode = channel.config?.mode || (channel.config?.appId ? 'app' : 'webhook');
            document.getElementById('ch-feishu-mode').value = mode;
            document.getElementById('ch-webhook').value = channel.config?.webhookUrl || channel.config?.webhook_url || '';
            document.getElementById('ch-feishu-app-id').value = channel.config?.appId || channel.config?.app_id || '';
            document.getElementById('ch-feishu-app-secret').value = channel.config?.appSecret || channel.config?.app_secret || '';
            document.getElementById('ch-feishu-receive-id').value = channel.config?.receiveId || channel.config?.token || '';
            document.getElementById('ch-feishu-receive-id-type').value = channel.config?.receiveIdType || 'chat_id';
        } else if (channel.provider === 'wechat') {
            document.getElementById('ch-wechat-api').value = channel.config?.apiBaseUrl || 'http://localhost:5001';
            document.getElementById('ch-wechat-token').value = channel.config?.token || '';
            document.getElementById('ch-wechat-chat').value = channel.config?.chat || '';
        } else if (channel.provider === 'telegram') {
            document.getElementById('ch-telegram-token').value = channel.config?.token || '';
            document.getElementById('ch-telegram-chat').value = channel.config?.chatId || '';
        } else {
            document.getElementById('ch-webhook').value = channel.config?.webhookUrl || channel.config?.webhook_url || '';
        }
        document.getElementById('ch-shortlink-enabled').checked = channel.config?.shortLinkEnabled === true;
        document.getElementById('ch-enabled').checked = channel.enabled !== false;
    } else {
        document.getElementById('ch-form').reset();
        document.getElementById('ch-feishu-mode').value = 'webhook';
        document.getElementById('ch-feishu-receive-id-type').value = 'chat_id';
        document.getElementById('ch-wechat-api').value = 'http://localhost:5001';
        document.getElementById('ch-enabled').checked = true;
    }
    
    updateProviderHint();
    showModal('ch-modal');
}

document.getElementById('btn-add-ch').addEventListener('click', () => showChannelModal());

document.getElementById('ch-provider').addEventListener('change', updateProviderHint);
document.getElementById('ch-feishu-mode').addEventListener('change', updateProviderHint);
document.getElementById('btn-open-sink-config').addEventListener('click', () => {
    hideModal('ch-modal');
    document.querySelector('.tab[data-tab="config"]').click();
    setTimeout(() => document.getElementById('sink-config-card').scrollIntoView({ behavior: 'smooth', block: 'start' }), 100);
});

document.getElementById('ch-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    
    const provider = document.getElementById('ch-provider').value;
    const enabled = document.getElementById('ch-enabled').checked;

    let config;
    if (provider === 'feishu') {
        const mode = document.getElementById('ch-feishu-mode').value;
        if (mode === 'app') {
            config = {
                mode,
                appId: document.getElementById('ch-feishu-app-id').value.trim(),
                appSecret: document.getElementById('ch-feishu-app-secret').value.trim(),
                receiveId: document.getElementById('ch-feishu-receive-id').value.trim(),
                receiveIdType: document.getElementById('ch-feishu-receive-id-type').value
            };
        } else {
            config = {
                mode,
                webhookUrl: document.getElementById('ch-webhook').value.trim()
            };
        }
    } else if (provider === 'wechat') {
        config = {
            apiBaseUrl: document.getElementById('ch-wechat-api').value.trim() || 'http://localhost:5001',
            token: document.getElementById('ch-wechat-token').value.trim(),
            chat: document.getElementById('ch-wechat-chat').value.trim()
        };
    } else if (provider === 'telegram') {
        config = {
            token: document.getElementById('ch-telegram-token').value.trim(),
            chatId: document.getElementById('ch-telegram-chat').value.trim()
        };
    } else {
        const webhook = document.getElementById('ch-webhook').value.trim();
        config = { webhookUrl: webhook };
    }

    config.shortLinkEnabled = document.getElementById('ch-shortlink-enabled').checked;

    const channel = { provider, config, enabled };
    
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
        invalidateChannelsCache();
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
    API.patch(`/api/channels/${id}/enabled`, { enabled }).then(() => {
        UTILS.showToast(`通道已${enabled ? '启用' : '禁用'}`, 'success');
        invalidateChannelsCache();
        loadChannels();
    }).catch(err => UTILS.showToast(err.message, 'error'));
}

function deleteChannel(id) {
    if (!confirm('确定删除这条通道吗？')) return;
    
    API.delete(`/api/channels/${id}`).then(() => {
        UTILS.showToast('通道删除成功', 'success');
        invalidateChannelsCache();
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
                    <div class="empty-icon">—</div>
                    <h3>推送日志</h3>
                    <p>查看所有已发生的推送记录</p>
                    <p>包括成功/失败状态、推送时间、错误信息</p>
                </div>
            `;
            return;
        }

        setTimeout(() => {
            container.innerHTML = logs.map(entry => renderLogEntry(entry)).join('');
        }, 200);
    }).catch(err => {
        container.innerHTML = `<div class="empty"><div class="empty-icon">!</div><p>加载失败: ${UTILS.escape(err.message)}</p></div>`;
    });
}

function renderLogEntry(entry) {
    let labelHtml = '';
    if (entry.label) labelHtml = ` <span class="hot-label ${UTILS.getLabelClass(entry.label)}" style="font-size:10px;vertical-align:middle;">${UTILS.escape(entry.label)}</span>`;

    let hotHtml = '';
    if (entry.hotValue) hotHtml = ` <span class="log-hot-value">${UTILS.formatNum(entry.hotValue)}</span>`;

    let channelsHtml = '';
    if (entry.channels && entry.channels.length) {
        channelsHtml = entry.channels.map(ch => {
            const icon = ch.status === 'SUCCESS' ? '✓' : '✗';
            const cls = ch.status === 'SUCCESS' ? 'ch-ok' : 'ch-fail';
            let line = `<span class="${cls}">${icon} ${shortProviderName(ch.provider)}`;
            if (ch.target) line += ` · ${UTILS.escape(ch.target)}`;
            if (ch.error) line += ` <span class="ch-err-msg">(${UTILS.escape(ch.error)})</span>`;
            if (ch.deliveredAt) line += ` <span class="ch-time">${UTILS.formatTime(ch.deliveredAt)}</span>`;
            line += '</span>';
            return line;
        }).join('');
    }

    return `
        <div class="log-card log-card-v2">
            <div class="log-kw-main">
                <span class="log-kw">${UTILS.escape(entry.keyword)}</span>${labelHtml}${hotHtml}
            </div>
            ${channelsHtml ? `<div class="log-ch-list">${channelsHtml}</div>` : ''}
            <div class="log-time">${UTILS.formatTime(entry.deliveredAt)}</div>
        </div>
    `;
}

function shortProviderName(provider) {
    const names = {
        feishu: '飞书', dingtalk: '钉钉', wecom: '企微',
        wechat: '微信', telegram: 'Telegram', generic: 'Webhook'
    };
    return names[provider] || provider;
}

document.getElementById('btn-clear-logs').addEventListener('click', async () => {
    if (!confirm('确定清空所有推送日志吗？这将重置去重状态，已推送过的关键词可能再次推送。')) return;
    try {
        await API.delete('/api/delivery-logs');
        UTILS.showToast('推送日志已清空，去重状态已重置', 'success');
        loadLogs();
    } catch (err) {
        UTILS.showToast('清空失败: ' + err.message, 'error');
    }
});

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

    // Initialize charts then load initial tab
    setTimeout(() => {
        try {
            ChartManager.initCharts();
        } catch (e) { console.warn('Chart initialization failed:', e); }
        loadTab('hotsearch');
    }, 100);

    // Force password change on first login
    if (mustChangePassword) {
        setTimeout(() => {
            UTILS.showToast('首次登录，请修改默认密码', 'warning');
            showModal('pwd-modal');
        }, 500);
    }
}

// Auto login if token exists and not expired
if (API.token) {
    try {
        const payload = JSON.parse(atob(API.token.split('.')[1]));
        const nowSec = Math.floor(Date.now() / 1000);
        if (payload.exp && payload.exp < nowSec) {
            API.logout(); // token expired, clear and show login
        } else {
            showDashboard(payload.sub || 'admin');
        }
    } catch (e) {
        API.logout();
    }
}

// Add freshness banner
setTimeout(() => {
    const hotsearchTab = document.getElementById('tab-hotsearch');
    if (!hotsearchTab.querySelector('.freshness-banner')) {
        hotsearchTab.insertAdjacentHTML('afterbegin', `
            <div class="freshness-banner">
                <div class="freshness-title">🎯 数据时效性说明</div>
                <p>数据为定时抓取快照，非实时更新。最近更新：<span class="freshness-time">—</span></p>
                <p style="font-size:11px;color:var(--text-light);margin-top:4px;">系统按“系统配置”中的抓取频率自动获取微博热搜榜最新数据</p>
            </div>
        `);
    }
}, 100);

// Export for debugging
window.State = State;
window.API = API;
window.UTILS = UTILS;
