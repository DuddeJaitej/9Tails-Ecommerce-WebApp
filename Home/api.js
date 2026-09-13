/**
 * api.js  —  Centralized HTTP client for 9Tails ↔ Spring Boot API
 * Base URL: http://localhost:8080/api
 * All functions return the `data` field from ApiResponse, or throw on error.
 */

(function (global) {
    'use strict';

    const BASE = 'http://localhost:8081/api';

    // ─── Token helpers ────────────────────────────────────────────────────────
    function getToken() { return localStorage.getItem('tap_jwt') || ''; }
    function setToken(t) { localStorage.setItem('tap_jwt', t); }
    function clearToken() { localStorage.removeItem('tap_jwt'); }

    function authHeaders() {
        const t = getToken();
        return t ? { 'Authorization': `Bearer ${t}`, 'Content-Type': 'application/json' }
                 : { 'Content-Type': 'application/json' };
    }

    // ─── Core fetch wrapper ──────────────────────────────────────────────────
    async function req(method, path, body) {
        const opts = { method, headers: authHeaders() };
        if (body !== undefined) opts.body = JSON.stringify(body);

        let res;
        try {
            res = await fetch(BASE + path, opts);
        } catch (e) {
            throw new Error('Cannot reach the server. Make sure Spring Boot is running on port 8081.');
        }

        const json = await res.json().catch(() => ({}));

        if (!res.ok) {
            throw new Error(json.message || `HTTP ${res.status}`);
        }
        return json.data;          // unwrap ApiResponse<T>.data
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

            login: (email, password) =>
                api.post('/auth/login', { email, password }),
        },

        // ── Products ─────────────────────────────────────────────────────────
        products: {
            list:     (page = 0, size = 20, sort = 'newest') =>
                api.get(`/products?page=${page}&size=${size}&sort=${sort}`),

            byCategory: (cat, page = 0, size = 20, sort = 'newest') =>
                api.get(`/products/category/${encodeURIComponent(cat)}?page=${page}&size=${size}&sort=${sort}`),

            search:   (q, page = 0, size = 20) =>
                api.get(`/products/search?q=${encodeURIComponent(q)}&page=${page}&size=${size}`),

            get:      (id) => api.get(`/products/${id}`),

            related:  (id) => api.get(`/products/${id}/related`),
        },

        // ── Categories ────────────────────────────────────────────────────────
        categories: {
            list: () => api.get('/categories'),
        },

        // ── Cart ─────────────────────────────────────────────────────────────
        cart: {
            get:    ()                      => api.get('/cart'),
            add:    (productId, qty = 1)    => api.post(`/cart?productId=${productId}&quantity=${qty}`),
            update: (productId, qty)        => api.patch(`/cart/${productId}?quantity=${qty}`),
            remove: (productId)             => api.del(`/cart/${productId}`),
            clear:  ()                      => api.del('/cart'),
        },

        // ── Orders ───────────────────────────────────────────────────────────
        orders: {
            place:    (addressId, paymentMethod) =>
                api.post('/orders', { addressId, paymentMethod }),

            list:     (page = 0, size = 10) =>
                api.get(`/orders?page=${page}&size=${size}`),

            get:      (id)  => api.get(`/orders/${id}`),
            cancel:   (id)  => api.patch(`/orders/${id}/cancel`),
            return:   (id)  => api.patch(`/orders/${id}/return`),
            exchange: (id)  => api.patch(`/orders/${id}/exchange`),
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
        },
    };

    global.tapApi = api;

}(window));
