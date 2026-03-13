/**
 * BillMe — Admin Dashboard Controller
 * Handles Navigation, Data Fetching, and Charts
 */

// Global Chart Instances (to allow destruction before re-render)
let txTrendChart = null;
let payDistChart = null;
let merchantGrowthChart = null;

document.addEventListener('DOMContentLoaded', () => {
    // 1. Auth Check
    const token = localStorage.getItem("billme_token");
    const role = localStorage.getItem("billme_role");

    if (!token || role !== "admin") {
        window.location.href = "../src/login.html";
        return;
    }

    // 2. Initialize UI
    initNavigation();
    loadCurrentSection();

    // 3. Set Dynamic Year
    const yearEl = document.getElementById('currentYear');
    if (yearEl) yearEl.textContent = new Date().getFullYear();

    // 4. Logout Handler
    const logoutBtn = document.getElementById('logoutBtn');
    if (logoutBtn) {
        logoutBtn.addEventListener('click', () => {
            window.API.auth.logout();
        });
    }
});

/* ===========================================================
   NAVIGATION SYSTEM
=========================================================== */

function initNavigation() {
    const navItems = document.querySelectorAll('.nav-item');
    navItems.forEach(item => {
        item.addEventListener('click', (e) => {
            const link = item.querySelector('a');
            if (!link) return;
            
            const sectionId = link.getAttribute('href').replace('#', '');
            showSection(sectionId);
        });
    });

    // Listen for hash changes
    window.addEventListener('hashchange', () => {
        loadCurrentSection();
    });
}

function loadCurrentSection() {
    const hash = window.location.hash.replace('#', '') || 'dashboard';
    showSection(hash);
}

function showSection(sectionId) {
    // Update Sidebar Active State
    document.querySelectorAll('.nav-item').forEach(item => {
        const link = item.querySelector('a');
        if (link && link.getAttribute('href') === `#${sectionId}`) {
            item.classList.add('active');
        } else {
            item.classList.remove('active');
        }
    });

    // Update Section Visibility
    document.querySelectorAll('.section-page').forEach(sec => {
        sec.classList.remove('active');
        if (sec.id === `sec-${sectionId}`) {
            sec.classList.add('active');
        }
    });

    // Update Topbar Title
    const titles = {
        'dashboard': 'Platform Overview',
        'merchants': 'Merchant Management',
        'customers': 'Customer Directory',
        'transactions': 'Global Transactions',
        'settings': 'Admin Settings'
    };
    const titleEl = document.querySelector('.topbar div[style*="font-size:20px"]');
    if (titleEl) titleEl.innerText = titles[sectionId] || 'Admin Console';

    // Load Section Data
    switch (sectionId) {
        case 'dashboard': loadDashboard(); break;
        case 'merchants': loadMerchants(); break;
        case 'customers': loadCustomers(); break;
        case 'transactions': loadTransactions(); break;
    }
}

/* ===========================================================
   DATA LOADERS
=========================================================== */

async function loadDashboard() {
    try {
        toggleLoader(true);
        const data = await window.API.admin.getDashboard();
        
        // Update Stat Cards
        document.getElementById('count-merchants').innerText = data.totalMerchants || 0;
        document.getElementById('count-customers').innerText = data.totalCustomers || 0;
        document.getElementById('count-txns').innerText = data.totalTransactions || 0;
        document.getElementById('platform-revenue').innerText = '₹' + (data.totalRevenue || 0).toLocaleString();

        // Update Activity Table
        renderActivity(data.recentActivity || []);

        // Initialize Charts
        initDashboardCharts(data);

    } catch (err) {
        console.error('Dashboard Load Error:', err);
        showToast('Failed to load dashboard data', 'error');
    } finally {
        toggleLoader(false);
    }
}

async function loadMerchants() {
    try {
        toggleLoader(true);
        const merchants = await window.API.admin.getMerchants();
        const body = document.getElementById('merchantsTableBody');
        
        body.innerHTML = merchants.map(m => `
            <tr>
                <td><strong>${m.businessName}</strong></td>
                <td>${m.ownerName}</td>
                <td>${m.email}</td>
                <td><span class="badge badge-${m.active ? 'success' : 'warning'}">${m.active ? 'Active' : 'Pending'}</span></td>
                <td>
                    <button class="btn btn-sm btn-outline-primary" onclick="viewMerchant('${m.id}')">View</button>
                </td>
            </tr>
        `).join('') || '<tr><td colspan="5" class="text-center">No merchants found</td></tr>';

    } catch (err) {
        showToast('Failed to load merchants', 'error');
    } finally {
        toggleLoader(false);
    }
}

