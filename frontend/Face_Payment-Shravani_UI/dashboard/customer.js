/**
 * BillMe — Customer Dashboard Logic
 * Hardened for Production Stabilization
 */

document.addEventListener('DOMContentLoaded', async () => {
    const token = localStorage.getItem("billme_token");
    const role = localStorage.getItem("billme_role");

    if (!token || role !== "customer") {
        window.location.href = "../src/login.html";
        return;
    }

    // Dynamic year
    const yearEl = document.getElementById('currentYear');
    if (yearEl) yearEl.textContent = new Date().getFullYear();

    try {
        await loadCustomerData();

        const loader = document.getElementById('pageLoader');
        const layout = document.getElementById('dashLayout');

        if (loader) loader.style.display = 'none';
        if (layout) layout.style.display = 'flex';

        // Initialize Navigation
        setupNavigation();

        // Initialize Logout
        setupLogout();

    } catch (err) {
        console.error('Failed to load dashboard:', err);
        if (window.API && window.API.showToast) {
            window.API.showToast('Failed to load dashboard data', 'error');
        }
    }
});

function setupNavigation() {
    document.querySelectorAll('.nav-item').forEach(item => {
        item.addEventListener('click', () => {
            const section = item.getAttribute('data-section');
            showSection(section);
        });
    });
}

function setupLogout() {
    const logoutBtn = document.getElementById('logoutBtn');
    if (logoutBtn) {
        logoutBtn.addEventListener('click', () => {
            localStorage.removeItem("billme_token");
            localStorage.removeItem("billme_role");
            localStorage.removeItem("billme_user_id");
            window.location.href = "../index.html";
        });
    }
}

function showSection(sectionId) {
    document.querySelectorAll('.section-page').forEach(sec => sec.classList.remove('active'));
    document.querySelectorAll('.nav-item').forEach(nav => nav.classList.remove('active'));

    const section = document.getElementById('sec-' + sectionId);
    if (section) section.classList.add('active');

    const nav = document.querySelector(`.nav-item[data-section="${sectionId}"]`);
    if (nav) nav.classList.add('active');

    const titles = {
        overview: 'Overview',
        invoices: 'My Invoices',
        profile: 'Profile Settings'
    };

    const titleEl = document.getElementById('pageTitle');
    if (titleEl) titleEl.innerText = titles[sectionId] || 'Dashboard';
}

async function loadCustomerData() {
    if (!window.API) {
        throw new Error("API client not loaded.");
    }

    try {
        /* ---------- PROFILE ---------- */
        const profile = await window.API.customer.getProfile();
        if (profile) {
            const name = profile.fullName || profile.username || "Customer";
            const avatarUrl = `https://ui-avatars.com/api/?name=${encodeURIComponent(name)}&background=1a73e8&color=fff`;

            setText('side-name', name);
            setText('side-id', '#' + (profile.id || 'CUST'));
            setText('prof-name', name);
            setText('prof-email', profile.email);
            setText('prof-id', '#' + (profile.id || 'CUST'));
            setText('prof-location', profile.location || 'Not Set');
            
            setText('prof-join', profile.createdAt ? new Date(profile.createdAt).toLocaleDateString() : '-');
            setText('prof-active', profile.lastActive ? new Date(profile.lastActive).toLocaleDateString() : 'Active Now');

            setImage('side-avatar', avatarUrl);
            setImage('prof-avatar', avatarUrl);
        }

        /* ---------- INVOICES ---------- */
        const invoices = await window.API.customer.getInvoices();
        renderInvoices(invoices || []);
        updateAnalytics(invoices || []);
    } catch (err) {
        console.error("Data load error:", err);
        throw err;
    }
}

