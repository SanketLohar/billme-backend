// ============================================================
//  BillMe — Merchant Dashboard Logic  (merchant.js)
// ============================================================
'use strict';
const token = localStorage.getItem("billme_token");
const role = (localStorage.getItem("billme_role") || "").toLowerCase();

if (!token || role !== "merchant") {
    console.warn("Unauthorized or invalid role:", role);
    window.location.href = "../src/login.html";
}
// ── State ────────────────────────────────────────────────────
let merchantProfile = null;
let productList = [];
let invoiceList = [];
let txPage = 0;
let txTotalPages = 1;
let currentInvId = null;  // for modal

// ── Chart instances ──────────────────────────────────────────
let chartStatus = null, chartPayment = null, chartActivity = null;

// ── Init ─────────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', async () => {
    requireAuth('../src/login.html');
    
    // Dynamic year
    const yearEl = document.getElementById('currentYear');
    if (yearEl) yearEl.textContent = new Date().getFullYear();

    await loadDashboard();
    bindNav();
    bindSidebarToggle();
    bindProfileForm();
    bindProductForm();
    bindInvoiceForm();
    bindTransactions();
    bindMisc();
});

async function loadDashboard() {
    try {
        const [profile, products, invoices, balanceSheet, wallet, paymentMethods] = await Promise.all([
            API.merchant.getProfile().catch(() => null),
            API.merchant.getProducts().catch(() => []),
            API.merchant.getInvoices().catch(() => []),
            API.wallet.getBalanceSheet().catch(() => null),
            API.wallet.getWallet().catch(() => ({ balance: 0, escrowBalance: 0 })),
            API.merchant.getPaymentMethods().catch(() => ({ upi: 0, facepay: 0, card: 0 }))
        ]);

        merchantProfile = profile;
        productList = products || [];
        invoiceList = invoices || [];

        renderSidebar(profile);
        renderStatCards(productList, invoiceList, balanceSheet, wallet);
        renderProfileBanner(profile);
        populateProfile(profile);
        renderCharts(invoiceList, paymentMethods);
        renderProducts();
        renderInvoices();

    } catch (e) {
        showToast(`Failed to load dashboard: ${e.message}`, 'error');
    } finally {
        document.getElementById('pageLoader').style.display = 'none';
        document.getElementById('dashLayout').style.display = 'flex';
    }
}

// ── Sidebar data ─────────────────────────────────────────────
function renderSidebar(profile) {
    if (!profile) return;
    document.getElementById('sb-name').textContent = profile.businessName || 'Merchant';
    document.getElementById('sb-email').textContent = profile.ownerName || '';
    document.getElementById('sb-upi').textContent = profile.upiId || 'Not set';
    document.getElementById('topbarAvatar').textContent =
        (profile.businessName || 'M')[0].toUpperCase();

    const badge = document.getElementById('sb-badge');
    if (profile.profileCompleted) {
        badge.textContent = 'Profile Complete';
        badge.className = 'profile-badge complete';
        badge.setAttribute('data-i18n', 'dash_status_complete');
    } else {
        badge.textContent = 'Profile Incomplete';
        badge.className = 'profile-badge incomplete';
        badge.setAttribute('data-i18n', 'dash_status_incomplete');
    }
    // Re-apply translations for dynamic badge
    if (window.applyTranslations) {
        const lang = localStorage.getItem('billme_lang') || 'en';
        window.applyTranslations(lang);
    }
}

