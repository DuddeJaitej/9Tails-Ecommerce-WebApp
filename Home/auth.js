/**
 * auth.js  —  Authentication layer for 9Tails
 *
 * Strategy:
 *   • Login / Register → Spring Boot API (/auth/login, /auth/register)
 *   • JWT stored in  localStorage['tap_jwt']
 *   • Session user    localStorage['tapUser'] (name, email, photo — no password)
 *   • Google OAuth credentials are exchanged with Spring Boot for a JWT session
 *
 * Backward-compat: all pages call  window.tapAuth.initAuth({...})  unchanged.
 */

(function () {
    'use strict';

    const USER_KEY    = 'tapUser';
    const DEFAULT_AVATAR = 'https://ui-avatars.com/api/?background=ff76a4&color=fff&bold=true&name=';
    let openActiveAuthModal = null;

    function buildAvatar(name) {
        return DEFAULT_AVATAR + encodeURIComponent((name || 'User').trim());
    }

    function requestLogin(message) {
        if (message) showAuthToast('info', message);
        if (openActiveAuthModal) {
            openActiveAuthModal('login');
            return;
        }
        document.querySelector('.login')?.click();
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
        return !!(getSession() && window.tapApi?.getToken());
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
        const message = document.createElement('span');
        message.textContent = msg;
        const closeButton = document.createElement('button');
        closeButton.type = 'button';
        closeButton.textContent = '×';
        closeButton.setAttribute('aria-label', 'Dismiss notification');
        closeButton.style.cssText = 'background:none;border:none;cursor:pointer;color:#aaa;font-size:1rem;padding:0 4px';
        closeButton.addEventListener('click', () => t.remove());
        t.append(message, closeButton);
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
        const authBody   = root.querySelector('.auth-body');
        if (authBody && !root.getElementById('customerForgotLink')) {
            const googleButton = root.getElementById('googleSignIn');
            const divider = root.querySelector('.auth-divider');
            const passwordResetUi = `
                <button type="button" class="customer-forgot-link" id="customerForgotLink">Forgot password?</button>
                <form id="customerForgotForm" class="customer-password-form hidden">
                    <p>Enter your account email. If it belongs to a customer account, we’ll send a one-time reset link.</p>
                    <label for="customerForgotEmail">Email</label>
                    <input id="customerForgotEmail" type="email" autocomplete="email" maxlength="254" required>
                    <button class="auth-submit" id="customerForgotSubmit" type="submit">Send reset link</button>
                    <button class="customer-form-back" id="customerForgotBack" type="button">Back to login</button>
                </form>
                <form id="customerResetForm" class="customer-password-form hidden">
                    <p>Choose a new password. Reset links expire after 30 minutes and can only be used once.</p>
                    <input id="customerResetToken" type="hidden">
                    <label for="customerNewPassword">New password</label>
                    <input id="customerNewPassword" type="password" autocomplete="new-password" minlength="12" maxlength="72" required>
                    <label for="customerConfirmPassword">Confirm new password</label>
                    <input id="customerConfirmPassword" type="password" autocomplete="new-password" minlength="12" maxlength="72" required>
                    <button class="auth-submit" id="customerResetSubmit" type="submit">Reset password</button>
                </form>
                <p class="customer-password-feedback hidden" id="customerPasswordFeedback" role="status"></p>`;
            if (divider) divider.insertAdjacentHTML('beforebegin', passwordResetUi);
            else if (googleButton) googleButton.insertAdjacentHTML('afterend', passwordResetUi);
            else authBody.insertAdjacentHTML('beforeend', passwordResetUi);
        }
        const authTabs   = root.querySelectorAll('.auth-tab');
        const authFooter = root.querySelector('.auth-footer');
        const authNameGrp= root.querySelector('.auth-name-group');
        const authSubmit = root.querySelector('.auth-submit');
        const authSwitchTx= root.querySelector('.auth-switch-text');
        const googleBtn  = root.getElementById('googleSignIn');
        const authDivider = root.querySelector('.auth-divider');
        const customerForgotLink = root.getElementById('customerForgotLink');
        const customerForgotForm = root.getElementById('customerForgotForm');
        const customerResetForm = root.getElementById('customerResetForm');
        const customerPasswordFeedback = root.getElementById('customerPasswordFeedback');
        const pageOverlay= root.getElementById('pageOverlay');
        const profileTrig= root.querySelector('.profile-trigger');
        const profileMenu= root.querySelector('.profile-menu');
        const profileMenuEmail = root.querySelector('.profile-menu-email');
        const adminDashboardLink = root.querySelector('.admin-dashboard-link');
        const operationsDashboardLink = root.querySelector('.operations-dashboard-link');
        const emailInput = root.getElementById('email');
        const pwdInput   = root.getElementById('password');
        const nameInput  = root.getElementById('authFullName');

        if (!authModal || !authForm) return null;

        // ── Render current session ──────────────────────────────────────────
        function refreshUI() {
            const user = isLoggedIn() ? getSession() : null;
            if (user) {
                if (loginBtn)   loginBtn.classList.add('hidden');
                if (navProfile) navProfile.classList.remove('hidden');
                if (profileName) profileName.textContent = user.name;
                if (profilePhoto) {
                    profilePhoto.src = user.photo || buildAvatar(user.name);
                    profilePhoto.onerror = () => { profilePhoto.src = buildAvatar(user.name); };
                }
                if (profileMenuEmail) profileMenuEmail.textContent = user.email;
                if (adminDashboardLink) adminDashboardLink.classList.toggle('hidden', user.role !== 'ADMIN');
                if (operationsDashboardLink) {
                    operationsDashboardLink.classList.toggle('hidden',
                        !['OWNER', 'DELIVERY_AGENT', 'WAREHOUSE_OPERATOR'].includes(user.role));
                }
            } else {
                if (loginBtn)   loginBtn.classList.remove('hidden');
                if (navProfile) navProfile.classList.add('hidden');
                if (adminDashboardLink) adminDashboardLink.classList.add('hidden');
                if (operationsDashboardLink) operationsDashboardLink.classList.add('hidden');
            }
        }
        refreshUI();
        syncCartCountFromApi();
        window.addEventListener('tap:unauthorized', () => {
            clearSession();
            refreshUI();
        });

        // ── Modal helpers ───────────────────────────────────────────────────
        function openModal(mode = 'login') { setMode(mode); showCustomerAuthView('login'); authModal.classList.remove('hidden'); if (pageOverlay) pageOverlay.classList.remove('hidden'); }
        function closeModal() { authModal.classList.add('hidden'); if (pageOverlay) pageOverlay.classList.add('hidden'); authForm.reset(); }
        openActiveAuthModal = openModal;

        function completeLogin(result) {
            if (!result?.token || !result.email) {
                throw new Error('The server did not return a valid login session.');
            }
            window.tapApi.setToken(result.token);
            saveSession(result);
            closeModal();
            showAuthToast('success', `Welcome, ${result.fullName || result.email}! 👋`);
            refreshUI();
            syncCartCountFromApi();
            window.dispatchEvent(new Event('tap:authenticated'));
        }

        function setMode(mode) {
            authModal.dataset.mode = mode;
            authTabs.forEach(t => t.classList.toggle('active', t.dataset.mode === mode));
            if (authNameGrp) authNameGrp.classList.toggle('hidden', mode !== 'register');
            if (authSubmit)  authSubmit.textContent = mode === 'register' ? 'Create Account' : 'Login';
            if (customerForgotLink) customerForgotLink.classList.toggle('hidden', mode !== 'login');
            if (authSwitchTx) authSwitchTx.innerHTML = mode === 'register'
                ? 'Already have an account? <button type="button" class="switch-auth" data-mode="login">Login</button>'
                : "Don't have an account? <button type=\"button\" class=\"switch-auth\" data-mode=\"register\">Register</button>";
            bindSwitchBtns();
            showCustomerAuthView('login');
        }

        function showCustomerAuthView(view) {
            const resetMode = view === 'reset';
            const forgotMode = view === 'forgot';
            authTabs.forEach(tab => tab.classList.toggle('hidden', resetMode || forgotMode));
            authForm.classList.toggle('hidden', resetMode || forgotMode);
            if (authFooter) authFooter.classList.toggle('hidden', resetMode || forgotMode);
            if (customerForgotLink) customerForgotLink.classList.toggle('hidden', resetMode || forgotMode);
            if (authDivider) authDivider.classList.toggle('hidden', resetMode || forgotMode);
            if (googleBtn) googleBtn.classList.toggle('hidden', resetMode || forgotMode);
            if (customerForgotForm) customerForgotForm.classList.toggle('hidden', !forgotMode);
            if (customerResetForm) customerResetForm.classList.toggle('hidden', !resetMode);
            if (customerPasswordFeedback) {
                customerPasswordFeedback.classList.add('hidden');
                customerPasswordFeedback.textContent = '';
                customerPasswordFeedback.classList.remove('feedback-success');
            }
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

                completeLogin(result);

            } catch (err) {
                showAuthToast('error', err.message || 'Authentication failed. Please try again.');
            } finally {
                if (authSubmit) {
                    authSubmit.disabled = false;
                    authSubmit.textContent = mode === 'register' ? 'Create Account' : 'Login';
                }
            }
        });

        if (customerForgotLink && customerForgotForm && customerResetForm) {
            customerForgotLink.addEventListener('click', () => {
                document.getElementById('customerForgotEmail').value = emailInput.value.trim().toLowerCase();
                showCustomerAuthView('forgot');
            });

            document.getElementById('customerForgotBack').addEventListener('click', () => {
                showCustomerAuthView('login');
            });

            customerForgotForm.addEventListener('submit', async event => {
                event.preventDefault();
                const button = document.getElementById('customerForgotSubmit');
                button.disabled = true;
                button.textContent = 'Sending…';
                try {
                    const email = document.getElementById('customerForgotEmail').value.trim().toLowerCase();
                    const result = await window.tapApi.auth.requestCustomerPasswordReset(email);
                    customerPasswordFeedback.textContent = result?.message
                        || 'If a customer account exists for that email, a password reset link has been sent.';
                    customerPasswordFeedback.classList.remove('hidden');
                    customerPasswordFeedback.classList.add('feedback-success');
                } catch (error) {
                    customerPasswordFeedback.textContent =
                        error.message || 'The reset email could not be sent. Please try again.';
                    customerPasswordFeedback.classList.remove('hidden', 'feedback-success');
                } finally {
                    button.disabled = false;
                    button.textContent = 'Send reset link';
                }
            });

            customerResetForm.addEventListener('submit', async event => {
                event.preventDefault();
                const password = document.getElementById('customerNewPassword').value;
                const confirmation = document.getElementById('customerConfirmPassword').value;
                const token = document.getElementById('customerResetToken').value;
                const button = document.getElementById('customerResetSubmit');
                if (!token || password !== confirmation) {
                    customerPasswordFeedback.textContent = !token
                        ? 'This reset link is incomplete. Request a new password reset email.'
                        : 'The passwords do not match.';
                    customerPasswordFeedback.classList.remove('hidden', 'feedback-success');
                    return;
                }

                button.disabled = true;
                button.textContent = 'Resetting…';
                try {
                    const result = await window.tapApi.auth.resetCustomerPassword(token, password);
                    customerResetForm.reset();
                    setMode('login');
                    showCustomerAuthView('login');
                    customerPasswordFeedback.textContent =
                        result?.message || 'Password reset successfully. Sign in with your new password.';
                    customerPasswordFeedback.classList.remove('hidden');
                    customerPasswordFeedback.classList.add('feedback-success');
                    window.history.replaceState({}, document.title, window.location.pathname);
                } catch (error) {
                    customerPasswordFeedback.textContent =
                        error.message || 'The reset link could not be used. Request a new link.';
                    customerPasswordFeedback.classList.remove('hidden', 'feedback-success');
                } finally {
                    button.disabled = false;
                    button.textContent = 'Reset password';
                }
            });

            const resetToken = new URLSearchParams(window.location.search).get('resetToken');
            if (resetToken) {
                document.getElementById('customerResetToken').value = resetToken;
                openModal('login');
                showCustomerAuthView('reset');
            }
        }

        // ── Google OAuth exchanged for a Spring Boot JWT ─────────────────────
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
                        if (response.error) {
                            showAuthToast('error', 'Google sign-in failed. Please try again.');
                            return;
                        }
                        try {
                            const result = await window.tapApi.auth.google(response.access_token);
                            completeLogin(result);
                        } catch (err) {
                            showAuthToast('error', err.message || 'Google sign-in failed. Please try again.');
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
    let cartCountSyncVersion = 0;

    function setCartCount(countEl, value) {
        const count = String(value);
        if (countEl.textContent === count) return;
        countEl.textContent = count;
        countEl.classList.remove('cart-count-updated');
        void countEl.offsetWidth;
        countEl.classList.add('cart-count-updated');
        setTimeout(() => countEl.classList.remove('cart-count-updated'), 350);
    }

    async function syncCartCountFromApi() {
        const requestVersion = ++cartCountSyncVersion;
        const countEl = document.querySelector('.cart-count');
        if (!countEl) return;
        if (!isLoggedIn()) {
            setCartCount(countEl, 0);
            return;
        }

        const session = getSession();
        const cacheKey = `tap_cart_count:${session?.email || 'default'}`;
        try {
            const cachedCount = localStorage.getItem(cacheKey);
            if (cachedCount !== null) setCartCount(countEl, cachedCount);
        } catch (e) {
            console.warn('Cached cart count could not be read:', e.message);
        }

        try {
            const summary = await window.tapApi.cart.get();
            if (requestVersion !== cartCountSyncVersion) return;
            const count = summary?.totalItems ?? 0;
            setCartCount(countEl, count);
            try {
                localStorage.setItem(cacheKey, String(count));
            } catch (e) {
                console.warn('Cart count could not be cached:', e.message);
            }
        } catch (e) {
            if (requestVersion === cartCountSyncVersion) {
                if (window.tapApi?.getToken()) {
                    console.warn('Cart count could not be refreshed:', e.message);
                }
            }
        }
    }

    window.tapAuth = {
        initAuth,
        getSession,
        clearSession,
        isLoggedIn,
        syncCartCountFromApi,
        requestLogin,
        notify: showAuthToast,
        USER_KEY
    };

}());