function renderInvoices(invoices) {
    const recentBody = document.getElementById('recentInvoicesBody');
    const allBody = document.getElementById('allInvoicesBody');

    if (!recentBody || !allBody) return;

    const rows = invoices.map(inv => {
        const invoiceId = inv.id || inv.invoiceId;
        if (!invoiceId) return '';

        const amount = Number(inv.totalPayable || 0);
        const status = inv.status || "UNPAID";

        let statusBadgeClass = 'warning';
        if (status === 'PAID') statusBadgeClass = 'success';
        if (status === 'REFUNDED') statusBadgeClass = 'secondary';
        if (status === 'REFUND_REQUESTED') statusBadgeClass = 'info';
        if (status === 'FAILED') statusBadgeClass = 'danger';

        let actionsHtml = `<button class="btn btn-secondary btn-sm" onclick="previewInvoice('${invoiceId}')" title="Preview"><i class="fas fa-eye"></i></button>`;
        
        if (status === 'UNPAID') {
            actionsHtml += `<button class="btn btn-primary btn-sm" onclick="payInvoice('${inv.invoiceNumber}', '${inv.paymentToken}')">Pay Now</button>`;
        } else if (status === 'PAID') {
            actionsHtml += `<button class="btn btn-outline-danger btn-sm" onclick="requestRefund('${invoiceId}')">Request Refund</button>`;
        } else if (status === 'REFUND_REQUESTED') {
            actionsHtml += `<span class="badge badge-info" style="font-size:11px;">Pending Refund</span>`;
        }

        return `
        <tr>
            <td>${inv.invoiceNumber || "-"}</td>
            <td>${inv.merchantName || "Merchant"}</td>
            <td class="fw-600">₹${amount.toFixed(2)}</td>
            <td>
                <span class="badge badge-${statusBadgeClass}">
                    ${status.replace('_', ' ')}
                </span>
            </td>
            <td>${inv.createdAt ? new Date(inv.createdAt).toLocaleDateString() : "-"}</td>
            <td>
                <div class="d-flex gap-1 align-items-center">
                    ${actionsHtml}
                    <button class="btn btn-secondary btn-sm" onclick="downloadInvoice('${invoiceId}')" title="Download PDF">
                        <i class="fas fa-download"></i>
                    </button>
                </div>
            </td>
        </tr>`;
    }).join('');

    allBody.innerHTML = invoices.length ? rows : '<tr><td colspan="6" class="text-center text-muted">No invoices found</td></tr>';
    recentBody.innerHTML = invoices.length ? rows.split('</tr>').slice(0, 5).join('</tr>') : '<tr><td colspan="6" class="text-center text-muted">No recent activity</td></tr>';
}

function updateAnalytics(invoices) {
    const paidInvoices = invoices.filter(i => i.status === 'PAID');
    const totalSpent = paidInvoices.reduce((sum, i) => sum + Number(i.totalPayable || 0), 0);
    const distinctMerchants = new Set(invoices.map(i => i.merchantName)).size;

    setText('stat-total-spent', `₹${totalSpent.toLocaleString()}`);
    setText('stat-total-purchases', invoices.length);
    setText('stat-avg-order', invoices.length ? `₹${(totalSpent / invoices.length).toFixed(2)}` : '₹0.00');
    setText('stat-merchants', distinctMerchants);
}

// Global actions exposed to window
window.previewInvoice = function(invoiceId) {
    if (!invoiceId || invoiceId === 'undefined') return;
    window.location.href = `../pay-invoice.html?id=${invoiceId}`;
};

window.downloadInvoice = async function(invoiceId) {
    if (!invoiceId || invoiceId === 'undefined') {
        if (window.API && window.API.showToast) window.API.showToast('Invalid Invoice ID', 'error');
        return;
    }

    try {
        if (window.API && window.API.showToast) window.API.showToast('Preparing download...', 'info');
        const blob = await window.API.merchant.downloadInvoicePdf(invoiceId);
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `invoice_${invoiceId}.pdf`;
        document.body.appendChild(a);
        a.click();
        setTimeout(() => {
            window.URL.revokeObjectURL(url);
            a.remove();
        }, 100);
        if (window.API && window.API.showToast) window.API.showToast('Download started', 'success');
    } catch (err) {
        console.error('Download error:', err);
        if (window.API && window.API.showToast) window.API.showToast(err.message || 'Failed to download invoice', 'error');
    }
};

window.payInvoice = function(invoiceNumber, token) {
    if (!invoiceNumber || invoiceNumber === 'undefined') {
        if (window.API && window.API.showToast) window.API.showToast('Invalid Invoice', 'error');
        return;
    }
    window.location.href = `../pay-invoice.html?num=${invoiceNumber}&token=${token}`;
};

window.requestRefund = async function(invoiceId) {
    if (!invoiceId || invoiceId === 'undefined') {
        if (window.API && window.API.showToast) window.API.showToast('Invalid Invoice ID', 'error');
        return;
    }
    if (!confirm('Are you sure you want to request a refund for this invoice?')) return;

    try {
        if (window.API && window.API.showToast) window.API.showToast('Initiating refund request...', 'info');
        await window.API.payment.requestRefund(invoiceId);
        if (window.API && window.API.showToast) window.API.showToast('Refund request submitted successfully', 'success');
        await loadCustomerData();
    } catch (err) {
        console.error('Refund request failed:', err);
        if (window.API && window.API.showToast) window.API.showToast(err.message || 'Failed to request refund', 'error');
    }
};

// Internal helpers
function setText(id, value) {
    const el = document.getElementById(id);
    if (el) el.innerText = value;
}

function setImage(id, src) {
    const el = document.getElementById(id);
    if (el) el.src = src;
}
