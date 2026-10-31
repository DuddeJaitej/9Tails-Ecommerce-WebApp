/**
 * api.js  —  Centralized HTTP client for 9Tails ↔ Spring Boot API
 * Base URL: same host as the frontend on port 8081, unless explicitly overridden.
 * All functions return the `data` field from ApiResponse, or throw on error.
 */

(function (global) {
    'use strict';

    const API_ORIGIN = global.TAP_API_BASE_URL
        || `${global.location.protocol}//${global.location.hostname}:8081`;
    const BASE = `${API_ORIGIN.replace(/\/+$/, '')}/api`;
    const TOKEN_KEY = global.TAP_API_TOKEN_KEY || 'tap_jwt';
    const ACTIVE_TOKEN_KEY = 'tap_api_active_token_key';
    const ROLE_SCOPED_TOKENS = global.TAP_API_ROLE_SCOPED_TOKENS === true;
    let activeTokenKey = ROLE_SCOPED_TOKENS
        ? sessionStorage.getItem(ACTIVE_TOKEN_KEY) || TOKEN_KEY
        : TOKEN_KEY;

    // ─── Token helpers ────────────────────────────────────────────────────────
    function getToken() { return localStorage.getItem(activeTokenKey) || ''; }
    function setToken(t, storageKey = activeTokenKey) {
        const key = ROLE_SCOPED_TOKENS ? storageKey : TOKEN_KEY;
        if (ROLE_SCOPED_TOKENS) {
            activeTokenKey = key;
            sessionStorage.setItem(ACTIVE_TOKEN_KEY, key);
        }
        localStorage.setItem(key, t);
    }
    function clearToken(storageKey = activeTokenKey) {
        const key = ROLE_SCOPED_TOKENS ? storageKey : TOKEN_KEY;
        localStorage.removeItem(key);
        if (ROLE_SCOPED_TOKENS && key === activeTokenKey) {
            activeTokenKey = TOKEN_KEY;
            sessionStorage.removeItem(ACTIVE_TOKEN_KEY);
        }
    }

    function authHeaders() {
        const t = getToken();
        return t ? {
            'Authorization': `Bearer ${t}`,
            'Content-Type': 'application/json'
        } : {
            'Content-Type': 'application/json'
        };
    }

    function authErrorMessage(path, status, response) {
        const message = typeof response?.message === 'string' ? response.message : '';
        const isLogin = /\/(?:operations\/login|admin\/login|login)$/.test(path);
        const isRegistration = /\/(?:register|bootstrap)$/.test(path);

        if (/initial administrator registration is closed/i.test(message)) {
            return 'The first administrator account is already set up. Please sign in.';
        }
        if (isRegistration && /email already registered/i.test(message)) {
            return 'An account with this email already exists. Try signing in instead.';
        }
        if (status === 401) {
            if (/account is disabled/i.test(message)) {
                return 'This account is currently disabled. Please contact an administrator.';
            }
            if (path.endsWith('/google')) {
                return 'Google sign-in could not be completed. Please try again.';
            }
            return isLogin
                ? 'The email or password is incorrect. Please check them and try again.'
                : 'We could not verify your request. Please try again or request a new link.';
        }
        if (status === 409 && isRegistration) {
            return 'This account could not be created because it conflicts with an existing account. Please check your details.';
        }
        if (status === 403) {
            return 'You do not have permission to do this. Contact an administrator if you need access.';
        }
        if (status === 404) {
            return 'We could not find the requested information. Please refresh and try again.';
        }
        if (status === 410) {
            return 'This link has expired. Request a new one and try again.';
        }
        if (status === 429) {
            return 'Too many attempts. Please wait a moment and try again.';
        }
        if (status >= 500) {
            return 'Something went wrong on our end. Please try again in a moment.';
        }
        if (status === 400 && isLogin) {
            return 'Please enter a valid email address and password.';
        }
        if (status === 400 && isRegistration) {
            return 'Please check your details and try again. Make sure all required fields are filled in.';
        }
        if (status === 400) {
            return 'Some information is missing or invalid. Please check it and try again.';
        }
        return 'We could not complete your request. Please try again.';
    }

    // ─── Core fetch wrapper ──────────────────────────────────────────────────
    async function req(method, path, body) {
        const opts = { method, headers: authHeaders() };
        if (body !== undefined) opts.body = JSON.stringify(body);

        let res;
        try {
            res = await fetch(BASE + path, opts);
        } catch {
            if (path.startsWith('/auth/')) {
                throw new Error('We could not connect. Check your internet connection and try again.');
            }
            throw new Error('Cannot reach the server. Make sure Spring Boot is running on port 8081.');
        }

        const json = await res.json().catch(() => ({}));

        if (!res.ok) {
            if (path.startsWith('/auth/')) {
                throw new Error(authErrorMessage(path, res.status, json));
            }
            if (path.includes('/owner/fulfillments/') && path.endsWith('/pickup-location')
                    && res.status >= 500) {
                throw new Error(
                    'Pickup location could not be assigned because the backend returned an internal error. Restart Spring Boot from the backend folder to load the latest changes, then refresh Operations Studio. If it still fails, check the backend console for the logged exception.'
                );
            }
            if (res.status === 401 && !path.startsWith('/auth/')) {
                clearToken();
                global.dispatchEvent(new CustomEvent('tap:unauthorized', {
                    detail: { path }
                }));
            }
            if (res.status === 401) {
                throw new Error(json.message || 'Your session is invalid or has expired. Please sign in again.');
            }
            if (res.status === 403) {
                throw new Error(json.message || 'Your account is not authorized to perform this action.');
            }
            if (json.message === 'Validation failed' && json.data && typeof json.data === 'object') {
                const details = Object.entries(json.data)
                    .map(([field, message]) => `${field}: ${message}`)
                    .join('; ');
                throw new Error(details ? `Please correct these fields: ${details}` : json.message);
            }
            throw new Error(json.message || `HTTP ${res.status}`);
        }
        return json.data;          // unwrap ApiResponse<T>.data
    }

    async function getResource(path) {
        let response;
        try {
            response = await fetch(BASE + path, { method: 'GET', headers: authHeaders() });
        } catch {
            throw new Error('Cannot reach the server. Make sure Spring Boot is running on port 8081.');
        }
        if (!response.ok) {
            const json = await response.json().catch(() => ({}));
            if (response.status === 401) {
                clearToken();
                global.dispatchEvent(new CustomEvent('tap:unauthorized', { detail: { path } }));
            }
            throw new Error(json.message || `HTTP ${response.status}`);
        }
        return response.blob();
    }

    async function upload(path, file, fields = {}) {
        const headers = {};
        const token = getToken();
        if (token) headers.Authorization = `Bearer ${token}`;
        const formData = new FormData();
        formData.append('file', file);
        Object.entries(fields).forEach(([name, value]) => formData.append(name, value));

        let response;
        try {
            response = await fetch(BASE + path, { method: 'POST', headers, body: formData });
        } catch {
            throw new Error('Cannot reach the server. Make sure Spring Boot is running on port 8081.');
        }
        const json = await response.json().catch(() => ({}));
        if (!response.ok) {
            if (response.status === 401 && !path.startsWith('/auth/')) {
                clearToken();
                global.dispatchEvent(new CustomEvent('tap:unauthorized', { detail: { path } }));
            }
            throw new Error(json.message || `HTTP ${response.status}`);
        }
        return json.data;
    }

    async function getAllPages(fetchPage, pageSize = 100) {
        const all = [];
        let pageNumber = 0;
        while (true) {
            const page = await fetchPage(pageNumber, pageSize);
            if (Array.isArray(page)) return all.concat(page);

            if (!page || !Array.isArray(page.content)) {
                throw new Error('The server returned an invalid paginated response.');
            }

            const content = page.content;
            const hasTotalPages = Number.isInteger(page.totalPages);
            if (typeof page.last !== 'boolean' && !hasTotalPages) {
                throw new Error('The server returned an incomplete paginated response.');
            }
            all.push(...content);
            if (page.last === true || (hasTotalPages && pageNumber + 1 >= page.totalPages)) {
                return all;
            }
            if (content.length === 0) {
                throw new Error('The server returned an empty page before the order history was complete.');
            }
            pageNumber += 1;
        }
    }

    const api = {
        get:    (path)        => req('GET',    path),
        post:   (path, body)  => req('POST',   path, body),
        put:    (path, body)  => req('PUT',    path, body),
        patch:  (path, body)  => req('PATCH',  path, body),
        del:    (path)        => req('DELETE', path),
        getToken, setToken, clearToken,

        // ── Auth ──────────────────────────────────────────────────────────────
        auth: {
            register: (fullName, email, password) =>
                api.post('/auth/register', { fullName, email, password }),

            registerOwner: (fullName, email, password) =>
                api.post('/auth/owner/register', { fullName, email, password }),

            login: (email, password) =>
                api.post('/auth/login', { email, password }),

            operationsLogin: (email, password) =>
                api.post('/auth/operations/login', { email, password }),

            registerDelivery: (details) =>
                api.post('/auth/delivery/register', details),

            deliveryWarehouses: () => api.get('/auth/delivery/warehouses'),

            adminLogin: (email, password) =>
                api.post('/auth/admin/login', { email, password }),

            adminCreateFromDashboard: (email, password) =>
                api.post('/auth/admin/register', { email, password }),

            adminRegistrationStatus: () =>
                api.get('/auth/admin/registration-status'),

            registerInitialAdmin: (email, password) =>
                api.post('/auth/admin/bootstrap', { email, password }),

            requestAdminPasswordReset: (email) =>
                api.post('/auth/admin/forgot-password', { email }),

            resetAdminPassword: (token, password) =>
                api.post('/auth/admin/reset-password', { token, password }),

            requestCustomerPasswordReset: (email) =>
                api.post('/auth/forgot-password', { email }),

            resetCustomerPassword: (token, password) =>
                api.post('/auth/reset-password', { token, password }),

            google: (accessToken) =>
                api.post('/auth/google', { accessToken }),
        },

        // ── Products ─────────────────────────────────────────────────────────
        products: {
            list:     (page = 0, size = 20, sort = 'newest') =>
                api.get(`/products?page=${page}&size=${size}&sort=${sort}`),

            listAll:   (size = 100, sort = 'newest') =>
                getAllPages((page, pageSize) => api.products.list(page, pageSize, sort), size),

            reviews: (limit = 10) =>
                api.get(`/products/reviews?limit=${limit}`),

            byCategory: (cat, page = 0, size = 20, sort = 'newest') =>
                api.get(`/products/category/${encodeURIComponent(cat)}?page=${page}&size=${size}&sort=${sort}`),

            byCategoryAll: (cat, size = 100, sort = 'newest') =>
                getAllPages((page, pageSize) => api.products.byCategory(cat, page, pageSize, sort), size),

            search:   (q, page = 0, size = 20) =>
                api.get(`/products/search?q=${encodeURIComponent(q)}&page=${page}&size=${size}`),

            get:      (id) => api.get(`/products/${id}`),

            related:  (id) => api.get(`/products/${id}/related`),

            owned: (page = 0, size = 100) =>
                api.get(`/products/owned?page=${page}&size=${size}`),

            create: (product) => api.post('/products', product),

            update: (id, product) => api.put(`/products/${id}`, product),

            remove: (id) => api.del(`/products/${id}`),

            uploadImage: (file) => upload('/products/images', file),
        },

        // ── Categories ────────────────────────────────────────────────────────
        categories: {
            list: () => api.get('/categories'),
        },

        // ── Cart ─────────────────────────────────────────────────────────────
        cart: {
            get:    ()                      => api.get('/cart'),
            add:    (productId, qty = 1)    => api.post(`/cart?productId=${productId}&quantity=${qty}`),
            update: (productId, qty)        => api.put(`/cart/${productId}?quantity=${qty}`),
            remove: (productId)             => api.del(`/cart/${productId}`),
            clear:  ()                      => api.del('/cart'),
        },

        // ── Orders ───────────────────────────────────────────────────────────
        orders: {
            place:    (addressId, paymentMethod) =>
                api.post('/orders', { addressId, paymentMethod }),

            createRazorpayOrder: (addressId) =>
                api.post('/orders/razorpay/order', { addressId }),

            verifyRazorpayPayment: (paymentResult) =>
                api.post('/orders/razorpay/verify', {
                    razorpay_order_id: paymentResult.razorpay_order_id,
                    razorpay_payment_id: paymentResult.razorpay_payment_id,
                    razorpay_signature: paymentResult.razorpay_signature
                }),

            list:     (page = 0, size = 10) =>
                api.get(`/orders?page=${page}&size=${size}`),
            rateItem: (orderId, itemId, rating, reviewText) =>
                api.put(`/orders/${orderId}/items/${itemId}/rating`, { rating, reviewText }),

            listAll:  (size = 50) =>
                getAllPages((page, pageSize) => api.orders.list(page, pageSize), size),

            get:      (id)  => api.get(`/orders/${id}`),
            cancel:   (id)  => api.patch(`/orders/${id}/cancel`),
            cancelItem: (orderId, itemId) =>
                api.patch(`/orders/${orderId}/items/${itemId}/cancel`),
            createServiceRequest: (orderId, itemId, payload) =>
                api.post(`/orders/${orderId}/items/${itemId}/service-request`, payload),
            uploadEvidence: (file) => upload('/orders/evidence', file),
            updateStatus: (id, status) =>
                api.patch(`/orders/${id}/status?status=${encodeURIComponent(status)}`),
        },

        // ── Admin ────────────────────────────────────────────────────────────
        admin: {
            dashboard: () => api.get('/admin/dashboard'),
            orders: (page = 0, size = 20) => api.get(`/admin/orders?page=${page}&size=${size}`),
            inventory: (page = 0, size = 20) => api.get(`/admin/inventory?page=${page}&size=${size}`),
            customers: (page = 0, size = 20) => api.get(`/admin/customers?page=${page}&size=${size}`),
            users: () => api.get('/users'),
            warehouses: () => api.get('/admin/warehouses'),
            deliveryEvents: () => api.get('/delivery/admin-events'),
            setWarehouseActive: (id, active) =>
                api.patch(`/admin/warehouses/${id}/activation`, { active }),
            assignProductOwner: (productId, ownerId, businessLocationId) =>
                api.patch(`/products/${productId}/owner`, { ownerId, businessLocationId }),
        },

        // ── Owner and delivery operations ──────────────────────────────────
        businessLocations: {
            list: () => api.get('/business-locations'),
            forOwner: (ownerId) => api.get(`/admin/business-locations/owners/${ownerId}`),
            create: (location) => api.post('/business-locations', location),
            remove: (id) => api.del(`/business-locations/${id}`),
        },

        owner: {
            fulfillments: () => api.get('/owner/fulfillments'),
            setPickupLocation: (id, businessLocationId) =>
                api.patch(`/owner/fulfillments/${id}/pickup-location`, { businessLocationId }),
            confirm: (id) => api.post(`/owner/fulfillments/${id}/confirm`),
            reject: (id, reason) => api.post(`/owner/fulfillments/${id}/reject`, { reason }),
            qrToken: (id, stage) =>
                api.get(`/owner/fulfillments/${id}/qr/${encodeURIComponent(stage)}`),
        },

        warehouse: {
            deliveryAgents: () => api.get('/warehouse/delivery-agents'),
            setDeliveryAgentActive: (id, active) =>
                api.patch(`/warehouse/delivery-agents/${id}/activation`, { active }),
        },

        delivery: {
            tasks: () => api.get('/delivery/offers'),
            warehouseTasks: () => api.get('/delivery/warehouse-tasks'),
            warehouseArrivalQr: () => api.get('/delivery/warehouse-arrival-qr'),
            warehouseArrivalTasks: (token) =>
                api.post('/delivery/warehouse-arrival-tasks', { token }),
            confirmWarehouseArrival: (token, fulfillmentId, notes) =>
                api.post('/delivery/warehouse-arrival', { token, fulfillmentId, notes }),
            accept: (id) => api.post(`/delivery/fulfillments/${id}/accept`),
            scan: (token) => api.post('/delivery/scan', { token }),
            assign: (id, deliveryAgentId) =>
                api.post(`/delivery/fulfillments/${id}/assignment`, { deliveryAgentId }),
            assignTransfer: (id, deliveryAgentId) =>
                api.post(`/delivery/fulfillments/${id}/transfer-assignment`, { deliveryAgentId }),
            acceptWarehousePickup: (id) =>
                api.post(`/delivery/fulfillments/${id}/warehouse-acceptance`),
            rejectWarehousePickup: (id, reason) =>
                api.post(`/delivery/fulfillments/${id}/warehouse-rejection`, { reason }),
            pickup: (body) => api.post('/delivery/pickup', body),
            warehouseReceipt: (body) => api.post('/delivery/warehouse-receipt', body),
            warehouseDispatch: (body) => api.post('/delivery/warehouse-dispatch', body),
            destinationWarehouseReceipt: (body) =>
                api.post('/delivery/warehouse-destination-receipt', body),
            assignFinal: (id, deliveryAgentId) =>
                api.post(`/delivery/fulfillments/${id}/final-assignment`, { deliveryAgentId }),
            dispatch: (body) => api.post('/delivery/dispatch', body),
            requestCustomerConfirmation: (body) =>
                api.post('/delivery/customer-confirmation-request', body),
            confirmCustomerDelivery: (id, delivered, notes) =>
                api.post(`/delivery/customer/fulfillments/${id}/confirmation`, { delivered, notes }),
            history: (id) => api.get(`/delivery/fulfillments/${id}/history`),
        },

        serviceRequests: {
            warehouse: () => api.get('/service-requests/warehouse'),
            agent: () => api.get('/service-requests/agent'),
            evidence: (path) => getResource(path),
            review: (id, decision, notes) =>
                api.patch(`/service-requests/${id}/review`, { decision, notes }),
            assignPickup: (id, deliveryAgentId) =>
                api.patch(`/service-requests/${id}/pickup-assignment`, { deliveryAgentId }),
            assignSellerRoute: (id, deliveryAgentId) =>
                api.patch(`/service-requests/${id}/seller-assignment`, { deliveryAgentId }),
            assignReplacement: (id, deliveryAgentId) =>
                api.patch(`/service-requests/${id}/replacement-assignment`, { deliveryAgentId }),
            advance: (id, stage) => api.patch(`/service-requests/${id}/stage`, { stage }),
            confirmSellerReceipt: (id, notes) =>
                api.patch(`/service-requests/${id}/seller-receipt`, { notes }),
        },

        notifications: {
            list: (size = 20) => api.get(`/notifications?size=${size}`),
            markRead: (id) => api.patch(`/notifications/${id}/read`),
            markAllRead: () => api.patch('/notifications/read-all'),
        },

        // ── Addresses ────────────────────────────────────────────────────────
        addresses: {
            list:   ()            => api.get('/addresses'),
            add:    (dto)         => api.post('/addresses', dto),
            update: (id, dto)     => api.put(`/addresses/${id}`, dto),
            remove: (id)          => api.del(`/addresses/${id}`),
        },

        // ── Wishlist ─────────────────────────────────────────────────────────
        wishlist: {
            list:   ()   => api.get('/wishlist'),
            toggle: (id) => api.post(`/wishlist/${id}`),
            status: (id) => api.get(`/wishlist/${id}/status`),
        },

        // ── User ─────────────────────────────────────────────────────────────
        user: {
            me: () => api.get('/users/me'),
            updateRole: (id, role) => api.patch(`/users/${id}/role`, { role }),
        },
    };

    global.tapApi = api;

}(window));