// ── Stat cards (real data from balance sheet & wallet) ─────────
function renderStatCards(products, invoices, balanceSheet, wallet) {
    const revenue = balanceSheet ? balanceSheet.totalRevenue : 0;
    const balance = wallet ? wallet.balance : 0;
    const escrow = wallet ? wallet.escrowBalance : 0;

    document.getElementById('stat-revenue').textContent = `₹${(revenue || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 })}`;
    document.getElementById('stat-balance').textContent = `₹${(balance || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 })}`;
    document.getElementById('stat-escrow').textContent = `₹${(escrow || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 })}`;
    document.getElementById('stat-products').textContent = products.length;
    document.getElementById('stat-invoices').textContent = invoices.length;

    // Remove "Awaiting" messages if there is activity
    if (balance > 0 || escrow > 0 || revenue > 0) {
        document.querySelectorAll('.financial-pending').forEach(el => el.style.display = 'none');
    } else {
        document.querySelectorAll('.financial-pending').forEach(el => el.style.display = 'block');
    }
}

// ── Charts (use API payment methods and invoice counts) ─
function renderCharts(invoices, paymentMethods) {
    // Invoice status distribution
    const statusCounts = { PAID: 0, PENDING: 0, UNPAID: 0, CANCELLED: 0 };
    invoices.forEach(inv => {
        const s = inv.status?.toUpperCase() || 'UNPAID';
        if (s in statusCounts) statusCounts[s]++;
        else statusCounts.UNPAID++;
    });

    // Payment method distribution from API
    const methodCounts = {
        UPI: paymentMethods.upi || 0,
        FacePay: paymentMethods.facepay || 0,
        Card: paymentMethods.card || 0
    };

    // Monthly activity
    const monthLabels = [];
    const monthData = {};
    const now = new Date();
    for (let i = 11; i >= 0; i--) {
        const d = new Date(now.getFullYear(), now.getMonth() - i, 1);
        const key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
        monthLabels.push(d.toLocaleString('default', { month: 'short', year: '2-digit' }));
        monthData[key] = 0;
    }
    invoices.forEach(inv => {
        if (!inv.issuedAt) return;
        const d = new Date(inv.issuedAt);
        const key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
        if (key in monthData) monthData[key]++;
    });

    if (chartStatus) chartStatus.destroy();
    chartStatus = new Chart(document.getElementById('chartInvoiceStatus'), {
        type: 'doughnut',
        data: {
            labels: Object.keys(statusCounts),
            datasets: [{
                data: Object.values(statusCounts),
                backgroundColor: ['#34a853', '#fbbc04', '#ea4335', '#9aa0a6'],
                borderWidth: 2, borderColor: '#fff'
            }]
        },
        options: {
            responsive: true, maintainAspectRatio: false,
            plugins: { legend: { position: 'bottom' } }
        }
    });

    if (chartPayment) chartPayment.destroy();
    chartPayment = new Chart(document.getElementById('chartPaymentMethods'), {
        type: 'pie',
        data: {
            labels: Object.keys(methodCounts),
            datasets: [{
                data: Object.values(methodCounts),
                backgroundColor: ['#1a73e8', '#34a853', '#fbbc04'],
                borderWidth: 2, borderColor: '#fff'
            }]
        },
        options: {
            responsive: true, maintainAspectRatio: false,
            plugins: { legend: { position: 'bottom' } }
        }
    });

    if (chartActivity) chartActivity.destroy();
    chartActivity = new Chart(document.getElementById('chartActivity'), {
        type: 'bar',
        data: {
            labels: monthLabels,
            datasets: [{
                label: 'Invoices Issued',
                data: Object.values(monthData),
                backgroundColor: 'rgba(26,115,232,0.7)',
                borderRadius: 4
            }]
        },
        options: {
            responsive: true, maintainAspectRatio: false,
            plugins: { legend: { display: false } },
            scales: { y: { beginAtZero: true, ticks: { stepSize: 1 } } }
        }
    });
}

// ── Profile form ─────────────────────────────────────────────
function populateProfile(profile) {
    if (!profile) return;
    const f = ['email', 'businessName', 'ownerName', 'phone', 'address', 'city', 'state', 'pinCode',
        'upiId', 'bankName', 'accountHolderName', 'accountNumber', 'ifscCode'];
    f.forEach(k => {
        const el = document.getElementById(`p-${k}`);
        if (el) el.value = profile[k] || '';
    });
    const gstEl = document.getElementById('p-gstRegistered');
    if (gstEl) {
        gstEl.checked = !!profile.gstRegistered;
        toggleGstinField(!!profile.gstRegistered);
    }
    const gstinEl = document.getElementById('p-gstin');
    if (gstinEl) gstinEl.value = profile.gstin || '';

    updateProfileSectionStatuses(profile);
}

