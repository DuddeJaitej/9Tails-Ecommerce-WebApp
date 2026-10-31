(function () {
    'use strict';

    const loginPanel = document.getElementById('deliveryLogin');
    const app = document.getElementById('deliveryApp');
    const loginForm = document.getElementById('deliveryLoginForm');
    const loginFeedback = document.getElementById('deliveryLoginFeedback');
    const scannerVideo = document.getElementById('deliveryScannerVideo');
    const scannerMessage = document.getElementById('deliveryScannerMessage');
    const scanResult = document.getElementById('deliveryScanResult');
    const stageForm = document.getElementById('deliveryStageForm');
    const stageFields = document.getElementById('deliveryStageFields');
    let currentUser = null;
    let activeScan = null;
    let scannerControls = null;
    let scannerReader = null;
    let scanBusy = false;
    let refreshTimer = null;
    let feedbackTimer = null;
    let historyToastTimer = null;
    let currentTasks = [];
    let warehouseAgents = [];
    let warehouseArrivalQr = null;

    function tokenKeyForRole(role) {
        return ({
            DELIVERY_AGENT: 'tap_delivery_agent_jwt',
            WAREHOUSE_OPERATOR: 'tap_warehouse_operator_jwt',
            ADMIN: 'tap_delivery_admin_jwt'
        })[role] || 'tap_delivery_jwt';
    }

    const stageLabels = {
        PICKUP_FROM_OWNER: 'Picked Up from Owner',
        WAREHOUSE_RECEIVED: 'Received at origin warehouse',
        OUT_FOR_DELIVERY_TO_STORE: 'Dispatched to destination warehouse',
        STORE_RECEIVED: 'Received at destination warehouse',
        OUT_FOR_DELIVERY: 'Out for Delivery',
        DELIVERED: 'Delivered'
    };

    function escapeHtml(value) {
        return String(value ?? '').replace(/[&<>"']/g, character => ({
            '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
        })[character]);
    }

    function showFeedback(message, isError = false) {
        const feedback = document.getElementById('deliveryFeedback');
        if (feedbackTimer) clearTimeout(feedbackTimer);
        feedbackTimer = null;
        feedback.textContent = message;
        feedback.classList.toggle('hidden', !message);
        feedback.classList.toggle('error', isError);
        if (message && !isError) {
            feedbackTimer = setTimeout(() => {
                feedback.textContent = '';
                feedback.classList.add('hidden');
                feedbackTimer = null;
            }, 5000);
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

    function showHistoryToast(events) {
        const toast = document.getElementById('deliveryHistoryToast');
        const list = document.getElementById('deliveryHistoryToastList');
        const title = document.getElementById('deliveryHistoryToastTitle');
        if (historyToastTimer) clearTimeout(historyToastTimer);
        title.textContent = `Order history · ${events.length} ${events.length === 1 ? 'update' : 'updates'}`;
        list.innerHTML = events.length
            ? events.map(item => `
                <li class="delivery-history-toast-item">
                    <strong>${escapeHtml(String(item.eventType || 'Update').replaceAll('_', ' '))}</strong>
                    <span>${escapeHtml(item.outcome || 'RECORDED')}</span>
                    <time datetime="${escapeHtml(item.createdAt || '')}">${escapeHtml(
                        new Date(item.createdAt).toLocaleString('en-IN', {
                            day: '2-digit', month: 'short', year: 'numeric',
                            hour: '2-digit', minute: '2-digit'
                        })
                    )}</time>
                </li>
            `).join('')
            : '<li class="delivery-history-toast-empty">No workflow history recorded.</li>';
        toast.classList.remove('hidden');
        historyToastTimer = setTimeout(() => {
            toast.classList.add('hidden');
            historyToastTimer = null;
        }, 10000);
    }

    function formatDate(value) {
        if (!value) return '—';
        const date = new Date(value);
        return Number.isNaN(date.getTime()) ? '—' : date.toLocaleDateString('en-IN', {
            day: '2-digit', month: 'short', year: 'numeric'
        });
    }

    function usesSingleWarehouse(task) {
        return task.warehouseLocationId !== null && task.warehouseLocationId !== undefined
            && task.destinationWarehouseLocationId !== null
            && task.destinationWarehouseLocationId !== undefined
            && String(task.warehouseLocationId) === String(task.destinationWarehouseLocationId);
    }

    function nextRouteTarget(task, isPickupAgent, isTransferAgent, isFinalAgent) {
        let label;
        let latitude;
        let longitude;
        if (currentUser.role === 'DELIVERY_AGENT') {
            if (isPickupAgent && task.status === 'CONFIRMED') {
                label = 'Route to seller pickup';
                latitude = task.pickupLatitude;
                longitude = task.pickupLongitude;
            } else if (isPickupAgent && task.status === 'PICKED_UP_FROM_OWNER') {
                label = 'Route to origin warehouse';
                latitude = task.warehouseLatitude;
                longitude = task.warehouseLongitude;
            } else if (isTransferAgent && task.status === 'PACKED_READY') {
                label = 'Route to origin warehouse';
                latitude = task.warehouseLatitude;
                longitude = task.warehouseLongitude;
            } else if (isTransferAgent && task.status === 'IN_TRANSIT_TO_STORE') {
                label = 'Route to destination warehouse';
                latitude = task.destinationWarehouseLatitude;
                longitude = task.destinationWarehouseLongitude;
            } else if (isFinalAgent && task.status === 'AT_DESTINATION_STORE') {
                label = 'Route to destination warehouse';
                latitude = task.destinationWarehouseLatitude;
                longitude = task.destinationWarehouseLongitude;
            } else if (isFinalAgent && task.status === 'OUT_FOR_DELIVERY') {
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
        } else {
            const target = ({
                CONFIRMED: ['Route to seller pickup', task.pickupLatitude, task.pickupLongitude],
                PICKED_UP_FROM_OWNER: ['Route to origin warehouse', task.warehouseLatitude, task.warehouseLongitude],
                PACKED_READY: ['Route to destination warehouse', task.destinationWarehouseLatitude, task.destinationWarehouseLongitude],
                IN_TRANSIT_TO_STORE: ['Route to destination warehouse', task.destinationWarehouseLatitude, task.destinationWarehouseLongitude],
                AT_DESTINATION_STORE: ['Route to customer', task.customerLatitude, task.customerLongitude],
                OUT_FOR_DELIVERY: ['Route to customer', task.customerLatitude, task.customerLongitude]
            })[task.status];
            if (target) [label, latitude, longitude] = target;
        }
        if (!label || !window.TapMaps.validCoordinates(latitude, longitude)) return null;
        return { label, coordinates: { latitude, longitude } };
    }

    function taskStage(task) {
        const status = task.status;
        if (currentUser.role === 'WAREHOUSE_OPERATOR') {
            if (status === 'PACKED_READY' && usesSingleWarehouse(task)) {
                return 'STORE_RECEIVED';
            }
            return null;
        }
        if (currentUser.role === 'DELIVERY_AGENT') {
            if (String(task.transferDeliveryAgentId) === String(currentUser.id)) {
                if (status === 'PACKED_READY') return 'OUT_FOR_DELIVERY_TO_STORE';
                if (status === 'IN_TRANSIT_TO_STORE') return 'WAREHOUSE_RECEIVED';
            }
            if (String(task.finalDeliveryAgentId) === String(currentUser.id)) {
                if (status === 'AT_DESTINATION_STORE') return 'OUT_FOR_DELIVERY';
                if (status === 'OUT_FOR_DELIVERY') return 'DELIVERED';
            }
            if (String(task.deliveryAgentId) === String(currentUser.id)) {
                if (status === 'CONFIRMED') return 'PICKUP_FROM_OWNER';
            }
            return null;
        }
        return ({
            CONFIRMED: 'PICKUP_FROM_OWNER',
            PACKED_READY: 'OUT_FOR_DELIVERY_TO_STORE',
            IN_TRANSIT_TO_STORE: 'STORE_RECEIVED',
            AT_DESTINATION_STORE: 'OUT_FOR_DELIVERY',
            OUT_FOR_DELIVERY: 'DELIVERED'
        })[status] || null;
    }

    function deliveryAgentNextStep(task, isPickupAgent, isTransferAgent, isFinalAgent, offerPending) {
        if (task.status === 'CONFIRMED' && isPickupAgent) {
            return offerPending
                ? 'Accept this route offer, then collect the package from the seller and scan its package QR.'
                : 'Go to the seller pickup address, collect the package, and scan its package QR.';
        }
        if (task.status === 'PICKED_UP_FROM_OWNER' && isPickupAgent) {
            return 'Take the package to the origin warehouse and scan that warehouse’s arrival QR.';
        }
        if (task.status === 'PACKED_READY' && isPickupAgent
                && usesSingleWarehouse(task)) {
            return 'This is the destination warehouse too. The warehouse operator will choose a different agent for final delivery.';
        }
        if (task.status === 'PACKED_READY' && isPickupAgent) {
            return 'Your pickup leg is complete. The origin warehouse operator will assign a transfer agent to carry the package to the destination warehouse.';
        }
        if (task.status === 'PACKED_READY' && isTransferAgent) {
            return offerPending
                ? 'Accept the transfer offer, collect the package at the origin warehouse, then follow the Google Maps route to the destination warehouse.'
                : 'Collect the package at the origin warehouse and scan its package QR to start the transfer.';
        }
        if (task.status === 'IN_TRANSIT_TO_STORE' && isTransferAgent) {
            return 'Follow the Google Maps route to the destination warehouse and scan that warehouse’s arrival QR.';
        }
        if (task.status === 'AT_DESTINATION_STORE' && isFinalAgent) {
            return offerPending
                ? 'Accept this final-delivery offer, then collect the package from the destination warehouse and scan its package QR.'
                : 'Collect the package from the destination warehouse and scan its package QR to start final delivery.';
        }
        if (task.status === 'OUT_FOR_DELIVERY' && isFinalAgent) {
            return 'Follow the route to the customer and scan the package QR to record the delivery handoff.';
        }
        if (task.status === 'DELIVERY_CONFIRMATION_PENDING' || task.status === 'DELIVERED') {
            return 'Delivery is complete. The customer confirmation status is shown in this order’s progress.';
        }
        if (task.status === 'CANCELLED' || task.status === 'REJECTED') {
            return 'This order is no longer active; do not continue delivery.';
        }
        if (task.status === 'PACKED_READY') {
            return 'The origin warehouse is processing the package for its destination warehouse.';
        }
        if (task.status === 'IN_TRANSIT_TO_STORE') {
            return 'The package is on its way to the destination warehouse. Wait for the warehouse operator to record its arrival.';
        }
        if (task.status === 'AT_DESTINATION_STORE') {
            return 'The destination warehouse operator will assign a different agent to deliver the package to the customer.';
        }
        if (task.status === 'OUT_FOR_DELIVERY') {
            return 'The package is waiting for its final-delivery agent.';
        }
        return 'Waiting for the next delivery assignment or stage update.';
    }

    function renderTasks(tasks, serviceRequests = []) {
        currentTasks = tasks;
        const container = document.getElementById('deliveryTaskList');
        document.getElementById('deliveryTaskCount').textContent =
            `${tasks.length + serviceRequests.length} ${tasks.length + serviceRequests.length === 1 ? 'task' : 'tasks'}`;
        if (!tasks.length && !serviceRequests.length) {
            container.innerHTML = currentUser.role === 'WAREHOUSE_OPERATOR'
                ? '<p class="muted">No orders are waiting at this warehouse. After the owner confirms an order, it will appear here with the seller pickup address and a Google Maps route link. Accept the request and assign an active agent. The agent scans the package QR at pickup, then scans this warehouse’s arrival QR when the package reaches the hub.</p>'
                : '<p class="muted">No delivery tasks are currently available for your account.</p>';
            return;
        }

        const orderTasks = tasks.map(task => {
            const stage = taskStage(task);
            const availableTransferAgents = warehouseAgents.filter(agent => agent.active
                && String(agent.id) !== String(task.deliveryAgentId)
                && String(agent.id) !== String(task.finalDeliveryAgentId));
            const availableFinalAgents = warehouseAgents.filter(agent => agent.active
                && String(agent.id) !== String(task.deliveryAgentId)
                && String(agent.id) !== String(task.transferDeliveryAgentId));
            const isPickupAgent = currentUser.role === 'DELIVERY_AGENT'
                && String(task.deliveryAgentId) === String(currentUser.id);
            const isTransferAgent = currentUser.role === 'DELIVERY_AGENT'
                && String(task.transferDeliveryAgentId) === String(currentUser.id);
            const isFinalAgent = currentUser.role === 'DELIVERY_AGENT'
                && String(task.finalDeliveryAgentId) === String(currentUser.id);
            const needsWarehouseArrival = currentUser.role === 'DELIVERY_AGENT'
                && isPickupAgent
                && task.status === 'PICKED_UP_FROM_OWNER';
            const needsDestinationWarehouseArrival = currentUser.role === 'DELIVERY_AGENT'
                && isTransferAgent
                && task.status === 'IN_TRANSIT_TO_STORE';
            const firstOfferPending = isPickupAgent && !task.deliveryAgentAcceptedAt;
            const transferOfferPending = isTransferAgent && !task.transferAgentAcceptedAt;
            const finalOfferPending = isFinalAgent && !task.finalAgentAcceptedAt;
            const offerPending = firstOfferPending || transferOfferPending || finalOfferPending;
            const offerExpiresAt = finalOfferPending ? task.finalAgentOfferExpiresAt
                : transferOfferPending ? task.transferAgentOfferExpiresAt
                : task.deliveryAgentOfferExpiresAt;
            const nextStep = currentUser.role === 'DELIVERY_AGENT'
                ? deliveryAgentNextStep(
                    task, isPickupAgent, isTransferAgent, isFinalAgent, offerPending)
                : null;
            const routeTarget = nextRouteTarget(task, isPickupAgent, isTransferAgent, isFinalAgent);
            const directionsUrl = routeTarget
                ? window.TapMaps.directionsUrl(null, routeTarget.coordinates)
                : null;
            return `<article class="delivery-task-card">
                <h3>Order ${escapeHtml(task.orderNumber)}
                    <span class="status-pill ${task.status === 'DELIVERY_CONFIRMATION_PENDING' ? 'waiting' : ''}">${escapeHtml(task.status.replaceAll('_', ' '))}</span>
                </h3>
                <p>${(task.items || []).map(item =>
                    `${escapeHtml(item.productName)} × ${Number(item.quantity)}`
                ).join(' · ')}</p>
                <p>Pickup: ${escapeHtml(task.pickupStreet || '—')}, ${escapeHtml(task.pickupCity || '')} ${escapeHtml(task.pickupPostalCode || '')}</p>
                <p>Origin warehouse: ${escapeHtml(task.warehouseName || '—')} · ${escapeHtml(task.warehouseCity || '')}</p>
                <p>Destination warehouse: ${escapeHtml(task.destinationWarehouseName || '—')} · ${escapeHtml(task.destinationWarehouseCity || '')}</p>
                ${task.customerStreet ? `<p>Deliver to: ${escapeHtml(task.customerName || '')}, ${escapeHtml(task.customerStreet)}, ${escapeHtml(task.customerCity || '')} ${escapeHtml(task.customerPostalCode || '')}</p>` : ''}
                ${task.customerPhone ? `<p>Customer mobile: <a href="tel:${escapeHtml(task.customerPhone)}">${escapeHtml(task.customerPhone)}</a></p>` : ''}
                <p>Expected: ${escapeHtml(formatDate(task.expectedDeliveryDate))}</p>
                ${currentUser.role === 'DELIVERY_AGENT'
                    ? `<p class="delivery-next-step"><strong>Next step:</strong> ${escapeHtml(nextStep)}</p>`
                    : stage ? `<p>Next stage: <strong>${escapeHtml(stageLabels[stage])}</strong></p>` : ''}
                ${currentUser.role === 'WAREHOUSE_OPERATOR' && task.status === 'PICKED_UP_FROM_OWNER' ? '<p class="muted">Waiting for the assigned agent to scan this warehouse’s arrival QR. The customer timeline advances when the agent checks in.</p>' : ''}
                ${offerPending ? `<p class="offer-countdown">Offer expires: ${escapeHtml(new Date(offerExpiresAt).toLocaleTimeString('en-IN'))}</p>` : ''}
                ${currentUser.role === 'WAREHOUSE_OPERATOR' && task.status === 'CONFIRMED' ? `
                    <div class="pickup-agent-assignment">
                        ${task.warehousePickupDecision === 'PENDING' ? `
                            <p>The owner confirmed this order. Review the seller pickup address and Google Maps route, then accept or decline this warehouse pickup request.</p>
                            <div class="card-actions">
                                <button class="button button-primary" data-accept-warehouse-pickup="${escapeHtml(task.id)}" type="button">Accept pickup request</button>
                                <button class="button button-light" data-reject-warehouse-pickup="${escapeHtml(task.id)}" type="button">Decline pickup</button>
                            </div>
                        ` : ''}
                        ${task.warehousePickupDecision === 'REJECTED'
                            ? `<p class="status-pill waiting">Pickup declined</p><p>${escapeHtml(task.warehousePickupDecisionNotes || 'No alternate staffed warehouse is available. An administrator has been notified.')}</p>`
                            : ''}
                        ${task.warehousePickupDecision === 'ACCEPTED' && task.deliveryAgentAcceptedAt
                            ? `<p><strong>${escapeHtml(task.deliveryAgentName || 'The assigned agent')}</strong> accepted this route. The pickup scan will update the customer’s order progress.</p>`
                            : task.warehousePickupDecision === 'ACCEPTED' && task.deliveryAgentName
                                ? `<p>Currently offered to <strong>${escapeHtml(task.deliveryAgentName)}</strong> · awaiting acceptance</p>`
                                : task.warehousePickupDecision === 'ACCEPTED'
                                    ? '<p>Select an active agent at your warehouse to collect this order from the owner.</p>'
                                    : ''}
                        ${task.warehousePickupDecision === 'ACCEPTED' && !task.deliveryAgentAcceptedAt && warehouseAgents.some(agent => agent.active) ? `
                            <label for="pickupAgent-${escapeHtml(task.id)}">Assign pickup and warehouse route
                                <select id="pickupAgent-${escapeHtml(task.id)}" data-pickup-agent-select="${escapeHtml(task.id)}">
                                    <option value="">Choose an active delivery agent</option>
                                    ${warehouseAgents.filter(agent => agent.active).map(agent => `<option value="${escapeHtml(agent.id)}" ${String(agent.id) === String(task.deliveryAgentId) ? 'selected' : ''}>${escapeHtml(agent.fullName)} · ${escapeHtml(agent.email)}</option>`).join('')}
                                </select>
                            </label>
                            <button class="button button-primary" data-assign-pickup-agent="${escapeHtml(task.id)}" type="button">${task.deliveryAgentId ? 'Reassign route' : 'Assign pickup route'}</button>
                        ` : task.warehousePickupDecision === 'ACCEPTED' && !task.deliveryAgentAcceptedAt && !warehouseAgents.some(agent => agent.active) ? '<p class="muted">No active delivery agents are registered at this warehouse yet.</p>' : ''}
                        <p class="muted">The agent scans the package QR at the owner, then scans this warehouse’s arrival QR at check-in. Arrival updates customer tracking and unlocks the warehouse’s next dispatch step.</p>
                    </div>
                ` : ''}
                ${currentUser.role === 'WAREHOUSE_OPERATOR' && task.status === 'PACKED_READY'
                        && !usesSingleWarehouse(task) ? `
                    <div class="pickup-agent-assignment">
                        <p>The package is at the origin warehouse. Choose a different active agent to carry it to ${escapeHtml(task.destinationWarehouseName || 'the destination warehouse')}.</p>
                        ${task.transferDeliveryAgentId
                            ? `<p>${escapeHtml(task.transferDeliveryAgentName || 'Transfer agent')} · ${task.transferAgentAcceptedAt ? 'accepted' : 'awaiting offer acceptance'}</p>`
                            : ''}
                        ${availableTransferAgents.length ? `
                            <label for="transferAgent-${escapeHtml(task.id)}">Assign warehouse transfer agent
                                <select id="transferAgent-${escapeHtml(task.id)}" data-transfer-agent-select="${escapeHtml(task.id)}" ${task.transferAgentAcceptedAt ? 'disabled' : ''}>
                                    <option value="">Choose an active transfer agent</option>
                                    ${availableTransferAgents.map(agent => `<option value="${escapeHtml(agent.id)}" ${String(agent.id) === String(task.transferDeliveryAgentId) ? 'selected' : ''}>${escapeHtml(agent.fullName)} · ${escapeHtml(agent.email)}</option>`).join('')}
                                </select>
                            </label>
                            <button class="button button-primary" data-assign-transfer-agent="${escapeHtml(task.id)}" type="button" ${task.transferAgentAcceptedAt ? 'disabled' : ''}>${task.transferDeliveryAgentId ? 'Reassign transfer route' : 'Assign transfer route'}</button>
                        ` : `<p class="muted">A different active agent is required for this handoff. Register another delivery agent for this warehouse or activate an existing agent in the Delivery agents panel.</p>
                             <a class="button button-light" href="DeliveryAgentRegister.html">Register a delivery agent</a>`}
                    </div>
                ` : ''}
                ${currentUser.role === 'WAREHOUSE_OPERATOR' && task.status === 'AT_DESTINATION_STORE' ? `
                    <div class="pickup-agent-assignment">
                        <p>The package has arrived. Choose a different active agent registered at this destination warehouse to deliver it to the customer.</p>
                        ${task.finalDeliveryAgentId
                            ? `<p>${escapeHtml(task.finalDeliveryAgentName || 'Final-delivery agent')} · ${task.finalAgentAcceptedAt ? 'accepted' : 'awaiting offer acceptance'}</p>`
                            : ''}
                        ${availableFinalAgents.length ? `
                            <label for="finalAgent-${escapeHtml(task.id)}">Assign final-delivery agent
                                <select id="finalAgent-${escapeHtml(task.id)}" data-final-agent-select="${escapeHtml(task.id)}" ${task.finalAgentAcceptedAt ? 'disabled' : ''}>
                                    <option value="">Choose an active delivery agent</option>
                                    ${availableFinalAgents.map(agent => `<option value="${escapeHtml(agent.id)}" ${String(agent.id) === String(task.finalDeliveryAgentId) ? 'selected' : ''}>${escapeHtml(agent.fullName)} · ${escapeHtml(agent.email)}</option>`).join('')}
                                </select>
                            </label>
                            <button class="button button-primary" data-assign-final-agent="${escapeHtml(task.id)}" type="button" ${task.finalAgentAcceptedAt ? 'disabled' : ''}>${task.finalDeliveryAgentId ? 'Reassign final route' : 'Assign final route'}</button>
                        ` : `<p class="muted">A different active agent is required for final delivery. Register another delivery agent for this warehouse or activate an existing agent in the Delivery agents panel.</p>
                             <a class="button button-light" href="DeliveryAgentRegister.html">Register a delivery agent</a>`}
                    </div>
                ` : ''}
                <div class="card-actions">
                    ${offerPending ? `<button class="button button-primary" data-accept-offer="${escapeHtml(task.id)}" type="button">Accept ${isTransferAgent ? 'transfer' : isFinalAgent ? 'final delivery' : 'pickup'} route</button>` : ''}
                    ${directionsUrl ? `<a class="button button-light" href="${escapeHtml(directionsUrl)}" target="_blank" rel="noopener noreferrer">${routeTarget.label} in Google Maps</a>` : ''}
                    ${(stage && !offerPending) || needsWarehouseArrival || needsDestinationWarehouseArrival ? `<button class="button button-primary" data-focus-scanner type="button">${needsWarehouseArrival || needsDestinationWarehouseArrival ? 'Scan warehouse arrival QR' : 'Scan package QR'}</button>` : ''}
                    <button class="button button-light" data-task-history="${escapeHtml(task.id)}" type="button">View history</button>
                </div>
            </article>`;
        }).join('');
        const serviceTaskCards = currentUser.role === 'DELIVERY_AGENT'
            ? serviceRequests.map(request => {
                const stage = ({
                    PICKUP_ASSIGNED: 'PICKED_UP',
                    PICKED_UP: 'AT_WAREHOUSE',
                    SELLER_ROUTE_ASSIGNED: 'RECEIVED_BY_SELLER',
                    REPLACEMENT_ASSIGNED: 'COMPLETED'
                })[request.status];
                const target = request.status === 'PICKUP_ASSIGNED'
                        || request.status === 'REPLACEMENT_ASSIGNED'
                    ? { latitude: request.customerLatitude, longitude: request.customerLongitude }
                    : request.status === 'PICKED_UP'
                        ? { latitude: request.warehouseLatitude, longitude: request.warehouseLongitude }
                        : { latitude: request.sellerLatitude, longitude: request.sellerLongitude };
                const routeUrl = stage && window.TapMaps.validCoordinates(
                    target.latitude, target.longitude)
                    ? window.TapMaps.directionsUrl(null, target) : null;
                const routeLabel = request.status === 'PICKUP_ASSIGNED'
                        || request.status === 'REPLACEMENT_ASSIGNED'
                    ? 'Route to customer'
                    : request.status === 'PICKED_UP' ? 'Route to destination warehouse'
                        : 'Route to seller';
                return `<article class="delivery-task-card service-request-card">
                    <h3>${escapeHtml(request.type)} · ${escapeHtml(request.productName)}
                        <span class="status-pill">${escapeHtml(String(request.status).replaceAll('_', ' '))}</span></h3>
                    <p>Order ${escapeHtml(request.orderNumber)}</p>
                    <p>Customer: ${escapeHtml(request.customerName || '')}, ${escapeHtml(request.customerStreet || '')}, ${escapeHtml(request.customerCity || '')} ${escapeHtml(request.customerPostalCode || '')}</p>
                    <p>Destination warehouse: ${escapeHtml(request.warehouseName || '')}, ${escapeHtml(request.warehouseStreet || '')}, ${escapeHtml(request.warehouseCity || '')} ${escapeHtml(request.warehousePostalCode || '')}</p>
                    <p>Seller: ${escapeHtml(request.sellerName || '')}, ${escapeHtml(request.sellerStreet || '')}, ${escapeHtml(request.sellerCity || '')} ${escapeHtml(request.sellerPostalCode || '')}</p>
                    ${routeUrl ? `<p><a class="button button-light" href="${escapeHtml(routeUrl)}" target="_blank" rel="noopener noreferrer">${routeLabel} in Google Maps</a></p>` : ''}
                    ${stage ? `<p class="delivery-next-step"><strong>Next step:</strong> Confirm ${escapeHtml(String(stage).replaceAll('_', ' ').toLowerCase())} after reaching the destination.</p>
                        <button class="button button-primary" data-service-stage="${escapeHtml(stage)}" data-service-id="${escapeHtml(request.id)}" type="button">Confirm ${escapeHtml(String(stage).replaceAll('_', ' '))}</button>`
                        : '<p class="muted">No return or exchange action is available yet.</p>'}
                </article>`;
            }).join('')
            : '';
        container.innerHTML = orderTasks + serviceTaskCards;
    }

    async function loadWarehouseAgents() {
        warehouseAgents = await window.tapApi.warehouse.deliveryAgents();
        renderTasks(currentTasks);
        document.getElementById('warehouseAgentList').innerHTML = warehouseAgents.length
            ? warehouseAgents.map(agent => `
                <article class="delivery-task-card">
                    <h3>${escapeHtml(agent.fullName)}
                        <span class="status-pill ${agent.active ? '' : 'waiting'}">${agent.active ? 'Active' : 'Awaiting approval'}</span>
                    </h3>
                    <p>${escapeHtml(agent.email)} · ${escapeHtml(agent.warehouseName || currentUser.deliveryWarehouseName || 'Your warehouse')}</p>
                    <button class="button ${agent.active ? 'button-light' : 'button-primary'}"
                            data-agent-activation="${escapeHtml(agent.id)}"
                            data-active="${agent.active ? 'false' : 'true'}" type="button">
                        ${agent.active ? 'Deactivate' : 'Approve agent'}
                    </button>
                </article>`).join('')
            : '<p class="muted">No delivery agents have registered for your warehouse.</p>';
    }

    function renderWarehouseArrivalQr() {
        const panel = document.getElementById('warehouseArrivalQrPanel');
        panel.classList.toggle('hidden', currentUser.role !== 'WAREHOUSE_OPERATOR');
        if (currentUser.role !== 'WAREHOUSE_OPERATOR' || !warehouseArrivalQr) return;
        if (typeof window.qrcode !== 'function') {
            throw new Error('Warehouse QR generator failed to load. Check your internet connection and reload.');
        }
        const qr = window.qrcode(0, 'M');
        qr.addData(warehouseArrivalQr.token);
        qr.make();
        const canvas = document.getElementById('warehouseArrivalQrCanvas');
        const context = canvas.getContext('2d');
        if (!context) throw new Error('This browser cannot display the warehouse arrival QR.');
        const count = qr.getModuleCount();
        const cell = Math.floor(canvas.width / (count + 8));
        const offset = Math.floor((canvas.width - count * cell) / 2);
        context.fillStyle = '#fff';
        context.fillRect(0, 0, canvas.width, canvas.height);
        context.fillStyle = '#111';
        for (let row = 0; row < count; row++) {
            for (let column = 0; column < count; column++) {
                if (qr.isDark(row, column)) {
                    context.fillRect(offset + column * cell, offset + row * cell, cell, cell);
                }
            }
        }
        document.getElementById('warehouseArrivalQrName').textContent =
            `${warehouseArrivalQr.warehouseName} arrival QR`;
        document.getElementById('warehouseArrivalQrAddress').textContent =
            warehouseArrivalQr.warehouseAddress;
        document.getElementById('warehouseArrivalQrDownload').href = canvas.toDataURL('image/png');
    }

    async function loadNotifications() {
        const summary = await window.tapApi.notifications.list(20);
        const notifications = summary.notifications || [];
        const unreadCount = Number(summary.unreadCount ?? 0);
        const badge = document.getElementById('deliveryNotificationBadge');
        badge.textContent = unreadCount > 9 ? '9+' : String(unreadCount);
        badge.classList.toggle('hidden', unreadCount === 0);
        document.getElementById('deliveryNotificationToggle').setAttribute(
            'aria-label', unreadCount ? `Notifications, ${unreadCount} unread` : 'Notifications');
        document.getElementById('deliveryNotifications').innerHTML = notifications.length
            ? notifications.map(notification => `
                <article class="notification-row ${notification.read ? '' : 'unread'}">
                    <div class="notification-info">
                        <h3>${escapeHtml(notification.title)}</h3>
                        <p>${escapeHtml(notification.message)}</p>
                        <small>${escapeHtml(formatDate(notification.createdAt))}</small>
                    </div>
                    <div class="notification-actions">
                        ${notification.read ? '' : `<button class="text-button" data-read-notification="${escapeHtml(notification.id)}" type="button">Mark read</button>`}
                    </div>
                </article>`).join('')
            : '<p class="muted">No recent notifications.</p>';
    }

    async function refresh() {
        showFeedback('');
        if (currentUser.role === 'DELIVERY_AGENT' && !currentUser.deliveryAgentActive
                || currentUser.role === 'WAREHOUSE_OPERATOR' && !currentUser.warehouseActive) {
            return;
        }
        if (currentUser.role === 'WAREHOUSE_OPERATOR') {
            await loadWarehouseAgents();
            if (!warehouseArrivalQr) {
                warehouseArrivalQr = await window.tapApi.delivery.warehouseArrivalQr();
            }
            renderWarehouseArrivalQr();
        }
        const [tasks, serviceRequests] = currentUser.role === 'DELIVERY_AGENT'
            ? await Promise.all([
                window.tapApi.delivery.tasks(),
                window.tapApi.serviceRequests.agent()
            ])
            : [await window.tapApi.delivery.warehouseTasks(), []];
        renderTasks(tasks, serviceRequests);
        await loadNotifications();
        if (warehouseArrivalQr?.refreshed) {
            showFeedback('The saved warehouse arrival QR could not be opened, so a replacement was generated. Please print and display the new QR; previous copies will no longer work.');
            warehouseArrivalQr.refreshed = false;
        }
    }

    async function boot() {
        try {
            currentUser = await window.tapApi.user.me();
            if (!['DELIVERY_AGENT', 'WAREHOUSE_OPERATOR', 'ADMIN'].includes(currentUser.role)) {
                app.classList.add('hidden');
                loginPanel.classList.remove('hidden');
                loginFeedback.textContent =
                    'This page is for delivery agents and warehouse operators. Ask an administrator to assign your role.';
                return;
            }
            const roleTokenKey = tokenKeyForRole(currentUser.role);
            const sessionToken = window.tapApi.getToken();
            window.tapApi.setToken(sessionToken, roleTokenKey);
            if (roleTokenKey !== 'tap_delivery_jwt') {
                window.tapApi.clearToken('tap_delivery_jwt');
            }
            loginPanel.classList.add('hidden');
            app.classList.remove('hidden');
            document.getElementById('deliveryUserName').textContent =
                currentUser.fullName || currentUser.email;
            document.getElementById('deliveryUserRole').textContent =
                currentUser.role.replaceAll('_', ' ');
            document.getElementById('deliveryWorkspaceTitle').textContent =
                currentUser.role === 'WAREHOUSE_OPERATOR'
                    ? 'Warehouse operator workspace'
                    : currentUser.role === 'DELIVERY_AGENT'
                        ? 'Delivery agent workspace'
                        : 'Delivery operations workspace';
            document.getElementById('deliveryQueueTitle').textContent =
                currentUser.role === 'WAREHOUSE_OPERATOR' ? 'Warehouse tasks' : 'Assigned delivery tasks';
            document.getElementById('deliveryScannerTitle').textContent =
                currentUser.role === 'WAREHOUSE_OPERATOR'
                    ? 'Warehouse package scanner'
                    : 'Delivery agent package scanner';
            document.getElementById('deliveryScannerMessage').textContent =
                currentUser.role === 'WAREHOUSE_OPERATOR'
                    ? 'Scan the package QR after the assigned agent records pickup from the owner. The next warehouse action will be shown after scanning.'
                    : 'Accept the assigned route first. At the owner, scan the pickup QR and submit the pickup stage.';
            const awaitingApproval = currentUser.role === 'DELIVERY_AGENT'
                && !currentUser.deliveryAgentActive
                || currentUser.role === 'WAREHOUSE_OPERATOR' && !currentUser.warehouseActive;
            document.getElementById('deliveryApprovalPending').classList.toggle(
                'hidden', !awaitingApproval);
            document.getElementById('deliveryTasksPanel').classList.toggle(
                'hidden', awaitingApproval);
            document.getElementById('deliveryScanner').classList.toggle(
                'hidden', awaitingApproval);
            document.getElementById('warehouseAgentPanel').classList.toggle(
                'hidden', currentUser.role !== 'WAREHOUSE_OPERATOR' || awaitingApproval);
            if (awaitingApproval) {
                const warehousePending = currentUser.role === 'WAREHOUSE_OPERATOR';
                document.getElementById('deliveryApprovalEyebrow').textContent =
                    warehousePending ? 'Administrator approval' : 'Warehouse operator approval';
                document.getElementById('deliveryApprovalTitle').textContent =
                    warehousePending ? 'Warehouse awaiting approval' : 'Account awaiting activation';
                document.getElementById('deliveryApprovalMessage').textContent = warehousePending
                    ? 'Your warehouse registration is under administrator review. Delivery operations will be enabled once its location is approved.'
                    : `Your account is pending approval from ${currentUser.deliveryWarehouseName || 'your selected warehouse operator'}. You will receive delivery tasks after approval.`;
            }
            try {
                await refresh();
            } catch (error) {
                showFeedback(`Delivery data could not be loaded: ${error.message}`, true);
            }
            if (refreshTimer) clearInterval(refreshTimer);
            refreshTimer = setInterval(() => {
                if (!document.hidden) refresh().catch(error => showFeedback(error.message, true));
            }, 30000);
        } catch (error) {
            app.classList.add('hidden');
            loginPanel.classList.remove('hidden');
            loginFeedback.textContent = error.message;
        }
    }

    async function handleScan(rawToken) {
        if (scanBusy) return;
        scanBusy = true;
        activeScan = null;
        stageForm.classList.add('hidden');
        scanResult.classList.add('hidden');
        scannerMessage.textContent = 'Checking package QR and current order stage…';
        try {
            const token = rawToken.trim();
            if (token.startsWith('9TAILS-WH:')) {
                if (currentUser.role !== 'DELIVERY_AGENT') {
                    throw new Error('Warehouse arrival QR codes can only be scanned by the assigned delivery agent.');
                }
                const tasks = await window.tapApi.delivery.warehouseArrivalTasks(token);
                if (!tasks.length) {
                    throw new Error('No assigned package is waiting to arrive at this warehouse.');
                }
                activeScan = { token, stage: 'WAREHOUSE_RECEIVED', warehouseArrival: true };
                scanResult.innerHTML = `
                    <article class="delivery-task-card">
                        <h3>${escapeHtml(tasks[0].warehouseName || 'Warehouse')} arrival QR verified</h3>
                        <p>Select the assigned order that has arrived to record the warehouse handoff.</p>
                    </article>`;
                scanResult.classList.remove('hidden');
                stageFields.innerHTML = `
                    <label class="delivery-stage-select" for="warehouseArrivalFulfillment">Order received
                        <select id="warehouseArrivalFulfillment" required>
                            <option value="">Choose the arrived order</option>
                            ${tasks.map(task => `<option value="${escapeHtml(task.id)}">Order ${escapeHtml(task.orderNumber)}</option>`).join('')}
                        </select>
                    </label>
                    <label for="deliveryStageNote">Note (optional)</label>
                    <textarea id="deliveryStageNote" rows="2" maxlength="1000"></textarea>`;
                document.getElementById('deliveryStageSubmit').textContent = 'Confirm warehouse arrival';
                scannerMessage.textContent = 'Choose the order you brought to this warehouse, then confirm arrival.';
                stageForm.classList.remove('hidden');
                document.getElementById('deliveryManualToken').value = '';
                return;
            }
            const result = await window.tapApi.delivery.scan(token);
            activeScan = { ...result, token };
            const fulfillment = result.fulfillment;
            const label = stageLabels[result.stage] || result.stage.replaceAll('_', ' ');
            scanResult.innerHTML = `<article class="delivery-task-card">
                <h3>Order ${escapeHtml(fulfillment.orderNumber)}</h3>
                <p>Current stage to submit: <strong>${escapeHtml(label)}</strong></p>
                <p>${(fulfillment.items || []).map(item =>
                    `${escapeHtml(item.productName)} × ${Number(item.quantity)}`
                ).join(' · ')}</p>
                ${fulfillment.pickupStreet ? `<p>Pickup: ${escapeHtml(fulfillment.pickupStreet)}, ${escapeHtml(fulfillment.pickupCity || '')} ${escapeHtml(fulfillment.pickupPostalCode || '')}</p>` : ''}
                ${fulfillment.customerStreet ? `<p>Delivery: ${escapeHtml(fulfillment.customerName || '')}, ${escapeHtml(fulfillment.customerStreet)}, ${escapeHtml(fulfillment.customerCity || '')} ${escapeHtml(fulfillment.customerPostalCode || '')}</p>` : ''}
                ${fulfillment.customerPhone ? `<p>Customer mobile: <a href="tel:${escapeHtml(fulfillment.customerPhone)}">${escapeHtml(fulfillment.customerPhone)}</a></p>` : ''}
            </article>`;
            scanResult.classList.remove('hidden');
            stageFields.innerHTML = `
                <label class="delivery-stage-select" for="deliveryStageChoice">Stage to record
                    <select id="deliveryStageChoice" required>
                        <option value="${escapeHtml(result.stage)}" selected>${escapeHtml(label)}</option>
                    </select>
                </label>
                <label for="deliveryStageNote">Note (optional)</label>
                <textarea id="deliveryStageNote" rows="2" maxlength="1000"></textarea>`;
            document.getElementById('deliveryStageSubmit').textContent = result.stage === 'DELIVERED'
                ? 'Submit delivery handoff' : `Submit ${label}`;
            scannerMessage.textContent = result.stage === 'DELIVERED'
                ? 'Submitting records the handoff and asks the customer to confirm receipt.'
                : 'Only the next stage is enabled for this order.';
            stageForm.classList.remove('hidden');
            document.getElementById('deliveryManualToken').value = '';
        } catch (error) {
            scannerMessage.textContent = error.message;
            showFeedback(error.message, true);
        } finally {
            scanBusy = false;
        }
    }

    async function startScanner() {
        const start = document.getElementById('deliveryStartScanner');
        const stop = document.getElementById('deliveryStopScanner');
        if (!window.isSecureContext || !navigator.mediaDevices?.getUserMedia) {
            scannerMessage.textContent = 'Camera scanning requires HTTPS or localhost. Use manual token entry on this connection.';
            return;
        }
        if (!window.ZXingBrowser?.BrowserMultiFormatReader) {
            scannerMessage.textContent = 'Camera scanner library is unavailable. Check your internet connection and retry.';
            document.getElementById('deliveryRetryScanner').hidden = false;
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
                        const token = result.getText();
                        stopScanner();
                        handleScan(token);
                    } else if (error && error.name !== 'NotFoundException') {
                        scannerMessage.textContent = 'Cannot read that QR. Hold it steady or enter the token manually.';
                    }
                });
            stop.disabled = false;
            scannerMessage.textContent = currentUser.role === 'DELIVERY_AGENT'
                ? 'Point the rear camera at the package QR or the origin warehouse arrival QR.'
                : 'Point the rear camera at the package QR.';
        } catch (error) {
            scannerMessage.textContent = error.message || 'Camera permission was denied or the camera is unavailable.';
            scannerVideo.classList.add('hidden');
            document.getElementById('deliveryRetryScanner').hidden = false;
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
        document.getElementById('deliveryStartScanner').disabled = false;
        document.getElementById('deliveryStopScanner').disabled = true;
    }

    loginForm.addEventListener('submit', async event => {
        event.preventDefault();
        const button = document.getElementById('deliveryLoginButton');
        button.disabled = true;
        loginFeedback.textContent = '';
        try {
            const result = await window.tapApi.auth.operationsLogin(
                document.getElementById('deliveryEmail').value.trim().toLowerCase(),
                document.getElementById('deliveryPassword').value
            );
            if (!['DELIVERY_AGENT', 'WAREHOUSE_OPERATOR', 'ADMIN'].includes(result.role)) {
                throw new Error('This page is for delivery agents and warehouse operators. Ask an administrator to assign your role.');
            }
            window.tapApi.setToken(result.token, tokenKeyForRole(result.role));
            await boot();
        } catch (error) {
            loginFeedback.textContent = error.message;
        } finally {
            button.disabled = false;
        }
    });

    document.getElementById('deliveryTaskList').addEventListener('click', async event => {
        const serviceStage = event.target.closest('[data-service-stage]');
        if (serviceStage) {
            serviceStage.disabled = true;
            try {
                const stage = serviceStage.dataset.serviceStage;
                await window.tapApi.serviceRequests.advance(
                    Number(serviceStage.dataset.serviceId), stage);
                await refresh();
                if (stage === 'COMPLETED') {
                    showTaskToast('Congratulations!', 'Great work delivering the replacement to the customer.');
                } else {
                    showTaskToast('Great work!', 'Return/exchange route stage recorded successfully.');
                }
            } catch (error) {
                showFeedback(error.message, true);
                serviceStage.disabled = false;
            }
            return;
        }
        const acceptPickup = event.target.closest('[data-accept-warehouse-pickup]');
        if (acceptPickup) {
            acceptPickup.disabled = true;
            try {
                await window.tapApi.delivery.acceptWarehousePickup(
                    Number(acceptPickup.dataset.acceptWarehousePickup));
                showFeedback('Pickup request accepted. Assign an active agent to collect the package from the owner.');
                showTaskToast('Great work!', 'This pickup request is now accepted by your warehouse.');
                await refresh();
            } catch (error) {
                showFeedback(error.message, true);
                acceptPickup.disabled = false;
            }
            return;
        }
        const rejectPickup = event.target.closest('[data-reject-warehouse-pickup]');
        if (rejectPickup) {
            const reason = window.prompt('Why is this warehouse unable to accept the pickup?');
            if (!reason?.trim()) return;
            rejectPickup.disabled = true;
            try {
                await window.tapApi.delivery.rejectWarehousePickup(
                    Number(rejectPickup.dataset.rejectWarehousePickup), reason.trim());
                showFeedback('Pickup decision recorded. The request was routed to the next eligible warehouse when available.');
                await refresh();
            } catch (error) {
                showFeedback(error.message, true);
                rejectPickup.disabled = false;
            }
            return;
        }
        const assignPickup = event.target.closest('[data-assign-pickup-agent]');
        if (assignPickup) {
            const fulfillmentId = Number(assignPickup.dataset.assignPickupAgent);
            const select = document.querySelector(`[data-pickup-agent-select="${CSS.escape(String(fulfillmentId))}"]`);
            if (!select?.value) {
                showFeedback('Choose an active delivery agent for this pickup route.', true);
                return;
            }
            assignPickup.disabled = true;
            try {
                await window.tapApi.delivery.assign(fulfillmentId, Number(select.value));
                showFeedback('Pickup route assigned. The delivery agent has one minute to accept.');
                showTaskToast('Great work!', 'Pickup agent assigned. The warehouse handoff is ready.');
                await refresh();
            } catch (error) {
                showFeedback(error.message, true);
                assignPickup.disabled = false;
            }
            return;
        }
        const assignTransfer = event.target.closest('[data-assign-transfer-agent]');
        if (assignTransfer) {
            const fulfillmentId = Number(assignTransfer.dataset.assignTransferAgent);
            const select = document.querySelector(
                `[data-transfer-agent-select="${CSS.escape(String(fulfillmentId))}"]`);
            if (!select?.value) {
                showFeedback('Choose an active agent registered at the origin warehouse.', true);
                return;
            }
            assignTransfer.disabled = true;
            try {
                await window.tapApi.delivery.assignTransfer(
                    fulfillmentId, Number(select.value));
                showFeedback('Transfer route assigned. The agent has one minute to accept.');
                showTaskToast('Great work!', 'Transfer agent assigned. The package is ready for its next leg.');
                await refresh();
            } catch (error) {
                showFeedback(error.message, true);
                assignTransfer.disabled = false;
            }
            return;
        }
        const assignFinal = event.target.closest('[data-assign-final-agent]');
        if (assignFinal) {
            const fulfillmentId = Number(assignFinal.dataset.assignFinalAgent);
            const select = document.querySelector(
                `[data-final-agent-select="${CSS.escape(String(fulfillmentId))}"]`);
            if (!select?.value) {
                showFeedback('Choose an active agent registered at the destination warehouse.', true);
                return;
            }
            assignFinal.disabled = true;
            try {
                await window.tapApi.delivery.assignFinal(
                    fulfillmentId, Number(select.value));
                showFeedback('Final-delivery route assigned. The agent has one minute to accept.');
                showTaskToast('Great work!', 'Final delivery agent assigned. The package is ready for its next leg.');
                await refresh();
            } catch (error) {
                showFeedback(error.message, true);
                assignFinal.disabled = false;
            }
            return;
        }
        const accept = event.target.closest('[data-accept-offer]');
        if (accept) {
            accept.disabled = true;
            try {
                await window.tapApi.delivery.accept(Number(accept.dataset.acceptOffer));
                showFeedback('Route accepted. Complete the assigned leg within 24 hours.');
                await refresh();
            } catch (error) {
                showFeedback(error.message, true);
                accept.disabled = false;
            }
            return;
        }
        if (event.target.closest('[data-focus-scanner]')) {
            document.getElementById('deliveryScanner').scrollIntoView({
                behavior: 'smooth', block: 'start'
            });
            document.getElementById('deliveryStartScanner').focus();
            return;
        }
        const history = event.target.closest('[data-task-history]');
        if (!history) return;
        try {
            const events = await window.tapApi.delivery.history(history.dataset.taskHistory);
            showHistoryToast(events);
        } catch (error) {
            showFeedback(error.message, true);
        }
    });

    document.getElementById('deliveryHistoryToastClose').addEventListener('click', () => {
        const toast = document.getElementById('deliveryHistoryToast');
        toast.classList.add('hidden');
        if (historyToastTimer) clearTimeout(historyToastTimer);
        historyToastTimer = null;
    });

    document.getElementById('deliveryNotifications').addEventListener('click', async event => {
        const button = event.target.closest('[data-read-notification]');
        if (!button) return;
        try {
            await window.tapApi.notifications.markRead(button.dataset.readNotification);
            await loadNotifications();
        } catch (error) {
            showFeedback(error.message, true);
        }
    });

    document.getElementById('warehouseAgentList').addEventListener('click', async event => {
        const activation = event.target.closest('[data-agent-activation]');
        if (!activation) return;
        activation.disabled = true;
        try {
            const active = activation.dataset.active === 'true';
            await window.tapApi.warehouse.setDeliveryAgentActive(
                Number(activation.dataset.agentActivation), active);
            await loadWarehouseAgents();
            showFeedback(active
                ? 'Delivery agent approved and notified.'
                : 'Delivery agent deactivated and notified.');
            await loadNotifications();
        } catch (error) {
            showFeedback(error.message, true);
            activation.disabled = false;
        }
    });

    const notificationToggle = document.getElementById('deliveryNotificationToggle');
    const notificationPopover = document.getElementById('deliveryNotificationPopover');
    function setNotificationsOpen(open) {
        notificationPopover.classList.toggle('hidden', !open);
        notificationToggle.setAttribute('aria-expanded', String(open));
    }
    notificationToggle.addEventListener('click', () => {
        const open = notificationPopover.classList.contains('hidden');
        setNotificationsOpen(open);
        if (open) document.getElementById('deliveryNotificationClose').focus();
    });
    document.getElementById('deliveryNotificationClose').addEventListener('click', () => {
        setNotificationsOpen(false);
        notificationToggle.focus();
    });
    document.addEventListener('click', event => {
        if (!event.target.closest('.delivery-notification-menu')) setNotificationsOpen(false);
    });
    document.addEventListener('keydown', event => {
        if (event.key === 'Escape' && !notificationPopover.classList.contains('hidden')) {
            setNotificationsOpen(false);
            notificationToggle.focus();
        }
    });

    document.getElementById('deliveryStageForm').addEventListener('submit', async event => {
        event.preventDefault();
        if (!activeScan) return;
        const button = document.getElementById('deliveryStageSubmit');
        button.disabled = true;
        try {
            if (activeScan.warehouseArrival) {
                const fulfillmentId = document.getElementById('warehouseArrivalFulfillment').value;
                if (!fulfillmentId) throw new Error('Choose the order that has arrived at this warehouse.');
                await window.tapApi.delivery.confirmWarehouseArrival(
                    activeScan.token,
                    Number(fulfillmentId),
                    document.getElementById('deliveryStageNote').value.trim() || null
                );
                activeScan = null;
                stageForm.classList.add('hidden');
                scanResult.classList.add('hidden');
                await refresh();
                scannerMessage.textContent = 'Warehouse arrival confirmed. The next route is now available to the warehouse operator.';
                showFeedback('Warehouse arrival recorded and customer tracking updated.');
                showTaskToast('Great work!', 'Warehouse arrival confirmed. The next route is ready for assignment.');
                return;
            }
            if (document.getElementById('deliveryStageChoice').value !== activeScan.stage) {
                throw new Error('The active stage changed. Scan the package QR again.');
            }
            const body = {
                token: activeScan.token,
                notes: document.getElementById('deliveryStageNote').value.trim() || null
            };
            if (activeScan.stage === 'DELIVERED') {
                await window.tapApi.delivery.requestCustomerConfirmation(body);
            } else {
                const endpoint = {
                    PICKUP_FROM_OWNER: window.tapApi.delivery.pickup,
                    WAREHOUSE_RECEIVED: window.tapApi.delivery.warehouseReceipt,
                    OUT_FOR_DELIVERY_TO_STORE: window.tapApi.delivery.warehouseDispatch,
                    STORE_RECEIVED: window.tapApi.delivery.destinationWarehouseReceipt,
                    OUT_FOR_DELIVERY: window.tapApi.delivery.dispatch
                }[activeScan.stage];
                await endpoint(body);
            }
            const submittedStage = stageLabels[activeScan.stage] || activeScan.stage;
            activeScan = null;
            stageForm.classList.add('hidden');
            scanResult.classList.add('hidden');
            await refresh();
            scannerMessage.textContent = submittedStage === 'Delivered'
                ? 'Delivery completed. The customer order and warehouse tracking are now updated.'
                : `${submittedStage} submitted. Scan the same package QR at the next checkpoint.`;
            showFeedback(submittedStage === 'Delivered'
                ? 'Delivery completed successfully. Customer and warehouse tracking were updated.'
                : `${submittedStage} recorded. Order tracking and customer notifications were updated.`);
            if (submittedStage === 'Delivered') {
                showTaskToast('Congratulations!', 'Great work delivering this package to the customer.');
            } else if (currentUser.role === 'WAREHOUSE_OPERATOR') {
                showTaskToast('Great work!', `${submittedStage} recorded successfully.`);
            }
        } catch (error) {
            showFeedback(error.message, true);
        } finally {
            button.disabled = false;
        }
    });

    document.getElementById('deliveryManualScanForm').addEventListener('submit', event => {
        event.preventDefault();
        handleScan(document.getElementById('deliveryManualToken').value);
    });
    document.getElementById('deliveryStartScanner').addEventListener('click', startScanner);
    document.getElementById('deliveryStopScanner').addEventListener('click', stopScanner);
    document.getElementById('deliveryRetryScanner').addEventListener('click', () => {
        document.getElementById('deliveryRetryScanner').hidden = true;
        startScanner();
    });
    document.getElementById('deliveryLogout').addEventListener('click', () => {
        stopScanner();
        if (refreshTimer) clearInterval(refreshTimer);
        refreshTimer = null;
        window.tapApi.clearToken();
        currentUser = null;
        setNotificationsOpen(false);
        app.classList.add('hidden');
        loginPanel.classList.remove('hidden');
    });

    window.addEventListener('pagehide', stopScanner);
    if (window.tapApi.getToken()) boot();
}());
