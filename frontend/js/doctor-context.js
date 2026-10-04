/**
 * MediCareX Doctor Shared Patient Context Helper
 * Manages patient context synchronization across independent Doctor tools:
 * - Patient Overview
 * - AI Clinical Decision Support
 * - Prescription Safety & Formulary
 * - Patient Health Timeline
 * - Treatment Progress Tracker
 */

const DoctorContext = {
    STORAGE_KEY: 'medicarex_doctor_selected_patient',

    getSelectedPatientId() {
        const urlParams = new URLSearchParams(window.location.search);
        const urlId = urlParams.get('patientId');
        if (urlId && !isNaN(parseInt(urlId))) {
            const parsed = parseInt(urlId);
            sessionStorage.setItem(this.STORAGE_KEY, parsed);
            return parsed;
        }
        const stored = sessionStorage.getItem(this.STORAGE_KEY);
        if (stored && !isNaN(parseInt(stored))) {
            return parseInt(stored);
        }
        return 1; // Default to first patient
    },

    setSelectedPatientId(patientId, updateUrl = true) {
        if (!patientId) return;
        const pId = parseInt(patientId);
        sessionStorage.setItem(this.STORAGE_KEY, pId);
        if (updateUrl) {
            const url = new URL(window.location.href);
            url.searchParams.set('patientId', pId);
            window.history.replaceState({}, '', url.toString());
        }
    },

    async loadPatientsRoster(selectElId, activePatientId, onChangeCallback) {
        const select = document.getElementById(selectElId);
        if (!select) return [];

        try {
            const res = await window.api.getDoctorPatients();
            const list = res.data || [];

            select.innerHTML = '';
            if (list.length === 0) {
                const opt = document.createElement('option');
                opt.value = "";
                opt.textContent = "No registered patients";
                select.appendChild(opt);
                return [];
            }

            const uniqueMap = new Map();
            list.forEach(p => {
                const pId = p.id != null ? p.id : p.patientId;
                if (pId != null && !uniqueMap.has(pId)) {
                    uniqueMap.set(pId, p);
                }
            });

            uniqueMap.forEach((p, pId) => {
                const opt = document.createElement('option');
                opt.value = pId;
                const name = p.fullName || p.name || 'Patient';
                const meta = [];
                if (p.gender) meta.push(p.gender);
                if (p.bloodGroup) meta.push(p.bloodGroup);
                opt.textContent = meta.length > 0 ? `${name} (#PAT-${pId} • ${meta.join(', ')})` : `${name} (#PAT-${pId})`;
                if (pId === activePatientId) {
                    opt.selected = true;
                }
                select.appendChild(opt);
            });

            select.onchange = (e) => {
                const newId = parseInt(e.target.value);
                if (newId) {
                    this.setSelectedPatientId(newId, true);
                    if (typeof onChangeCallback === 'function') {
                        onChangeCallback(newId);
                    }
                }
            };

            return Array.from(uniqueMap.values());
        } catch (err) {
            console.error('Failed to load patient roster:', err);
            return [];
        }
    },

    renderPatientHeader(containerId, patient, activeToolKey = 'overview') {
        const container = document.getElementById(containerId);
        if (!container) return;

        const p = patient || {};
        const pId = p.id || p.patientId || this.getSelectedPatientId();
        const name = p.fullName || p.name || 'Priya S. Sharma';
        const avatar = p.avatarUrl || 'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=120';
        const gender = p.gender || 'Female';
        const blood = p.bloodGroup || 'O+';
        const phone = p.phone || '+91 98765 43210';
        const insurance = p.insuranceProvider || 'Star Health Premier';

        let ageText = '';
        if (p.dob) {
            const birthYear = parseInt(p.dob.substring(0, 4));
            const curYear = new Date().getFullYear();
            if (!isNaN(birthYear) && curYear > birthYear) {
                ageText = `${curYear - birthYear} Yrs`;
            }
        }

        container.innerHTML = `
            <div class="patient-banner-card" style="background: #FFFFFF; border: 1px solid var(--border); border-radius: 12px; padding: 1.25rem 1.5rem; margin-bottom: 1.5rem; box-shadow: 0 1px 3px rgba(0,0,0,0.03);">
                <div class="flex flex-wrap items-center justify-between gap-4">
                    <div class="flex items-center gap-4">
                        <img src="${avatar}" alt="${name}" style="width: 58px; height: 58px; border-radius: 50%; object-fit: cover; border: 2px solid #EFF6FF;" />
                        <div>
                            <div class="flex items-center gap-2 flex-wrap">
                                <h2 style="font-family: 'Outfit', sans-serif; font-size: 1.3rem; font-weight: 700; color: var(--navy); margin: 0;">${name}</h2>
                                <span class="badge badge-gray" style="font-size: 11px; font-weight: 600;">#PAT-${pId}</span>
                                <span class="badge badge-blue" style="font-size: 11px; font-weight: 600;">${blood}</span>
                            </div>
                            <div class="flex flex-wrap items-center gap-2 mt-1" style="font-size: 12.5px; color: var(--text-secondary);">
                                <span>${gender}${ageText ? ' • ' + ageText : ''}</span>
                                <span>•</span>
                                <span>📞 ${phone}</span>
                                <span>•</span>
                                <span>🛡️ ${insurance}</span>
                            </div>
                        </div>
                    </div>

                    <div class="flex items-center gap-3">
                        <div class="flex items-center gap-2" style="background: #F8FAFC; border: 1px solid var(--border); border-radius: 8px; padding: 0.35rem 0.65rem;">
                            <label for="doctor-patient-switcher" style="font-size: 12px; font-weight: 600; color: var(--navy); margin: 0;">Active Patient:</label>
                            <select id="doctor-patient-switcher" class="form-select" style="min-width: 200px; padding: 0.25rem 0.5rem; font-size: 12.5px; background: #FFFFFF;">
                                <option value="${pId}">Loading roster...</option>
                            </select>
                        </div>
                    </div>
                </div>

                <!-- Navigation Action Links to Independent Tool Pages -->
                <div class="flex items-center gap-2 mt-3 pt-3" style="border-top: 1px solid var(--border); overflow-x: auto; white-space: nowrap; scrollbar-width: thin;">
                    <span style="font-size: 11px; font-weight: 700; color: var(--text-muted); text-transform: uppercase; margin-right: 0.25rem;">Patient Features:</span>
                    <a href="/doctor-patient-overview.html?patientId=${pId}" class="btn btn-sm ${activeToolKey === 'overview' ? 'btn-primary' : 'btn-outline'}" style="font-size: 11.5px; padding: 0.25rem 0.65rem;">
                        📋 Overview
                    </a>
                    <a href="/doctor-ai-support.html?patientId=${pId}" class="btn btn-sm ${activeToolKey === 'ai-support' ? 'btn-primary' : 'btn-outline'}" style="font-size: 11.5px; padding: 0.25rem 0.65rem;">
                        ✨ AI Clinical Support
                    </a>
                    <a href="/doctor-prescription-safety.html?patientId=${pId}" class="btn btn-sm ${activeToolKey === 'prescription-safety' ? 'btn-primary' : 'btn-outline'}" style="font-size: 11.5px; padding: 0.25rem 0.65rem;">
                        🛡️ Prescription Safety
                    </a>
                    <a href="/doctor-patient-timeline.html?patientId=${pId}" class="btn btn-sm ${activeToolKey === 'timeline' ? 'btn-primary' : 'btn-outline'}" style="font-size: 11.5px; padding: 0.25rem 0.65rem;">
                        📅 Health Timeline
                    </a>
                    <a href="/doctor-treatment-progress.html?patientId=${pId}" class="btn btn-sm ${activeToolKey === 'treatment-progress' ? 'btn-primary' : 'btn-outline'}" style="font-size: 11.5px; padding: 0.25rem 0.65rem;">
                        📈 Treatment Progress
                    </a>
                </div>
            </div>
        `;
    }
};

window.DoctorContext = DoctorContext;