function updateProfileSectionStatuses(profile) {
    const contactOk = profile && profile.phone && profile.address && profile.city && profile.state && profile.pinCode;
    const bankOk = profile && profile.bankName && profile.accountNumber && profile.ifscCode;

    const lang = localStorage.getItem('billme_lang') || 'en';
    const dict = typeof translations !== 'undefined' ? (translations[lang] || translations.en) : null;

    const cSt = document.getElementById('psc-contact-status');
    const bSt = document.getElementById('psc-bank-status');
    
    if (cSt) { 
        cSt.textContent = dict ? (contactOk ? dict.dash_status_complete : dict.dash_status_incomplete) : (contactOk ? 'Complete' : 'Incomplete'); 
        cSt.className = `psc-status${contactOk ? ' ok' : ''}`; 
    }
    if (bSt) { 
        bSt.textContent = dict ? (bankOk ? dict.dash_status_complete : dict.dash_status_incomplete) : (bankOk ? 'Complete' : 'Incomplete'); 
        bSt.className = `psc-status${bankOk ? ' ok' : ''}`; 
    }
}

function toggleGstinField(on) {
    const grp = document.getElementById('gstin-group');
    const msg = document.getElementById('gst-info-msg');
    if (grp) grp.style.display = on ? 'block' : 'none';
    if (msg) {
        msg.style.display = on ? 'none' : 'flex';
    }
}

function bindProfileForm() {
    document.getElementById('p-gstRegistered')?.addEventListener('change', e => {
        toggleGstinField(e.target.checked);
    });

    document.getElementById('saveProfileBtn')?.addEventListener('click', async () => {
        const btn = document.getElementById('saveProfileBtn');
        btn.disabled = true; btn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Saving...';
        try {
            const payload = {
                phone: document.getElementById('p-phone').value.trim(),
                address: document.getElementById('p-address').value.trim(),
                city: document.getElementById('p-city').value.trim(),
                state: document.getElementById('p-state').value.trim(),
                pinCode: document.getElementById('p-pinCode').value.trim(),
                upiId: document.getElementById('p-upiId').value.trim(),
                bankName: document.getElementById('p-bankName').value.trim(),
                accountHolderName: document.getElementById('p-accountHolderName').value.trim(),
                accountNumber: document.getElementById('p-accountNumber').value.trim(),
                ifscCode: document.getElementById('p-ifscCode').value.trim(),
                gstRegistered: document.getElementById('p-gstRegistered').checked,
                gstin: document.getElementById('p-gstin').value.trim() || null,
            };
            const updated = await API.merchant.updateProfile(payload);
            merchantProfile = updated;
            renderSidebar(updated);
            renderProfileBanner(updated);
            updateProfileSectionStatuses(updated);
            showToast('Profile updated successfully!', 'success');
        } catch (e) {
            showToast(e.message || 'Failed to save profile', 'error');
        } finally {
            btn.disabled = false; btn.innerHTML = '<i class="fas fa-save"></i> Save Changes';
        }
    });
}

