(function () {
    'use strict';

    const navigation = document.getElementById('customerNotificationNav');
    const bell = document.getElementById('customerNotificationBell');
    const menu = document.getElementById('customerNotificationMenu');
    const list = document.getElementById('customerNotificationList');
    const count = document.getElementById('customerNotificationCount');
    const markAllButton = document.getElementById('customerNotificationMarkAll');
    if (!navigation || !bell || !menu || !list || !window.tapApi) return;

    let timer = null;
    let loading = false;

    function currentUser() {
        try {
            return JSON.parse(localStorage.getItem('tapUser') || 'null');
        } catch {
            return null;
        }
    }

    function formatDate(value) {
        const date = new Date(value);
        return Number.isNaN(date.getTime()) ? '' : date.toLocaleString('en-IN');
    }

    function showAuthenticatedState() {
        const user = currentUser();
        const authenticated = Boolean(user && window.tapApi.getToken());
        document.querySelectorAll('.operations-dashboard-link').forEach(link => {
            link.classList.toggle('hidden', !user
                || !['OWNER', 'DELIVERY_AGENT', 'WAREHOUSE_OPERATOR'].includes(user.role));
        });
        navigation.classList.toggle('hidden', !authenticated);
        if (!authenticated) {
            menu.classList.add('hidden');
            bell.setAttribute('aria-expanded', 'false');
            if (timer) clearInterval(timer);
            timer = null;
            list.replaceChildren();
            count.textContent = '0';
            count.classList.add('hidden');
            return;
        }
        sync().catch(error => {
            console.warn('Notifications could not be refreshed:', error.message);
            renderError(error.message);
        });
        if (!timer) timer = setInterval(() => sync().catch(error => {
            console.warn('Notifications could not be refreshed:', error.message);
            renderError(error.message);
        }), 30000);
    }

    function renderError(message) {
        const paragraph = document.createElement('p');
        paragraph.className = 'notification-empty';
        paragraph.textContent = message || 'Notifications are temporarily unavailable.';
        list.replaceChildren(paragraph);
    }

    async function sync() {
        if (loading || !window.tapApi.getToken()) return;
        loading = true;
        try {
            const summary = await window.tapApi.notifications.list(10);
            count.textContent = summary.unreadCount > 99 ? '99+' : String(summary.unreadCount || 0);
            count.classList.toggle('hidden', !summary.unreadCount);
            const notifications = summary.notifications || [];
            if (!notifications.length) {
                const empty = document.createElement('p');
                empty.className = 'notification-empty';
                empty.textContent = 'You are all caught up.';
                list.replaceChildren(empty);
                return;
            }
            const items = notifications.map(notification => {
                const button = document.createElement('button');
                button.type = 'button';
                button.className = `customer-notification-item${notification.read ? '' : ' unread'}`;
                button.dataset.notificationId = String(notification.id);
                button.dataset.orderId = notification.orderId ? String(notification.orderId) : '';
                const title = document.createElement('strong');
                title.textContent = notification.title;
                const message = document.createElement('span');
                message.textContent = notification.message;
                const timestamp = document.createElement('small');
                timestamp.textContent = formatDate(notification.createdAt);
                button.append(title, message, timestamp);
                return button;
            });
            list.replaceChildren(...items);
        } finally {
            loading = false;
        }
    }

    bell.addEventListener('click', async () => {
        const open = menu.classList.toggle('hidden');
        bell.setAttribute('aria-expanded', String(!open));
        if (!open) await sync().catch(error => renderError(error.message));
    });

    list.addEventListener('click', async event => {
        const item = event.target.closest('[data-notification-id]');
        if (!item) return;
        try {
            await window.tapApi.notifications.markRead(item.dataset.notificationId);
            if (item.dataset.orderId) {
                window.location.href = `OrderTracking.html?id=${encodeURIComponent(item.dataset.orderId)}`;
            } else {
                await sync();
            }
        } catch (error) {
            renderError(error.message);
        }
    });

    markAllButton.addEventListener('click', async () => {
        try {
            await window.tapApi.notifications.markAllRead();
            await sync();
        } catch (error) {
            renderError(error.message);
        }
    });

    window.addEventListener('tap:authenticated', showAuthenticatedState);
    window.addEventListener('tap:unauthorized', showAuthenticatedState);
    document.addEventListener('visibilitychange', () => {
        if (!document.hidden) showAuthenticatedState();
    });
    document.addEventListener('click', event => {
        if (!navigation.contains(event.target) && !menu.classList.contains('hidden')) {
            menu.classList.add('hidden');
            bell.setAttribute('aria-expanded', 'false');
        }
    });

    showAuthenticatedState();
}());
