'use strict';

const form = document.getElementById('adminRegistrationForm');
const accessMessage = document.getElementById('adminRegistrationAccess');
const feedback = document.getElementById('adminRegistrationFeedback');
const button = document.getElementById('adminRegistrationButton');

if (window.tapApi.getToken()) {
    form.classList.remove('hidden');
} else {
    accessMessage.textContent =
        'Sign in to the Admin dashboard first. Public administrator registration is disabled.';
}

form.addEventListener('submit', async event => {
    event.preventDefault();
    if (!window.tapApi.getToken()) {
        form.classList.add('hidden');
        accessMessage.textContent =
            'Your administrator session is missing. Sign in to the Admin dashboard, then return here.';
        return;
    }

    button.disabled = true;
    button.textContent = 'Creating account…';
    feedback.textContent = '';
    feedback.classList.remove('feedback-success');
    try {
        const account = await window.tapApi.auth.adminCreateFromDashboard(
            document.getElementById('newAdminEmail').value.trim().toLowerCase(),
            document.getElementById('newAdminPassword').value
        );
        feedback.textContent = `Administrator account created for ${account.email}.`;
        feedback.classList.add('feedback-success');
        form.reset();
    } catch (error) {
        feedback.textContent = error.message || 'Administrator account could not be created.';
    } finally {
        button.disabled = false;
        button.textContent = 'Create administrator account';
    }
});