// ── Products ─────────────────────────────────────────────────
function renderProducts() {
    const tbody = document.getElementById('productsBody');
    if (!productList.length) {
        tbody.innerHTML = '<tr><td colspan="5" class="text-center text-muted" style="padding:32px;"><i class="fas fa-box" style="font-size:32px;display:block;margin-bottom:8px;opacity:0.3;"></i>No products yet. Click "Add Product" to get started.</td></tr>';
        return;
    }
    tbody.innerHTML = productList.map(p => `
        <tr>
            <td><strong>${esc(p.name)}</strong></td>
            <td>₹${(p.price || 0).toFixed(2)}</td>
            <td><span class="badge badge-info">${p.gstRate || 0}%</span></td>
            <td>${p.barcode ? esc(p.barcode) : '<span class="text-muted">—</span>'}</td>
            <td>
                <button class="action-btn red" onclick="deleteProduct(${p.id})" title="Delete">
                    <i class="fas fa-trash"></i>
                </button>
            </td>
        </tr>
    `).join('');
}

function bindProductForm() {
    document.getElementById('addProductBtn')?.addEventListener('click', () => {
        const form = document.getElementById('addProductForm');
        form.style.display = form.style.display === 'none' ? 'block' : 'none';
        applyGstRuleToProductForm();
    });

    document.getElementById('cancelProdBtn')?.addEventListener('click', () => {
        document.getElementById('addProductForm').style.display = 'none';
    });

    document.getElementById('saveProdBtn')?.addEventListener('click', saveProduct);
}

function applyGstRuleToProductForm() {
    const gstSelect = document.getElementById('prod-gstRate');
    const hint = document.getElementById('prod-gst-hint');
    const warning = document.getElementById('prod-gst-warning');
    const isGst = merchantProfile?.gstRegistered === true;

    if (isGst) {
        gstSelect.disabled = false;
        hint.textContent = 'Select the GST slab applicable to this product per government regulations.';
        warning.style.display = 'none';
    } else {
        gstSelect.disabled = true;
        gstSelect.value = '0';
        hint.textContent = 'GST is not applicable because your business is not GST registered.';
        warning.style.display = 'none';
    }

    // If someone somehow manually puts gstRate > 0 while not registered, warn
    gstSelect.addEventListener('change', () => {
        if (!isGst && parseInt(gstSelect.value) > 0) {
            warning.style.display = 'flex';
        } else {
            warning.style.display = 'none';
        }
    });
}

async function saveProduct() {
    const name = document.getElementById('prod-name').value.trim();
    const price = parseFloat(document.getElementById('prod-price').value);
    const barcode = document.getElementById('prod-barcode').value.trim();
    const gstRate = merchantProfile?.gstRegistered
        ? parseInt(document.getElementById('prod-gstRate').value)
        : 0;

    if (!name) { showToast('Product name is required', 'warning'); return; }
    if (isNaN(price) || price < 0) { showToast('Enter a valid price', 'warning'); return; }

    const btn = document.getElementById('saveProdBtn');
    btn.disabled = true; btn.innerHTML = '<i class="fas fa-spinner fa-spin"></i>';
    try {
        const payload = { name, price, barcode: barcode || null, gstRate };
        const newProd = await API.merchant.createProduct(payload);
        productList.push(newProd);
        renderProducts();
        document.getElementById('stat-products').textContent = productList.length;
        document.getElementById('addProductForm').style.display = 'none';
        ['prod-name', 'prod-price', 'prod-barcode'].forEach(id => document.getElementById(id).value = '');
        document.getElementById('prod-gstRate').value = '0';
        showToast('Product created!', 'success');
    } catch (e) {
        showToast(e.message || 'Failed to create product', 'error');
    } finally {
        btn.disabled = false; btn.innerHTML = '<i class="fas fa-save"></i> Save Product';
    }
}

async function deleteProduct(id) {
    if (!confirm('Delete this product?')) return;
    try {
        await API.merchant.deleteProduct(id);
        productList = productList.filter(p => p.id !== id);
        renderProducts();
        document.getElementById('stat-products').textContent = productList.length;
        showToast('Product deleted', 'success');
    } catch (e) {
        showToast(e.message || 'Failed to delete product', 'error');
    }
}

