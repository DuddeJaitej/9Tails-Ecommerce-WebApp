(function () {
    'use strict';

    const loginPanel = document.getElementById('operationsLogin');
    const app = document.getElementById('operationsApp');
    const loginForm = document.getElementById('operationsLoginForm');
    const loginFeedback = document.getElementById('loginFeedback');
    const ownerRegistrationForm = document.getElementById('ownerRegistrationForm');
    const ownerRegistrationFeedback = document.getElementById('ownerRegistrationFeedback');
    const appFeedback = document.getElementById('appFeedback');
    const scannerVideo = document.getElementById('scannerVideo');
    const scannerMessage = document.getElementById('scannerMessage');
    const scanResult = document.getElementById('scanResult');
    const stageForm = document.getElementById('stageForm');
    const stageFields = document.getElementById('stageFields');
    let currentUser = null;
    let locations = [];
    let ownerTasks = [];
    let deliveryTasks = [];
    let warehouseServiceRequests = [];
    const ownerQrTokens = new Map();
    let categories = [];
    let agents = [];
    let adminUsers = [];
    let activeScan = null;
    let scannerControls = null;
    let scannerReader = null;
    let scanBusy = false;
    let editingProductId = null;
    let existingProductImages = [];
    let productImagePreviewUrls = [];
    let pollHandle = null;
    let feedbackTimeout = null;
    let pickupLocationSearchSequence = 0;
    let knownNotificationIds = new Set();
    let notificationsInitialized = false;

    function escapeHtml(value) {
        return String(value ?? '').replace(/[&<>"']/g, character => ({
            '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
        })[character]);
    }

    function setFeedback(message, isError = false) {
        if (feedbackTimeout) {
            clearTimeout(feedbackTimeout);
            feedbackTimeout = null;
        }
        appFeedback.querySelector('.feedback-message').textContent = message;
        appFeedback.classList.toggle('hidden', !message);
        appFeedback.classList.toggle('error', isError);
        if (message) {
            appFeedback.scrollIntoView({ behavior: 'smooth', block: 'center' });
            if (!isError) {
                feedbackTimeout = setTimeout(() => {
                    appFeedback.classList.add('hidden');
                    appFeedback.querySelector('.feedback-message').textContent = '';
                    feedbackTimeout = null;
                }, 5000);
            }
        }
    }

    function showTaskToast(title, message) {
        let container = document.getElementById('workflowToastContainer');
        if (!container) {
            container = document.createElement('div');
            container.id = 'workflowToastContainer';
            container.className = 'workflow-toast-container';
            container.setAttribute('aria-live', 'polite');
            document.body.appendChild(container);
        }
        const toast = document.createElement('div');
        toast.className = 'workflow-toast';
        const heading = document.createElement('strong');
        heading.textContent = title;
        const text = document.createElement('span');
        text.textContent = message;
        toast.append(heading, text);
        container.appendChild(toast);
        setTimeout(() => toast.remove(), 5000);
    }

    async function openServiceEvidence(button) {
        const preview = window.open('about:blank', '_blank');
        if (!preview) throw new Error('Allow pop-ups to view private return evidence.');
        try {
            const blob = await window.tapApi.serviceRequests.evidence(
                button.dataset.serviceEvidenceUrl);
            const url = URL.createObjectURL(blob);
            preview.location.href = url;
            setTimeout(() => URL.revokeObjectURL(url), 60000);
        } catch (error) {
            preview.close();
            throw error;
        }
    }

    function pageContent(value) {
        if (Array.isArray(value)) return value;
        return Array.isArray(value?.content) ? value.content : [];
    }

    function hasValidPickupLocation(task) {
        const latitude = Number(task.pickupLatitude);
        const longitude = Number(task.pickupLongitude);
        return Boolean(task.pickupStreet?.trim()
            && task.pickupPostalCode?.trim()
            && task.pickupLatitude != null
            && task.pickupLongitude != null
            && Number.isFinite(latitude)
            && Number.isFinite(longitude)
            && latitude >= -90 && latitude <= 90
            && longitude >= -180 && longitude <= 180);
    }

    function setRoleVisibility(role) {
        document.querySelectorAll('.owner-only').forEach(element => {
            const adminOnlyQueue = element.id === 'owner-orders';
            const visible = role === 'OWNER' || (role === 'ADMIN' && adminOnlyQueue);
            element.classList.toggle('hidden', !visible);
        });
        document.querySelectorAll('.delivery-only').forEach(element => {
            element.classList.toggle('hidden', role !== 'DELIVERY_AGENT'
                && role !== 'WAREHOUSE_OPERATOR' && role !== 'ADMIN');
        });
        document.querySelectorAll('.warehouse-only').forEach(element =>
            element.classList.toggle('hidden', role !== 'WAREHOUSE_OPERATOR'));
        document.querySelectorAll('.admin-only').forEach(element => {
            element.classList.toggle('hidden', role !== 'ADMIN');
        });
        document.getElementById('userName').textContent = currentUser.fullName || 'Operations user';
        document.getElementById('userRole').textContent = role.replaceAll('_', ' ').toLowerCase();
        document.getElementById('userAvatar').textContent =
            (currentUser.fullName || '9').trim().charAt(0).toUpperCase();
        document.getElementById('welcomeTitle').textContent =
            role === 'OWNER' ? 'Owner overview'
                : role === 'DELIVERY_AGENT' ? 'Delivery dashboard'
                    : role === 'WAREHOUSE_OPERATOR' ? 'Warehouse tasks'
                        : 'Administrator overview';
    }

    const workspace = document.querySelector('.workspace');
    const responsiveNavigation = window.matchMedia('(max-width: 850px)');
    function isCompactViewport() {
        return window.innerWidth <= 850;
    }
    let compactNavigation = isCompactViewport();

    function showMobileView(targetId) {
        const compact = isCompactViewport();
        workspace.classList.toggle('mobile-overview', compact);
        if (!compact) {
            workspace.classList.remove('show-overview');
            workspace.querySelectorAll('.mobile-view-hidden').forEach(section =>
                section.classList.remove('mobile-view-hidden'));
            return;
        }

        const showOverview = targetId === 'overview';
        workspace.classList.toggle('show-overview', showOverview);
        workspace.querySelectorAll(
            ':scope > .panel:not(.notifications-popover):not(.qr-display)'
        ).forEach(section => {
            section.classList.toggle('mobile-view-hidden', section.id !== targetId);
        });
        if (!showOverview) {
            workspace.querySelector(`#${CSS.escape(targetId)}`)?.scrollIntoView({
                behavior: 'smooth',
                block: 'start'
            });
        }
    }

    async function boot() {
        let authenticated = false;
        try {
            currentUser = await window.tapApi.user.me();
            if (!currentUser || !['OWNER', 'DELIVERY_AGENT', 'WAREHOUSE_OPERATOR', 'ADMIN']
                .includes(currentUser.role)) {
                throw new Error('This account does not have an operations role.');
            }
            authenticated = true;
            loginPanel.classList.add('hidden');
            app.classList.remove('hidden');
            setRoleVisibility(currentUser.role);
            const requestedSection = location.hash.slice(1);
            const requestedLink = requestedSection
                ? sidebarNav.querySelector(`a[href="#${CSS.escape(requestedSection)}"]:not(.hidden)`)
                : null;
            const initialView = requestedLink ? requestedSection : 'overview';
            showMobileView(initialView);
            setActiveNavigation(initialView);
            if (currentUser.role === 'OWNER' && (initialView === 'locations' || !isCompactViewport())) {
                requestAnimationFrame(ensurePickupMap);
            }
            if (currentUser.role === 'OWNER') await loadCategories();
            if (currentUser.role === 'ADMIN') await loadAdminAccess();
            const warehousePending = currentUser.role === 'WAREHOUSE_OPERATOR'
                && !currentUser.warehouseActive;
            document.getElementById('warehouseApprovalPending').classList.toggle(
                'hidden', !warehousePending);
            if (!warehousePending) await refresh();
            if (pollHandle) clearInterval(pollHandle);
            pollHandle = setInterval(async () => {
                try {
                    if (currentUser.role === 'WAREHOUSE_OPERATOR' && currentUser.warehouseActive) {
                        await loadWarehouseTasks();
                    }
                    await loadNotifications();
                } catch (error) {
                    setFeedback(error.message, true);
                }
            }, 30000);
        } catch (error) {
            if (authenticated) {
                setFeedback(error.message, true);
            } else {
                app.classList.add('hidden');
                loginPanel.classList.remove('hidden');
                loginFeedback.textContent = error.message;
            }
        }
    }

    async function refresh() {
        setFeedback('');
        const role = currentUser.role;
        let tasks = [];
        if (role === 'OWNER' || role === 'ADMIN') {
            if (role === 'OWNER') {
                await Promise.all([loadLocations(), loadProducts()]);
            }
            tasks = await loadOwnerTasks();
            document.getElementById('taskCount').textContent = String(tasks.length);
            document.getElementById('actionCount').textContent = String(tasks.filter(task =>
                task.status === 'AWAITING_OWNER_CONFIRMATION').length);
        } else if (role === 'DELIVERY_AGENT') {
            tasks = await window.tapApi.delivery.tasks();
            renderDeliveryTasks(tasks);
            document.getElementById('taskCount').textContent = String(tasks.length);
            document.getElementById('actionCount').textContent = String(tasks.filter(task =>
                !['DELIVERED', 'REJECTED', 'CANCELLED'].includes(task.status)).length);
        } else if (role === 'WAREHOUSE_OPERATOR') {
            tasks = await loadWarehouseTasks();
            await loadWarehouseDeliveryAgents();
        }
        await loadNotifications();
    }

    async function loadWarehouseTasks() {
        const [tasks, requests] = await Promise.all([
            window.tapApi.delivery.warehouseTasks(),
            window.tapApi.serviceRequests.warehouse()
        ]);
        deliveryTasks = tasks;
        warehouseServiceRequests = requests;
        renderDeliveryTasks(deliveryTasks);
        document.getElementById('taskCount').textContent =
            String(deliveryTasks.length + warehouseServiceRequests.length);
        document.getElementById('actionCount').textContent =
            String(deliveryTasks.length + warehouseServiceRequests.filter(request =>
                ['APPROVED', 'AT_WAREHOUSE', 'REPLACEMENT_READY'].includes(request.status)).length);
        return deliveryTasks;
    }

    async function loadWarehouseDeliveryAgents() {
        agents = await window.tapApi.warehouse.deliveryAgents();
        const container = document.getElementById('warehouseDeliveryAgents');
        container.innerHTML = agents.length ? agents.map(agent => `
            <article class="op-card">
                <h3>${escapeHtml(agent.fullName)}
                    <span class="status-pill ${agent.active ? '' : 'waiting'}">${agent.active ? 'Active' : 'Awaiting approval'}</span>
                </h3>
                <p>${escapeHtml(agent.email)} · ${escapeHtml(agent.warehouseName || currentUser.deliveryWarehouseName || 'Your warehouse')}</p>
                <div class="card-actions">
                    <button class="button ${agent.active ? 'button-danger' : 'button-primary'}"
                            data-agent-activation="${agent.id}" data-active="${agent.active ? 'false' : 'true'}"
                            type="button">${agent.active ? 'Deactivate' : 'Approve agent'}</button>
                </div>
            </article>
        `).join('') : '<p class="muted">No delivery agents have registered for your warehouse yet.</p>';
        renderDeliveryTasks(deliveryTasks);
    }

    async function loadOwnerTasks() {
        const items = await window.tapApi.owner.fulfillments();
        ownerTasks = items;
        renderOwnerTasks(items);
        return items;
    }

    async function loadLocations() {
        if (currentUser.role !== 'OWNER') return;
        locations = await window.tapApi.businessLocations.list();
        const select = document.getElementById('productLocation');
        select.innerHTML = '<option value="">Choose pickup location</option>' + locations.map(location =>
            `<option value="${location.id}">${escapeHtml(location.name)} · ${escapeHtml(location.city)} ${escapeHtml(location.postalCode)}</option>`
        ).join('');
        const locationList = document.getElementById('locationList');
        locationList.innerHTML = locations.length ? locations.map(location => `
            <article class="location-row">
                <div><strong>${escapeHtml(location.name)}</strong><small>${escapeHtml(location.street)}, ${escapeHtml(location.city)}, ${escapeHtml(location.state)} ${escapeHtml(location.postalCode)} · ${escapeHtml(location.contactPhone)} · ${escapeHtml(location.latitude)}, ${escapeHtml(location.longitude)}</small></div>
                <button class="button button-danger" data-delete-location="${location.id}" type="button">Remove</button>
            </article>
        `).join('') : '<p class="muted">Add an exact pickup location before publishing a product.</p>';
    }

    function setPickupLocationCoordinates(latitude, longitude) {
        const latitudeInput = document.getElementById('pickupLatitude');
        const longitudeInput = document.getElementById('pickupLongitude');
        latitudeInput.value = latitude.toFixed(6);
        longitudeInput.value = longitude.toFixed(6);
        const status = document.getElementById('pickupLocationMapStatus');
        const mapLink = document.getElementById('pickupLocationMapLink');
        ensurePickupMap(latitude, longitude);
        mapLink.href = window.TapMaps.locationUrl(latitude, longitude);
        mapLink.classList.remove('hidden');
        status.classList.remove('error');
        status.textContent = `Pickup point selected: ${latitude.toFixed(6)}, ${longitude.toFixed(6)}.`;
    }

    function ensurePickupMap(latitude, longitude) {
        const mapElement = document.getElementById('pickupLocationMapView');
        if (!mapElement) return;
        if (latitude === undefined || longitude === undefined) {
            const latitudeValue = document.getElementById('pickupLatitude').value;
            const longitudeValue = document.getElementById('pickupLongitude').value;
            latitude = latitudeValue && longitudeValue ? Number(latitudeValue) : 12.9716;
            longitude = latitudeValue && longitudeValue ? Number(longitudeValue) : 77.5946;
        }
        const zoom = latitude === 12.9716 && longitude === 77.5946 ? 12 : 16;
        mapElement.src = window.TapMaps.embedUrl(latitude, longitude, zoom);
    }

    function applyPickupGeocodedAddress(result) {
        const address = result.address || {};
        const street = [
            address.house_number,
            address.road || address.pedestrian || address.residential || address.footway
        ].filter(Boolean).join(' ')
            || address.neighbourhood || address.suburb || address.quarter || result.name || '';
        const form = document.getElementById('locationForm');
        if (street) form.elements.street.value = street.slice(0, 255);
        const city = address.city || address.town || address.village
            || address.municipality || address.county;
        if (city) form.elements.city.value = city.slice(0, 100);
        if (address.state) form.elements.state.value = address.state.slice(0, 100);
        if (address.postcode) {
            const postalCode = address.postcode.replace(/\D/g, '').slice(0, 6);
            if (postalCode) form.elements.postalCode.value = postalCode;
        }
    }

    async function searchPickupLocation() {
        const query = document.getElementById('pickupLocationSearch').value.trim();
        const button = document.getElementById('pickupLocationSearchButton');
        const resultsContainer = document.getElementById('pickupLocationSearchResults');
        const status = document.getElementById('pickupLocationMapStatus');
        if (query.length < 4) {
            status.textContent = 'Enter a fuller address, road, area, city, or PIN code to search.';
            return;
        }
        const sequence = ++pickupLocationSearchSequence;
        button.disabled = true;
        button.textContent = 'Searching…';
        resultsContainer.replaceChildren();
        status.textContent = 'Searching for pickup locations…';
        try {
            const parameters = new URLSearchParams({
                format: 'jsonv2',
                addressdetails: '1',
                limit: '5',
                countrycodes: 'in',
                q: query
            });
            const response = await fetch(
                `https://nominatim.openstreetmap.org/search?${parameters.toString()}`,
                { headers: { Accept: 'application/json' } }
            );
            if (!response.ok) {
                throw new Error(`Location search failed (HTTP ${response.status}).`);
            }
            const results = await response.json();
            if (sequence !== pickupLocationSearchSequence) return;
            if (!Array.isArray(results) || results.length === 0) {
                status.textContent = 'No matching address found. Try adding the street, locality, city, or PIN code.';
                return;
            }
            results.forEach(result => {
                const resultButton = document.createElement('button');
                resultButton.type = 'button';
                resultButton.textContent = result.display_name || result.name || 'Unnamed location';
                resultButton.addEventListener('click', () => {
                    const latitude = Number(result.lat);
                    const longitude = Number(result.lon);
                    if (!Number.isFinite(latitude) || !Number.isFinite(longitude)
                            || latitude < -90 || latitude > 90
                            || longitude < -180 || longitude > 180) {
                        status.textContent = 'This search result did not include valid coordinates. Choose another result.';
                        return;
                    }
                    applyPickupGeocodedAddress(result);
                    setPickupLocationCoordinates(latitude, longitude);
                    resultsContainer.replaceChildren();
                    status.textContent = `Selected location: ${result.display_name || 'Review the address and coordinates.'}`;
                });
                resultsContainer.append(resultButton);
            });
            status.textContent = 'Choose the search result that matches the exact pickup point:';
        } catch (error) {
            if (sequence === pickupLocationSearchSequence) {
                status.textContent = `Location search failed: ${error.message}`;
            }
        } finally {
            if (sequence === pickupLocationSearchSequence) {
                button.disabled = false;
                button.textContent = 'Search';
            }
        }
    }

    async function loadProducts() {
        if (currentUser.role !== 'OWNER') return;
        const page = await window.tapApi.products.owned(0, 100);
        const products = pageContent(page);
        document.getElementById('ownerProducts').innerHTML = products.length ? products.map(product => `
            <article class="product-row">
                <img src="${escapeHtml(product.imageUrl || '')}" alt="">
                <div class="product-info">
                    <strong>${escapeHtml(product.name)}</strong>
                    <small>${escapeHtml(product.categoryName)} · ₹${Number(product.price).toLocaleString('en-IN')} · Stock ${Number(product.stock)}</small>
                </div>
                <button class="button button-light" data-edit-product="${product.id}" type="button">Edit</button>
                <button class="button button-danger" data-delete-product="${product.id}" type="button">Remove</button>
            </article>
        `).join('') : '<p class="muted">No products yet. Add one above to publish it in the storefront.</p>';
    }

    async function loadCategories() {
        const select = document.getElementById('productCategory');
        const status = document.getElementById('categoryStatus');
        const retry = document.getElementById('retryCategories');
        select.disabled = true;
        select.replaceChildren(new Option('Loading categories...', ''));
        status.textContent = 'Loading available categories.';
        status.classList.remove('error');
        retry.classList.add('hidden');

        try {
            const result = await window.tapApi.categories.list();
            categories = pageContent(result)
                .filter(category => category && Number.isFinite(Number(category.id)) && category.name)
                .sort((left, right) => left.name.localeCompare(right.name));
            select.replaceChildren(new Option(
                categories.length ? 'Choose a category' : 'No categories available',
                ''
            ));
            categories.forEach(category => {
                select.add(new Option(category.name, String(category.id)));
            });
            select.disabled = categories.length === 0;
            status.textContent = categories.length
                ? `${categories.length} categories available.`
                : 'No categories are configured yet. Ask an administrator to check the catalog setup.';
            if (!categories.length) status.classList.add('error');
        } catch (error) {
            categories = [];
            select.replaceChildren(new Option('Categories could not be loaded', ''));
            status.textContent = 'Could not load categories. Check the connection and try again.';
            status.classList.add('error');
            retry.classList.remove('hidden');
            throw error;
        }
    }

    function renderOwnerTasks(tasks) {
        const container = document.getElementById('ownerFulfillments');
        if (!tasks.length) {
            container.innerHTML = '<p class="muted">No orders are waiting for your action.</p>';
            return;
        }
        const pickupLocations = locations.filter(location =>
            location.verifiedByOwner
            && location.latitude != null
            && location.longitude != null
            && Number.isFinite(Number(location.latitude))
            && Number.isFinite(Number(location.longitude))
            && Number(location.latitude) >= -90 && Number(location.latitude) <= 90
            && Number(location.longitude) >= -180 && Number(location.longitude) <= 180);
        container.innerHTML = tasks.map(task => `
            <article class="op-card">
                <h3>Order ${escapeHtml(task.orderNumber)} <span class="status-pill ${task.status === 'AWAITING_OWNER_CONFIRMATION' ? 'waiting' : ''}">${escapeHtml(task.status.replaceAll('_', ' '))}</span></h3>
                <p>${(task.items || []).map(item => `${escapeHtml(item.productName)} × ${Number(item.quantity)}`).join(' · ')}</p>
                <p>Pickup: ${task.pickupStreet ? `${escapeHtml(task.pickupName || 'Business location')} · ${escapeHtml(task.pickupStreet)} ${escapeHtml(task.pickupCity || '')} ${escapeHtml(task.pickupPostalCode || '')}` : 'A verified business pickup location is required.'}</p>
                <p>Expected delivery: ${task.expectedDeliveryDate ? formatDate(task.expectedDeliveryDate) : 'Set after confirmation'}</p>
                ${currentUser.role === 'OWNER' && task.status === 'AWAITING_OWNER_CONFIRMATION'
                    && !hasValidPickupLocation(task) ? `
                    <div class="pickup-location-selection">
                        ${pickupLocations.length ? `
                            <label for="pickupLocation-${task.id}">Choose a verified pickup address
                                <select id="pickupLocation-${task.id}" data-pickup-location-select="${task.id}">
                                    <option value="">Select pickup location</option>
                                    ${pickupLocations.map(location => `<option value="${location.id}">${escapeHtml(location.name)} · ${escapeHtml(location.street)}, ${escapeHtml(location.city)} ${escapeHtml(location.postalCode)}</option>`).join('')}
                                </select>
                            </label>
                            <button class="button button-light" data-set-pickup-location="${task.id}" type="button">Use this pickup location</button>
                        ` : `<p class="muted">Add and verify a pickup address with map coordinates in <a href="#locations">Business pickup locations</a> before confirming.</p>`}
                    </div>
                ` : ''}
                ${(task.serviceRequests || []).map(request => `
                    <section class="service-request-card">
                        <h4>${escapeHtml(request.type)} · ${escapeHtml(request.productName)}</h4>
                        <p>Status: <strong>${escapeHtml(String(request.status).replaceAll('_', ' '))}</strong></p>
                        <p>Reason: ${escapeHtml(String(request.reason).replaceAll('_', ' '))}</p>
                        <p>${escapeHtml(request.details)}</p>
                        <p><button class="button button-light" data-service-evidence-url="${escapeHtml(request.photoUrl)}" type="button">View customer photo</button>
                           <button class="button button-light" data-service-evidence-url="${escapeHtml(request.videoUrl)}" type="button">View customer video</button></p>
                        ${request.status === 'REQUESTED' ? `
                            <div class="card-actions">
                                <button class="button button-primary" data-service-review="${request.id}" data-decision="APPROVE" type="button">Approve request</button>
                                <button class="button button-danger" data-service-review="${request.id}" data-decision="REJECT" type="button">Decline request</button>
                            </div>
                        ` : ''}
                        ${request.status === 'RECEIVED_BY_SELLER' ? `
                            <label for="serviceReceiptNote-${request.id}">Receipt note (optional)
                                <input id="serviceReceiptNote-${request.id}" maxlength="1000">
                            </label>
                            <button class="button button-primary" data-service-receipt="${request.id}" type="button">Confirm old item received</button>
                        ` : ''}
                        ${request.reviewNotes ? `<p>Owner note: ${escapeHtml(request.reviewNotes)}</p>` : ''}
                    </section>
                `).join('')}
                <div class="card-actions">
                    ${task.status === 'AWAITING_OWNER_CONFIRMATION' ? `
                        <button class="button button-primary" data-confirm-fulfillment="${task.id}" type="button"
                            ${!hasValidPickupLocation(task) ? 'disabled title="Set a verified pickup location first"' : ''}>Confirm order</button>
                        <button class="button button-danger" data-reject-fulfillment="${task.id}" type="button">Reject order</button>
                    ` : ''}
                    ${task.status === 'CONFIRMED' ? `<button class="button button-light" data-owner-qr="${task.id}" type="button">Show pickup QR</button>` : ''}
                    ${currentUser.role === 'ADMIN' && task.status === 'CONFIRMED' ? renderAgentAssignment(task.id) : ''}
                </div>
                ${task.status === 'CONFIRMED' ? `
                    <div class="owner-pickup-qr ${ownerQrTokens.has(String(task.id)) ? '' : 'hidden'}" data-owner-qr-panel="${task.id}" aria-live="polite">
                        <div class="owner-pickup-qr-header">
                            <span>Pickup QR</span>
                            <button class="owner-pickup-qr-download" data-download-owner-qr="${task.id}" type="button" aria-label="Download pickup QR for order ${escapeHtml(task.orderNumber)}" title="Download printable QR code">
                                <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
                                    <path d="M12 3v11m0 0 4-4m-4 4-4-4M5 15v4a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-4" />
                                </svg>
                            </button>
                        </div>
                        <canvas data-owner-qr-canvas="${task.id}" width="440" height="440" aria-label="Unique pickup QR for order ${escapeHtml(task.orderNumber)}"></canvas>
                        <p>Unique pickup QR for order ${escapeHtml(task.orderNumber)}. Scan this code to record the owner pickup.</p>
                    </div>
                ` : ''}
            </article>
        `).join('');
        tasks.forEach(task => {
            const token = ownerQrTokens.get(String(task.id));
            const canvas = container.querySelector(`[data-owner-qr-canvas="${CSS.escape(String(task.id))}"]`);
            if (!token || !canvas) return;
            try {
                drawQr(canvas, token);
            } catch (error) {
                setFeedback(error.message, true);
            }
        });
    }

    function renderAgentAssignment(id) {
        if (!agents.length) return '<span class="muted">No delivery-agent accounts are configured.</span>';
        return `<select data-agent-select="${id}" aria-label="Select delivery agent">
            <option value="">Assign delivery agent</option>
            ${agents.map(agent => `<option value="${agent.id}">${escapeHtml(agent.fullName)} · ${escapeHtml(agent.email)}</option>`).join('')}
        </select><button class="button button-dark" data-assign-agent="${id}" type="button">Assign</button>`;
    }

    function usesSingleWarehouse(task) {
        return task.warehouseLocationId !== null && task.warehouseLocationId !== undefined
            && task.destinationWarehouseLocationId !== null
            && task.destinationWarehouseLocationId !== undefined
            && String(task.warehouseLocationId) === String(task.destinationWarehouseLocationId);
    }

    function taskAction(task) {
        if (currentUser.role === 'WAREHOUSE_OPERATOR') {
            if (task.status === 'PACKED_READY' && usesSingleWarehouse(task)) {
                return 'STORE_RECEIVED';
            }
            return null;
        }
        if (currentUser.role === 'DELIVERY_AGENT') {
            const firstLeg = String(task.deliveryAgentId) === String(currentUser.id);
            const transferLeg = String(task.transferDeliveryAgentId) === String(currentUser.id);
            const finalLeg = String(task.finalDeliveryAgentId) === String(currentUser.id);
            if (finalLeg) {
                if (task.status === 'AT_DESTINATION_STORE') return 'OUT_FOR_DELIVERY';
                if (task.status === 'OUT_FOR_DELIVERY') return 'DELIVERED';
            }
            if (transferLeg && task.status === 'PACKED_READY') return 'OUT_FOR_DELIVERY_TO_STORE';
            if (firstLeg) {
                if (task.status === 'CONFIRMED') return 'PICKUP_FROM_OWNER';
            }
            return null;
        }
        if (task.status === 'CONFIRMED') return 'PICKUP_FROM_OWNER';
        if (task.status === 'PICKED_UP_FROM_OWNER') return 'WAREHOUSE_RECEIVED';
        if (task.status === 'PACKED_READY') return 'OUT_FOR_DELIVERY_TO_STORE';
        if (task.status === 'IN_TRANSIT_TO_STORE') return 'STORE_RECEIVED';
        if (task.status === 'AT_DESTINATION_STORE') return 'OUT_FOR_DELIVERY';
        if (task.status === 'OUT_FOR_DELIVERY') return 'DELIVERED';
        return null;
    }

    function stageLabel(stage) {
        return {
            PICKUP_FROM_OWNER: 'Picked Up from Owner',
            WAREHOUSE_RECEIVED: 'Received at origin warehouse',
            OUT_FOR_DELIVERY_TO_STORE: 'Dispatched to destination warehouse',
            STORE_RECEIVED: 'Received at destination warehouse',
            OUT_FOR_DELIVERY: 'Out for Delivery',
            DELIVERED: 'Delivered'
        }[stage] || String(stage || '').replaceAll('_', ' ');
    }

    function taskRouteTarget(task) {
        let label;
        let latitude;
        let longitude;
        if (currentUser.role === 'DELIVERY_AGENT') {
            if (String(task.deliveryAgentId) === String(currentUser.id)
                    && task.status === 'CONFIRMED') {
                label = 'Route to seller pickup';
                latitude = task.pickupLatitude;
                longitude = task.pickupLongitude;
            } else if (String(task.deliveryAgentId) === String(currentUser.id)
                    && task.status === 'PICKED_UP_FROM_OWNER') {
                label = 'Route to origin warehouse';
                latitude = task.warehouseLatitude;
                longitude = task.warehouseLongitude;
            } else if (String(task.transferDeliveryAgentId) === String(currentUser.id)
                    && task.status === 'PACKED_READY') {
                label = 'Route to origin warehouse';
                latitude = task.warehouseLatitude;
                longitude = task.warehouseLongitude;
            } else if (String(task.transferDeliveryAgentId) === String(currentUser.id)
                    && task.status === 'IN_TRANSIT_TO_STORE') {
                label = 'Route to destination warehouse';
                latitude = task.destinationWarehouseLatitude;
                longitude = task.destinationWarehouseLongitude;
            } else if (String(task.finalDeliveryAgentId) === String(currentUser.id)
                    && task.status === 'AT_DESTINATION_STORE') {
                label = 'Route to destination warehouse';
                latitude = task.destinationWarehouseLatitude;
                longitude = task.destinationWarehouseLongitude;
            } else if (String(task.finalDeliveryAgentId) === String(currentUser.id)
                    && task.status === 'OUT_FOR_DELIVERY') {
                label = 'Route to customer';
                latitude = task.customerLatitude;
                longitude = task.customerLongitude;
            }
        } else if (currentUser.role === 'WAREHOUSE_OPERATOR') {
            if (task.status === 'CONFIRMED') {
                label = 'Route to seller pickup';
                latitude = task.pickupLatitude;
                longitude = task.pickupLongitude;
            } else if (task.status === 'PACKED_READY' && !usesSingleWarehouse(task)) {
                label = 'Route to destination warehouse';
                latitude = task.destinationWarehouseLatitude;
                longitude = task.destinationWarehouseLongitude;
            }
        }
        if (!label || !window.TapMaps.validCoordinates(latitude, longitude)) return null;
        return {
            label,
            url: window.TapMaps.directionsUrl(null, { latitude, longitude })
        };
    }

    function renderDeliveryTasks(tasks) {
        const container = document.getElementById('deliveryTasks');
        if (!tasks.length && !warehouseServiceRequests.length) {
            container.innerHTML = currentUser.role === 'WAREHOUSE_OPERATOR'
                ? `<article class="op-card warehouse-queue-empty">
                    <h3>No packages have reached your warehouse yet</h3>
                    <p>Warehouse work becomes available after an owner confirms an order and the assigned delivery agent scans its pickup QR at the owner’s location. The package will then appear here for warehouse receiving.</p>
                    ${currentUser.warehouseActive ? '' : '<p class="status-pill waiting">Waiting for administrator approval</p>'}
                </article>`
                : '<p class="muted">No delivery tasks are currently assigned or available.</p>';
            return;
        }
        const taskCards = tasks.map(task => {
            const stage = taskAction(task);
            const route = taskRouteTarget(task);
            const transferAgents = agents.filter(agent => agent.active
                && String(agent.id) !== String(task.deliveryAgentId)
                && String(agent.id) !== String(task.finalDeliveryAgentId));
            const finalAgents = agents.filter(agent => agent.active
                && String(agent.id) !== String(task.deliveryAgentId)
                && String(agent.id) !== String(task.transferDeliveryAgentId));
            const statusClass = ['DELIVERED', 'REJECTED', 'CANCELLED'].includes(task.status)
                ? 'closed' : task.status === 'DELIVERY_CONFIRMATION_PENDING' ? 'waiting' : '';
            return `<article class="op-card">
                <h3>Order ${escapeHtml(task.orderNumber)} <span class="status-pill ${statusClass}">${escapeHtml(task.status.replaceAll('_', ' '))}</span></h3>
                <p>${(task.items || []).map(item => `${escapeHtml(item.productName)} × ${Number(item.quantity)}`).join(' · ')}</p>
                <p>Pickup: ${escapeHtml(task.pickupStreet || '—')}, ${escapeHtml(task.pickupCity || '')} ${escapeHtml(task.pickupPostalCode || '')}</p>
                <p>Origin warehouse: ${escapeHtml(task.warehouseName || '—')} · ${escapeHtml(task.warehouseCity || '')}</p>
                <p>Destination warehouse: ${escapeHtml(task.destinationWarehouseName || '—')} · ${escapeHtml(task.destinationWarehouseCity || '')}</p>
                ${task.customerStreet ? `<p>Deliver to: ${escapeHtml(task.customerName || '')}, ${escapeHtml(task.customerStreet)}, ${escapeHtml(task.customerCity || '')} ${escapeHtml(task.customerPostalCode || '')}</p>` : ''}
                ${task.customerPhone ? `<p>Customer mobile: <a href="tel:${escapeHtml(task.customerPhone)}">${escapeHtml(task.customerPhone)}</a></p>` : ''}
                <p>Expected: ${task.expectedDeliveryDate ? formatDate(task.expectedDeliveryDate) : 'Awaiting owner confirmation'}</p>
                ${stage ? `<p>Next step: <strong>${escapeHtml(stageLabel(stage))}</strong></p>` : ''}
                ${currentUser.role === 'WAREHOUSE_OPERATOR' && task.status === 'PICKED_UP_FROM_OWNER'
                    ? '<p class="muted">Waiting for the pickup agent to scan this warehouse’s arrival QR.</p>' : ''}
                ${currentUser.role === 'WAREHOUSE_OPERATOR' && task.status === 'IN_TRANSIT_TO_STORE'
                    ? '<p class="muted">The transfer agent is on the way. They will scan this destination warehouse’s arrival QR before final delivery can be assigned.</p>' : ''}
                ${route ? `<p><a class="button button-light" href="${escapeHtml(route.url)}" target="_blank" rel="noopener noreferrer">${escapeHtml(route.label)} in Google Maps</a></p>` : ''}
                ${currentUser.role === 'WAREHOUSE_OPERATOR' && task.status === 'PACKED_READY'
                        && !usesSingleWarehouse(task) ? `
                    <p>The package is at the origin warehouse. Assign a different active agent to carry it to ${escapeHtml(task.destinationWarehouseName || 'the destination warehouse')}.</p>
                    ${task.transferDeliveryAgentId
                        ? `<p>${escapeHtml(task.transferDeliveryAgentName || 'Transfer agent')} · ${task.transferAgentAcceptedAt ? 'accepted' : 'awaiting acceptance'}</p>`
                        : ''}
                    ${transferAgents.length ? `
                        <label for="transferAgent-${escapeHtml(task.id)}">Warehouse transfer agent
                            <select id="transferAgent-${escapeHtml(task.id)}" data-transfer-agent-select="${escapeHtml(task.id)}" ${task.transferAgentAcceptedAt ? 'disabled' : ''}>
                                <option value="">Choose an active agent</option>
                                ${transferAgents.map(agent => `<option value="${escapeHtml(agent.id)}" ${String(agent.id) === String(task.transferDeliveryAgentId) ? 'selected' : ''}>${escapeHtml(agent.fullName)} · ${escapeHtml(agent.email)}</option>`).join('')}
                            </select>
                        </label>
                        <button class="button button-primary" data-assign-transfer-agent="${escapeHtml(task.id)}" type="button" ${task.transferAgentAcceptedAt ? 'disabled' : ''}>${task.transferDeliveryAgentId ? 'Reassign transfer agent' : 'Assign transfer agent'}</button>
                    ` : `<p class="muted">A different active agent is required for this handoff. Register another delivery agent for this warehouse or activate an existing agent in the Delivery agents panel.</p>
                         <a class="button button-light" href="DeliveryAgentRegister.html">Register a delivery agent</a>`}
                ` : ''}
                ${currentUser.role === 'WAREHOUSE_OPERATOR' && task.status === 'AT_DESTINATION_STORE' ? `
                    <p>The package has arrived. Choose a different active agent registered at this destination warehouse for final delivery.</p>
                    ${task.finalDeliveryAgentId
                        ? `<p>${escapeHtml(task.finalDeliveryAgentName || 'Final-delivery agent')} · ${task.finalAgentAcceptedAt ? 'accepted' : 'awaiting acceptance'}</p>`
                        : ''}
                    ${finalAgents.length ? `
                        <label for="finalAgent-${escapeHtml(task.id)}">Final-delivery agent
                            <select id="finalAgent-${escapeHtml(task.id)}" data-final-agent-select="${escapeHtml(task.id)}" ${task.finalAgentAcceptedAt ? 'disabled' : ''}>
                                <option value="">Choose an active agent</option>
                                ${finalAgents.map(agent => `<option value="${escapeHtml(agent.id)}" ${String(agent.id) === String(task.finalDeliveryAgentId) ? 'selected' : ''}>${escapeHtml(agent.fullName)} · ${escapeHtml(agent.email)}</option>`).join('')}
                            </select>
                        </label>
                        <button class="button button-primary" data-assign-final-agent="${escapeHtml(task.id)}" type="button" ${task.finalAgentAcceptedAt ? 'disabled' : ''}>${task.finalDeliveryAgentId ? 'Reassign final agent' : 'Assign final agent'}</button>
                    ` : `<p class="muted">A different active agent is required for this handoff. Register another delivery agent for this warehouse or activate an existing agent in the Delivery agents panel.</p>
                         <a class="button button-light" href="DeliveryAgentRegister.html">Register a delivery agent</a>`}
                ` : ''}
                <div class="card-actions">
                    ${stage ? `<button class="button button-primary" data-open-scanner type="button">Scan package QR · ${escapeHtml(stageLabel(stage))}</button>` : ''}
                    <button class="button button-light" data-history="${task.id}" type="button">History</button>
                </div>
            </article>`;
        }).join('');
        const requestCards = currentUser.role === 'WAREHOUSE_OPERATOR'
            ? warehouseServiceRequests.map(request => {
                const assignment = request.status === 'APPROVED' ? 'pickup'
                    : request.status === 'AT_WAREHOUSE' ? 'seller'
                    : request.status === 'REPLACEMENT_READY' ? 'replacement' : null;
                const target = request.status === 'APPROVED' || request.status === 'REPLACEMENT_READY'
                    ? { latitude: request.customerLatitude, longitude: request.customerLongitude }
                    : { latitude: request.sellerLatitude, longitude: request.sellerLongitude };
                const routeUrl = assignment && window.TapMaps.validCoordinates(
                    target.latitude, target.longitude)
                    ? window.TapMaps.directionsUrl(null, target) : null;
                const routeLabel = request.status === 'APPROVED' || request.status === 'REPLACEMENT_READY'
                    ? 'Route to customer' : 'Route to seller';
                return `<article class="op-card service-request-card">
                    <h3>${escapeHtml(request.type)} · ${escapeHtml(request.productName)}
                        <span class="status-pill waiting">${escapeHtml(String(request.status).replaceAll('_', ' '))}</span></h3>
                    <p>Order ${escapeHtml(request.orderNumber)} · ${escapeHtml(String(request.reason).replaceAll('_', ' '))}</p>
                    <p>${escapeHtml(request.details)}</p>
                    <p><button class="button button-light" data-service-evidence-url="${escapeHtml(request.photoUrl)}" type="button">Customer photo</button>
                       <button class="button button-light" data-service-evidence-url="${escapeHtml(request.videoUrl)}" type="button">Customer video</button></p>
                    ${request.deliveryAgentName ? `<p>Assigned agent: ${escapeHtml(request.deliveryAgentName)}</p>` : ''}
                    ${routeUrl ? `<p><a class="button button-light" href="${escapeHtml(routeUrl)}" target="_blank" rel="noopener noreferrer">${routeLabel} in Google Maps</a></p>` : ''}
                    ${assignment ? `
                        ${agents.some(agent => agent.active && String(agent.warehouseId) === String(request.warehouseId)) ? `
                            <label for="serviceAgent-${request.id}">${assignment === 'pickup'
                                ? 'Assign customer pickup agent'
                                : assignment === 'seller' ? 'Assign return-to-seller agent'
                                    : 'Assign replacement delivery agent'}
                                <select id="serviceAgent-${request.id}" data-service-agent-select="${request.id}">
                                    <option value="">Choose an active warehouse agent</option>
                                    ${agents.filter(agent => agent.active
                                        && String(agent.warehouseId) === String(request.warehouseId))
                                        .map(agent => `<option value="${escapeHtml(agent.id)}">${escapeHtml(agent.fullName)} · ${escapeHtml(agent.email)}</option>`).join('')}
                                </select>
                            </label>
                            <button class="button button-primary" data-service-assignment="${assignment}"
                                    data-service-id="${request.id}" type="button">Assign agent</button>
                        ` : '<p class="muted">No active delivery agent is registered at this warehouse.</p><a class="button button-light" href="DeliveryAgentRegister.html">Register a delivery agent</a>'}
                    ` : request.status === 'REQUESTED'
                        ? '<p class="muted">Waiting for the product owner to approve or decline this request.</p>'
                        : request.status === 'RECEIVED_BY_SELLER'
                            ? '<p class="muted">Waiting for the owner to confirm receipt of the returned item.</p>'
                            : request.status === 'COMPLETED'
                                ? '<p class="muted">This request is complete.</p>'
                                : `<p class="muted">${escapeHtml(String(request.status).replaceAll('_', ' '))}</p>`}
                </article>`;
            }).join('')
            : '';
        container.innerHTML = taskCards + requestCards;
    }

    async function loadNotifications() {
        if (!currentUser) return;
        const summary = await window.tapApi.notifications.list(30);
        const unreadCount = Number(summary.unreadCount || 0);
        document.getElementById('unreadCount').textContent = unreadCount.toLocaleString();
        const badge = document.getElementById('navUnreadCount');
        badge.textContent = unreadCount > 99 ? '99+' : String(unreadCount);
        badge.classList.toggle('hidden', unreadCount === 0);
        document.getElementById('toggleNotifications').setAttribute(
            'aria-label', unreadCount ? `Notifications, ${unreadCount} unread` : 'Notifications');
        const list = summary.notifications || [];
        if (notificationsInitialized && currentUser.role === 'WAREHOUSE_OPERATOR') {
            list.filter(notification => notification.type === 'DELIVERY_COMPLETED'
                && !knownNotificationIds.has(String(notification.id)))
                .forEach(notification => showTaskToast(
                    'Great work!',
                    notification.message || 'The package was delivered successfully.'));
        }
        knownNotificationIds = new Set(list.map(notification => String(notification.id)));
        notificationsInitialized = true;
        document.getElementById('notificationList').innerHTML = list.length
            ? list.map(notification => `
                <article class="notification-row ${notification.read ? '' : 'unread'}">
                    <div class="notification-info">
                        <h3>${escapeHtml(notification.title)}</h3>
                        <p>${escapeHtml(notification.message)}</p>
                        <small>${formatDateTime(notification.createdAt)}</small>
                    </div>
                    <div class="notification-actions">
                        ${notification.read ? '' : `<button class="text-button" data-read-notification="${escapeHtml(notification.id)}" type="button">Mark read</button>`}
                    </div>
                </article>
            `).join('')
            : '<p class="muted">You are all caught up.</p>';
    }

    function drawQr(canvas, token) {
        const context = canvas.getContext('2d');
        if (!context) {
            throw new Error('This browser cannot display the QR code.');
        }
        context.clearRect(0, 0, canvas.width, canvas.height);
        if (typeof window.qrcode !== 'function') {
            throw new Error('QR generator failed to load. Check your internet connection and reload Operations Studio.');
        }
        const qr = window.qrcode(0, 'M');
        qr.addData(String(token));
        qr.make();
        const moduleCount = qr.getModuleCount();
        const cellSize = Math.floor(canvas.width / (moduleCount + 8));
        const offset = Math.floor((canvas.width - moduleCount * cellSize) / 2);
        context.fillStyle = '#fff';
        context.fillRect(0, 0, canvas.width, canvas.height);
        context.fillStyle = '#111';
        for (let row = 0; row < moduleCount; row++) {
            for (let column = 0; column < moduleCount; column++) {
                if (qr.isDark(row, column)) {
                    context.fillRect(
                        offset + column * cellSize,
                        offset + row * cellSize,
                        cellSize,
                        cellSize
                    );
                }
            }
        }
    }

    async function handleScan(token) {
        if (scanBusy) return;
        scanBusy = true;
        stageForm.classList.add('hidden');
        scanResult.classList.add('hidden');
        scannerMessage.textContent = 'Validating this QR token with the server…';
        try {
            const result = await window.tapApi.delivery.scan(token.trim());
            activeScan = { ...result, token: token.trim() };
            const task = result.fulfillment;
            scanResult.innerHTML = `
                <article class="op-card">
                    <h3>Order ${escapeHtml(task.orderNumber)}</h3>
                    <p>Stage to record: <strong>${escapeHtml(stageLabel(result.stage))}</strong></p>
                    <p>${(task.items || []).map(item => `${escapeHtml(item.productName)} × ${Number(item.quantity)}`).join(' · ')}</p>
                    ${task.pickupStreet ? `<p>Pickup: ${escapeHtml(task.pickupStreet)}, ${escapeHtml(task.pickupCity || '')} ${escapeHtml(task.pickupPostalCode || '')}</p>` : ''}
                    ${task.customerStreet ? `<p>Delivery: ${escapeHtml(task.customerName || '')}, ${escapeHtml(task.customerStreet)}, ${escapeHtml(task.customerCity || '')} ${escapeHtml(task.customerPostalCode || '')}</p>` : ''}
                    ${task.customerPhone ? `<p>Customer mobile: <a href="tel:${escapeHtml(task.customerPhone)}">${escapeHtml(task.customerPhone)}</a></p>` : ''}
                </article>`;
            scanResult.classList.remove('hidden');
            stageFields.innerHTML = `
                <label for="stageAction">Stage to record</label>
                <select id="stageAction" required>
                    <option value="${escapeHtml(result.stage)}" selected>${escapeHtml(stageLabel(result.stage))}</option>
                </select>
                <label for="stageNotes">Notes (optional)</label>
                <textarea id="stageNotes" rows="2" maxlength="1000"></textarea>`;
            scannerMessage.textContent = result.stage === 'DELIVERED'
                ? 'Confirm the delivery handoff here. The customer will then receive a request to confirm receipt.'
                : 'Only the next stage for this order is available. Submit it to update tracking and notify the customer.';
            document.getElementById('stageSubmit').textContent = result.stage === 'DELIVERED'
                ? 'Request customer confirmation' : `Submit ${stageLabel(result.stage)}`;
            stageForm.classList.remove('hidden');
            document.getElementById('manualQrToken').value = '';
        } catch (error) {
            activeScan = null;
            scannerMessage.textContent = error.message;
            setFeedback(error.message, true);
        } finally {
            scanBusy = false;
        }
    }

    async function startScanner() {
        const start = document.getElementById('startScanner');
        const stop = document.getElementById('stopScanner');
        if (!window.isSecureContext || !navigator.mediaDevices?.getUserMedia) {
            scannerMessage.textContent = 'Camera scanning needs HTTPS or localhost. Use manual token entry on this connection.';
            return;
        }
        if (!window.ZXingBrowser?.BrowserMultiFormatReader) {
            scannerMessage.textContent = 'Camera scanner library is unavailable. Use manual token entry.';
            document.getElementById('retryScanner').hidden = false;
            return;
        }
        start.disabled = true;
        scannerMessage.textContent = 'Requesting camera permission…';
        scannerVideo.classList.remove('hidden');
        try {
            scannerReader = new window.ZXingBrowser.BrowserMultiFormatReader();
            scannerControls = await scannerReader.decodeFromConstraints(
                { video: { facingMode: { ideal: 'environment' } } },
                scannerVideo,
                (result, error) => {
                    if (result && !scanBusy) {
                        const text = result.getText();
                        stopScanner();
                        handleScan(text);
                    } else if (error && error.name !== 'NotFoundException') {
                        scannerMessage.textContent = 'Unable to read a QR code. Adjust the frame or enter the token manually.';
                    }
                });
            stop.disabled = false;
            scannerMessage.textContent = 'Point the rear camera at the order QR code.';
        } catch (error) {
            scannerMessage.textContent = error.message || 'Camera permission was denied or the camera is unavailable.';
            scannerVideo.classList.add('hidden');
            document.getElementById('retryScanner').hidden = false;
            start.disabled = false;
        }
    }

    function stopScanner() {
        if (scannerControls?.stop) scannerControls.stop();
        scannerControls = null;
        if (scannerReader?.reset) scannerReader.reset();
        if (scannerVideo.srcObject) {
            scannerVideo.srcObject.getTracks().forEach(track => track.stop());
            scannerVideo.srcObject = null;
        }
        scannerVideo.classList.add('hidden');
        document.getElementById('startScanner').disabled = false;
        document.getElementById('stopScanner').disabled = true;
    }

    function formatDate(value) {
        if (!value) return '—';
        const date = new Date(value);
        return Number.isNaN(date.getTime()) ? '—' : date.toLocaleDateString('en-IN', {
            day: '2-digit', month: 'short', year: 'numeric'
        });
    }

    function formatDateTime(value) {
        if (!value) return '—';
        const date = new Date(value);
        return Number.isNaN(date.getTime()) ? '—' : date.toLocaleString('en-IN');
    }

    async function loadAdminAccess() {
        if (currentUser.role !== 'ADMIN') return;
        adminUsers = await window.tapApi.admin.users();
        agents = adminUsers.filter(user => user.role === 'DELIVERY_AGENT');
        const owners = adminUsers.filter(user => user.role === 'OWNER');
        const ownerSelect = document.getElementById('staffUser');
        ownerSelect.innerHTML = '<option value="">Choose account</option>' + adminUsers
            .filter(user => user.role !== 'ADMIN')
            .map(user => `<option value="${user.id}">${escapeHtml(user.fullName)} · ${escapeHtml(user.email)} (${escapeHtml(user.role)})</option>`)
            .join('');

        const products = await window.tapApi.products.listAll(100);
        document.getElementById('adminProductAssignments').innerHTML = products.length
            ? products.map(product => {
                const ownerOptions = owners.map(owner => `
                    <option value="${owner.id}">
                        ${escapeHtml(owner.fullName)}
                    </option>`).join('');
                return `<article class="op-card admin-product-row" data-admin-product="${product.id}">
                    <h3>${escapeHtml(product.name)}</h3>
                    <p>${product.businessLocationId ? 'This product has an owner pickup location assigned.' : 'This product is currently managed by 9Tails Store.'}</p>
                    <div class="form-grid">
                        <label>Product owner<select data-owner-select required>
                            <option value="">Choose an owner</option>${ownerOptions}
                        </select></label>
                        <label>Pickup location<select data-location-select required>
                            <option value="">Choose an owner first</option>
                        </select></label>
                    </div>
                    <div class="card-actions">
                        <button class="button button-light" data-save-assignment type="button">Save ownership</button>
                    </div>
                </article>`;
            }).join('')
            : '<p class="muted">No active catalog products found.</p>';
    }

    async function uploadSelectedImages(files) {
        const selected = Array.from(files);
        if (!selected.length) return [];
        if (selected.length > 8) throw new Error('Choose no more than eight images.');
        const results = [];
        for (const file of selected) {
            if (file.size > 5 * 1024 * 1024) throw new Error(`${file.name} is larger than 5 MB.`);
            results.push(await window.tapApi.products.uploadImage(file));
        }
        return results.map(result => result.url);
    }

    function renderProductImagePreviews(images) {
        const grid = document.getElementById('imagePreviewGrid');
        const hint = document.getElementById('imagePreviewHint');
        productImagePreviewUrls.forEach(url => URL.revokeObjectURL(url));
        productImagePreviewUrls = images
            .map(image => image.url)
            .filter(url => url.startsWith('blob:'));
        grid.replaceChildren();

        images.forEach(({ url, name }, index) => {
            const card = document.createElement('figure');
            card.className = 'image-preview-card';
            const image = document.createElement('img');
            image.src = url;
            image.alt = `Product image ${index + 1}: ${name}`;
            image.loading = 'eager';
            image.addEventListener('error', () => {
                const error = document.createElement('span');
                error.className = 'preview-error';
                error.textContent = 'Preview could not be loaded.';
                card.append(error);
            }, { once: true });
            const caption = document.createElement('figcaption');
            caption.textContent = name;
            caption.title = name;
            card.append(image, caption);
            grid.append(card);
        });

        hint.textContent = images.length
            ? `${images.length} image${images.length === 1 ? '' : 's'} ready to preview. The full image is shown without cropping.`
            : 'Selected images will appear here before you publish.';
    }

    document.getElementById('productImages').addEventListener('change', event => {
        const files = Array.from(event.currentTarget.files);
        if (!files.length) {
            renderProductImagePreviews(existingProductImages.map((url, index) => ({
                url, name: `Saved image ${index + 1}`
            })));
            return;
        }
        const invalidFile = files.find(file =>
            !['image/jpeg', 'image/png', 'image/webp'].includes(file.type)
            || file.size > 5 * 1024 * 1024);
        if (files.length > 8 || invalidFile) {
            event.currentTarget.value = '';
            renderProductImagePreviews(existingProductImages.map((url, index) => ({
                url, name: `Saved image ${index + 1}`
            })));
            setFeedback(files.length > 8
                ? 'Choose no more than eight images.'
                : `${invalidFile.name} must be a JPEG, PNG, or WebP image no larger than 5 MB.`, true);
            return;
        }

        renderProductImagePreviews(files.map((file, index) => ({
            url: URL.createObjectURL(file),
            name: file.name
        })));
    });

    loginForm.addEventListener('submit', async event => {
        event.preventDefault();
        loginFeedback.textContent = '';
        const button = loginForm.querySelector('button[type="submit"]');
        button.disabled = true;
        try {
            const email = document.getElementById('loginEmail').value.trim().toLowerCase();
            const password = document.getElementById('loginPassword').value;
            const result = await window.tapApi.auth.operationsLogin(email, password);
            window.tapApi.setToken(result.token);
            await boot();
        } catch (error) {
            loginFeedback.textContent = error.message;
        } finally {
            button.disabled = false;
        }
    });

    document.getElementById('showOperationsSignIn').addEventListener('click', () => {
        loginForm.classList.remove('hidden');
        ownerRegistrationForm.classList.add('hidden');
        loginFeedback.textContent = '';
        ownerRegistrationFeedback.textContent = '';
        document.getElementById('showOperationsSignIn').classList.add('active');
        document.getElementById('showOperationsSignIn').setAttribute('aria-selected', 'true');
        document.getElementById('showOwnerRegistration').classList.remove('active');
        document.getElementById('showOwnerRegistration').setAttribute('aria-selected', 'false');
    });

    document.getElementById('showOwnerRegistration').addEventListener('click', () => {
        loginForm.classList.add('hidden');
        ownerRegistrationForm.classList.remove('hidden');
        loginFeedback.textContent = '';
        ownerRegistrationFeedback.textContent = '';
        document.getElementById('showOwnerRegistration').classList.add('active');
        document.getElementById('showOwnerRegistration').setAttribute('aria-selected', 'true');
        document.getElementById('showOperationsSignIn').classList.remove('active');
        document.getElementById('showOperationsSignIn').setAttribute('aria-selected', 'false');
    });

    ownerRegistrationForm.addEventListener('submit', async event => {
        event.preventDefault();
        ownerRegistrationFeedback.textContent = '';
        const button = ownerRegistrationForm.querySelector('button[type="submit"]');
        const password = document.getElementById('ownerPassword').value;
        const confirmation = document.getElementById('ownerPasswordConfirm').value;
        if (password !== confirmation) {
            ownerRegistrationFeedback.textContent = 'Passwords do not match.';
            return;
        }
        button.disabled = true;
        try {
            const result = await window.tapApi.auth.registerOwner(
                document.getElementById('ownerFullName').value.trim(),
                document.getElementById('ownerEmail').value.trim().toLowerCase(),
                password
            );
            window.tapApi.setToken(result.token);
            await boot();
        } catch (error) {
            ownerRegistrationFeedback.textContent = error.message;
        } finally {
            button.disabled = false;
        }
    });

    document.getElementById('operationsLogout').addEventListener('click', () => {
        stopScanner();
        if (pollHandle) clearInterval(pollHandle);
        window.tapApi.clearToken();
        currentUser = null;
        app.classList.add('hidden');
        loginPanel.classList.remove('hidden');
    });
    const notificationToggle = document.getElementById('toggleNotifications');
    const notificationPopover = document.getElementById('notifications');
    function setNotificationsOpen(open) {
        notificationPopover.classList.toggle('hidden', !open);
        notificationPopover.setAttribute('aria-hidden', String(!open));
        notificationToggle.setAttribute('aria-expanded', String(open));
    }
    notificationToggle.addEventListener('click', () => {
        setNotificationsOpen(notificationPopover.classList.contains('hidden'));
    });
    const sidebarNav = document.querySelector('.sidebar nav');
    function setActiveNavigation(targetId) {
        const targetLink = sidebarNav.querySelector(`a[href="#${CSS.escape(targetId)}"]:not(.hidden)`)
            || sidebarNav.querySelector('a[href="#overview"]');
        sidebarNav.querySelectorAll('a[href^="#"]').forEach(item =>
            item.classList.toggle('active', item === targetLink));
    }

    sidebarNav.addEventListener('click', event => {
        const notificationLink = event.target.closest('a[href="#notifications"]');
        if (notificationLink) {
            event.preventDefault();
            if (isCompactViewport()) showMobileView('overview');
            setActiveNavigation(isCompactViewport() ? 'overview' : location.hash.slice(1) || 'overview');
            setNotificationsOpen(true);
            notificationToggle.focus();
            return;
        }

        const link = event.target.closest('a[href^="#"]');
        if (!link) return;
        const targetId = link.getAttribute('href').slice(1);
        if (!document.getElementById(targetId) || link.classList.contains('hidden')) return;
        setNotificationsOpen(false);
        setActiveNavigation(targetId);
        if (targetId === 'locations' && currentUser.role === 'OWNER') {
            requestAnimationFrame(ensurePickupMap);
        }
        if (isCompactViewport()) {
            event.preventDefault();
            showMobileView(targetId);
        }
    });
    window.addEventListener('hashchange', () => {
        const targetId = location.hash.slice(1);
        if (targetId && targetId !== 'notifications') {
            setActiveNavigation(targetId);
            if (targetId === 'locations' && currentUser?.role === 'OWNER') {
                requestAnimationFrame(ensurePickupMap);
            }
        }
    });
    function syncResponsiveNavigation() {
        const compact = isCompactViewport();
        if (compact === compactNavigation
                && workspace.classList.contains('mobile-overview') === compact) return;
        compactNavigation = compact;
        const targetId = location.hash.slice(1);
        const targetLink = targetId && targetId !== 'notifications'
            ? sidebarNav.querySelector(`a[href="#${CSS.escape(targetId)}"]:not(.hidden)`) : null;
        const activeId = targetLink ? targetId : 'overview';
        setActiveNavigation(activeId);
        showMobileView(activeId);
    }

    responsiveNavigation.addEventListener('change', syncResponsiveNavigation);
    window.addEventListener('resize', syncResponsiveNavigation);
    document.addEventListener('click', event => {
        if (!event.target.closest('.operations-notifications')
                && !event.target.closest('a[href="#notifications"]')) {
            setNotificationsOpen(false);
        }
    });
    document.addEventListener('keydown', event => {
        if (event.key === 'Escape' && !notificationPopover.classList.contains('hidden')) {
            setNotificationsOpen(false);
            notificationToggle.focus();
        }
    });
    document.getElementById('roleForm').addEventListener('submit', async event => {
        event.preventDefault();
        const userId = document.getElementById('staffUser').value;
        const role = document.getElementById('staffRole').value;
        if (!userId) {
            setFeedback('Choose an account before updating its role.', true);
            return;
        }
        try {
            await window.tapApi.user.updateRole(userId, role);
            setFeedback('Account role updated. The account holder must sign in again to refresh their access.');
            await loadAdminAccess();
        } catch (error) {
            setFeedback(error.message, true);
        }
    });
    document.getElementById('adminProductAssignments').addEventListener('change', async event => {
        const select = event.target.closest('[data-owner-select]');
        if (!select) return;
        const card = select.closest('[data-admin-product]');
        const locationSelect = card.querySelector('[data-location-select]');
        if (!select.value) {
            locationSelect.innerHTML = '<option value="">Choose an owner first</option>';
            locationSelect.disabled = true;
            return;
        }
        locationSelect.innerHTML = '<option value="">Loading pickup locations…</option>';
        locationSelect.disabled = true;
        try {
            const locations = await window.tapApi.businessLocations.forOwner(select.value);
            locationSelect.innerHTML = locations.length
                ? '<option value="">Choose a pickup location</option>' + locations.map(location =>
                    `<option value="${location.id}">${escapeHtml(location.name)} · ${escapeHtml(location.city)}</option>`
                ).join('')
                : '<option value="">This owner has no pickup locations</option>';
            locationSelect.disabled = locations.length === 0;
        } catch (error) {
            locationSelect.innerHTML = '<option value="">Unable to load locations</option>';
            setFeedback(error.message, true);
        }
    });
    document.getElementById('adminProductAssignments').addEventListener('click', async event => {
        const button = event.target.closest('[data-save-assignment]');
        if (!button) return;
        const card = button.closest('[data-admin-product]');
        const ownerId = card.querySelector('[data-owner-select]').value;
        const locationId = card.querySelector('[data-location-select]').value;
        if (!ownerId || !locationId) {
            setFeedback('Choose an owner and one of that owner’s pickup locations.', true);
            return;
        }
        button.disabled = true;
        try {
            await window.tapApi.admin.assignProductOwner(
                card.dataset.adminProduct, Number(ownerId), Number(locationId));
            setFeedback('Product owner and pickup location updated.');
            await loadAdminAccess();
        } catch (error) {
            setFeedback(error.message, true);
        } finally {
            button.disabled = false;
        }
    });
    document.getElementById('startScanner').addEventListener('click', startScanner);
    document.getElementById('stopScanner').addEventListener('click', stopScanner);
    document.getElementById('retryScanner').addEventListener('click', () => {
        document.getElementById('retryScanner').hidden = true;
        startScanner();
    });
    document.getElementById('manualScanForm').addEventListener('submit', event => {
        event.preventDefault();
        handleScan(document.getElementById('manualQrToken').value);
    });
    stageForm.addEventListener('submit', async event => {
        event.preventDefault();
        if (!activeScan) return;
        const button = document.getElementById('stageSubmit');
        button.disabled = true;
        try {
            const selectedStage = document.getElementById('stageAction').value;
            if (selectedStage !== activeScan.stage) {
                throw new Error('The scanned order stage changed. Scan the package QR again.');
            }
            if (activeScan.stage === 'DELIVERED') {
                await window.tapApi.delivery.requestCustomerConfirmation({
                    token: activeScan.token,
                    notes: document.getElementById('stageNotes').value.trim() || null
                });
                stageForm.classList.add('hidden');
                scannerMessage.textContent = 'Delivery completed. Customer and warehouse tracking are updated.';
                activeScan = null;
                await refresh();
                setFeedback('Delivery completed successfully. Customer and warehouse tracking were updated.');
                showTaskToast('Congratulations!', 'Great work delivering this package to the customer.');
            } else {
                const body = {
                    token: activeScan.token,
                    notes: document.getElementById('stageNotes')?.value.trim() || null
                };
                const endpoint = {
                    PICKUP_FROM_OWNER: window.tapApi.delivery.pickup,
                    WAREHOUSE_RECEIVED: window.tapApi.delivery.warehouseReceipt,
                    OUT_FOR_DELIVERY_TO_STORE: window.tapApi.delivery.warehouseDispatch,
                    STORE_RECEIVED: window.tapApi.delivery.destinationWarehouseReceipt,
                    OUT_FOR_DELIVERY: window.tapApi.delivery.dispatch
                }[activeScan.stage];
                await endpoint(body);
                stageForm.classList.add('hidden');
                const recordedStage = stageLabel(activeScan.stage);
                activeScan = null;
                await refresh();
                scannerMessage.textContent =
                    `${recordedStage} recorded. Scan the same package QR at the next checkpoint.`;
                setFeedback(`${recordedStage} recorded. Customer tracking and notifications were updated.`);
                if (currentUser.role === 'WAREHOUSE_OPERATOR') {
                    showTaskToast('Great work!', `${recordedStage} recorded successfully.`);
                }
            }
        } catch (error) {
            setFeedback(error.message, true);
        } finally {
            button.disabled = false;
        }
    });

    document.getElementById('pickupLocationSearchButton').addEventListener('click', searchPickupLocation);
    document.getElementById('pickupLocationSearch').addEventListener('keydown', event => {
        if (event.key === 'Enter') {
            event.preventDefault();
            searchPickupLocation();
        }
    });

    document.getElementById('locationForm').addEventListener('submit', async event => {
        event.preventDefault();
        const form = event.currentTarget;
        const latitude = Number(form.elements.latitude.value);
        const longitude = Number(form.elements.longitude.value);
        if (!form.elements.latitude.value || !form.elements.longitude.value
                || !Number.isFinite(latitude) || !Number.isFinite(longitude)
                || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            setFeedback('Search for the pickup address and choose a result to set its coordinates before saving.', true);
            return;
        }
        const payload = Object.fromEntries(new FormData(form));
        payload.latitude = latitude;
        payload.longitude = longitude;
        payload.verifiedByOwner = form.elements.verifiedByOwner.checked;
        try {
            await window.tapApi.businessLocations.create(payload);
            form.reset();
            pickupLocationSearchSequence += 1;
            document.getElementById('pickupLocationSearchResults').replaceChildren();
            const mapLink = document.getElementById('pickupLocationMapLink');
            mapLink.removeAttribute('href');
            mapLink.classList.add('hidden');
            ensurePickupMap();
            document.getElementById('pickupLocationMapStatus').textContent =
                'Search for an address and choose a result to center the Google map and set its coordinates.';
            setFeedback('Pickup location saved. The PIN is a postal code and does not geocode or independently verify the address.');
            await loadLocations();
            await loadOwnerTasks();
        } catch (error) {
            setFeedback(error.message, true);
        }
    });

    document.getElementById('productForm').addEventListener('submit', async event => {
        event.preventDefault();
        const form = event.currentTarget;
        const button = document.getElementById('productSubmit');
        button.disabled = true;
        try {
            const payload = Object.fromEntries(new FormData(form));
            const imageUrls = await uploadSelectedImages(document.getElementById('productImages').files);
            const allImages = imageUrls.length ? imageUrls : existingProductImages;
            if (!allImages.length) throw new Error('Upload at least one product image.');
            payload.categoryId = Number(payload.categoryId);
            payload.businessLocationId = Number(payload.businessLocationId);
            payload.price = Number(payload.price);
            payload.stock = Number(payload.stock);
            payload.imageUrl = allImages[0];
            payload.galleryImages = allImages.slice(1).join(',');
            if (editingProductId) {
                await window.tapApi.products.update(editingProductId, payload);
                setFeedback('Product updated in the live storefront.');
            } else {
                await window.tapApi.products.create(payload);
                setFeedback('Product published in the live storefront.');
            }
            form.reset();
            editingProductId = null;
            existingProductImages = [];
            renderProductImagePreviews([]);
            document.getElementById('productImages').required = true;
            document.getElementById('productSubmit').textContent = 'Publish product';
            document.getElementById('cancelProductEdit').classList.add('hidden');
            await loadProducts();
        } catch (error) {
            setFeedback(error.message, true);
        } finally {
            button.disabled = false;
        }
    });

    document.getElementById('cancelProductEdit').addEventListener('click', () => {
        editingProductId = null;
        existingProductImages = [];
        document.getElementById('productForm').reset();
        renderProductImagePreviews([]);
        document.getElementById('productImages').required = true;
        document.getElementById('productSubmit').textContent = 'Publish product';
        document.getElementById('cancelProductEdit').classList.add('hidden');
    });
    document.getElementById('retryCategories').addEventListener('click', () => {
        loadCategories().then(() => setFeedback('Categories loaded.'))
            .catch(error => setFeedback(error.message, true));
    });

    document.getElementById('ownerFulfillments').addEventListener('click', async event => {
        const confirmId = event.target.closest('[data-confirm-fulfillment]')?.dataset.confirmFulfillment;
        const rejectId = event.target.closest('[data-reject-fulfillment]')?.dataset.rejectFulfillment;
        const serviceReview = event.target.closest('[data-service-review]');
        const serviceEvidence = event.target.closest('[data-service-evidence-url]');
        const serviceReceiptId = event.target.closest('[data-service-receipt]')?.dataset.serviceReceipt;
        const pickupLocationId = event.target.closest('[data-set-pickup-location]')?.dataset.setPickupLocation;
        const qrId = event.target.closest('[data-owner-qr]')?.dataset.ownerQr;
        const downloadQrId = event.target.closest('[data-download-owner-qr]')?.dataset.downloadOwnerQr;
        const assignId = event.target.closest('[data-assign-agent]')?.dataset.assignAgent;
        try {
            if (serviceEvidence) {
                await openServiceEvidence(serviceEvidence);
            } else if (serviceReview) {
                let reviewNotes = '';
                if (serviceReview.dataset.decision === 'REJECT') {
                    const enteredNotes = window.prompt('Reason for declining this request?');
                    if (enteredNotes === null) return;
                    reviewNotes = enteredNotes.trim();
                }
                await window.tapApi.serviceRequests.review(
                    Number(serviceReview.dataset.serviceReview),
                    serviceReview.dataset.decision,
                    reviewNotes
                );
                setFeedback(serviceReview.dataset.decision === 'APPROVE'
                    ? 'Request approved. The warehouse was notified to assign a return pickup agent.'
                    : 'Request declined. The customer and warehouse were notified.');
                await refresh();
            } else if (serviceReceiptId) {
                const note = document.getElementById(`serviceReceiptNote-${serviceReceiptId}`)?.value || '';
                await window.tapApi.serviceRequests.confirmSellerReceipt(
                    Number(serviceReceiptId), note.trim());
                setFeedback('Returned product receipt recorded. The next return/exchange stage is ready.');
                await refresh();
            } else if (downloadQrId) {
                const canvas = document.querySelector(`[data-owner-qr-canvas="${CSS.escape(String(downloadQrId))}"]`);
                if (!canvas) throw new Error('The pickup QR is not available to download. Show it first.');
                const task = ownerTasks.find(item => String(item.id) === String(downloadQrId));
                const orderNumber = String(task?.orderNumber || `order-${downloadQrId}`)
                    .replace(/[^a-zA-Z0-9_-]/g, '-');
                const downloadLink = document.createElement('a');
                downloadLink.href = canvas.toDataURL('image/png');
                downloadLink.download = `pickup-qr-${orderNumber}.png`;
                downloadLink.click();
            } else if (pickupLocationId) {
                const select = document.querySelector(`[data-pickup-location-select="${CSS.escape(pickupLocationId)}"]`);
                if (!select?.value) {
                    setFeedback('Choose a verified pickup location before assigning it to this order.', true);
                    return;
                }
                const location = locations.find(item => String(item.id) === select.value);
                if (!location) {
                    throw new Error('The selected pickup location is no longer available. Refresh the page and select it again.');
                }
                await window.tapApi.owner.setPickupLocation(pickupLocationId, Number(select.value));
                ownerTasks = ownerTasks.map(task => String(task.id) === pickupLocationId ? {
                    ...task,
                    pickupName: location.name,
                    pickupStreet: location.street,
                    pickupCity: location.city,
                    pickupState: location.state,
                    pickupPostalCode: location.postalCode,
                    pickupLatitude: Number(location.latitude),
                    pickupLongitude: Number(location.longitude)
                } : task);
                renderOwnerTasks(ownerTasks);
                setFeedback('Pickup location assigned to this order. You can now confirm it.');
            } else if (confirmId) {
                const result = await window.tapApi.owner.confirm(confirmId);
                await loadOwnerTasks();
                setFeedback(`Order ${result.fulfillment.orderNumber} confirmed. Expected delivery: ${formatDate(result.fulfillment.expectedDeliveryDate)}.`);
            } else if (rejectId) {
                const reason = window.prompt('Reason for rejecting this seller fulfillment:');
                if (!reason?.trim()) return;
                await window.tapApi.owner.reject(rejectId, reason.trim());
                setFeedback('Seller fulfillment rejected and the customer was notified.');
                await refresh();
            } else if (qrId) {
                const token = await window.tapApi.owner.qrToken(qrId, 'PICKUP_FROM_OWNER');
                ownerQrTokens.set(String(qrId), token.token);
                renderOwnerTasks(ownerTasks);
                if (token.refreshed) {
                    setFeedback('A replacement pickup QR was generated because the saved QR could not be opened. Any previously downloaded QR is no longer valid.');
                }
                document.querySelector(`[data-owner-qr-panel="${CSS.escape(String(qrId))}"]`)
                    ?.scrollIntoView({ behavior: 'smooth', block: 'center' });
            } else if (assignId) {
                const select = document.querySelector(`[data-agent-select="${CSS.escape(assignId)}"]`);
                if (!select?.value) throw new Error('Select a delivery agent first.');
                await window.tapApi.delivery.assign(assignId, Number(select.value));
                setFeedback('Delivery agent assigned and notified.');
                await refresh();
            }
        } catch (error) {
            setFeedback(error.message, true);
        }
    });

    document.getElementById('warehouseDeliveryAgents').addEventListener('click', async event => {
        const button = event.target.closest('[data-agent-activation]');
        if (!button) return;
        button.disabled = true;
        try {
            await window.tapApi.warehouse.setDeliveryAgentActive(
                Number(button.dataset.agentActivation),
                button.dataset.active === 'true'
            );
            await loadWarehouseDeliveryAgents();
            setFeedback(button.dataset.active === 'true'
                ? 'Delivery agent approved and notified.'
                : 'Delivery agent deactivated and notified.');
        } catch (error) {
            setFeedback(error.message, true);
            button.disabled = false;
        }
    });

    document.getElementById('deliveryTasks').addEventListener('click', async event => {
        const scannerButton = event.target.closest('[data-open-scanner]');
        const historyButton = event.target.closest('[data-history]');
        const transferButton = event.target.closest('[data-assign-transfer-agent]');
        const finalButton = event.target.closest('[data-assign-final-agent]');
        const serviceAssignment = event.target.closest('[data-service-assignment]');
        const serviceEvidence = event.target.closest('[data-service-evidence-url]');
        try {
            if (serviceEvidence) {
                await openServiceEvidence(serviceEvidence);
            } else if (serviceAssignment) {
                const requestId = Number(serviceAssignment.dataset.serviceId);
                const select = document.querySelector(
                    `[data-service-agent-select="${CSS.escape(String(requestId))}"]`);
                if (!select?.value) throw new Error('Choose an active agent registered at this warehouse.');
                serviceAssignment.disabled = true;
                try {
                    if (serviceAssignment.dataset.serviceAssignment === 'pickup') {
                        await window.tapApi.serviceRequests.assignPickup(requestId, Number(select.value));
                    } else if (serviceAssignment.dataset.serviceAssignment === 'seller') {
                        await window.tapApi.serviceRequests.assignSellerRoute(requestId, Number(select.value));
                    } else {
                        await window.tapApi.serviceRequests.assignReplacement(requestId, Number(select.value));
                    }
                    setFeedback('Return/exchange agent assigned and notified.');
                    await loadWarehouseTasks();
                } catch (error) {
                    serviceAssignment.disabled = false;
                    throw error;
                }
            } else if (transferButton || finalButton) {
                const button = transferButton || finalButton;
                const fulfillmentId = Number(button.dataset.assignTransferAgent
                    || button.dataset.assignFinalAgent);
                const selectorAttribute = transferButton
                    ? 'data-transfer-agent-select' : 'data-final-agent-select';
                const select = document.querySelector(
                    `[${selectorAttribute}="${CSS.escape(String(fulfillmentId))}"]`);
                if (!select?.value) {
                    throw new Error(transferButton
                        ? 'Choose an active agent registered at the origin warehouse.'
                        : 'Choose an active agent registered at the destination warehouse.');
                }
                button.disabled = true;
                try {
                    if (transferButton) {
                        await window.tapApi.delivery.assignTransfer(
                            fulfillmentId, Number(select.value));
                        setFeedback('Transfer route assigned. The agent has one minute to accept.');
                        showTaskToast('Great work!', 'Transfer agent assigned. The warehouse handoff is ready.');
                    } else {
                        await window.tapApi.delivery.assignFinal(
                            fulfillmentId, Number(select.value));
                        setFeedback('Final-delivery route assigned. The agent has one minute to accept.');
                        showTaskToast('Great work!', 'Final delivery agent assigned. The package is ready for its next leg.');
                    }
                    await loadWarehouseTasks();
                } catch (error) {
                    button.disabled = false;
                    throw error;
                }
            } else if (scannerButton) {
                if (isCompactViewport()) {
                    showMobileView('scanner');
                    document.querySelectorAll('.sidebar nav a').forEach(item =>
                        item.classList.toggle('active', item.getAttribute('href') === '#scanner'));
                }
                document.getElementById('scanner').scrollIntoView({
                    behavior: 'smooth',
                    block: 'start'
                });
            } else if (historyButton) {
                const events = await window.tapApi.delivery.history(historyButton.dataset.history);
                setFeedback(events.map(item =>
                    `${formatDateTime(item.createdAt)} · ${item.eventType} · ${item.outcome}`
                ).join('\n') || 'No workflow events recorded.');
            }
        } catch (error) {
            setFeedback(error.message, true);
        }
    });

    document.getElementById('ownerProducts').addEventListener('click', async event => {
        const deleteButton = event.target.closest('[data-delete-product]');
        const editButton = event.target.closest('[data-edit-product]');
        try {
            if (deleteButton) {
                if (!window.confirm('Remove this product from the storefront?')) return;
                await window.tapApi.products.remove(deleteButton.dataset.deleteProduct);
                setFeedback('Product removed from the storefront.');
                await loadProducts();
            } else if (editButton) {
                const id = Number(editButton.dataset.editProduct);
                const page = await window.tapApi.products.owned(0, 100);
                const product = pageContent(page).find(item => Number(item.id) === id);
                if (!product) throw new Error('Product was not found.');
                const form = document.getElementById('productForm');
                form.elements.name.value = product.name || '';
                form.elements.categoryId.value = String(product.categoryId);
                form.elements.shortDescription.value = product.shortDescription || '';
                form.elements.price.value = product.price;
                form.elements.stock.value = product.stock;
                form.elements.productCondition.value = product.productCondition || '';
                form.elements.description.value = product.description || '';
                form.elements.specifications.value = product.specifications || '';
                form.elements.returnPolicy.value = product.returnPolicy || '';
                form.elements.businessLocationId.value = String(product.businessLocationId || '');
                existingProductImages = [product.imageUrl, ...(product.galleryImages || [])].filter(Boolean);
                renderProductImagePreviews(existingProductImages.map((url, index) => ({
                    url, name: `Saved image ${index + 1}`
                })));
                editingProductId = id;
                document.getElementById('productImages').required = false;
                document.getElementById('productSubmit').textContent = 'Save product changes';
                document.getElementById('cancelProductEdit').classList.remove('hidden');
                document.getElementById('products').scrollIntoView({ behavior: 'smooth' });
            }
        } catch (error) {
            setFeedback(error.message, true);
        }
    });

    document.getElementById('locationList').addEventListener('click', async event => {
        const button = event.target.closest('[data-delete-location]');
        if (!button || !window.confirm('Remove this pickup location?')) return;
        try {
            await window.tapApi.businessLocations.remove(button.dataset.deleteLocation);
            setFeedback('Pickup location removed.');
            await loadLocations();
        } catch (error) {
            setFeedback(error.message, true);
        }
    });

    document.getElementById('notificationList').addEventListener('click', async event => {
        const button = event.target.closest('[data-read-notification]');
        if (!button) return;
        try {
            await window.tapApi.notifications.markRead(button.dataset.readNotification);
            await loadNotifications();
        } catch (error) {
            setFeedback(error.message, true);
        }
    });

    document.getElementById('markAllRead').addEventListener('click', async () => {
        try {
            await window.tapApi.notifications.markAllRead();
            await loadNotifications();
        } catch (error) {
            setFeedback(error.message, true);
        }
    });

    window.addEventListener('pagehide', stopScanner);
    window.tapApi.getToken() ? boot() : loginPanel.classList.remove('hidden');
}());
