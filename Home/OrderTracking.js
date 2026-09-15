// OrderTracking.js — rebuilt for reliability

(function () {
    'use strict';

    /* ── Auth & nav ── */
    try {
        window.tapAuth.initAuth({
            loginSelector: '.login',
            navProfileSelector: '.nav-profile',
            profileNameSelector: '.profile-name',
            profilePhotoSelector: '.profile-photo',
            logoutButtonSelector: '.logout-btn'
        });
    } catch (e) { /* auth optional */ }

    const navToggle = document.getElementById('navToggle');
    const navLinksEl = document.getElementById('navLinks');
    if (navToggle && navLinksEl) {
        navToggle.addEventListener('click', () => {
            const open = navLinksEl.classList.toggle('open');
            navToggle.setAttribute('aria-expanded', String(open));
        });
    }

    const cartToggle = document.querySelector('.cart-toggle');
    if (cartToggle) cartToggle.addEventListener('click', () => { window.location.href = 'Cart.html'; });

    const cartCountEl = document.querySelector('.cart-count');
    try {
        const cartItems = JSON.parse(localStorage.getItem('tapCart') || '{}');
        if (cartCountEl) cartCountEl.textContent = Object.values(cartItems).reduce((s, i) => s + (i.qty || 0), 0);
    } catch (e) { if (cartCountEl) cartCountEl.textContent = '0'; }

    /* ── Toast ── */
    function showToast(type, title, msg, ms) {
        ms = ms || 4000;
        const colors = { success: '#10b981', error: '#ef4444', info: '#6366f1', warning: '#f59e0b' };
        const icons  = { success: '✅', error: '❌', info: 'ℹ️', warning: '⚠️' };
        let tc = document.getElementById('ot-toast-container');
        if (!tc) {
            tc = document.createElement('div');
            tc.id = 'ot-toast-container';
            tc.style.cssText = 'position:fixed;top:22px;right:22px;z-index:99999;display:grid;gap:10px;pointer-events:none;';
            document.body.appendChild(tc);
        }
        if (!document.getElementById('ot-kf')) {
            const s = document.createElement('style');
            s.id = 'ot-kf';
            s.textContent = '@keyframes otIn{from{opacity:0;transform:translateX(50px)}to{opacity:1;transform:none}}';
            document.head.appendChild(s);
        }
        const t = document.createElement('div');
        t.style.cssText = `display:flex;align-items:center;gap:12px;min-width:260px;max-width:360px;
            padding:14px 18px;background:#fff;border-radius:16px;pointer-events:all;
            box-shadow:0 10px 36px rgba(0,0,0,0.13);border-left:4px solid ${colors[type] || '#999'};
            animation:otIn .4s cubic-bezier(.34,1.56,.64,1) both;`;
        t.innerHTML = `<span style="font-size:1.3rem">${icons[type] || 'ℹ️'}</span>
            <div style="display:grid;gap:2px;flex:1">
              <strong style="font-size:.92rem;color:#1a1a2e">${title}</strong>
              ${msg ? `<span style="font-size:.81rem;color:#666">${msg}</span>` : ''}
            </div>
            <button onclick="this.parentElement.remove()" style="background:none;border:none;cursor:pointer;color:#bbb;font-size:1.1rem">×</button>`;
        tc.appendChild(t);
        setTimeout(() => { try { t.remove(); } catch(e){} }, ms);
    }

    /* ── Helpers ── */
    const fmt = function(v) { return '₹' + Number(v || 0).toFixed(2); };

    // Normalize image path — strip leading ../ for Python root server
    function imgPath(p) { return (p || '').replace(/^\.\.\//, ''); }

    function fmtDate(d) {
        var p = new Date(d);
        if (isNaN(p.getTime())) return String(d);
        return p.toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });
    }

    function fmtDateTime(d) {
        var p = new Date(d);
        if (isNaN(p.getTime())) return String(d);
        var dd  = String(p.getDate()).padStart(2, '0');
        var mon = p.toLocaleString('en-IN', { month: 'short' });
        var yr  = p.getFullYear();
        var hh  = String(p.getHours()).padStart(2, '0');
        var mm  = String(p.getMinutes()).padStart(2, '0');
        var ss  = String(p.getSeconds()).padStart(2, '0');
        return dd + '-' + mon + '-' + yr + ', ' + hh + ':' + mm + ':' + ss;
    }

    /* ── Read order — API first, localStorage fallback ── */
    var urlParams = new URLSearchParams(window.location.search);
    var orderId   = urlParams.get('id');

    if (!orderId) {
        showToast('error', 'Missing order ID', 'Redirecting to orders…');
        setTimeout(function() { window.location.href = 'Orders.html'; }, 1800);
        return;
    }

    var order = null;
    var orders = [];

    async function loadOrder() {
        var isLoggedIn = window.tapAuth && window.tapAuth.isLoggedIn();

        // ── Try API first ────────────────────────────────────────────────────
        if (isLoggedIn && window.tapApi) {
            try {
                var apiOrder = await window.tapApi.orders.get(orderId);
                if (apiOrder) {
                    // Normalise API response to the shape OrderTracking.js expects
                    order = normaliseApiOrder(apiOrder);
                    renderPage();
                    return;
                }
            } catch (e) {
                // Fall through to localStorage
            }
        }

        // ── Fallback: localStorage ───────────────────────────────────────────
        try { orders = JSON.parse(localStorage.getItem('tapOrders') || '[]'); } catch(e) {}

        order = orders.find(function(o) { return o.id === orderId; });
        if (!order) {
            var decoded = decodeURIComponent(orderId);
            order = orders.find(function(o) { return o.id === decoded; });
        }

        if (!order) {
            showToast('error', 'Order not found', 'ID: ' + orderId);
            var page = document.querySelector('.tracking-page');
            if (page) {
                page.innerHTML =
                    '<div style="padding:48px 24px;text-align:center;display:grid;gap:20px;justify-items:center">' +
                    '<div style="font-size:3rem">📦</div>' +
                    '<h2 style="color:#7c616c">Order not found</h2>' +
                    '<p style="color:#999">We could not find order <strong>#' + orderId + '</strong>.</p>' +
                    '<a href="Orders.html" style="padding:14px 28px;background:linear-gradient(135deg,#ff76a4,#c97fa0);color:#fff;border-radius:999px;text-decoration:none;font-weight:700">← Back to Orders</a>' +
                    '</div>';
            }
            return;
        }
        renderPage();
    }

    /* Convert Spring Boot API order shape → legacy shape used in this file */
    function normaliseApiOrder(o) {
        return {
            id:            o.orderNumber || String(o.id),
            status:        titleCaseStatus(o.status),
            placedAt:      o.placedAt,
            subtotal:      Number(o.subtotal),
            delivery:      Number(o.deliveryCharge),
            total:         Number(o.total),
            paymentMethod: (o.paymentMethod || 'COD').toLowerCase(),
            returnRequested:  o.returnRequested,
            exchangeRequested:o.exchangeRequested,
            address: {
                fullName:   o.addressFullName,
                street:     o.addressStreet,
                city:       o.addressCity,
                postalCode: o.addressPostalCode,
                country:    o.addressCountry
            },
            items: (o.items || []).map(function(i) {
                return {
                    name:     i.productName,
                    img:      i.productImageUrl,
                    category: i.categoryName,
                    price:    Number(i.unitPrice),
                    qty:      i.quantity
                };
            }),
            _apiId: o.id   // keep numeric ID for API calls
        };
    }

    function titleCaseStatus(s) {
        var map = {
            'PENDING':          'Pending',
            'CONFIRMED':        'Confirmed',
            'PACKED':           'Packed & Ready',
            'SHIPPED':          'Shipped',
            'OUT_FOR_DELIVERY': 'Out for Delivery',
            'DELIVERED':        'Delivered',
            'CANCELLED':        'Cancelled'
        };
        return map[s] || s;
    }

    /* save: update localStorage entry (API already persisted via PATCH call) */
    function saveOrders() {
        var idx = orders.findIndex(function(o) { return o.id === order.id; });
        if (idx >= 0) { orders[idx] = order; localStorage.setItem('tapOrders', JSON.stringify(orders)); }
    }

    /* ── renderPage: called after order is loaded ── */
    function renderPage() {
        /* Status & dates */
        var currentStatus = order.status || 'Pending';
        var currentStep   = (STATUS_INDEX[currentStatus] !== undefined) ? STATUS_INDEX[currentStatus] : 0;
        var placedMs      = new Date(order.placedAt).getTime();
        if (isNaN(placedMs)) placedMs = Date.now();
        var deliveryDate  = new Date(placedMs + 5 * 24 * 60 * 60 * 1000);

        /* Header */
        setEl('breadcrumbOrderId', 'Order #' + order.id);
        setEl('orderIdDisplay',    '#' + order.id);
        setEl('orderPlacedDate',   fmtDateTime(order.placedAt));
        setEl('orderExpectedDate', fmtDate(deliveryDate));

        var badge = document.getElementById('orderStatusBadge');
        if (badge) {
            badge.className   = 'tracking-status-badge status-' + currentStatus.toLowerCase().replace(/\s+/g, '-');
            badge.textContent = currentStatus;
        }

        /* Timeline */
        buildTimeline(currentStatus, currentStep, deliveryDate);

        /* Details */
        populateOrderDetails();

        /* Action sections */
        setupActionSections(currentStatus);

        /* Delivery banner */
        var bannerTitle = document.querySelector('.delivery-banner-title');
        var bannerSub   = document.querySelector('.delivery-banner-sub');
        var bannerMessages = {
            'Pending':          { title: 'Order received!',             sub: 'Your order is confirmed and being processed.' },
            'Confirmed':        { title: 'Order confirmed!',            sub: 'We\'re getting your items ready to ship.' },
            'Order placed':     { title: 'Order placed!',               sub: 'Your order is confirmed and being processed.' },
            'Packed & Ready':   { title: 'Packed and ready!',           sub: 'Your package is packed and awaiting pickup.' },
            'Shipped':          { title: 'Your order is shipped!',      sub: 'Your package is on its way to you.' },
            'Out for Delivery': { title: 'Out for delivery!',           sub: 'Your delivery hero is heading to your door.' },
            'Delivered':        { title: 'Successfully delivered! 🎉',  sub: 'Your order has arrived. Enjoy your purchase!' },
            'Cancelled':        { title: 'Order cancelled',             sub: 'This order has been cancelled.' }
        };
        var bmsg = bannerMessages[currentStatus] || bannerMessages['Pending'];
        if (bannerTitle) bannerTitle.textContent = bmsg.title;
        if (bannerSub)   bannerSub.textContent   = bmsg.sub;

        /* Bind action buttons */
        bindActions(currentStatus);
    }

    /* ── Status & dates ── */
    var STATUS_INDEX = {
        'Pending': 0, 'Confirmed': 0, 'Order placed': 0,
        'Packed & Ready': 1, 'Shipped': 1,
        'Out for Delivery': 2,
        'Delivered': 3,
        'Cancelled': -1
    };

    var STEPS = [
        { label: 'Order Placed',     desc: 'We received your order',       icon: '📦' },
        { label: 'Packed & Ready',   desc: 'Packed at our warehouse',       icon: '🏭' },
        { label: 'Out for Delivery', desc: 'On its way to your door',       icon: '🚚' },
        { label: 'Delivered',        desc: 'Successfully delivered to you', icon: '✅' }
    ];

    function setEl(id, val) {
        var el = document.getElementById(id);
        if (el) el.textContent = val;
    }

    /* ── Timeline ── */
    function buildTimeline(currentStatus, currentStep, deliveryDate) {
        var timeline    = document.getElementById('trackingTimeline');
        var progressBar = document.getElementById('timelineProgressBar');
        if (!timeline) return;

        // remove any old step nodes
        Array.from(timeline.querySelectorAll('.timeline-step')).forEach(function(el) { el.remove(); });

        if (currentStep < 0) {
            progressBar.style.width = '0%';
            var note = document.createElement('p');
            note.style.cssText = 'color:#ef4444;font-weight:700;padding:16px;text-align:center;flex:1;';
            note.textContent = '✗ This order was cancelled.';
            timeline.appendChild(note);
            return;
        }

        STEPS.forEach(function(step, i) {
            var isActive  = i <= currentStep;
            var isCurrent = i === currentStep;

            var div = document.createElement('div');
            div.className   = 'timeline-step' + (isActive ? ' active' : '') + (isCurrent ? ' current' : '');
            div.dataset.step = String(i);
            div.style.animation = 'stepFadeIn 0.5s ' + (i * 0.15) + 's both';

            div.innerHTML =
                '<div class="timeline-step-icon">' +
                    '<span class="timeline-checkmark">' + (isActive ? step.icon : '') + '</span>' +
                '</div>' +
                '<div class="timeline-step-content">' +
                    '<h4>' + step.label + '</h4>' +
                    '<p>' + step.desc + '</p>' +
                    (i === 0 ? '<span class="timeline-step-date">' + fmtDateTime(order.placedAt) + '</span>' : '') +
                    (i === 3 && currentStep === 3 ? '<span class="timeline-step-date">' + fmtDate(deliveryDate) + '</span>' : '') +
                '</div>';

            timeline.appendChild(div);
        });

        var pct = currentStep <= 0 ? 0 : (currentStep / (STEPS.length - 1)) * 100;
        setTimeout(function() { progressBar.style.width = pct + '%'; }, 250);
    }

    /* ── Order items & details ── */
    function populateOrderDetails() {
        var items  = order.items || [];
        var listEl = document.getElementById('trackingProductsList');

        if (listEl) {
            if (!items.length) {
                listEl.innerHTML = '<p style="color:#aaa;padding:16px 0;font-size:.9rem">No items in this order.</p>';
            } else {
                var html = items.map(function(item, idx) {
                    var lineTotal = (item.price || 0) * (item.qty || 1);
                    return (
                        '<div class="ot-product-row" data-idx="' + idx + '">' +
                            '<div class="ot-product-main">' +
                                '<img src="' + imgPath(item.img || '') + '" alt="' + (item.name || '') + '" class="ot-product-thumb" />' +
                                '<div class="ot-product-info">' +
                                    '<strong>' + (item.name || 'Product') + '</strong>' +
                                    '<span>' + (item.category || '') + '</span>' +
                                '</div>' +
                                '<div class="ot-product-price">' + fmt(lineTotal) + '</div>' +
                                '<span class="ot-expand-arrow">▼</span>' +
                            '</div>' +
                            '<div class="ot-product-detail">' +
                                '<div class="ot-detail-cell"><span class="ot-detail-label">Unit Price</span><span class="ot-detail-val">' + fmt(item.price) + '</span></div>' +
                                '<div class="ot-detail-cell"><span class="ot-detail-label">Quantity</span><span class="ot-detail-val">' + (item.qty || 1) + '</span></div>' +
                                '<div class="ot-detail-cell"><span class="ot-detail-label">Subtotal</span><span class="ot-detail-val ot-highlight">' + fmt(lineTotal) + '</span></div>' +
                            '</div>' +
                        '</div>'
                    );
                }).join('');
                listEl.innerHTML = html;

                // bind click expand
                listEl.querySelectorAll('.ot-product-row').forEach(function(row) {
                    row.querySelector('.ot-product-main').addEventListener('click', function() {
                        var isOpen = row.classList.toggle('open');
                        row.querySelector('.ot-product-detail').classList.toggle('open', isOpen);
                        row.querySelector('.ot-expand-arrow').style.transform = isOpen ? 'rotate(180deg)' : '';
                    });
                });
            }
        }

        // summary
        setEl('orderSubtotal', fmt(order.subtotal));
        setEl('orderDelivery', fmt(order.delivery));
        setEl('orderTotal',    fmt(order.total));

        // address
        var addr   = order.address || {};
        var addrEl = document.getElementById('trackingAddress');
        if (addrEl) {
            if (addr.fullName || addr.street) {
                addrEl.innerHTML =
                    '<p class="tracking-address-name">'  + (addr.fullName || '') + '</p>' +
                    '<p>' + (addr.street || '') + '</p>' +
                    '<p>' + [addr.city, addr.postalCode].filter(Boolean).join(', ') + '</p>' +
                    '<p>' + (addr.country || '') + '</p>';
            } else {
                addrEl.innerHTML = '<p style="color:#aaa;font-size:.9rem">No address on record.</p>';
            }
        }

        // payment
        var pmEl = document.getElementById('trackingPayment');
        if (pmEl) {
            var pmText = (order.paymentMethod === 'upi') ? 'UPI / Online Payment' : 'Cash on Delivery';
            pmEl.innerHTML = '<p class="tracking-payment-method">' + pmText + '</p>';
        }
    }

    /* ── Action sections ── */
    function setupActionSections(currentStatus) {
        var cancelEl = document.getElementById('cancelSection');
        var retExEl  = document.getElementById('returnExchangeSection');
        if (!cancelEl || !retExEl) return;

        if (currentStatus === 'Delivered') {
            cancelEl.classList.add('hidden');
            retExEl.classList.remove('hidden');
        } else if (currentStatus === 'Cancelled') {
            cancelEl.classList.add('hidden');
            retExEl.classList.add('hidden');
        } else {
            cancelEl.classList.remove('hidden');
            retExEl.classList.add('hidden');
        }
    }

    /* ── Confirm modal ── */
    var modal       = document.getElementById('confirmModal');
    var overlay     = document.getElementById('pageOverlay');
    var modalTitle  = document.getElementById('modalTitle');
    var modalMsg    = document.getElementById('modalMessage');
    var modalCancel = document.getElementById('modalCancel');
    var modalOk     = document.getElementById('modalConfirm');
    var pendingAct  = null;

    function openModal(title, msg, action) {
        if (modalTitle) modalTitle.textContent = title;
        if (modalMsg)   modalMsg.textContent   = msg;
        pendingAct = action;
        if (modal)   modal.classList.remove('hidden');
        if (overlay) overlay.classList.remove('hidden');
    }
    function closeModal() {
        if (modal)   modal.classList.add('hidden');
        if (overlay) overlay.classList.add('hidden');
        pendingAct = null;
    }
    if (modalCancel) modalCancel.addEventListener('click', closeModal);
    if (overlay)     overlay.addEventListener('click', closeModal);
    if (modalOk)     modalOk.addEventListener('click', function() { if (pendingAct) pendingAct(); closeModal(); });

    /* ── Action button bindings (API-first, localStorage fallback) ── */
    function bindActions(currentStatus) {
        var cancelBtn   = document.getElementById('cancelOrderBtn');
        var returnBtn   = document.getElementById('returnOrderBtn');
        var exchangeBtn = document.getElementById('exchangeOrderBtn');

        if (cancelBtn) {
            cancelBtn.addEventListener('click', function() {
                openModal('Cancel Order', 'Are you sure? This cannot be undone.', async function() {
                    try {
                        var apiId = order._apiId;
                        if (apiId && window.tapApi && window.tapAuth.isLoggedIn()) {
                            await window.tapApi.orders.cancel(apiId);
                        }
                    } catch(e) { /* fallback */ }
                    order.status = 'Cancelled';
                    saveOrders();
                    showToast('success', 'Order cancelled', 'Your order has been cancelled.');
                    setTimeout(function() { window.location.reload(); }, 1600);
                });
            });
        }

        if (returnBtn) {
            returnBtn.addEventListener('click', function() {
                openModal('Request Return', 'Our team will contact you within 24 hours to arrange pickup.', async function() {
                    try {
                        var apiId = order._apiId;
                        if (apiId && window.tapApi && window.tapAuth.isLoggedIn()) {
                            await window.tapApi.orders.return(apiId);
                        }
                    } catch(e) { /* fallback */ }
                    order.returnRequested   = true;
                    order.returnRequestedAt = new Date().toISOString();
                    saveOrders();
                    showToast('success', 'Return requested', 'We will reach out within 24 hours.');
                });
            });
        }

        if (exchangeBtn) {
            exchangeBtn.addEventListener('click', function() {
                openModal('Request Exchange', 'Our team will contact you within 24 hours to process the exchange.', async function() {
                    try {
                        var apiId = order._apiId;
                        if (apiId && window.tapApi && window.tapAuth.isLoggedIn()) {
                            await window.tapApi.orders.exchange(apiId);
                        }
                    } catch(e) { /* fallback */ }
                    order.exchangeRequested   = true;
                    order.exchangeRequestedAt = new Date().toISOString();
                    saveOrders();
                    showToast('success', 'Exchange requested', 'We will reach out within 24 hours.');
                });
            });
        }
    }

    /* ── Inline styles for product rows (no CSS file dependency) ── */
    var styleTag = document.createElement('style');
    styleTag.textContent = `
        .ot-product-row {
            border-radius: 14px;
            border: 1px solid rgba(124,97,108,0.12);
            overflow: hidden;
            background: #fff;
            transition: box-shadow 0.2s;
            margin-bottom: 12px;
        }
        .ot-product-row:last-child { margin-bottom: 0; }
        .ot-product-row:hover { box-shadow: 0 6px 20px rgba(124,97,108,0.1); }
        .ot-product-main {
            display: grid;
            grid-template-columns: 74px 1fr auto auto;
            gap: 14px;
            align-items: center;
            padding: 14px;
            cursor: pointer;
            background: linear-gradient(135deg,#fafafa,#fff);
        }
        .ot-product-main:hover { background: #fff6fb; }
        .ot-product-thumb {
            width: 74px; height: 98px;
            object-fit: cover;
            border-radius: 10px;
            box-shadow: 0 3px 10px rgba(0,0,0,0.09);
        }
        .ot-product-info { display: grid; gap: 4px; min-width: 0; }
        .ot-product-info strong { font-size: .97rem; color: #2d1a28; font-weight: 700; }
        .ot-product-info span   { font-size: .83rem; color: #9c7f8c; }
        .ot-product-price { font-size: 1.05rem; font-weight: 700; color: #7c616c; white-space: nowrap; }
        .ot-expand-arrow {
            font-size: .82rem; color: #ccc;
            transition: transform 0.28s ease, color 0.2s;
        }
        .ot-product-row.open .ot-expand-arrow { color: #ff76a4; }

        /* Detail panel */
        .ot-product-detail {
            display: grid;
            grid-template-columns: repeat(3, 1fr);
            gap: 10px;
            padding: 0 14px;
            max-height: 0;
            overflow: hidden;
            opacity: 0;
            transition: max-height 0.35s cubic-bezier(0.4,0,0.2,1),
                        opacity    0.25s ease,
                        padding    0.25s ease;
        }
        .ot-product-detail.open {
            max-height: 160px;
            opacity: 1;
            padding: 0 14px 14px;
        }
        .ot-detail-cell {
            display: grid; gap: 4px;
            padding: 10px 14px;
            background: #f9f4f8;
            border-radius: 10px;
            border: 1px solid rgba(124,97,108,0.08);
        }
        .ot-detail-label {
            font-size: .72rem; font-weight: 700;
            text-transform: uppercase; letter-spacing: .1em;
            color: #a0789a;
        }
        .ot-detail-val { font-size: 1rem; font-weight: 700; color: #2d1a28; }
        .ot-detail-val.ot-highlight { color: #ff76a4; font-size: 1.08rem; }

        @media (max-width: 480px) {
            .ot-product-main { grid-template-columns: 60px 1fr auto auto; gap: 10px; }
            .ot-product-thumb { width: 60px; height: 80px; }
            .ot-product-detail { grid-template-columns: repeat(2, 1fr); }
        }
    `;
    document.head.appendChild(styleTag);

    /* ── Init ── */
    loadOrder();   // async: fetches from API or localStorage, then calls renderPage()

})();