// ── Invoices ─────────────────────────────────────────────────
function renderInvoices() {
    const tbody = document.getElementById('invoicesBody');
    if (!invoiceList.length) {
        tbody.innerHTML = '<tr><td colspan="6" class="text-center text-muted" style="padding:32px;"><i class="fas fa-file-invoice" style="font-size:32px;display:block;margin-bottom:8px;opacity:0.3;"></i>No invoices yet.</td></tr>';
        return;
    }
    tbody.innerHTML = invoiceList.map(inv => `
        <tr>
            <td><strong>${esc(inv.invoiceNumber || '#' + inv.invoiceId)}</strong></td>
            <td>₹${(inv.amount || 0).toFixed(2)}</td>
            <td><span class="invoice-status inv-${inv.status}">${inv.status || '—'}</span></td>
            <td>${inv.paymentMethod || '<span class="text-muted">—</span>'}</td>
            <td>${inv.issuedAt ? new Date(inv.issuedAt).toLocaleDateString('en-IN') : '—'}</td>
            <td>
                <button class="action-btn blue" onclick="previewInvoice(${inv.invoiceId})" title="Preview"><i class="fas fa-eye"></i></button>
                <button class="action-btn green" onclick="downloadInvoicePdf(${inv.invoiceId},'${esc(inv.invoiceNumber || inv.invoiceId)}')" title="Download PDF"><i class="fas fa-download"></i></button>
                ${inv.status !== 'PAID' ? `<button class="action-btn" onclick="payInvoice(${inv.invoiceId})" title="Pay"><i class="fas fa-credit-card" style="color:#1a73e8;"></i></button>` : ''}
            </td>
        </tr>
    `).join('');
}

function previewInvoice(id) {
    const inv = invoiceList.find(i => i.invoiceId === id);
    if (!inv) return;
    currentInvId = id;

    document.getElementById('modalInvTitle').textContent = `Invoice ${inv.invoiceNumber || '#' + id}`;

    const items = (inv.items || []).map(item => `
        <tr>
            <td>${esc(item.productName || 'Item')}</td>
            <td class="text-right">${item.quantity || 1}</td>
            <td class="text-right">₹${(item.unitPrice || 0).toFixed(2)}</td>
            <td class="text-right">${item.gstRate || 0}%</td>
            <td class="text-right fw-600">₹${(item.lineTotal || item.unitPrice || 0).toFixed(2)}</td>
        </tr>
    `).join('');

    document.getElementById('modalInvBody').innerHTML = `
        <div class="inv-detail-row"><span class="inv-detail-row__label">Invoice #</span><span class="inv-detail-row__val">${inv.invoiceNumber || id}</span></div>
        <div class="inv-detail-row"><span class="inv-detail-row__label">Status</span><span class="invoice-status inv-${inv.status}">${inv.status}</span></div>
        <div class="inv-detail-row"><span class="inv-detail-row__label">Issued At</span><span class="inv-detail-row__val">${inv.issuedAt ? new Date(inv.issuedAt).toLocaleString('en-IN') : '—'}</span></div>
        ${inv.paidAt ? `<div class="inv-detail-row"><span class="inv-detail-row__label">Paid At</span><span class="inv-detail-row__val">${new Date(inv.paidAt).toLocaleString('en-IN')}</span></div>` : ''}
        ${items.length ? `
        <div style="margin:16px 0 8px;font-weight:600;font-size:14px;">Items</div>
        <div class="data-table-wrapper"><table class="data-table">
            <thead><tr><th>Product</th><th>Qty</th><th>Unit Price</th><th>GST</th><th>Total</th></tr></thead>
            <tbody>${items}</tbody>
        </table></div>` : ''}
        <div class="mt-2">
            <div class="inv-detail-row"><span class="inv-detail-row__label">Subtotal</span><span class="inv-detail-row__val">₹${(inv.subtotal || 0).toFixed(2)}</span></div>
            ${inv.cgstAmount ? `<div class="inv-detail-row"><span class="inv-detail-row__label">CGST</span><span class="inv-detail-row__val">₹${(inv.cgstAmount || 0).toFixed(2)}</span></div>` : ''}
            ${inv.sgstAmount ? `<div class="inv-detail-row"><span class="inv-detail-row__label">SGST</span><span class="inv-detail-row__val">₹${(inv.sgstAmount || 0).toFixed(2)}</span></div>` : ''}
            ${inv.igstAmount ? `<div class="inv-detail-row"><span class="inv-detail-row__label">IGST</span><span class="inv-detail-row__val">₹${(inv.igstAmount || 0).toFixed(2)}</span></div>` : ''}
            ${inv.processingFee ? `<div class="inv-detail-row"><span class="inv-detail-row__label">Processing Fee</span><span class="inv-detail-row__val">₹${(inv.processingFee || 0).toFixed(2)}</span></div>` : ''}
        </div>
        <div class="inv-total-row">
            <span>Total Payable</span>
            <span>₹${(inv.totalPayable || inv.amount || 0).toFixed(2)}</span>
        </div>
    `;

    const payBtn = document.getElementById('modalPayBtn');
    payBtn.style.display = inv.status === 'PAID' ? 'none' : 'inline-flex';

    openModal('invoiceModal');
}

