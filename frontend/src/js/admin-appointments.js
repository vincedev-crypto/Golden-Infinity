const appointmentAdmin = (() => {
    const API_BASE = window.APP_APPOINTMENT_API_BASE || 'http://localhost:8084/api/v1/appointments';
    const WS_BASE = window.APP_APPOINTMENT_WS_BASE || API_BASE.replace(/^http/, 'ws').replace('/api/v1/appointments', '/api/v1/ws/appointments');
    const STAFF_ROLES = new Set(['STAFF', 'ADMIN', 'SUPERADMIN']);
    const STATUS_OPTIONS = ['REQUESTED', 'CONFIRMED', 'RESCHEDULED', 'CANCELLED', 'COMPLETED'];

    let appointments = [];
    let selectedId = null;
    let socket = null;
    let reconnectTimer = null;

    document.addEventListener('DOMContentLoaded', async () => {
        const authenticated = await auth.silentRefresh();
        const user = auth.getUser();

        if (!authenticated || !user) {
            window.location.href = 'login.html?next=admin-appointments.html';
            return;
        }

        if (!STAFF_ROLES.has(user.role)) {
            showMessage('This page is only for Golden Infinity staff and administrators.', 'danger');
            renderEmpty('Access denied.');
            return;
        }

        const greeting = document.getElementById('admin-greeting');
        if (greeting) {
            greeting.textContent = `${user.firstName} ${user.lastName}`;
        }

        setDefaultDates();
        bindFilters();
        await loadAppointments();
        connectLiveUpdates();
    });

    function setDefaultDates() {
        const start = document.getElementById('filterStart');
        const end = document.getElementById('filterEnd');
        const now = new Date();
        const later = new Date();
        later.setDate(now.getDate() + 30);
        start.value = toDateInput(now);
        end.value = toDateInput(later);
    }

    function bindFilters() {
        const filters = document.getElementById('appointmentFilters');
        filters.addEventListener('submit', async (event) => {
            event.preventDefault();
            await loadAppointments();
        });
    }

    async function loadAppointments() {
        try {
            showMessage('', '');
            renderEmpty('Loading appointments...');

            const startValue = document.getElementById('filterStart').value;
            const endValue = document.getElementById('filterEnd').value;
            const startAt = new Date(`${startValue}T00:00:00`).toISOString();
            const endAt = new Date(`${endValue}T23:59:59`).toISOString();
            const params = new URLSearchParams({
                startAt,
                endAt,
                sort: 'preferredStartAt,asc',
                size: '100'
            });

            const response = await auth.fetchWithAuth(`${API_BASE}?${params}`);
            const result = await response.json();

            if (!response.ok) {
                throw new Error(result.error?.message || 'Could not load appointments');
            }

            appointments = result.data?.content || [];
            selectedId = appointments.some(item => item.appointmentId === selectedId) ? selectedId : null;
            renderTable();
            renderEditor(selectedId ? findAppointment(selectedId) : null);
        } catch (error) {
            showMessage(error.message || 'Could not load appointments', 'danger');
            renderEmpty('No appointments loaded.');
        }
    }

    function renderTable() {
        const body = document.getElementById('appointmentsBody');
        if (!appointments.length) {
            renderEmpty('No appointments in this date range.');
            return;
        }

        body.innerHTML = '';
        appointments.forEach((appointment) => {
            const row = document.createElement('tr');
            row.className = appointment.appointmentId === selectedId ? 'selected-row' : '';
            row.tabIndex = 0;
            row.innerHTML = `
                <td data-label="Reference"><strong>${escapeHtml(appointment.appointmentRef)}</strong></td>
                <td data-label="Client">${escapeHtml(appointment.clientName)}<br><span>${escapeHtml(appointment.companyName || appointment.clientEmail)}</span></td>
                <td data-label="Purpose">${formatPurpose(appointment.purpose)}</td>
                <td data-label="Schedule">${formatDateTime(appointment.preferredStartAt)}<br><span>${formatDateTime(appointment.preferredEndAt)}</span></td>
                <td data-label="Status"><span class="status-pill status-${appointment.status.toLowerCase()}">${formatStatus(appointment.status)}</span></td>
            `;
            row.addEventListener('click', () => selectAppointment(appointment.appointmentId));
            row.addEventListener('keydown', (event) => {
                if (event.key === 'Enter' || event.key === ' ') {
                    event.preventDefault();
                    selectAppointment(appointment.appointmentId);
                }
            });
            body.appendChild(row);
        });
    }

    function renderEmpty(text) {
        const body = document.getElementById('appointmentsBody');
        body.innerHTML = `<tr><td colspan="5">${escapeHtml(text)}</td></tr>`;
    }

    function selectAppointment(appointmentId) {
        selectedId = appointmentId;
        renderTable();
        renderEditor(findAppointment(appointmentId));
    }

    function renderEditor(appointment) {
        const editor = document.getElementById('appointmentEditor');
        if (!appointment) {
            editor.innerHTML = '<p class="text-muted mb-0">Select an appointment to manage it.</p>';
            return;
        }

        editor.innerHTML = `
            <div class="editor-head">
                <div>
                    <p class="eyebrow profile-eyebrow">Appointment</p>
                    <h2>${escapeHtml(appointment.appointmentRef)}</h2>
                </div>
                <span class="status-pill status-${appointment.status.toLowerCase()}">${formatStatus(appointment.status)}</span>
            </div>
            <dl class="appointment-details">
                <div><dt>Client</dt><dd>${escapeHtml(appointment.clientName)}</dd></div>
                <div><dt>Email</dt><dd><a href="mailto:${escapeAttribute(appointment.clientEmail)}">${escapeHtml(appointment.clientEmail)}</a></dd></div>
                <div><dt>Phone</dt><dd>${escapeHtml(appointment.clientPhone || 'Not provided')}</dd></div>
                <div><dt>Company</dt><dd>${escapeHtml(appointment.companyName || 'Not provided')}</dd></div>
                <div><dt>Purpose</dt><dd>${formatPurpose(appointment.purpose)}</dd></div>
                <div><dt>Client notes</dt><dd>${escapeHtml(appointment.notes || 'None')}</dd></div>
            </dl>
            <form id="appointmentUpdateForm" class="appointment-update-form">
                <div>
                    <label class="form-label" for="appointmentStatus">Status</label>
                    <select class="form-select" id="appointmentStatus">
                        ${STATUS_OPTIONS.map(status => `<option value="${status}" ${status === appointment.status ? 'selected' : ''}>${formatStatus(status)}</option>`).join('')}
                    </select>
                </div>
                <div>
                    <label class="form-label" for="adminStart">Start</label>
                    <input class="form-control" type="datetime-local" id="adminStart" value="${toDateTimeLocal(appointment.preferredStartAt)}">
                </div>
                <div>
                    <label class="form-label" for="adminEnd">End</label>
                    <input class="form-control" type="datetime-local" id="adminEnd" value="${toDateTimeLocal(appointment.preferredEndAt)}">
                </div>
                <div>
                    <label class="form-label" for="internalNotes">Internal notes</label>
                    <textarea class="form-control" id="internalNotes" rows="4" maxlength="2000">${escapeHtml(appointment.internalNotes || '')}</textarea>
                </div>
                <button class="btn btn-primary w-100" type="submit">Save Appointment</button>
            </form>
        `;

        document.getElementById('appointmentUpdateForm').addEventListener('submit', async (event) => {
            event.preventDefault();
            await updateAppointment(appointment.appointmentId);
        });
    }

    async function updateAppointment(appointmentId) {
        try {
            const appointment = findAppointment(appointmentId);
            if (!appointment) {
                throw new Error('Appointment not found');
            }

            const startValue = document.getElementById('adminStart').value;
            const endValue = document.getElementById('adminEnd').value;
            const payload = {
                status: document.getElementById('appointmentStatus').value,
                internalNotes: document.getElementById('internalNotes').value.trim()
            };

            if (startValue !== toDateTimeLocal(appointment.preferredStartAt)) {
                payload.preferredStartAt = new Date(startValue).toISOString();
            }

            if (endValue !== toDateTimeLocal(appointment.preferredEndAt)) {
                payload.preferredEndAt = new Date(endValue).toISOString();
            }

            const response = await auth.fetchWithAuth(`${API_BASE}/${appointmentId}`, {
                method: 'PATCH',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
            const result = await response.json();

            if (!response.ok) {
                throw new Error(getErrorMessage(result, 'Could not update appointment'));
            }

            const updated = result.data;
            appointments = appointments.map(item =>
                item.appointmentId === updated.appointmentId ? updated : item
            );
            selectedId = updated.appointmentId;
            renderTable();
            renderEditor(updated);
            showMessage('Appointment updated.', 'success');
        } catch (error) {
            showMessage(error.message || 'Could not update appointment', 'danger');
        }
    }

    async function connectLiveUpdates() {
        if (!('WebSocket' in window)) {
            setSocketStatus('Live updates unavailable on this browser.', 'offline');
            return;
        }

        if (!auth.getAccessToken()) {
            const refreshed = await auth.silentRefresh();
            if (!refreshed) {
                setSocketStatus('Live updates waiting for login.', 'offline');
                return;
            }
        }

        if (socket && [WebSocket.CONNECTING, WebSocket.OPEN].includes(socket.readyState)) {
            return;
        }

        const token = encodeURIComponent(auth.getAccessToken());
        socket = new WebSocket(`${WS_BASE}?token=${token}`);
        setSocketStatus('Live updates connecting...', 'connecting');

        socket.addEventListener('open', () => {
            setSocketStatus('Live updates active.', 'online');
        });

        socket.addEventListener('message', (event) => {
            const message = JSON.parse(event.data);
            if (!message.appointment) {
                return;
            }
            mergeLiveAppointment(message.appointment);
            showMessage('Appointment list updated live.', 'info');
        });

        socket.addEventListener('close', () => {
            setSocketStatus('Live updates reconnecting...', 'offline');
            clearTimeout(reconnectTimer);
            reconnectTimer = setTimeout(connectLiveUpdates, 4000);
        });

        socket.addEventListener('error', () => {
            setSocketStatus('Live updates temporarily unavailable.', 'offline');
        });
    }

    function mergeLiveAppointment(appointment) {
        if (!isWithinCurrentRange(appointment)) {
            appointments = appointments.filter(item => item.appointmentId !== appointment.appointmentId);
        } else if (appointments.some(item => item.appointmentId === appointment.appointmentId)) {
            appointments = appointments.map(item =>
                item.appointmentId === appointment.appointmentId ? appointment : item
            );
        } else {
            appointments = [...appointments, appointment]
                .sort((a, b) => new Date(a.preferredStartAt) - new Date(b.preferredStartAt));
        }

        if (selectedId === appointment.appointmentId) {
            renderEditor(isWithinCurrentRange(appointment) ? appointment : null);
        }
        renderTable();
    }

    function isWithinCurrentRange(appointment) {
        const startValue = document.getElementById('filterStart').value;
        const endValue = document.getElementById('filterEnd').value;
        const rangeStart = new Date(`${startValue}T00:00:00`);
        const rangeEnd = new Date(`${endValue}T23:59:59`);
        const appointmentStart = new Date(appointment.preferredStartAt);
        return appointmentStart >= rangeStart && appointmentStart <= rangeEnd;
    }

    function setSocketStatus(text, state) {
        const status = document.getElementById('socketStatus');
        if (!status) {
            return;
        }
        status.textContent = text;
        status.className = `socket-status socket-${state}`;
    }

    function findAppointment(appointmentId) {
        return appointments.find(item => item.appointmentId === appointmentId);
    }

    function showMessage(text, variant) {
        const message = document.getElementById('admin-message');
        message.textContent = text;
        message.className = 'alert d-none';
        if (text && variant) {
            message.classList.remove('d-none');
            message.classList.add(`alert-${variant}`);
        }
    }

    function getErrorMessage(result, fallback) {
        const details = result?.error?.details;
        if (details && typeof details === 'object') {
            const messages = Object.entries(details)
                .map(([field, message]) => `${formatFieldName(field)}: ${message}`)
                .filter(Boolean);
            if (messages.length) {
                return messages.join(' ');
            }
        }

        return result?.error?.message || result?.message || fallback;
    }

    function formatFieldName(field) {
        return String(field || '')
            .replace(/([A-Z])/g, ' $1')
            .replace(/^./, (char) => char.toUpperCase());
    }

    function formatPurpose(value) {
        return formatStatus(value).replace('Crew Management', 'Crew management');
    }

    function formatStatus(value) {
        return String(value || '').toLowerCase().split('_')
            .map(part => part.charAt(0).toUpperCase() + part.slice(1))
            .join(' ');
    }

    function formatDateTime(value) {
        return new Intl.DateTimeFormat('en-PH', {
            dateStyle: 'medium',
            timeStyle: 'short',
            timeZone: 'Asia/Manila'
        }).format(new Date(value));
    }

    function toDateInput(date) {
        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, '0');
        const day = String(date.getDate()).padStart(2, '0');
        return `${year}-${month}-${day}`;
    }

    function toDateTimeLocal(value) {
        const date = new Date(value);
        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, '0');
        const day = String(date.getDate()).padStart(2, '0');
        const hours = String(date.getHours()).padStart(2, '0');
        const minutes = String(date.getMinutes()).padStart(2, '0');
        return `${year}-${month}-${day}T${hours}:${minutes}`;
    }

    function escapeHtml(value) {
        return String(value)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    function escapeAttribute(value) {
        return escapeHtml(value).replace(/`/g, '&#96;');
    }

    return { loadAppointments };
})();
