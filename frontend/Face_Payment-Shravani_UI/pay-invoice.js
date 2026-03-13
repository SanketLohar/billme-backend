document.addEventListener('DOMContentLoaded', async () => {
    const params = new URLSearchParams(window.location.search);
    const invoiceNumber = params.get('invoiceNumber') || params.get('num');
    const token = params.get('token');

    // UI Elements
    const loader = document.getElementById('loader');
    const content = document.getElementById('payment-content');
    const successArea = document.getElementById('success-area');
    const actionArea = document.getElementById('action-area');
    const cameraSection = document.getElementById('cameraSection');
    const video = document.getElementById('video');
    
    // Data Elements
    const merchantName = document.getElementById('merchant-name');
    const merchantGst = document.getElementById('merchant-gst');
    const itemsList = document.getElementById('items-list');
    const subTotalElem = document.getElementById('sub-total');
    const taxTotalElem = document.getElementById('tax-total');
    const grandTotalElem = document.getElementById('grand-total');
    const invoiceLabel = document.getElementById('invoice-label');

    let currentInvoice = null;
    let stream = null;

    if (!invoiceNumber || !token) {
        showToast('Invalid payment link. Missing invoice number or token.', 'error');
        loader.innerHTML = '<p class="text-danger">Invalid payment link. Please check your URL.</p>';
        return;
    }

    try {
        // Fetch Public Invoice Data
        const invoice = await window.API.invoice.getPublic(invoiceNumber, token);

        if (!invoice) throw new Error("Invoice not found or link expired");
        currentInvoice = invoice;

        // Populate UI
        merchantName.innerText = invoice.merchantName || 'BillMe Merchant';
        merchantGst.innerText = invoice.merchantGSTIN ? `GSTIN: ${invoice.merchantGSTIN}` : 'GST Not Applicable';
        invoiceLabel.innerText = `Invoice #${invoice.invoiceNumber}`;
        
        itemsList.innerHTML = (invoice.items || []).map(item => `
            <div class="item-row">
                <span>${esc(item.productName)} (x${item.quantity})</span>
                <span class="fw-600">₹${(item.totalPrice || 0).toFixed(2)}</span>
            </div>
        `).join('');

        subTotalElem.innerText = `₹${(invoice.subtotal || 0).toFixed(2)}`;
        taxTotalElem.innerText = `₹${(invoice.gstTotal || 0).toFixed(2)}`;
        grandTotalElem.innerText = `₹${(invoice.totalPayable || 0).toFixed(2)}`;

        loader.style.display = 'none';
        content.style.display = 'block';

        if (invoice.status === 'PAID') {
            actionArea.innerHTML = '<div class="badge badge-success w-100 py-3" style="font-size:16px; border-radius:12px;"><i class="fas fa-check-circle"></i> INVOICE ALREADY PAID</div>';
        }

    } catch (err) {
        console.error(err);
        showToast(err.message || 'Failed to load invoice details', 'error');
        loader.innerHTML = `<p class="text-danger">${err.message || 'Failed to load invoice.'}</p>`;
    }

    // --- UPI / Razorpay ---
    document.getElementById('btn-upi')?.addEventListener('click', async () => {
        const btn = document.getElementById('btn-upi');
        btn.disabled = true;
        btn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Processing...';

        try {
            // Internal ID used for order creation
            const orderId = await window.API.payment.createOrder(currentInvoice.id);

            const options = {
                key: 'rzp_test_dummy_key', // This would be dynamic in production
                amount: currentInvoice.totalPayable * 100,
                currency: "INR",
                name: "BillMe Payment",
                description: `Invoice ${currentInvoice.invoiceNumber}`,
                order_id: orderId,
                handler: async function (response) {
                    // In a real app, we verify on backend
                    try {
                        await window.API.payment.verifyRazorpay({
                            razorpay_payment_id: response.razorpay_payment_id,
                            razorpay_order_id: response.razorpay_order_id,
                            razorpay_signature: response.razorpay_signature
                        });
                        showSuccess();
                    } catch (e) {
                        showToast('Payment verification failed', 'error');
                    }
                },
                theme: { color: "#1a73e8" }
            };
            const rzp = new window.Razorpay(options);
            rzp.open();
        } catch (err) {
            showToast(err.message || 'Payment initiation failed', 'error');
        } finally {
            btn.disabled = false;
            btn.innerHTML = '<i class="fas fa-qrcode"></i> UPI / Card';
        }
    });

    // --- FacePay ---
    document.getElementById('btn-facepay')?.addEventListener('click', async () => {
        const btn = document.getElementById('btn-facepay');
        btn.disabled = true;
        btn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Loading AI...';

        try {
            // Load face-api.js models from CDN or local
            const MODEL_URL = 'https://justadudewhohacks.github.io/face-api.js/models';
            await faceapi.nets.ssdMobilenetv1.loadFromUri(MODEL_URL);
            await faceapi.nets.faceLandmark68Net.loadFromUri(MODEL_URL);
            await faceapi.nets.faceRecognitionNet.loadFromUri(MODEL_URL);

            actionArea.style.display = 'none';
            cameraSection.style.display = 'block';

            stream = await navigator.mediaDevices.getUserMedia({ video: {} });
            video.srcObject = stream;
        } catch (err) {
            showToast('FacePay initialization failed. Check camera permissions.', 'error');
            btn.disabled = false;
            btn.innerHTML = '<i class="fas fa-camera"></i> FacePay';
        }
    });

    document.getElementById('btn-verify')?.addEventListener('click', async () => {
        const btn = document.getElementById('btn-verify');
        btn.disabled = true;
        btn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Verifying Face...';

        try {
            const detection = await faceapi.detectSingleFace(video).withFaceLandmarks().withFaceDescriptor();
            if (!detection) throw new Error("No face detected. Please look into the camera.");

            const descriptor = Array.from(detection.descriptor);
            // Use API.payment namespace as standardized
            const res = await window.API.payment.payWithFace(currentInvoice.id, { embedding: descriptor });

            if (res) showSuccess();
        } catch (err) {
            showToast(err.message || 'Face comparison failed. Not recognized.', 'error');
            btn.disabled = false;
            btn.innerHTML = '<i class="fas fa-fingerprint"></i> Verify & Pay';
        }
    });

    function showSuccess() {
        content.style.display = 'none';
        successArea.style.display = 'block';
        if (stream) {
            stream.getTracks().forEach(t => t.stop());
        }
        showToast('Payment successful!', 'success');
    }

    function esc(str) {
        if (!str) return '';
        return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    }
});