async function downloadInvoicePdf(id, invNum) {
    try {
        showToast('Downloading PDF...', 'info');
        const blob = await API.merchant.downloadInvoicePdf(id);
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url; a.download = `invoice-${invNum}.pdf`;
        document.body.appendChild(a); a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
        showToast('PDF downloaded!', 'success');
    } catch (e) {
        showToast(e.message || 'PDF download failed', 'error');
    }
}

function payInvoice(id) {
    closeModal('invoiceModal');
    window.location.href = `../pay-invoice.html?invoiceId=${id}`;
}

function bindInvoiceForm() {
    document.getElementById('createInvoiceBtn')?.addEventListener('click', () => {
        document.getElementById('invoiceListCard').style.display = 'none';
        document.getElementById('createInvoiceForm').style.display = 'block';
        renderInvoiceItemRow(); // add first row
    });

    document.getElementById('backToListBtn')?.addEventListener('click', () => {
        document.getElementById('invoiceListCard').style.display = 'block';
        document.getElementById('createInvoiceForm').style.display = 'none';
    });
    document.getElementById('cancelInvoiceBtn')?.addEventListener('click', () => {
        document.getElementById('invoiceListCard').style.display = 'block';
        document.getElementById('createInvoiceForm').style.display = 'none';
    });

    document.getElementById('addInvItemBtn')?.addEventListener('click', renderInvoiceItemRow);
    document.getElementById('submitInvoiceBtn')?.addEventListener('click', submitInvoice);

    document.getElementById('modalDownloadBtn')?.addEventListener('click', () => {
        if (currentInvId) {
            const inv = invoiceList.find(i => i.invoiceId === currentInvId);
            downloadInvoicePdf(currentInvId, inv?.invoiceNumber || currentInvId);
        }
    });
    document.getElementById('modalPayBtn')?.addEventListener('click', () => {
        if (currentInvId) payInvoice(currentInvId);
    });
}

function renderInvoiceItemRow() {
    const container = document.getElementById('inv-items-container');
    const row = document.createElement('div');
    row.className = 'grid-2 mb-2';
    row.style.cssText = 'grid-template-columns:2fr 1fr 1fr auto;gap:8px;align-items:end;';
    row.innerHTML = `
        <div class="form-group mb-0">
            <label class="form-label">Product</label>
            <select class="form-input inv-prod-select">
                <option value="">— Select —</option>
                ${productList.map(p => `<option value="${p.id}" data-price="${p.price}" data-gst="${p.gstRate || 0}">${esc(p.name)} (₹${p.price})</option>`).join('')}
            </select>
        </div>
        <div class="form-group mb-0">
            <label class="form-label">Qty</label>
            <input type="number" class="form-input inv-qty" value="1" min="1">
        </div>
        <div class="form-group mb-0">
            <label class="form-label">Unit Price</label>
            <input type="number" class="form-input inv-unit-price" step="0.01">
        </div>
        <button type="button" class="btn btn-danger btn-sm" style="margin-bottom:20px;" onclick="this.closest('.grid-2').remove()"><i class="fas fa-times"></i></button>
    `;
    container.appendChild(row);
}

