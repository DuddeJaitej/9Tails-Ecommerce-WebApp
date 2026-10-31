'use strict';

const adminLogin = document.getElementById('adminLogin');
const dashboardShell = document.getElementById('dashboardShell');
const loginForm = document.getElementById('adminLoginForm');
const loginFeedback = document.getElementById('loginFeedback');
const loginButton = document.getElementById('adminLoginButton');
const adminForgotForm = document.getElementById('adminForgotForm');
const adminResetForm = document.getElementById('adminResetForm');
const adminBootstrapPanel = document.getElementById('adminBootstrapPanel');
const adminBootstrapForm = document.getElementById('adminBootstrapForm');
const adminBootstrapMessage = document.getElementById('adminBootstrapMessage');
const adminBootstrapFeedback = document.getElementById('adminBootstrapFeedback');
const showForgotPasswordButton = document.getElementById('showForgotPassword');
const dashboardAlert = document.getElementById('dashboardAlert');
const orderSearch = document.getElementById('orderSearch');
const adminOverviewContent = document.getElementById('overview');
const transactionsSection = document.getElementById('transactions');
const lowerDashboardGrid = document.querySelector('.lower-grid');
const compactDashboard = window.matchMedia('(max-width: 900px)');
let dashboardData = null;
let salesChart = null;
let statusChart = null;
let paymentChart = null;
let ordersPageIndex = 0;
let inventoryPageIndex = 0;
let customersPageIndex = 0;
let compactDashboardMode = window.innerWidth <= 900;

function isCompactDashboard() {
    return window.innerWidth <= 900;
}

function showDashboardSection(sectionId = 'overview') {
    const compact = isCompactDashboard();
    dashboardShell.classList.toggle('compact-view', compact);
    dashboardShell.classList.toggle('show-overview', sectionId === 'overview');
    document.querySelectorAll('.sidebar-link').forEach(link =>
        link.classList.toggle('active', link.getAttribute('href') === `#${sectionId}`));
    if (!compact) {
        adminOverviewContent.classList.remove('compact-hidden');
        transactionsSection.classList.remove('compact-hidden');
        lowerDashboardGrid.classList.remove('compact-hidden');
        document.querySelectorAll('.lower-grid > [id="inventory"], .lower-grid > [id="customers"], .lower-grid > [id="staff"]')
            .forEach(section => section.classList.remove('compact-hidden'));
        return;
    }

    adminOverviewContent.classList.toggle('compact-hidden', sectionId !== 'overview');
    transactionsSection.classList.toggle('compact-hidden', sectionId !== 'transactions');
    lowerDashboardGrid.classList.toggle('compact-hidden',
        sectionId !== 'inventory' && sectionId !== 'customers' && sectionId !== 'staff');
    document.querySelectorAll('.lower-grid > [id="inventory"], .lower-grid > [id="customers"], .lower-grid > [id="staff"]')
        .forEach(section => section.classList.toggle('compact-hidden', section.id !== sectionId));

    if (sectionId === 'overview') {
        window.scrollTo({ top: 0, behavior: 'smooth' });
    } else {
        document.getElementById(sectionId)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }

}

function syncDashboardViewport() {
    const compact = isCompactDashboard();
    if (compact === compactDashboardMode
            && dashboardShell.classList.contains('compact-view') === compact) return;
    compactDashboardMode = compact;
    showDashboardSection('overview');
}

const orderStatuses = ['PENDING', 'CONFIRMED', 'PACKED', 'SHIPPED', 'OUT_FOR_DELIVERY', 'DELIVERED', 'CANCELLED'];
const chartColors = ['#ff76a4', '#b795c7', '#78b9a2', '#7aa9c7', '#e7b66b', '#d891ad', '#9d8b98'];
const currencyFormat = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 });

function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>"']/g, character => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    })[character]);
}

function getSession() {
    try { return JSON.parse(localStorage.getItem('tapAdminUser') || 'null'); } catch { return null; }
}

function displayLogin(message = '') {
    adminLogin.classList.remove('hidden');
    dashboardShell.classList.add('hidden');
    loginFeedback.textContent = message;
}

function showLoginForm() {
    loginForm.classList.remove('hidden');
    adminForgotForm.classList.add('hidden');
    adminResetForm.classList.add('hidden');
    showForgotPasswordButton.classList.remove('hidden');
}

async function loadAdminBootstrapStatus() {
    try {
        const registrationAvailable = await window.tapApi.auth.adminRegistrationStatus();
        if (registrationAvailable === true) {
            adminBootstrapPanel.classList.remove('hidden');
            adminBootstrapMessage.textContent =
                'No administrator account exists yet. Register the first admin to secure the dashboard.';
            adminBootstrapForm.classList.remove('hidden');
            return;
        }
    } catch (error) {
        adminBootstrapPanel.classList.remove('hidden');
        adminBootstrapMessage.textContent =
            `First-admin registration status could not be checked: ${error.message}`;
    }
}

