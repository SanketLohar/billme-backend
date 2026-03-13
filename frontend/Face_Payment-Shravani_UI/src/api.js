// ============================================================
// BillMe — Centralized API Client
// ============================================================

const API_BASE_URL = "http://localhost:8080";

/* ===========================================================
   TOKEN HELPERS
=========================================================== */

const getToken = () => localStorage.getItem("billme_token");
const setToken = (t) => localStorage.setItem("billme_token", t);

const getRole = () => localStorage.getItem("billme_role");
const setRole = (r) => localStorage.setItem("billme_role", r);

const clearAuth = () => {
  localStorage.removeItem("billme_token");
  localStorage.removeItem("billme_role");
  localStorage.removeItem("billme_user_id");
};

/* ===========================================================
   CORE API CALL
=========================================================== */

async function apiCall(endpoint, options = {}) {

  const url = `${API_BASE_URL}${endpoint}`;

  const headers = {
    ...(options.headers || {}),
  };

  // Attach JSON header only if body exists
  if (options.body) {
    headers["Content-Type"] = "application/json";
  }

  // Attach JWT token
  const token = getToken();
  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }

  let response;

  try {

    response = await fetch(url, {
      ...options,
      headers,
    });

  } catch (err) {

    throw new Error("Backend not reachable (port 8080)");

  }

  /* ======================
     TOKEN EXPIRED
  ====================== */

  if (response.status === 401) {

    console.warn("Session expired");

    clearAuth();

    window.location.href = "../src/login.html";

    return null;
  }

  /* ======================
     RESPONSE PARSING
  ====================== */

  const contentType = response.headers.get("content-type") || "";

  let data;

  if (contentType.includes("application/json")) {

    data = await response.json();

  } else {

    data = await response.text();

  }

  if (!response.ok) {

    throw new Error(
      data?.message || `Request failed (${response.status})`
    );

  }

  return data;
}

/* ===========================================================
   FILE DOWNLOAD
=========================================================== */

async function apiDownload(endpoint) {

  const token = getToken();

  const headers = {};

  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }

  const response = await fetch(`${API_BASE_URL}${endpoint}`, {
    headers,
  });

  if (response.status === 401) {

    clearAuth();
    window.location.href = "../src/login.html";
    return;

  }

  if (!response.ok) {

    throw new Error(`Download failed (${response.status})`);

  }

  return response.blob();
}

/* ===========================================================
   API ENDPOINTS
=========================================================== */