async function submitInvoice() {
    const custEmail = document.getElementById('inv-custEmail').value.trim();
    const custName = document.getElementById('inv-custName').value.trim();

    const itemRows = document.querySelectorAll('#inv-items-container .grid-2');
    const items = [];
    itemRows.forEach(row => {
        const sel = row.querySelector('.inv-prod-select');
        const qty = parseInt(row.querySelector('.inv-qty').value) || 1;
        const price = parseFloat(row.querySelector('.inv-unit-price').value);
        if (sel.value) {
            items.push({ productId: parseInt(sel.value), quantity: qty, unitPrice: price || parseFloat(sel.selectedOptions[0]?.dataset.price || 0) });
        }
    });

    if (!custEmail) { showToast('Customer email is required', 'warning'); return; }
    if (!items.length) { showToast('Add at least one item', 'warning'); return; }

    const btn = document.getElementById('submitInvoiceBtn');
    btn.disabled = true; btn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Creating...';

    try {
        const payload = { customerEmail: custEmail, customerName: custName || null, items };
        await API.merchant.createInvoice(payload);
        invoiceList = await API.merchant.getInvoices().catch(() => invoiceList);
        renderInvoices();
        renderCharts(invoiceList);
        document.getElementById('stat-invoices').textContent = invoiceList.length;
        document.getElementById('invoiceListCard').style.display = 'block';
        document.getElementById('createInvoiceForm').style.display = 'none';
        showToast('Invoice created!', 'success');
    } catch (e) {
        showToast(e.message || 'Failed to create invoice', 'error');
    } finally {
        btn.disabled = false; btn.innerHTML = '<i class="fas fa-paper-plane"></i> Create Invoice';
    }
}

// ── Transactions ─────────────────────────────────────────────
function bindTransactions() {
    document.getElementById('applyTxFilter')?.addEventListener('click', () => {
        txPage = 0; loadTransactions();
    });
    document.getElementById('txPrev')?.addEventListener('click', () => {
        if (txPage > 0) { txPage--; loadTransactions(); }
    });
    document.getElementById('txNext')?.addEventListener('click', () => {
        if (txPage < txTotalPages - 1) { txPage++; loadTransactions(); }
    });
}

async function loadTransactions() {
    const tbody = document.getElementById('txBody');
    tbody.innerHTML = '<tr><td colspan="7" class="text-center"><div class="spinner spinner-sm" style="margin:20px auto;"></div></td></tr>';
    try {
        const params = {
            page: txPage, size: 10,
            type: document.getElementById('txFilter-type').value || undefined,
            status: document.getElementById('txFilter-status').value || undefined,
        };
        const data = await API.wallet.getTransactions(params);

        if (!data || !data.content || !data.content.length) {
            tbody.innerHTML = '<tr><td colspan="7" class="text-center text-muted" style="padding:32px;">No transactions found.</td></tr>';
            return;
        }

        txTotalPages = data.totalPages || 1;
        document.getElementById('txPageInfo').textContent =
            `Page ${txPage + 1} of ${txTotalPages} (${data.totalElements || 0} total)`;
        document.getElementById('txPrev').disabled = txPage === 0;
        document.getElementById('txNext').disabled = txPage >= txTotalPages - 1;

        tbody.innerHTML = data.content.map(tx => {
            const isCredit = tx.direction === 'CREDIT';
            return `
            <tr>
                <td><code style="font-size:12px;">#${tx.transactionId}</code></td>
                <td>
                    <span class="badge ${isCredit ? 'badge-success' : 'badge-danger'}">
                        <i class="fas fa-arrow-${isCredit ? 'down' : 'up'}"></i>
                        ${tx.direction}
                    </span>
                </td>
                <td><strong style="color:${isCredit ? 'var(--secondary)' : 'var(--danger)'};">
                    ${isCredit ? '+' : '-'}₹${(tx.amount || 0).toFixed(2)}
                </strong></td>
                <td>${tx.type || '—'}</td>
                <td><span class="badge ${tx.status === 'SUCCESS' ? 'badge-success' : tx.status === 'PENDING' ? 'badge-warning' : 'badge-danger'}">${tx.status || '—'}</span></td>
                <td>${esc(tx.counterparty || '—')}</td>
                <td>${tx.timestamp ? new Date(tx.timestamp).toLocaleString('en-IN') : '—'}</td>
            </tr>`;
        }).join('');
    } catch (e) {
        tbody.innerHTML = `<tr><td colspan="7" class="text-center text-muted" style="padding:32px;">Failed to load transactions: ${e.message}</td></tr>`;
    }
}