async function loadCustomers() {
    try {
        toggleLoader(true);
        const customers = await window.API.admin.getCustomers();
        const body = document.getElementById('customersTableBody');
        
        body.innerHTML = customers.map(c => `
            <tr>
                <td><code>${c.id}</code></td>
                <td>${c.fullName || c.username}</td>
                <td>${c.email}</td>
                <td>₹${(c.totalSpent || 0).toFixed(2)}</td>
                <td>${new Date(c.createdAt).toLocaleDateString()}</td>
            </tr>
        `).join('') || '<tr><td colspan="5" class="text-center">No customers found</td></tr>';

    } catch (err) {
        showToast('Failed to load customers', 'error');
    } finally {
        toggleLoader(false);
    }
}

async function loadTransactions() {
    try {
        toggleLoader(true);
        const txns = await window.API.admin.getTransactions();
        const body = document.getElementById('transactionsTableBody');
        
        body.innerHTML = txns.map(t => `
            <tr>
                <td><code>#${t.id}</code></td>
                <td class="fw-600 text-primary">₹${t.amount.toFixed(2)}</td>
                <td><span class="badge badge-info">${t.paymentMethod}</span></td>
                <td>${t.merchantName}</td>
                <td>${t.customerName}</td>
                <td><span class="badge badge-${t.status === 'SUCCESS' ? 'success' : 'warning'}">${t.status}</span></td>
                <td>${new Date(t.timestamp || t.createdAt).toLocaleString()}</td>
            </tr>
        `).join('') || '<tr><td colspan="7" class="text-center">No transactions recorded</td></tr>';

    } catch (err) {
        showToast('Failed to load transactions', 'error');
    } finally {
        toggleLoader(false);
    }
}

/* ===========================================================
   CHART SYSTEM
=========================================================== */

function initDashboardCharts(data) {
    // 1. Transactions Trend
    if (txTrendChart) txTrendChart.destroy();
    const trendCtx = document.getElementById('txTrendChart').getContext('2d');
    txTrendChart = new Chart(trendCtx, {
        type: 'line',
        data: {
            labels: data.monthlyLabels || ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun'],
            datasets: [{
                label: 'Transactions',
                data: data.monthlyTransactions || [0, 0, 0, 0, 0, 0],
                borderColor: '#1a73e8',
                backgroundColor: 'rgba(26, 115, 232, 0.1)',
                fill: true,
                tension: 0.4
            }]
        },
        options: { responsive: true, maintainAspectRatio: false }
    });

    // 2. Payment Distribution
    if (payDistChart) payDistChart.destroy();
    const payCtx = document.getElementById('payDistChart').getContext('2d');
    const payMethods = data.paymentMethods || { 'UPI': 0, 'FacePay': 0, 'Card': 0 };
    payDistChart = new Chart(payCtx, {
        type: 'doughnut',
        data: {
            labels: Object.keys(payMethods),
            datasets: [{
                data: Object.values(payMethods),
                backgroundColor: ['#4285f4', '#34a853', '#fbbc05', '#ea4335']
            }]
        },
        options: { responsive: true, maintainAspectRatio: false }
    });

    // 3. Merchant Growth
    if (merchantGrowthChart) merchantGrowthChart.destroy();
    const growthCtx = document.getElementById('merchantGrowthChart').getContext('2d');
    merchantGrowthChart = new Chart(growthCtx, {
        type: 'bar',
        data: {
            labels: data.growthLabels || ['Week 1', 'Week 2', 'Week 3', 'Week 4'],
            datasets: [{
                label: 'New Merchants',
                data: data.merchantGrowth || [0, 0, 0, 0],
                backgroundColor: '#1a73e8'
            }]
        },
        options: { responsive: true, maintainAspectRatio: false }
    });
}

function renderActivity(activities) {
    const body = document.getElementById('adminActivityBody');
    body.innerHTML = activities.map(act => `
        <tr>
            <td>${act.user}</td>
            <td>${act.action}</td>
            <td><span class="badge badge-info">${act.status}</span></td>
            <td>${act.time}</td>
        </tr>
    `).join('') || '<tr><td colspan="4" class="text-center">No recent activity</td></tr>';
}

/* ===========================================================
   UI HELPERS
=========================================================== */

function toggleLoader(show) {
    const loader = document.getElementById('pageLoader');
    if (loader) loader.style.display = show ? 'flex' : 'none';
}

function viewMerchant(id) {
    showToast(`Merchant details view for ${id} coming soon`, 'info');
}