function setDashboardUser(user) {
    document.getElementById('adminName').textContent = user.name || user.fullName || 'Administrator';
    document.getElementById('adminEmailLabel').textContent = user.email || '';
    document.getElementById('adminAvatar').textContent = (user.name || user.fullName || 'A').trim().charAt(0).toUpperCase();
}

async function showDashboard(user) {
    adminLogin.classList.add('hidden');
    dashboardShell.classList.remove('hidden');
    setDashboardUser(user);
    showDashboardSection('overview');
    await loadDashboard();
}

function showDashboardError(message) {
    dashboardAlert.textContent = message;
    dashboardAlert.classList.remove('hidden');
}

function formatCurrency(value) {
    return currencyFormat.format(Number(value) || 0);
}

function formatDate(value, options = { day: '2-digit', month: 'short', year: 'numeric' }) {
    if (!value) return '—';
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? '—' : date.toLocaleDateString('en-IN', options);
}

function renderMetrics(data) {
    document.getElementById('paidRevenue').textContent = formatCurrency(data.paidRevenue);
    document.getElementById('totalOrders').textContent = Number(data.totalOrders || 0).toLocaleString('en-IN');
    document.getElementById('pendingOrders').textContent = Number(data.pendingOrders || 0).toLocaleString('en-IN');
    document.getElementById('totalCustomers').textContent = Number(data.totalCustomers || 0).toLocaleString('en-IN');
    document.getElementById('activeProducts').textContent = Number(data.activeProducts || 0).toLocaleString('en-IN');
    document.getElementById('lowStockProducts').textContent = Number(data.lowStockProducts || 0).toLocaleString('en-IN');
}