// ── Section navigation ────────────────────────────────────────
function bindNav() {
    document.querySelectorAll('.nav-item').forEach(item => {
        item.addEventListener('click', function () {
            const section = this.dataset.section;
            if (!section) return;
            document.querySelectorAll('.nav-item').forEach(n => n.classList.remove('active'));
            this.classList.add('active');
            document.querySelectorAll('.section-page').forEach(p => p.classList.remove('active'));
            const sec = document.getElementById(`sec-${section}`);
            if (sec) sec.classList.add('active');
            document.getElementById('pageTitle').textContent =
                this.querySelector('a').textContent.trim();

            // Load transactions on first visit
            if (section === 'transactions') loadTransactions();

            // Mobile: close sidebar
            if (window.innerWidth <= 768) {
                document.getElementById('sidebar').classList.remove('open');
            }
        });
    });

    // Profile banner CTA
    document.getElementById('bannerGoProfile')?.addEventListener('click', () => {
        document.querySelector('[data-section="profile"]')?.click();
    });
}

function bindSidebarToggle() {
    document.getElementById('menuToggle')?.addEventListener('click', () => {
        document.getElementById('sidebar').classList.toggle('open');
    });
    // Close sidebar on outside click (mobile)
    document.getElementById('mainContent')?.addEventListener('click', () => {
        if (window.innerWidth <= 768) {
            document.getElementById('sidebar').classList.remove('open');
        }
    });
}

function bindMisc() {
    document.getElementById('logoutBtn')?.addEventListener('click', async () => {
        const rt = localStorage.getItem('billme_refresh');
        if (rt) API.auth.logout(rt).catch(() => { });
        clearAuth();
        window.location.href = '../index.html';
    });
    document.getElementById('refreshBtn')?.addEventListener('click', () => {
        loadDashboard();
    });
}

// ── Collapse accordion ────────────────────────────────────────
window.toggleSection = function (header) {
    const body = header.nextElementSibling;
    body.classList.toggle('open');
};

// ── Modal helpers ─────────────────────────────────────────────
window.openModal = function (id) {
    const m = document.getElementById(id);
    m.style.display = 'flex';
    setTimeout(() => m.classList.add('active'), 10);
};
window.closeModal = function (id) {
    const m = document.getElementById(id);
    m.classList.remove('active');
    setTimeout(() => m.style.display = 'none', 250);
};
document.querySelectorAll('.modal-backdrop').forEach(m => {
    m.addEventListener('click', e => { if (e.target === m) closeModal(m.id); });
});

// ── Safe HTML escape ──────────────────────────────────────────
function esc(str) {
    if (str == null) return '';
    return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

// Expose globals used in inline HTML
window.previewInvoice = previewInvoice;
window.downloadInvoicePdf = downloadInvoicePdf;
window.payInvoice = payInvoice;
window.deleteProduct = deleteProduct;