const API = {

  /* ======================
     AUTH
  ====================== */

  auth: {

    login: (data) =>
      apiCall("/auth/login", {
        method: "POST",
        body: JSON.stringify(data),
      }),

    registerMerchant: (data) =>
      apiCall("/auth/register/merchant", {
        method: "POST",
        body: JSON.stringify(data),
      }),

    registerCustomer: (data) =>
      apiCall("/auth/register/customer", {
        method: "POST",
        body: JSON.stringify(data),
      }),

    logout: () => {

      clearAuth();

      window.location.href = "../src/login.html";

    }

  },

  /* ======================
     MERCHANT
  ====================== */

  merchant: {

    getProfile: () => apiCall("/api/merchant/profile"),

    updateProfile: (data) => 
      apiCall("/api/merchant/profile", {
        method: "PUT",
        body: JSON.stringify(data)
      }),

    getProducts: () => apiCall("/api/merchant/products"),

    getInvoices: () => apiCall("/merchant/invoices"),

    createInvoice: (data) =>
      apiCall("/merchant/invoices", {
        method: "POST",
        body: JSON.stringify(data),
      }),

    downloadInvoicePdf: (id) =>
      apiDownload(`/invoice/${id}/pdf`),

    getPaymentMethods: () => apiCall("/api/merchant/reports/payment-methods"),

  },

  /* ======================
     CUSTOMER
  ====================== */

  customer: {

    getProfile: () => apiCall("/api/customer/profile"),

    getInvoices: () => apiCall("/api/customer/invoices"),

  },

  /* ======================
     INVOICE
  ====================== */

  invoice: {

    getPreview: (id) =>
      apiCall(`/api/invoices/${id}/preview`),

    getPublic: (invoiceNumber, token) => 
      apiCall(`/public/invoices/${invoiceNumber}?token=${token}`),

  },

  /* ======================
     PAYMENT
  ====================== */

  payment: {

    createOrder: (invoiceId) => 
      apiCall(`/api/payments/create-order/${invoiceId}`, {
        method: "POST"
      }),

    payWithFace: (invoiceId, data) =>
      apiCall(`/customer/invoices/${invoiceId}/pay/face`, {
        method: "POST",
        body: JSON.stringify(data)
      }),

    verifyRazorpay: (data) => 
      apiCall("/api/payments/verify", {
        method: "POST",
        body: JSON.stringify(data)
      }),

    refund: (invoiceId) =>
      apiCall(`/api/payments/refund/${invoiceId}`, {
        method: "POST"
      })

  },

  /* ======================
     WALLET / FINANCIALS
  ====================== */

  wallet: {

    getWallet: () => apiCall("/api/merchant/wallet"),

    getBalanceSheet: () => apiCall("/api/merchant/reports/balance-sheet"),

    getTransactions: () => apiCall("/api/merchant/reports/transactions"),

    exportTransactions: () => apiDownload("/api/merchant/reports/export")

  },

  /* ======================
     CHATBOT
  ====================== */

  chatbot: {

    ask: (data) => 
      apiCall("/api/chatbot/ask", {
        method: "POST",
        body: JSON.stringify(data)
      })

  },

  /* ======================
     ADMIN
  ====================== */

  admin: {

    getDashboard: () =>
      apiCall("/api/admin/dashboard"),

    getMerchants: () => apiCall("/api/admin/merchants"),

    getCustomers: () => apiCall("/api/admin/customers"),

    getTransactions: () => apiCall("/api/admin/transactions")

  }

};

/* ===========================================================
   AUTH HELPERS
=========================================================== */

function saveAuthResponse(response) {

  if (!response) return;

  const token = response.accessToken || response.token;

  if (token) setToken(token);

  if (response.role) {
    setRole(response.role.toLowerCase());
  }

}

/* ===========================================================
   SESSION CHECK
=========================================================== */

function isLoggedIn() {

  return !!getToken();

}

function requireAuth() {

  if (!isLoggedIn()) {

    window.location.href = "../src/login.html";

  }

}

/* ===========================================================
   ROLE REDIRECT
=========================================================== */

function redirectByRole() {

  const role = (getRole() || "").toLowerCase();

  if (role === "admin") {
    window.location.href = "../dashboard/Admin.html";
  }

  if (role === "merchant") {
    window.location.href = "../dashboard/merchant.html";
  }

  if (role === "customer") {
    window.location.href = "../dashboard/customer.html";
  }

}

/* ===========================================================
   TOAST
=========================================================== */

function showToast(message, type = "info") {

  console.log(`[${type}]`, message);

  // In a real production app, use a nice UI toast.
  // For now, keep it simple but functional.
  const toast = document.createElement("div");
  toast.className = `toast toast-${type}`;
  toast.innerText = message;
  toast.style.cssText = "position:fixed; bottom:20px; right:20px; padding:12px 24px; border-radius:8px; color:#fff; z-index:9999; font-weight:600; box-shadow:0 4px 12px rgba(0,0,0,0.15);";
  
  if (type === "success") toast.style.backgroundColor = "#34a853";
  else if (type === "error") toast.style.backgroundColor = "#ea4335";
  else toast.style.backgroundColor = "#4285f4";

  document.body.appendChild(toast);
  
  setTimeout(() => {
    toast.style.opacity = "0";
    toast.style.transition = "opacity 0.5s";
    setTimeout(() => toast.remove(), 500);
  }, 3000);

}

/* ===========================================================
   GLOBAL EXPORT
=========================================================== */

window.API = API;

window.saveAuthResponse = saveAuthResponse;
window.redirectByRole = redirectByRole;

window.getToken = getToken;
window.getRole = getRole;

window.requireAuth = requireAuth;
window.clearAuth = clearAuth;

window.showToast = showToast;