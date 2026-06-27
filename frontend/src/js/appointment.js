document.addEventListener('DOMContentLoaded', () => {
    const form = document.getElementById('appointmentForm');
    const message = document.getElementById('appointment-message');

    if (!form || !message) {
        return;
    }

    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        setMessage('', '');
        const calendarWindow = window.open('', '_blank');

        const formData = new FormData(form);
        const payload = {
            clientName: formData.get('clientName')?.toString().trim(),
            clientEmail: formData.get('clientEmail')?.toString().trim(),
            clientPhone: formData.get('clientPhone')?.toString().trim() || null,
            companyName: formData.get('companyName')?.toString().trim() || null,
            purpose: formData.get('purpose')?.toString(),
            preferredStartAt: toIsoInstant(formData.get('preferredStartAt')),
            preferredEndAt: toIsoInstant(formData.get('preferredEndAt')),
            notes: formData.get('notes')?.toString().trim() || null
        };

        try {
            const response = await fetch(window.APP_APPOINTMENT_API_BASE || 'http://localhost:8084/api/v1/appointments', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(payload)
            });

            const result = await response.json();
            if (!response.ok) {
                throw new Error(result.error?.message || result.message || 'Appointment request failed');
            }

            form.reset();
            const ref = result.data?.appointmentRef;
            const calendarUrl = buildGoogleCalendarUrl(payload, ref);
            if (calendarWindow) {
                calendarWindow.location.href = calendarUrl;
            } else {
                window.location.href = calendarUrl;
            }
            setMessage(`Appointment request received${ref ? `: ${ref}` : ''}. Google Calendar opened with the event details.`, 'success');
        } catch (error) {
            if (calendarWindow) {
                calendarWindow.close();
            }
            setMessage(error.message || 'Appointment request failed', 'danger');
        }
    });

    function toIsoInstant(value) {
        if (!value) {
            return null;
        }
        return new Date(value.toString()).toISOString();
    }

    function buildGoogleCalendarUrl(payload, ref) {
        const start = toGoogleCalendarDate(payload.preferredStartAt);
        const end = toGoogleCalendarDate(payload.preferredEndAt);
        const details = [
            ref ? `Appointment reference: ${ref}` : null,
            `Client name: ${payload.clientName}`,
            `Email: ${payload.clientEmail}`,
            payload.clientPhone ? `Phone: ${payload.clientPhone}` : null,
            payload.companyName ? `Company: ${payload.companyName}` : null,
            `Purpose: ${payload.purpose}`,
            payload.notes ? `Notes: ${payload.notes}` : null
        ].filter(Boolean).join('\n');

        const params = new URLSearchParams({
            action: 'TEMPLATE',
            text: `Golden Infinity Appointment${ref ? ` - ${ref}` : ''}`,
            dates: `${start}/${end}`,
            details,
            location: '1431 A. Mabini St. Rm. 505 5th Flr. MRS Bldg., Roxas Blvd., Ermita, Manila',
            sf: 'true',
            output: 'xml'
        });

        return `https://calendar.google.com/calendar/render?${params.toString()}`;
    }

    function toGoogleCalendarDate(value) {
        return new Date(value).toISOString().replace(/[-:]/g, '').replace(/\.\d{3}Z$/, 'Z');
    }

    function setMessage(text, variant) {
        message.textContent = text;
        message.className = 'alert d-none';
        if (text && variant) {
            message.classList.remove('d-none');
            message.classList.add(`alert-${variant}`);
        }
    }
});
