/**
 * auth.js  —  Authentication layer for 9Tails
 *
 * Strategy:
 *   • Login / Register → Spring Boot API (/auth/login, /auth/register)
 *   • JWT stored in  localStorage['tap_jwt']
 *   • Session user    localStorage['tapUser'] (name, email, photo — no password)
 *   • Google OAuth still handled client-side (fetches profile, then calls /auth/login
 *     with google token path — or just stores profile locally as before)
 *
 * Backward-compat: all pages call  window.tapAuth.initAuth({...})  unchanged.
 */

(function () {
    'use strict';

    const USER_KEY    = 'tapUser';
    const DEFAULT_AVATAR = 'https://ui-avatars.com/api/?background=ff76a4&color=fff&bold=true&name=';

    function buildAvatar(name) {
        return DEFAULT_AVATAR + encodeURIComponent((name || 'User').trim());
    }

    function getSession() {
        try { return JSON.parse(localStorage.getItem(USER_KEY) || 'null'); } catch { return null; }
    }

    function saveSession(user) {
        localStorage.setItem(USER_KEY, JSON.stringify({
            name:  user.fullName || user.name || user.email?.split('@')[0] || 'Customer',
            email: user.email,
            photo: user.photo || buildAvatar(user.fullName || user.name),
            role:  user.role  || 'USER'
        }));
    }

    function clearSession() {
        localStorage.removeItem(USER_KEY);
        localStorage.removeItem('tap_jwt');
        // Also clear local data caches
        localStorage.removeItem('tapCart');
        localStorage.removeItem('tapOrders');
        localStorage.removeItem('tapWishlist');
        localStorage.removeItem('tapAddresses');
    }

    function isLoggedIn() {
        return !!(getSession() && localStorage.getItem('tap_jwt'));
    }

    /* ── Toast helper (inline so auth.js has no deps) ─────────────────────── */
    function showAuthToast(type, msg) {
        const colors = { success: '#10b981', error: '#ef4444', info: '#6366f1' };
        let tc = document.getElementById('auth-toast-container');
        if (!tc) {
            tc = document.createElement('div');
            tc.id = 'auth-toast-container';
            tc.style.cssText = 'position:fixed;top:22px;right:22px;z-index:999999;display:grid;gap:10px;pointer-events:none;';
            document.body.appendChild(tc);
        }
        const t = document.createElement('div');
        t.style.cssText = `display:flex;align-items:center;gap:10px;padding:13px 18px;background:#fff;
            border-radius:14px;box-shadow:0 8px 28px rgba(0,0,0,0.13);pointer-events:all;
            border-left:4px solid ${colors[type]||'#999'};font-size:.9rem;color:#1a1a2e;
            animation:authToastIn .35s cubic-bezier(.34,1.56,.64,1) both;`;
        t.innerHTML = `<span>${msg}</span><button onclick="this.parentElement.remove()"
            style="background:none;border:none;cursor:pointer;color:#aaa;font-size:1rem;padding:0 4px">×</button>`;
        if (!document.getElementById('auth-toast-kf')) {
            const s = document.createElement('style');
            s.id = 'auth-toast-kf';
            s.textContent = '@keyframes authToastIn{from{opacity:0;transform:translateX(50px)}to{opacity:1;transform:none}}';
            document.head.appendChild(s);
        }
        tc.appendChild(t);
        setTimeout(() => t.remove(), 4000);
    }

    /* ── initAuth ─────────────────────────────────────────────────────────── */
    function initAuth(options = {}) {
        const root       = options.root || document;
        const loginBtn   = root.querySelector(options.loginSelector       || '.login');
        const navProfile = root.querySelector(options.navProfileSelector  || '.nav-profile');
        const profileName= root.querySelector(options.profileNameSelector || '.profile-name');
        const profilePhoto=root.querySelector(options.profilePhotoSelector|| '.profile-photo');
        const logoutBtn  = root.querySelector(options.logoutButtonSelector|| '.logout-btn');
        const authModal  = root.getElementById('authModal');
        const authClose  = root.getElementById('authClose');
        const authForm   = root.getElementById('authForm');
        const authTabs   = root.querySelectorAll('.auth-tab');
        const authNameGrp= root.querySelector('.auth-name-group');
        const authSubmit = root.querySelector('.auth-submit');
        const authSwitchTx= root.querySelector('.auth-switch-text');
        const googleBtn  = root.getElementById('googleSignIn');
        const pageOverlay= root.getElementById('pageOverlay');
        const profileTrig= root.querySelector('.profile-trigger');
        const profileMenu= root.querySelector('.profile-menu');
        const profileMenuEmail = root.querySelector('.profile-menu-email');
        const emailInput = root.getElementById('email');
        const pwdInput   = root.getElementById('password');
        const nameInput  = root.getElementById('authFullName');

        if (!authModal || !authForm) return null;

        // ── Render current session ──────────────────────────────────────────
        function refreshUI() {
            const user = getSession();
            if (user) {
                if (loginBtn)   loginBtn.classList.add('hidden');
                if (navProfile) navProfile.classList.remove('hidden');
                if (profileName) profileName.textContent = user.name;
                if (profilePhoto) {
                    profilePhoto.src = user.photo || buildAvatar(user.name);
                    profilePhoto.onerror = () => { profilePhoto.src = buildAvatar(user.name); };
                }
                if (profileMenuEmail) profileMenuEmail.textContent = user.email;
            } else {
                if (loginBtn)   loginBtn.classList.remove('hidden');
                if (navProfile) navProfile.classList.add('hidden');
            }
        }
        refreshUI();

        // ── Modal helpers ───────────────────────────────────────────────────
        function openModal(mode = 'login') { setMode(mode); authModal.classList.remove('hidden'); if (pageOverlay) pageOverlay.classList.remove('hidden'); }
        function closeModal() { authModal.classList.add('hidden'); if (pageOverlay) pageOverlay.classList.add('hidden'); authForm.reset(); }

        function setMode(mode) {
            authModal.dataset.mode = mode;
            authTabs.forEach(t => t.classList.toggle('active', t.dataset.mode === mode));
            if (authNameGrp) authNameGrp.classList.toggle('hidden', mode !== 'register');
            if (authSubmit)  authSubmit.textContent = mode === 'register' ? 'Create Account' : 'Login';
            if (authSwitchTx) authSwitchTx.innerHTML = mode === 'register'
                ? 'Already have an account? <button type="button" class="switch-auth" data-mode="login">Login</button>'
                : "Don't have an account? <button type=\"button\" class=\"switch-auth\" data-mode=\"register\">Register</button>";
            bindSwitchBtns();
        }

        function bindSwitchBtns() {
            root.querySelectorAll('.switch-auth').forEach(b => {
                b.addEventListener('click', () => setMode(b.dataset.mode));
            });
        }

        // ── Form submit → API ───────────────────────────────────────────────
        authForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const mode     = authModal.dataset.mode || 'login';
            const email    = (emailInput?.value || '').trim().toLowerCase();
            const password = (pwdInput?.value   || '').trim();
            const fullName = (nameInput?.value  || '').trim();

            if (!email || !password) {
                showAuthToast('error', 'Please enter your email and password.');
                return;
            }

            // Disable submit while request is in-flight
            if (authSubmit) { authSubmit.disabled = true; authSubmit.textContent = 'Please wait…'; }

            try {
                let result;
                if (mode === 'register') {
                    if (!fullName) { showAuthToast('error', 'Please enter your full name.'); return; }
                    result = await window.tapApi.auth.register(fullName, email, password);
                } else {
                    result = await window.tapApi.auth.login(email, password);
                }

                // Persist JWT + session
                window.tapApi.setToken(result.token);
                saveSession(result);
                closeModal();
                showAuthToast('success', `Welcome, ${result.fullName || result.email}! 👋`);
                refreshUI();

                // Sync cart count from API after login
                syncCartCountFromApi();

            } catch (err) {
                showAuthToast('error', err.message || 'Authentication failed. Please try again.');
            } finally {
                if (authSubmit) {
                    authSubmit.disabled = false;
                    authSubmit.textContent = mode === 'register' ? 'Create Account' : 'Login';
                }
            }
        });

        // ── Google OAuth (client-side profile fetch, then save session) ─────
        let tokenClient = null;

        function handleGoogleLogin() {
            const clientId = window.GOOGLE_CLIENT_ID || '';
            if (!clientId) { showAuthToast('error', 'Google OAuth client ID not configured.'); return; }
            if (!window.google?.accounts?.oauth2) { showAuthToast('info', 'Google sign-in is loading…'); return; }

            if (!tokenClient) {
                tokenClient = window.google.accounts.oauth2.initTokenClient({
                    client_id: clientId,
                    scope: 'openid profile email',
                    callback: async (response) => {
                        if (response.error) return;
                        try {
                            const p = await fetch(`https://www.googleapis.com/oauth2/v3/userinfo?access_token=${encodeURIComponent(response.access_token)}`).then(r => r.json());
                            // Google users: we just save their profile session locally
                            // (no backend call — treat as guest with profile)
                            saveSession({ fullName: p.name, email: p.email, photo: p.picture, role: 'USER' });
                            closeModal();
                            showAuthToast('success', `Welcome, ${p.name}!`);
                            refreshUI();
                        } catch {
                            showAuthToast('error', 'Unable to load Google profile. Try again.');
                        }
                    },
                    prompt: 'select_account'
                });
            }
            tokenClient.requestAccessToken({ prompt: 'select_account' });
        }

        // ── Profile menu toggle ─────────────────────────────────────────────
        if (profileTrig) {
            profileTrig.addEventListener('click', (e) => {
                e.stopPropagation();
                const isOpen = profileTrig.getAttribute('aria-expanded') === 'true';
                profileTrig.setAttribute('aria-expanded', String(!isOpen));
                if (profileMenu) profileMenu.classList.toggle('hidden', isOpen);
            });
            document.addEventListener('click', () => {
                profileTrig.setAttribute('aria-expanded', 'false');
                if (profileMenu) profileMenu.classList.add('hidden');
            });
        }

        // ── Logout ──────────────────────────────────────────────────────────
        if (logoutBtn) {
            logoutBtn.addEventListener('click', () => {
                clearSession();
                refreshUI();
                showAuthToast('info', 'You have been logged out.');
                setTimeout(() => window.location.href = 'Home.html', 1200);
            });
        }

        // ── Event bindings ──────────────────────────────────────────────────
        if (loginBtn)  loginBtn.addEventListener('click', () => openModal('login'));
        if (authClose) authClose.addEventListener('click', closeModal);
        if (pageOverlay) pageOverlay.addEventListener('click', closeModal);
        if (googleBtn) googleBtn.addEventListener('click', handleGoogleLogin);
        authTabs.forEach(t => t.addEventListener('click', () => setMode(t.dataset.mode)));
        bindSwitchBtns();

        return { openModal, closeModal, refreshUI, isLoggedIn };
    }

    /* ── Cart count sync from API ─────────────────────────────────────────── */
    async function syncCartCountFromApi() {
        const countEl = document.querySelector('.cart-count');
        if (!countEl) return;
        if (!isLoggedIn()) {
            // fallback: read from localStorage
            const cart = JSON.parse(localStorage.getItem('tapCart') || '{}');
            const count = Object.values(cart).reduce((s, i) => s + (i.qty || 0), 0);
            countEl.textContent = count;
            return;
        }
        try {
            const summary = await window.tapApi.cart.get();
            countEl.textContent = summary?.totalItems ?? 0;
        } catch {
            // If API fails, fall back to localStorage
            const cart = JSON.parse(localStorage.getItem('tapCart') || '{}');
            countEl.textContent = Object.values(cart).reduce((s, i) => s + (i.qty || 0), 0);
        }
    }

    window.tapAuth = { initAuth, getSession, clearSession, isLoggedIn, syncCartCountFromApi, USER_KEY };

}());