function renderSalesChart(data) {
    const canvas = document.getElementById('salesChart');
    if (salesChart) salesChart.destroy();
    if (!window.Chart) {
        canvas.insertAdjacentHTML('afterend', '<p class="table-empty">Charts are unavailable. Check your internet connection and refresh.</p>');
        return;
    }

    salesChart = new Chart(canvas, {
        type: 'bar',
        data: {
            labels: (data.dailySales || []).map(day => formatDate(day.date, { weekday: 'short', day: 'numeric' })),
            datasets: [
                {
                    type: 'line',
                    label: 'Paid revenue',
                    data: (data.dailySales || []).map(day => Number(day.revenue) || 0),
                    borderColor: '#ff76a4',
                    backgroundColor: 'rgba(255, 118, 164, 0.14)',
                    pointBackgroundColor: '#ff76a4',
                    pointRadius: 3,
                    pointHoverRadius: 5,
                    tension: 0.38,
                    fill: true,
                    yAxisID: 'revenue'
                },
                {
                    type: 'bar',
                    label: 'Orders',
                    data: (data.dailySales || []).map(day => Number(day.orders) || 0),
                    backgroundColor: 'rgba(183, 149, 199, 0.65)',
                    borderRadius: 6,
                    maxBarThickness: 18,
                    yAxisID: 'orders'
                }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            interaction: { mode: 'index', intersect: false },
            plugins: {
                legend: { display: false },
                tooltip: {
                    backgroundColor: '#30252f',
                    padding: 11,
                    callbacks: {
                        label: context => context.dataset.yAxisID === 'revenue'
                            ? ` Paid revenue: ${formatCurrency(context.raw)}`
                            : ` Orders: ${context.raw}`
                    }
                }
            },
            scales: {
                x: { grid: { display: false }, ticks: { color: '#94848f', font: { size: 10 } } },
                revenue: {
                    type: 'linear',
                    position: 'left',
                    beginAtZero: true,
                    grid: { color: 'rgba(124, 97, 108, 0.08)' },
                    ticks: { color: '#94848f', font: { size: 9 }, callback: value => `₹${Number(value).toLocaleString('en-IN')}` }
                },
                orders: { type: 'linear', position: 'right', beginAtZero: true, grid: { display: false }, ticks: { display: false } }
            }
        }
    });
}

function renderDoughnut(canvasId, legendId, items, instance, label) {
    const canvas = document.getElementById(canvasId);
    const values = items || [];
    if (instance) instance.destroy();
    if (!window.Chart) return null;

    const nonZeroItems = values.filter(item => Number(item.count) > 0);
    const chart = new Chart(canvas, {
        type: 'doughnut',
        data: {
            labels: nonZeroItems.map(item => item.label.replaceAll('_', ' ')),
            datasets: [{
                data: nonZeroItems.map(item => Number(item.count)),
                backgroundColor: nonZeroItems.map((_, index) => chartColors[index % chartColors.length]),
                borderWidth: 3,
                borderColor: '#fff',
                hoverOffset: 5
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            cutout: '69%',
            plugins: {
                legend: { display: false },
                tooltip: { callbacks: { label: context => ` ${context.label}: ${context.raw}` } }
            }
        },
        plugins: [{
            id: `center-${canvasId}`,
            afterDraw(chartInstance) {
                const { ctx, chartArea } = chartInstance;
                if (!chartArea) return;
                const total = nonZeroItems.reduce((sum, item) => sum + Number(item.count), 0);
                const centerX = (chartArea.left + chartArea.right) / 2;
                const centerY = (chartArea.top + chartArea.bottom) / 2;
                ctx.save();
                ctx.textAlign = 'center';
                ctx.fillStyle = '#30252f';
                ctx.font = '700 19px Inter, Segoe UI, sans-serif';
                ctx.fillText(String(total), centerX, centerY + 1);
                ctx.fillStyle = '#94848f';
                ctx.font = '10px Inter, Segoe UI, sans-serif';
                ctx.fillText(label, centerX, centerY + 17);
                ctx.restore();
            }
        }]
    });

    const legend = document.getElementById(legendId);
    legend.replaceChildren();
    nonZeroItems.forEach((item, index) => {
        const entry = document.createElement('span');
        const dot = document.createElement('i');
        dot.style.backgroundColor = chartColors[index % chartColors.length];
        const text = document.createTextNode(`${item.label.replaceAll('_', ' ')} · ${item.count}`);
        entry.append(dot, text);
        legend.append(entry);
    });
    if (!nonZeroItems.length) {
        const empty = document.createElement('span');
        empty.textContent = 'No recorded activity';
        legend.append(empty);
    }
    return chart;
}

function renderOrders(pageData) {
    const tbody = document.getElementById('ordersTable');
    const orders = pageData.content || [];
    document.getElementById('ordersPageSummary').textContent =
        `${orders.length ? pageData.page * pageData.size + 1 : 0}–${pageData.page * pageData.size + orders.length} of ${pageData.totalElements} transactions`;
    document.getElementById('ordersPageLabel').textContent = `Page ${pageData.page + 1} of ${Math.max(pageData.totalPages, 1)}`;
    document.getElementById('ordersPrevious').disabled = pageData.first === true || pageData.page === 0;
    document.getElementById('ordersNext').disabled = pageData.last === true;
    if (!orders?.length) {
        tbody.innerHTML = `<tr><td class="table-empty" colspan="6">${pageData.totalElements ? 'No transactions on this page.' : 'No orders have been placed yet.'}</td></tr>`;
        return;
    }

    tbody.innerHTML = orders.map(order => `
        <tr data-order-search="${escapeHtml(`${order.orderNumber} ${order.customerName} ${order.customerEmail} ${order.status}`.toLowerCase())}">
            <td><span class="order-number">${escapeHtml(order.orderNumber)}</span></td>
            <td><div class="customer-cell"><strong>${escapeHtml(order.customerName)}</strong><span>${escapeHtml(order.customerEmail)}</span></div></td>
            <td class="date-cell">${escapeHtml(formatDate(order.placedAt, { day: '2-digit', month: 'short' }))}</td>
            <td><span class="payment-pill">${escapeHtml(order.paymentMethod || '—')} · ${escapeHtml(order.paymentStatus || '—')}</span></td>
            <td class="amount-cell">${escapeHtml(formatCurrency(order.total))}</td>
            <td>
                <select class="status-select" data-order-id="${escapeHtml(order.id)}" aria-label="Update status for ${escapeHtml(order.orderNumber)}">
                    ${orderStatuses.map(status => `<option value="${status}" ${status === order.status ? 'selected' : ''}>${status.replaceAll('_', ' ')}</option>`).join('')}
                </select>
            </td>
        </tr>
    `).join('');

    tbody.querySelectorAll('.status-select').forEach(select => {
        select.addEventListener('change', async () => {
            const previousStatus = orders.find(order => String(order.id) === select.dataset.orderId)?.status;
            select.disabled = true;
            dashboardAlert.classList.add('hidden');
            try {
                await window.tapApi.orders.updateStatus(select.dataset.orderId, select.value);
                await loadDashboard();
            } catch (error) {
                showDashboardError(`Order status could not be updated: ${error.message}`);
                if (previousStatus) select.value = previousStatus;
            } finally {
                select.disabled = false;
            }
        });
    });
    filterOrders();
}

function safeImageUrl(value) {
    if (!value) return '';
    try {
        const imagePath = String(value).trim();
        const url = /^https?:\/\//i.test(imagePath)
            ? new URL(imagePath)
            : new URL(`/${imagePath.replace(/^(?:\.\.\/|\.\/)+/, '').replace(/^\/+/, '')}`, window.location.origin);
        return ['http:', 'https:'].includes(url.protocol) ? url.href : '';
    } catch {
        return '';
    }
}

function renderInventory(pageData) {
    const tbody = document.getElementById('inventoryTable');
    const products = pageData.content || [];
    document.getElementById('inventoryCount').textContent = `${Number(pageData.totalElements || 0)} active products`;
    document.getElementById('inventoryPageSummary').textContent =
        `${products.length ? pageData.page * pageData.size + 1 : 0}–${pageData.page * pageData.size + products.length} of ${pageData.totalElements} products`;
    document.getElementById('inventoryPageLabel').textContent = `Page ${pageData.page + 1} of ${Math.max(pageData.totalPages, 1)}`;
    document.getElementById('inventoryPrevious').disabled = pageData.first === true || pageData.page === 0;
    document.getElementById('inventoryNext').disabled = pageData.last === true;
    if (!products?.length) {
        tbody.innerHTML = '<tr><td class="table-empty" colspan="4">No product inventory is available.</td></tr>';
        return;
    }
    tbody.innerHTML = products.map(product => {
        const image = safeImageUrl(product.imageUrl);
        const visual = image
            ? `<img src="${escapeHtml(image)}" alt="${escapeHtml(product.name)}" loading="lazy" data-inventory-image>`
            : '<span class="inventory-placeholder" aria-hidden="true"></span>';
        const stock = Number(product.stock) || 0;
        return `<tr>
            <td><div class="inventory-product">${visual}<strong>${escapeHtml(product.name)}</strong></div></td>
            <td>${escapeHtml(product.category || '—')}</td>
            <td>${escapeHtml(formatCurrency(product.price))}</td>
            <td><span class="stock-level ${stock <= 5 ? 'low' : ''}">${stock}</span></td>
        </tr>`;
    }).join('');

    tbody.querySelectorAll('img[data-inventory-image]').forEach(image => {
        image.addEventListener('error', () => {
            const placeholder = document.createElement('span');
            placeholder.className = 'inventory-placeholder';
            placeholder.setAttribute('aria-hidden', 'true');
            image.replaceWith(placeholder);
        }, { once: true });
    });
}

function renderCustomers(pageData) {
    const container = document.getElementById('customersList');
    const customers = pageData.content || [];
    document.getElementById('customersPageSummary').textContent =
        `${customers.length ? pageData.page * pageData.size + 1 : 0}–${pageData.page * pageData.size + customers.length} of ${pageData.totalElements} customers`;
    document.getElementById('customersPageLabel').textContent = `Page ${pageData.page + 1} of ${Math.max(pageData.totalPages, 1)}`;
    document.getElementById('customersPrevious').disabled = pageData.first === true || pageData.page === 0;
    document.getElementById('customersNext').disabled = pageData.last === true;
    if (!customers?.length) {
        container.innerHTML = '<p class="table-empty">No customer accounts yet.</p>';
        return;
    }
    container.innerHTML = customers.map(customer => {
        const initials = String(customer.name || 'C').split(/\s+/).slice(0, 2).map(part => part.charAt(0)).join('').toUpperCase();
        return `<div class="customer-row">
            <span class="customer-initials">${escapeHtml(initials)}</span>
            <div class="customer-copy"><strong>${escapeHtml(customer.name)}</strong><span>${escapeHtml(customer.email)}</span></div>
            <span class="customer-date">${escapeHtml(formatDate(customer.joinedAt, { month: 'short', day: 'numeric' }))}</span>
        </div>`;
    }).join('');
}

async function loadOrdersPage(page) {
    try {
        const result = await window.tapApi.admin.orders(page, 20);
        ordersPageIndex = result.page;
        renderOrders(result);
    } catch (error) {
        showDashboardError(`Transactions could not be loaded: ${error.message}`);
    }
}

async function loadDeliveryAuditEvents() {
    const tbody = document.getElementById('deliveryAuditEvents');
    try {
        const events = await window.tapApi.admin.deliveryEvents();
        if (!events.length) {
            tbody.innerHTML = '<tr><td class="table-empty" colspan="5">No owner or warehouse decisions have been recorded.</td></tr>';
            return;
        }
        tbody.innerHTML = events.map(event => `
            <tr>
                <td><span class="order-number">${escapeHtml(event.orderNumber || '—')}</span></td>
                <td>${escapeHtml(event.eventType.replaceAll('_', ' '))}</td>
                <td>${escapeHtml(event.actorName || 'System')}</td>
                <td class="date-cell">${escapeHtml(formatDate(event.createdAt, {
                    day: '2-digit', month: 'short', year: 'numeric',
                    hour: '2-digit', minute: '2-digit'
                }))}</td>
                <td>${escapeHtml(event.notes || event.outcome || '—')}</td>
            </tr>
        `).join('');
    } catch (error) {
        tbody.innerHTML = `<tr><td class="table-empty" colspan="5">Audited activity could not be loaded: ${escapeHtml(error.message)}</td></tr>`;
        showDashboardError(`Delivery decision history could not be loaded: ${error.message}`);
    }
}

async function loadInventoryPage(page) {
    try {
        const result = await window.tapApi.admin.inventory(page, 20);
        inventoryPageIndex = result.page;
        renderInventory(result);
    } catch (error) {
        showDashboardError(`Inventory could not be loaded: ${error.message}`);
    }
}

async function loadCustomersPage(page) {
    try {
        const result = await window.tapApi.admin.customers(page, 20);
        customersPageIndex = result.page;
        renderCustomers(result);
    } catch (error) {
        showDashboardError(`Customer records could not be loaded: ${error.message}`);
    }
}

async function loadStaffDirectory() {
    const container = document.getElementById('staffList');
    try {
        const users = await window.tapApi.admin.users();
        const admins = users.filter(user => user.role === 'ADMIN');
        const storeOperators = users.filter(user => user.role === 'WAREHOUSE_OPERATOR');
        const owners = users.filter(user => user.role === 'OWNER');
        const deliveryAgents = users.filter(user => user.role === 'DELIVERY_AGENT');
        const renderStaffRows = staff => staff.length ? staff.map(user => {
            const name = user.fullName || user.email || 'Unnamed account';
            const initials = name.trim().split(/\s+/).slice(0, 2)
                .map(part => part.charAt(0)).join('').toUpperCase();
            const workspace = user.role === 'OWNER'
                ? '<a href="Operations.html#owner-orders">Open owner operations</a>'
                : user.role === 'DELIVERY_AGENT'
                    ? '<a href="Delivery.html">Open delivery workspace</a>'
                    : '';
            return `<div class="staff-row">
                <span class="customer-initials">${escapeHtml(initials)}</span>
                <div class="customer-copy"><strong>${escapeHtml(name)}</strong><span>${escapeHtml(user.email || 'No email')}</span></div>
                <span class="staff-role">${escapeHtml(user.role === 'DELIVERY_AGENT'
                    ? user.email || 'No email'
                    : user.role.replaceAll('_', ' '))}</span>
                ${workspace}
            </div>`;
        }).join('') : '<p class="staff-empty">No accounts in this group yet.</p>';

        const renderStaffGroup = (id, icon, iconClass, title, description, staff) => `
            <section class="staff-group" aria-labelledby="${id}">
                <header class="staff-group-heading">
                    <div class="staff-group-icon ${iconClass}" aria-hidden="true">${icon}</div>
                    <div><h3 id="${id}">${title}</h3><p>${description}</p></div>
                    <span class="staff-group-count">${staff.length}</span>
                </header>
                <div class="staff-group-list">${renderStaffRows(staff)}</div>
            </section>`;

        const agentsByWarehouse = new Map();
        deliveryAgents.forEach(agent => {
            const warehouseKey = agent.deliveryWarehouseId == null
                ? 'unassigned'
                : String(agent.deliveryWarehouseId);
            if (!agentsByWarehouse.has(warehouseKey)) {
                agentsByWarehouse.set(warehouseKey, {
                    name: agent.deliveryWarehouseName || 'Warehouse not assigned',
                    agents: []
                });
            }
            agentsByWarehouse.get(warehouseKey).agents.push(agent);
        });
        const deliveryAgentGroups = [...agentsByWarehouse.entries()]
            .sort(([, first], [, second]) => first.name.localeCompare(second.name))
            .map(([warehouseId, group], index) => renderStaffGroup(
                `deliveryTeamHeading-${index}`,
                'D',
                'delivery-team-icon',
                `Delivery agents · ${escapeHtml(group.name)}`,
                warehouseId === 'unassigned'
                    ? 'Agents without a warehouse registration'
                    : 'Agents registered under this warehouse',
                group.agents
            ));

        container.innerHTML = [
            renderStaffGroup('adminsHeading', 'A', 'admin-team-icon', 'Admins',
                'Administrator dashboard access', admins),
            ...(deliveryAgentGroups.length ? deliveryAgentGroups : [
                renderStaffGroup('deliveryTeamHeading', 'D', 'delivery-team-icon', 'Delivery agents',
                    'Delivery team and route workspace', deliveryAgents)
            ]),
            renderStaffGroup('storeOperatorsHeading', 'S', 'operator-team-icon', 'Store operators',
                'Warehouse and store operations', storeOperators),
            renderStaffGroup('ownersHeading', 'O', 'owner-team-icon', 'Owner operations',
                'Store owners and their workspace', owners)
        ].join('');
    } catch (error) {
        container.innerHTML = `<p class="table-empty">Admin and delivery accounts could not be loaded: ${escapeHtml(error.message)}</p>`;
    }
}

async function loadWarehouses() {
    const container = document.getElementById('warehouseList');
    try {
        const warehouses = await window.tapApi.admin.warehouses();
        container.innerHTML = warehouses.length ? warehouses.map(warehouse => `
            <article class="warehouse-approval-card">
                <div class="warehouse-card-content">
                    <div class="warehouse-card-heading">
                        <div>
                            <span class="warehouse-card-kicker">Warehouse hub</span>
                            <h3>${escapeHtml(warehouse.name)}</h3>
                        </div>
                        <span class="warehouse-status ${warehouse.active ? 'is-approved' : 'is-pending'}">
                            <span aria-hidden="true"></span>${warehouse.active ? 'Approved' : 'Awaiting approval'}
                        </span>
                    </div>
                    <div class="warehouse-card-details">
                        <div class="warehouse-detail">
                            <span class="warehouse-detail-label">Operator</span>
                            <strong>${escapeHtml(warehouse.operatorName || 'No operator')}</strong>
                            <span>${escapeHtml(warehouse.operatorEmail || 'No email')}</span>
                        </div>
                        <div class="warehouse-detail">
                            <span class="warehouse-detail-label">Warehouse address</span>
                            <strong>${escapeHtml(warehouse.street)}</strong>
                            <span>${escapeHtml(warehouse.city)}, ${escapeHtml(warehouse.state)} ${escapeHtml(warehouse.postalCode)}</span>
                        </div>
                        <div class="warehouse-detail warehouse-coordinate-detail">
                            <span class="warehouse-detail-label">Exact coordinates</span>
                            <code>${escapeHtml(warehouse.latitude)}, ${escapeHtml(warehouse.longitude)}</code>
                        </div>
                    </div>
                    <div class="warehouse-card-actions">
                    ${window.TapMaps.validCoordinates(warehouse.latitude, warehouse.longitude)
                        ? `<a class="warehouse-map-button"
                              href="${escapeHtml(window.TapMaps.locationUrl(warehouse.latitude, warehouse.longitude))}"
                              target="_blank" rel="noopener noreferrer"
                              aria-label="Open ${escapeHtml(warehouse.name)} exact location in Google Maps">
                                <span>Open location in Google Maps</span><span class="warehouse-map-arrow" aria-hidden="true">↗</span>
                          </a>`
                        : '<span class="warehouse-location-unavailable">Location coordinates are unavailable.</span>'}
                    </div>
                </div>
                <div class="warehouse-card-controls">
                    <button class="warehouse-action-button ${warehouse.active ? 'deactivate' : 'approve'}" type="button"
                            data-warehouse-activation="${warehouse.id}" data-active="${warehouse.active ? 'false' : 'true'}">
                        ${warehouse.active ? 'Deactivate' : 'Approve warehouse'}
                    </button>
                </div>
            </article>
        `).join('') : '<p class="table-empty">No warehouse registrations have been submitted.</p>';
        renderWarehouseMap(warehouses);
    } catch (error) {
        container.innerHTML = `<p class="table-empty">Warehouse registrations could not be loaded: ${escapeHtml(error.message)}</p>`;
    }
}

function renderWarehouseMap(warehouses) {
    const mapLinks = warehouses.filter(warehouse =>
        window.TapMaps.validCoordinates(warehouse.latitude, warehouse.longitude));
    document.getElementById('adminWarehouseMap').innerHTML = mapLinks.length
        ? `<div class="warehouse-map-shortcuts-heading">
                <span class="warehouse-map-shortcuts-icon" aria-hidden="true">↗</span>
                <div><strong>Warehouse locations</strong><p>Open a hub’s exact coordinates in Google Maps.</p></div>
           </div>
           <div class="warehouse-map-shortcuts">${mapLinks.map(warehouse =>
                `<a class="warehouse-map-shortcut"
                    href="${escapeHtml(window.TapMaps.locationUrl(warehouse.latitude, warehouse.longitude))}"
                    target="_blank" rel="noopener noreferrer"
                    aria-label="Open ${escapeHtml(warehouse.name)} exact coordinates in Google Maps">
                        <span>${escapeHtml(warehouse.name)}</span><span aria-hidden="true">↗</span>
                 </a>`
            ).join('')}</div>`
        : '<p>No valid warehouse coordinates are available yet.</p>';
}

function filterOrders() {
    const query = orderSearch.value.trim().toLocaleLowerCase();
    document.querySelectorAll('#ordersTable tr[data-order-search]').forEach(row => {
        row.classList.toggle('hidden', !row.dataset.orderSearch.includes(query));
    });
}

async function loadDashboard() {
    dashboardAlert.classList.add('hidden');
    try {
        const data = await window.tapApi.admin.dashboard();
        dashboardData = data;
        renderMetrics(data);
        renderSalesChart(data);
        statusChart = renderDoughnut('statusChart', 'statusLegend', data.orderStatuses, statusChart, 'orders');
        paymentChart = renderDoughnut('paymentChart', 'paymentLegend', data.paymentMethods, paymentChart, 'orders');
        await Promise.all([
            loadOrdersPage(ordersPageIndex),
            loadDeliveryAuditEvents(),
            loadInventoryPage(inventoryPageIndex),
            loadCustomersPage(customersPageIndex),
            loadStaffDirectory(),
            loadWarehouses()
        ]);
    } catch (error) {
        showDashboardError(`Dashboard data could not be loaded: ${error.message}`);
    }
}

loginForm.addEventListener('submit', async event => {
    event.preventDefault();
    loginFeedback.textContent = '';
    loginButton.disabled = true;
    loginButton.textContent = 'Checking access…';
    try {
        const email = document.getElementById('adminEmail').value.trim().toLowerCase();
        const password = document.getElementById('adminPassword').value;
        const user = await window.tapApi.auth.adminLogin(email, password);
        if (user.role !== 'ADMIN') {
            window.tapApi.clearToken();
            loginFeedback.textContent = 'Administrator access is required.';
            return;
        }
        window.tapApi.setToken(user.token);
        const session = {
            name: user.fullName || user.name || user.email.split('@')[0],
            email: user.email,
            role: user.role
        };
        localStorage.setItem('tapAdminUser', JSON.stringify(session));
        await showDashboard(session);
    } catch (error) {
        loginFeedback.textContent = error.message || 'Sign in failed. Please try again.';
    } finally {
        loginButton.disabled = false;
        loginButton.textContent = 'Continue to dashboard';
    }
});

adminBootstrapForm.addEventListener('submit', async event => {
    event.preventDefault();
    const emailInput = document.getElementById('bootstrapAdminEmail');
    const passwordInput = document.getElementById('bootstrapAdminPassword');
    const confirmationInput = document.getElementById('confirmBootstrapAdminPassword');
    const button = document.getElementById('bootstrapAdminButton');
    const email = emailInput.value.trim().toLowerCase();
    const password = passwordInput.value;
    adminBootstrapFeedback.textContent = '';
    adminBootstrapFeedback.classList.remove('feedback-success');
    if (password !== confirmationInput.value) {
        adminBootstrapFeedback.textContent = 'The passwords do not match.';
        confirmationInput.focus();
        return;
    }

    button.disabled = true;
    button.textContent = 'Registering…';
    try {
        const account = await window.tapApi.auth.registerInitialAdmin(email, password);
        adminBootstrapForm.reset();
        adminBootstrapForm.classList.add('hidden');
        document.getElementById('adminEmail').value = account.email;
        adminBootstrapMessage.textContent =
            'The first administrator account is ready. Sign in above with your new credentials.';
        adminBootstrapFeedback.textContent = 'Administrator account registered successfully.';
        adminBootstrapFeedback.classList.add('feedback-success');
    } catch (error) {
        adminBootstrapFeedback.textContent =
            error.message || 'Administrator registration failed. Please try again.';
        if (error.message && error.message.includes('registration is closed')) {
            adminBootstrapForm.classList.add('hidden');
            adminBootstrapMessage.textContent = error.message;
        }
    } finally {
        button.disabled = false;
        button.textContent = 'Register first administrator';
    }
});

showForgotPasswordButton.addEventListener('click', () => {
    loginFeedback.textContent = '';
    loginForm.classList.add('hidden');
    adminForgotForm.classList.remove('hidden');
    showForgotPasswordButton.classList.add('hidden');
    document.getElementById('forgotAdminEmail').value =
        document.getElementById('adminEmail').value.trim().toLowerCase();
});

document.getElementById('backToAdminLogin').addEventListener('click', () => {
    loginFeedback.textContent = '';
    showLoginForm();
});

adminForgotForm.addEventListener('submit', async event => {
    event.preventDefault();
    const button = document.getElementById('forgotPasswordButton');
    button.disabled = true;
    button.textContent = 'Sending…';
    loginFeedback.textContent = '';
    try {
        const email = document.getElementById('forgotAdminEmail').value.trim().toLowerCase();
        const result = await window.tapApi.auth.requestAdminPasswordReset(email);
        loginFeedback.textContent = result?.message ||
            'If an administrator account exists for that email, a password reset link has been sent.';
        loginFeedback.classList.add('feedback-success');
    } catch (error) {
        loginFeedback.textContent = error.message || 'The reset email could not be sent. Please try again.';
        loginFeedback.classList.remove('feedback-success');
    } finally {
        button.disabled = false;
        button.textContent = 'Send reset link';
    }
});

adminResetForm.addEventListener('submit', async event => {
    event.preventDefault();
    const button = document.getElementById('resetPasswordButton');
    const password = document.getElementById('newAdminPassword').value;
    const confirmation = document.getElementById('confirmAdminPassword').value;
    const token = document.getElementById('adminResetToken').value;
    loginFeedback.textContent = '';
    loginFeedback.classList.remove('feedback-success');
    if (!token) {
        loginFeedback.textContent = 'This reset link is incomplete. Request a new password reset email.';
        return;
    }
    if (password !== confirmation) {
        loginFeedback.textContent = 'The passwords do not match.';
        return;
    }

    button.disabled = true;
    button.textContent = 'Resetting…';
    try {
        const result = await window.tapApi.auth.resetAdminPassword(token, password);
        adminResetForm.reset();
        adminResetForm.classList.add('hidden');
        adminForgotForm.classList.add('hidden');
        loginForm.classList.remove('hidden');
        showForgotPasswordButton.classList.remove('hidden');
        loginFeedback.textContent = result?.message || 'Password reset successfully. Sign in with your new password.';
        loginFeedback.classList.add('feedback-success');
        window.history.replaceState({}, document.title, window.location.pathname);
    } catch (error) {
        loginFeedback.textContent = error.message || 'The reset link could not be used. Request a new link.';
    } finally {
        button.disabled = false;
        button.textContent = 'Reset password';
    }
});

document.getElementById('warehouseList').addEventListener('click', async event => {
    const button = event.target.closest('[data-warehouse-activation]');
    if (!button) return;
    button.disabled = true;
    try {
        await window.tapApi.admin.setWarehouseActive(
            Number(button.dataset.warehouseActivation),
            button.dataset.active === 'true'
        );
        await loadWarehouses();
        showDashboardError('');
    } catch (error) {
        showDashboardError(`Warehouse status could not be updated: ${error.message}`);
        button.disabled = false;
    }
});
document.getElementById('ordersPrevious').addEventListener('click', () => loadOrdersPage(Math.max(0, ordersPageIndex - 1)));
document.getElementById('ordersNext').addEventListener('click', () => loadOrdersPage(ordersPageIndex + 1));
document.getElementById('inventoryPrevious').addEventListener('click', () => loadInventoryPage(Math.max(0, inventoryPageIndex - 1)));
document.getElementById('inventoryNext').addEventListener('click', () => loadInventoryPage(inventoryPageIndex + 1));
document.getElementById('customersPrevious').addEventListener('click', () => loadCustomersPage(Math.max(0, customersPageIndex - 1)));
document.getElementById('customersNext').addEventListener('click', () => loadCustomersPage(customersPageIndex + 1));
document.getElementById('adminLogout').addEventListener('click', () => {
    window.tapApi.clearToken();
    localStorage.removeItem('tapAdminUser');
    displayLogin('You have signed out.');
});
orderSearch.addEventListener('input', filterOrders);
document.querySelectorAll('.sidebar-link').forEach(link => {
    link.addEventListener('click', event => {
        const sectionId = link.getAttribute('href').slice(1);
        if (isCompactDashboard()) {
            event.preventDefault();
            showDashboardSection(sectionId);
            return;
        }
        document.querySelectorAll('.sidebar-link').forEach(item =>
            item.classList.toggle('active', item === link));
    });
});

compactDashboard.addEventListener('change', syncDashboardViewport);
window.addEventListener('resize', syncDashboardViewport);

const resetToken = new URLSearchParams(window.location.search).get('resetToken');
loadAdminBootstrapStatus();
if (resetToken) {
    document.getElementById('adminResetToken').value = resetToken;
    loginForm.classList.add('hidden');
    adminForgotForm.classList.add('hidden');
    adminResetForm.classList.remove('hidden');
    showForgotPasswordButton.classList.add('hidden');
} else {
    const currentUser = getSession();
    if (currentUser?.role === 'ADMIN' && window.tapApi.getToken()) {
        showDashboard(currentUser);
    }
}
