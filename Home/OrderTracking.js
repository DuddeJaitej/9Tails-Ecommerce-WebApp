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
    if (cartCountEl && window.tapAuth) window.tapAuth.syncCartCountFromApi();

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

    function escapeOrderText(value) {
        return String(value == null ? '' : value).replace(/[&<>"']/g, function(character) {
            return {
                '&': '&amp;',
                '<': '&lt;',
                '>': '&gt;',
                '"': '&quot;',
                "'": '&#39;'
            }[character];
        });
    }

    // Normalize image path — strip leading ../ for Python root server
    function imgPath(p) {
        if (!p) return '';
        p = String(p).replace(/^\.\.\//, '');
        if (/^https?:\/\//i.test(p)) return p;
        if (!p.startsWith('/')) p = '/' + p;
        return p.split('/').map(encodeURIComponent).join('/');
    }

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

    /* ── Read order only from Spring Boot ── */
    var urlParams = new URLSearchParams(window.location.search);
    var orderId   = urlParams.get('id');

    if (!orderId) {
        showToast('error', 'Missing order ID', 'Redirecting to orders…');
        setTimeout(function() { window.location.href = 'Orders.html'; }, 1800);
        return;
    }

    var order = null;
    var orders = [];
    var trackingPoll = null;
    var activeConfirmationId = null;

    async function loadOrder(silent) {
        var isLoggedIn = window.tapAuth && window.tapAuth.isLoggedIn();

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
                if (!silent) showToast('error', 'Order could not be loaded', e.message || 'Please try again.');
                else console.warn('Order tracking refresh failed:', e.message);
                return;
            }
        }

        if (!silent) showToast('error', isLoggedIn ? 'Order not found' : 'Login required',
            'Sign in and ensure the order service is available.');
    }

    /* Convert Spring Boot API order shape → legacy shape used in this file */
    function normaliseApiOrder(o) {
        var fulfillments = o.fulfillments || [];
        var earliestExpectedDate = fulfillments.map(function(item) {
            return item.expectedDeliveryDate;
        }).filter(Boolean).sort()[0];
        return {
            id:            o.orderNumber || String(o.id),
            status:        titleCaseStatus(o.status),
            placedAt:      o.placedAt,
            subtotal:      Number(o.subtotal),
            delivery:      Number(o.deliveryCharge),
            total:         Number(o.total),
            paymentMethod: (o.paymentMethod || 'COD').toLowerCase(),
            expectedDeliveryDate: o.expectedDeliveryDate || earliestExpectedDate,
            fulfillments: fulfillments,
            serviceRequests: o.serviceRequests || [],
            rawStatus: o.status,
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
                    id:       i.id,
                    name:     i.productName,
                    img:      i.productImageUrl,
                    category: i.categoryName,
                    price:    Number(i.unitPrice),
                    qty:      i.quantity,
                    cancelled: i.cancelled,
                    fulfillmentStatus: i.fulfillmentStatus,
                    deliveredAt: i.deliveredAt
                };
            }),
            _apiId: o.id   // keep numeric ID for API calls
        };
    }

    function titleCaseStatus(s) {
        var map = {
            'PENDING':          'Pending',
            'AWAITING_OWNER_CONFIRMATION': 'Awaiting seller confirmation',
            'CONFIRMED':        'Confirmed',
            'PICKED_UP_FROM_OWNER': 'Picked Up from Owner',
            'PACKED_READY':     'Packed & Ready',
            'PACKED':           'Packed & Ready',
            'SHIPPED':          'Shipped',
            'IN_TRANSIT_TO_STORE': 'Moving to destination warehouse',
            'AT_DESTINATION_STORE': 'At destination warehouse',
            'OUT_FOR_DELIVERY': 'Out for Delivery',
            'DELIVERY_CONFIRMATION_PENDING': 'Delivery Confirmation Pending',
            'DELIVERED':        'Delivered',
            'REJECTED':         'Rejected',
            'CANCELLED':        'Cancelled'
        };
        return map[s] || s;
    }

    /* Keep the API response in memory until the next API reload. */
    function saveOrders() {
        var idx = orders.findIndex(function(o) { return o.id === order.id; });
        if (idx >= 0) orders[idx] = order;
    }

    /* ── renderPage: called after order is loaded ── */
    function renderPage() {
        /* Status & dates */
        var fulfillments = order.fulfillments || [];
        var currentStatus = fulfillments.length === 1
            ? titleCaseStatus(fulfillments[0].status)
            : (order.status || 'Pending');
        var deliveryDate = order.expectedDeliveryDate;

        /* Header */
        setEl('breadcrumbOrderId', 'Order #' + order.id);
        setEl('orderIdDisplay',    '#' + order.id);
        setEl('orderPlacedDate',   fmtDateTime(order.placedAt));
        setEl('orderExpectedDate', deliveryDate ? fmtDate(deliveryDate) : 'Pending seller confirmation');

        var badge = document.getElementById('orderStatusBadge');
        if (badge) {
            badge.className   = 'tracking-status-badge status-' + currentStatus.toLowerCase().replace(/\s+/g, '-');
            badge.textContent = currentStatus;
        }

        /* Timeline */
        buildTimeline(currentStatus, deliveryDate);
        renderDeliveryConfirmation();

        /* Details */
        populateOrderDetails();

        /* Action sections */
        setupActionSections(currentStatus);
        renderServiceRequestStatus();

        /* Delivery banner */
        var bannerTitle = document.querySelector('.delivery-banner-title');
        var bannerSub   = document.querySelector('.delivery-banner-sub');
        var bannerMessages = {
            'Pending':          { title: 'Order received!',             sub: 'Your order is confirmed and being processed.' },
            'Confirmed':        { title: 'Order confirmed!',            sub: 'We\'re getting your items ready to ship.' },
            'Order placed':     { title: 'Order placed!',               sub: 'Your order is confirmed and being processed.' },
            'Picked Up from Owner': { title: 'Picked up from the seller', sub: 'The package has been collected from the seller.' },
            'Packed & Ready':   { title: 'Packed and ready!',           sub: 'Your package is packed and awaiting pickup.' },
            'Moving to destination warehouse': { title: 'Moving between warehouses', sub: 'Your package is on its way to the warehouse nearest to you.' },
            'At destination warehouse': { title: 'Arrived at your local warehouse', sub: 'Your package is at the destination warehouse and waiting for final delivery assignment.' },
            'Shipped':          { title: 'Your order is shipped!',      sub: 'Your package is on its way to you.' },
            'Out for Delivery': { title: 'Out for delivery!',           sub: 'Your delivery hero is heading to your door.' },
            'Delivery Confirmation Pending': { title: 'Please confirm delivery', sub: 'Your package will remain unconfirmed until you respond.' },
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
    var STEPS = [
        { label: 'Order Placed',     desc: 'We received your order',       icon: '📦' },
        { label: 'Picked Up from Owner', desc: 'The package has been collected from the seller', icon: '🛍️' },
        { label: 'Packed & Ready',   desc: 'Packed at our warehouse',       icon: '🏭' },
        { label: 'Out for Delivery', desc: 'On its way to your door',       icon: '🚚' },
        { label: 'Delivered',        desc: 'Successfully delivered to you', icon: '✅' }
    ];

    function setEl(id, val) {
        var el = document.getElementById(id);
        if (el) el.textContent = val;
    }

    /* ── Timeline ── */
    function buildTimeline(currentStatus, expectedDeliveryDate) {
        var fallback = document.getElementById('trackingTimeline');
        var fulfillmentList = document.getElementById('fulfillmentTimelineList');
        if (!fallback || !fulfillmentList) return;
        var fulfillments = order.fulfillments || [];
        fulfillmentList.replaceChildren();

        if (fulfillments.length) {
            fallback.classList.add('hidden');
            fulfillments.forEach(function(fulfillment) {
                var card = document.createElement('article');
                card.className = 'fulfillment-timeline-card';
                var heading = document.createElement('h4');
                var productNames = (fulfillment.items || []).map(function(item) {
                    return item.productName;
                }).filter(Boolean).join(', ');
                heading.textContent = (fulfillment.ownerName || 'Seller')
                    + (productNames ? ' · ' + productNames : '');
                var status = document.createElement('span');
                var packageStatus = titleCaseStatus(fulfillment.status);
                status.className = 'fulfillment-timeline-status status-'
                    + packageStatus.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
                status.textContent = packageStatus;
                card.append(heading, status);
                var timeline = document.createElement('div');
                timeline.className = 'tracking-timeline five-stage';
                card.appendChild(timeline);
                fulfillmentList.appendChild(card);
                renderTimelineSteps(
                    timeline,
                    fulfillment.status,
                    fulfillment,
                    fulfillment.expectedDeliveryDate || expectedDeliveryDate
                );
            });
            return;
        }

        fallback.classList.remove('hidden');
        fallback.classList.add('five-stage');
        renderTimelineSteps(fallback, currentStatus, null, expectedDeliveryDate);
    }

    function statusKey(status) {
        if (status === 'PICKED_UP_FROM_OWNER') return status;
        if (status === 'PACKED_READY' || status === 'PACKED') return 'PACKED_READY';
        if (status === 'OUT_FOR_DELIVERY' || status === 'SHIPPED') return 'OUT_FOR_DELIVERY';
        if (status === 'DELIVERY_CONFIRMATION_PENDING') return status;
        return status;
    }

    function renderTimelineSteps(timeline, rawStatus, fulfillment, expectedDeliveryDate) {
        var status = statusKey(rawStatus);
        var currentStep = ({
            PENDING: 0,
            AWAITING_OWNER_CONFIRMATION: 0,
            CONFIRMED: 0,
            PICKED_UP_FROM_OWNER: 1,
            PACKED_READY: 2,
            IN_TRANSIT_TO_STORE: 2,
            AT_DESTINATION_STORE: 2,
            OUT_FOR_DELIVERY: 3,
            DELIVERY_CONFIRMATION_PENDING: 3,
            DELIVERED: 4
        })[status];
        var progressBar = document.createElement('div');
        progressBar.className = 'timeline-progress-bar';
        timeline.replaceChildren(progressBar);

        if (status === 'CANCELLED' || status === 'REJECTED') {
            var note = document.createElement('p');
            note.className = 'timeline-cancelled-note';
            note.textContent = 'This seller fulfillment was ' + status.toLowerCase() + '.';
            timeline.appendChild(note);
            return;
        }
        if (currentStep === undefined) currentStep = 0;

        var timestamps = fulfillment ? [
            fulfillment.placedAt,
            fulfillment.pickupAt,
            fulfillment.warehouseReceivedAt,
            fulfillment.dispatchedAt,
            fulfillment.deliveredAt
        ] : [order.placedAt, null, null, null, null];

        STEPS.forEach(function(step, index) {
            var isActive = index <= currentStep;
            var div = document.createElement('div');
            div.className = 'timeline-step' + (isActive ? ' active' : '')
                + (index === currentStep ? ' current' : '');
            var icon = document.createElement('div');
            icon.className = 'timeline-step-icon';
            var mark = document.createElement('span');
            mark.className = 'timeline-checkmark';
            mark.textContent = isActive ? '✓' : '';
            icon.appendChild(mark);

            var content = document.createElement('div');
            content.className = 'timeline-step-content';
            var title = document.createElement('h4');
            title.textContent = step.label;
            var description = document.createElement('p');
            description.textContent = index === 2 && status === 'IN_TRANSIT_TO_STORE'
                ? 'Your package is moving to the destination warehouse.'
                : index === 2 && status === 'AT_DESTINATION_STORE'
                    ? 'Your package has arrived at the destination warehouse.'
                    : step.desc;
            content.append(title, description);
            if (timestamps[index]) {
                var time = document.createElement('span');
                time.className = 'timeline-step-date';
                time.textContent = fmtDateTime(timestamps[index]);
                content.appendChild(time);
            }
            if (index === 4 && expectedDeliveryDate) {
                var expected = document.createElement('span');
                expected.className = 'timeline-step-date';
                expected.textContent = 'Expected ' + fmtDate(expectedDeliveryDate);
                content.appendChild(expected);
            }
            div.append(icon, content);
            timeline.appendChild(div);
        });

        var pct = currentStep <= 0 ? 0 : (currentStep / (STEPS.length - 1)) * 80;
        setTimeout(function() { progressBar.style.width = pct + '%'; }, 150);
    }

    function renderDeliveryConfirmation() {
        var pending = (order.fulfillments || []).filter(function(fulfillment) {
            return fulfillment.status === 'DELIVERY_CONFIRMATION_PENDING';
        });
        var banner = document.getElementById('deliveryConfirmationBanner');
        var summary = document.getElementById('deliveryConfirmationSummary');
        var reopen = document.getElementById('reopenDeliveryConfirmation');
        var dialog = document.getElementById('deliveryConfirmationDialog');
        if (!banner || !dialog) return;

        if (!pending.length) {
            banner.classList.add('hidden');
            if (dialog.open) dialog.close();
            activeConfirmationId = null;
            return;
        }

        activeConfirmationId = pending[0].id;
        banner.classList.remove('hidden');
        if (summary) {
            summary.textContent = pending.length === 1
                ? 'Please confirm whether your package arrived.'
                : pending.length + ' seller packages are awaiting your delivery confirmation.';
        }
        if (reopen) {
            reopen.onclick = function() {
                if (!dialog.open) dialog.showModal();
            };
        }

        var shownKey = 'delivery-confirmation-shown-' + activeConfirmationId;
        if (!sessionStorage.getItem(shownKey) && !dialog.open) {
            sessionStorage.setItem(shownKey, 'true');
            dialog.showModal();
        }
    }

    async function submitDeliveryConfirmation(delivered) {
        if (!activeConfirmationId || !window.tapApi) return;
        var yesButton = document.getElementById('deliveryReceived');
        var noButton = document.getElementById('deliveryNotReceived');
        var note = document.getElementById('deliveryConfirmationNote');
        [yesButton, noButton].forEach(function(button) {
            if (button) button.disabled = true;
        });
        try {
            await window.tapApi.delivery.confirmCustomerDelivery(
                activeConfirmationId,
                delivered,
                note ? note.value.trim() : ''
            );
            var dialog = document.getElementById('deliveryConfirmationDialog');
            if (dialog && dialog.open) dialog.close();
            if (note) note.value = '';
            showToast('success', delivered ? 'Delivery confirmed' : 'Delivery reported missing',
                delivered ? 'Thank you for confirming receipt.' : 'The delivery team has been asked to follow up.');
            await loadOrder(true);
        } catch (e) {
            showToast('error', 'Could not record your response', e.message || 'Please try again.');
        } finally {
            [yesButton, noButton].forEach(function(button) {
                if (button) button.disabled = false;
            });
        }
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
                        '<div class="ot-product-row' + (item.cancelled ? ' cancelled' : '') + '" data-idx="' + idx + '">' +
                            '<div class="ot-product-main">' +
                                '<img src="' + imgPath(item.img || '') + '" alt="' + (item.name || '') + '" class="ot-product-thumb" />' +
                                '<div class="ot-product-info">' +
                                    '<strong>' + (item.name || 'Product') + '</strong>' +
                                    '<span>' + (item.category || '') + '</span>' +
                                    (item.cancelled ? '<span class="ot-cancelled-badge">Cancelled</span>' : '') +
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
        if (cancelEl) {
            cancelEl.classList.toggle('hidden',
                currentStatus === 'Delivered' || currentStatus === 'Cancelled');
        }
        var canRequestService = (order.items || []).some(isReturnOrExchangeEligible);
        var hasServiceHistory = (order.serviceRequests || []).length > 0;
        if (retExEl) retExEl.classList.toggle('hidden', !canRequestService && !hasServiceHistory);
    }

    var pendingServiceRequestType = null;
    var serviceRequestDialog = document.getElementById('serviceRequestDialog');
    var serviceRequestForm = document.getElementById('serviceRequestForm');
    var serviceRequestPreviewUrls = new Map();

    function clearServiceRequestPreview(previewId) {
        var objectUrl = serviceRequestPreviewUrls.get(previewId);
        if (objectUrl) URL.revokeObjectURL(objectUrl);
        serviceRequestPreviewUrls.delete(previewId);
        var preview = document.getElementById(previewId);
        if (preview) {
            preview.replaceChildren();
            preview.classList.add('hidden');
        }
    }

    function showServiceRequestPreview(inputId, previewId, mediaType) {
        clearServiceRequestPreview(previewId);
        var input = document.getElementById(inputId);
        var preview = document.getElementById(previewId);
        var file = input && input.files && input.files[0];
        if (!file || !preview) return;

        var isImage = mediaType === 'image' && file.type.indexOf('image/') === 0;
        var isVideo = mediaType === 'video' && file.type === 'video/mp4';
        if (!isImage && !isVideo) return;

        var objectUrl = URL.createObjectURL(file);
        serviceRequestPreviewUrls.set(previewId, objectUrl);
        var media = document.createElement(isImage ? 'img' : 'video');
        media.src = objectUrl;
        if (isImage) {
            media.alt = 'Selected product photo';
        } else {
            media.controls = true;
            media.playsInline = true;
            media.preload = 'metadata';
        }
        preview.replaceChildren(media);
        preview.classList.remove('hidden');
    }

    var serviceRequestPhotoInput = document.getElementById('serviceRequestPhoto');
    var serviceRequestVideoInput = document.getElementById('serviceRequestVideo');
    if (serviceRequestPhotoInput) {
        serviceRequestPhotoInput.addEventListener('change', function() {
            showServiceRequestPreview('serviceRequestPhoto', 'serviceRequestPhotoPreview', 'image');
        });
    }
    if (serviceRequestVideoInput) {
        serviceRequestVideoInput.addEventListener('change', function() {
            showServiceRequestPreview('serviceRequestVideo', 'serviceRequestVideoPreview', 'video');
        });
    }
    if (serviceRequestForm) {
        serviceRequestForm.addEventListener('reset', function() {
            clearServiceRequestPreview('serviceRequestPhotoPreview');
            clearServiceRequestPreview('serviceRequestVideoPreview');
        });
    }
    if (serviceRequestDialog) {
        serviceRequestDialog.addEventListener('close', function() {
            clearServiceRequestPreview('serviceRequestPhotoPreview');
            clearServiceRequestPreview('serviceRequestVideoPreview');
        });
    }

    function renderServiceRequestStatus() {
        var status = document.getElementById('serviceRequestStatus');
        if (!status || !order) return;
        var requests = order.serviceRequests || [];
        status.innerHTML = requests.map(function(request) {
            return '<p><strong>' + escapeOrderText(request.productName) + '</strong> · ' +
                escapeOrderText(request.type) + ' · ' +
                escapeOrderText(String(request.status || '').replaceAll('_', ' ')) +
                (request.reviewNotes ? '<br>' + escapeOrderText(request.reviewNotes) : '') +
                '</p>';
        }).join('');
        var pendingItems = new Set(requests.filter(function(request) {
            return request.status !== 'REJECTED';
        }).map(function(request) { return String(request.orderItemId); }));
        ['returnOrderBtn', 'exchangeOrderBtn'].forEach(function(id) {
            var button = document.getElementById(id);
            if (!button) return;
            var availableItems = (order.items || []).filter(function(item) {
                return isReturnOrExchangeEligible(item) && !pendingItems.has(String(item.id));
            });
            button.disabled = availableItems.length === 0;
            button.textContent = button.disabled
                ? (order.serviceRequests || []).some(function(request) {
                    return request.status !== 'REJECTED';
                }) && (order.items || []).some(isReturnOrExchangeEligible)
                    ? 'A request is already in progress'
                    : 'No eligible delivered products'
                : id === 'returnOrderBtn' ? 'Request Return' : 'Request Exchange';
        });
    }

    function isReturnOrExchangeEligible(item) {
        if (item.cancelled || item.fulfillmentStatus !== 'DELIVERED' || !item.deliveredAt) return false;
        var deliveredAt = new Date(item.deliveredAt).getTime();
        return Number.isFinite(deliveredAt) && Date.now() <= deliveredAt + 7 * 24 * 60 * 60 * 1000;
    }

    function openServiceRequest(type) {
        if (!serviceRequestDialog || !order) {
            showToast('error', 'Request form unavailable', 'Refresh the page and try again.');
            return;
        }
        pendingServiceRequestType = type;
        document.getElementById('serviceRequestTitle').textContent =
            type === 'RETURN' ? 'Request a return' : 'Request an exchange';
        var requests = order.serviceRequests || [];
        var requestedItems = new Set(requests.filter(function(request) {
            return request.status !== 'REJECTED';
        }).map(function(request) { return String(request.orderItemId); }));
        var itemSelect = document.getElementById('serviceRequestItem');
        var serviceRequestForm = document.getElementById('serviceRequestForm');
        if (!itemSelect || !serviceRequestForm) {
            showToast('error', 'Request form unavailable', 'Refresh the page to load the latest return and exchange form.');
            return;
        }
        var availableItems = (order.items || []).filter(function(item) {
            return isReturnOrExchangeEligible(item) && !requestedItems.has(String(item.id));
        });
        itemSelect.innerHTML = availableItems.map(function(item) {
            return '<option value="' + escapeOrderText(item.id) + '">' +
                escapeOrderText(item.name) + ' × ' + Number(item.qty) + '</option>';
        }).join('');
        if (!availableItems.length) {
            var hasEligibleItems = (order.items || []).some(isReturnOrExchangeEligible);
            showToast('info', hasEligibleItems ? 'Request already submitted' : 'Return window ended',
                hasEligibleItems
                    ? 'Each product can have one return or exchange request at a time.'
                    : 'Requests are available within 7 days of delivery.');
            return;
        }
        serviceRequestForm.reset();
        if (!serviceRequestDialog.open) serviceRequestDialog.showModal();
    }

    function validateVideoDuration(file) {
        return new Promise(function(resolve, reject) {
            var video = document.createElement('video');
            var url = URL.createObjectURL(file);
            video.preload = 'metadata';
            video.onloadedmetadata = function() {
                URL.revokeObjectURL(url);
                if (video.duration > 120) {
                    reject(new Error('Choose a product video that is no longer than 2 minutes.'));
                } else {
                    resolve();
                }
            };
            video.onerror = function() {
                URL.revokeObjectURL(url);
                reject(new Error('The selected video could not be read. Choose a valid MP4 file.'));
            };
            video.src = url;
        });
    }

    var cancelServiceRequestButton = document.getElementById('cancelServiceRequest');
    if (cancelServiceRequestButton && serviceRequestDialog) {
        cancelServiceRequestButton.addEventListener('click', function() {
            serviceRequestDialog.close();
        });
    }
    var serviceRequestForm = document.getElementById('serviceRequestForm');
    if (serviceRequestForm) serviceRequestForm.addEventListener('submit', async function(event) {
        event.preventDefault();
        var submitButton = document.getElementById('submitServiceRequest');
        var photoInput = document.getElementById('serviceRequestPhoto');
        var videoInput = document.getElementById('serviceRequestVideo');
        var itemSelect = document.getElementById('serviceRequestItem');
        var reasonSelect = document.getElementById('serviceRequestReason');
        var detailsInput = document.getElementById('serviceRequestDetails');
        if (!submitButton || !photoInput || !videoInput || !itemSelect || !reasonSelect || !detailsInput) {
            showToast('error', 'Request form incomplete', 'Refresh the page and try again.');
            return;
        }
        var photo = photoInput.files[0];
        var video = videoInput.files[0];
        submitButton.disabled = true;
        try {
            if (!pendingServiceRequestType) throw new Error('Choose return or exchange before submitting.');
            if (!photo || !video) throw new Error('Attach both a product photo and an MP4 video.');
            if (photo.size > 5 * 1024 * 1024) throw new Error('The product photo must be 5 MB or smaller.');
            if (video.size > 100 * 1024 * 1024) throw new Error('The product video must be 100 MB or smaller.');
            await validateVideoDuration(video);
            var photoUpload = await window.tapApi.orders.uploadEvidence(photo);
            var videoUpload = await window.tapApi.orders.uploadEvidence(video);
            var itemId = itemSelect.value;
            var request = await window.tapApi.orders.createServiceRequest(order._apiId, itemId, {
                type: pendingServiceRequestType,
                reason: reasonSelect.value,
                details: detailsInput.value.trim(),
                photoUrl: photoUpload.url,
                videoUrl: videoUpload.url
            });
            order.serviceRequests = [...(order.serviceRequests || []), request];
            if (pendingServiceRequestType === 'RETURN') order.returnRequested = true;
            else order.exchangeRequested = true;
            serviceRequestDialog.close();
            showToast('success', 'Request sent to the owner', 'The warehouse and seller have been notified for review.');
            renderPage();
        } catch (error) {
            showToast('error', 'Request could not be submitted', error.message || 'Please try again.');
        } finally {
            submitButton.disabled = false;
        }
    });

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

    /* ── Action button bindings through Spring Boot ── */
    function bindActions(currentStatus) {
        var cancelBtn   = document.getElementById('cancelOrderBtn');
        var returnBtn   = document.getElementById('returnOrderBtn');
        var exchangeBtn = document.getElementById('exchangeOrderBtn');

        if (cancelBtn) {
            cancelBtn.onclick = function() {
                openModal('Cancel Order', 'Are you sure? This cannot be undone.', async function() {
                    try {
                        var apiId = order._apiId;
                        if (apiId && window.tapApi && window.tapAuth.isLoggedIn()) {
                            await window.tapApi.orders.cancel(apiId);
                        }
                    } catch(e) {
                        showToast('error', 'Update failed', e.message || 'Run Spring Boot and try again.');
                        return;
                    }
                    order.status = 'Cancelled';
                    saveOrders();
                    showToast('success', 'Order cancelled', 'Your order has been cancelled.');
                    setTimeout(function() { window.location.reload(); }, 1600);
                });
            };
        }

        if (returnBtn) {
            returnBtn.onclick = function() { openServiceRequest('RETURN'); };
        }

        if (exchangeBtn) {
            exchangeBtn.onclick = function() { openServiceRequest('EXCHANGE'); };
        }
    }

    var deliveryReceivedButton = document.getElementById('deliveryReceived');
    if (deliveryReceivedButton) deliveryReceivedButton.addEventListener('click', function() {
        submitDeliveryConfirmation(true);
    });
    var deliveryNotReceivedButton = document.getElementById('deliveryNotReceived');
    if (deliveryNotReceivedButton) deliveryNotReceivedButton.addEventListener('click', function() {
        submitDeliveryConfirmation(false);
    });
    window.addEventListener('beforeunload', function() {
        if (trackingPoll) clearInterval(trackingPoll);
    });
    trackingPoll = setInterval(function() {
        if (!document.hidden && window.tapAuth.isLoggedIn()) {
            loadOrder(true);
        }
    }, 20000);

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
        .ot-product-row.cancelled .ot-product-main { opacity: 0.68; }
        .ot-cancelled-badge { color: #a83245 !important; font-weight: 700; }
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
