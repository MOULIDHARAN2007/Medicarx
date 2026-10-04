/**
 * MediCareX API Client
 * Centralized REST fetch client with automatic JWT token attachment and error handling
 */

const API_BASE_URL = window.location.origin;

class ApiClient {
    getToken() {
        return localStorage.getItem('medicarex_token');
    }

    setToken(token) {
        if (token) {
            localStorage.setItem('medicarex_token', token);
        } else {
            localStorage.removeItem('medicarex_token');
        }
    }

    getCurrentUser() {
        try {
            const userStr = localStorage.getItem('medicarex_user');
            return userStr ? JSON.parse(userStr) : null;
        } catch (e) {
            return null;
        }
    }

    getUser() {
        return this.getCurrentUser();
    }

    setCurrentUser(user) {
        if (user) {
            localStorage.setItem('medicarex_user', JSON.stringify(user));
        } else {
            localStorage.removeItem('medicarex_user');
        }
    }

    setUser(user) {
        this.setCurrentUser(user);
    }

    async request(endpoint, options = {}) {
        const url = new URL(endpoint.startsWith('http') ? endpoint : `${API_BASE_URL}${endpoint}`);
        
        if (options.params) {
            Object.keys(options.params).forEach(key => {
                if (options.params[key] !== undefined && options.params[key] !== null) {
                    url.searchParams.append(key, options.params[key]);
                }
            });
        }

        const headers = {
            'Content-Type': 'application/json',
            ...(options.headers || {})
        };

        const token = this.getToken();
        if (token) {
            headers['Authorization'] = `Bearer ${token}`;
        }

        try {
            const response = await fetch(url.toString(), {
                ...options,
                headers
            });

            const data = await response.json().catch(() => ({}));

            if (!response.ok) {
                if (response.status === 401 && !endpoint.includes('/login')) {
                    // Unauthorized -> clear state and redirect to login
                    this.setToken(null);
                    this.setCurrentUser(null);
                    if (!window.location.pathname.includes('login.html') && !window.location.pathname.includes('register.html')) {
                        window.location.href = '/login.html';
                    }
                }
                throw new Error(data.error || `HTTP error ${response.status}`);
            }

            return data;
        } catch (error) {
            console.error(`API Error [${endpoint}]:`, error);
            throw error;
        }
    }

    get(endpoint, params = {}) {
        return this.request(endpoint, { method: 'GET', params });
    }

    post(endpoint, data = {}) {
        return this.request(endpoint, { method: 'POST', body: JSON.stringify(data) });
    }

    put(endpoint, data = {}) {
        return this.request(endpoint, { method: 'PUT', body: JSON.stringify(data) });
    }

    delete(endpoint) {
        return this.request(endpoint, { method: 'DELETE' });
    }

    // Auth Endpoints
    async login(email, password) {
        const res = await this.post('/api/auth/login', { email, password });
        if (res.data?.token) {
            this.setToken(res.data.token);
            this.setCurrentUser(res.data.user);
        }
        return res;
    }

    async register(userData) {
        const res = await this.post('/api/auth/register', userData);
        if (res.data?.token) {
            this.setToken(res.data.token);
            this.setCurrentUser(res.data.user);
        }
        return res;
    }

    async getMe() {
        return this.get('/api/auth/me');
    }

    logout() {
        this.setToken(null);
        this.setCurrentUser(null);
        window.location.href = '/login.html';
    }

    // Medicines
    getMedicines(params) {
        return this.get('/api/medicines', params);
    }

    getMedicineById(id) {
        return this.get(`/api/medicines/${id}`);
    }

    getSavedMedicines() {
        return this.get('/api/medicines/saved');
    }

    toggleSaveMedicine(medicineId) {
        return this.post('/api/medicines/toggle-save', { medicineId });
    }

    // Pharmacies
    getPharmacies(params) {
        return this.get('/api/pharmacies', params);
    }

    getNearbyPharmacies(params) {
        return this.get('/api/pharmacies/nearby', params);
    }

    getPharmacyById(id) {
        return this.get(`/api/pharmacies/${id}`);
    }

    getPharmaciesByMedicine(medicineId, params) {
        return this.get(`/api/pharmacies/medicine/${medicineId}`, params);
    }

    // Inventory
    getInventory(params) {
        return this.get('/api/inventory', params);
    }

    updateStock(stockData) {
        return this.post('/api/inventory/update', stockData);
    }

    addStock(stockData) {
        return this.post('/api/inventory/add', stockData);
    }

    addStockQuantity(id, additionalQuantity) {
        return this.post('/api/inventory/add-quantity', { id, additionalQuantity });
    }

    updateInventoryMedicine(medicineOrInventoryId, data) {
        return this.put(`/api/pharmacy/inventory/${medicineOrInventoryId}`, data);
    }

    clearInventoryStock(medicineOrInventoryId) {
        return this.post(`/api/pharmacy/inventory/${medicineOrInventoryId}/clear-stock`);
    }

    removeInventoryMedicine(medicineOrInventoryId) {
        return this.delete(`/api/pharmacy/inventory/${medicineOrInventoryId}`);
    }

    clearAllInventoryStock() {
        return this.post('/api/pharmacy/inventory/clear-all-stock');
    }

    removeAllInventoryMedicines() {
        return this.delete('/api/pharmacy/inventory/remove-all');
    }

    importInventoryCsv(arg1, arg2) {
        if (typeof arg1 === 'string') {
            return this.post('/api/inventory/import-csv', { csv: arg1 });
        } else if (Array.isArray(arg1)) {
            return this.post('/api/inventory/import-csv', { rows: arg1 });
        } else if (arg2 !== undefined) {
            if (typeof arg2 === 'string') {
                return this.post('/api/inventory/import-csv', { pharmacyId: arg1, csv: arg2 });
            } else {
                return this.post('/api/inventory/import-csv', { pharmacyId: arg1, rows: arg2 });
            }
        } else if (typeof arg1 === 'object' && arg1 !== null) {
            return this.post('/api/inventory/import-csv', arg1);
        } else {
            return this.post('/api/inventory/import-csv', { csv: String(arg1 || '') });
        }
    }

    deleteInventoryItem(id) {
        return this.delete(`/api/inventory/${id}`);
    }

    getPharmacyStats(pharmacyId) {
        return this.get(`/api/inventory/stats/${pharmacyId}`);
    }

    getExpiringInventory(pharmacyId) {
        return this.get(`/api/inventory/expiring/${pharmacyId}`);
    }

    // Prescriptions
    getPrescriptions() {
        return this.get('/api/prescriptions');
    }

    getPrescriptionById(id) {
        return this.get(`/api/prescriptions/${id}`);
    }

    createPrescription(rxData) {
        return this.post('/api/prescriptions/create', rxData);
    }

    async downloadPrescription(id, defaultName) {
        const token = this.getToken();
        const headers = {};
        if (token) headers['Authorization'] = `Bearer ${token}`;
        const url = id ? `${API_BASE_URL}/api/prescriptions/download?id=${id}` : `${API_BASE_URL}/api/prescriptions/download`;
        const res = await fetch(url, { headers });
        if (!res.ok) {
            const err = await res.json().catch(() => ({}));
            throw new Error(err.error || 'Failed to download prescription');
        }
        const blob = await res.blob();
        const blobUrl = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = blobUrl;
        a.download = defaultName || (id ? `Prescription_${id}.pdf` : 'Prescription.pdf');
        document.body.appendChild(a);
        a.click();
        a.remove();
        window.URL.revokeObjectURL(blobUrl);
    }

    // Health Records
    getHealthRecords(params) {
        return this.get('/api/records', params);
    }

    createHealthRecord(recordData) {
        return this.post('/api/records/create', recordData);
    }

    async downloadHealthRecord(id, defaultName) {
        const token = this.getToken();
        const headers = {};
        if (token) headers['Authorization'] = `Bearer ${token}`;
        const url = id ? `${API_BASE_URL}/api/records/download?id=${id}` : `${API_BASE_URL}/api/records/download`;
        const res = await fetch(url, { headers });
        if (!res.ok) {
            const err = await res.json().catch(() => ({}));
            throw new Error(err.error || 'Failed to download health record');
        }
        const blob = await res.blob();
        const blobUrl = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = blobUrl;
        a.download = defaultName || (id ? `HealthRecord_${id}.pdf` : 'HealthRecord.pdf');
        document.body.appendChild(a);
        a.click();
        a.remove();
        window.URL.revokeObjectURL(blobUrl);
    }

    // Notifications
    getNotifications(params) {
        return this.get('/api/notifications', params);
    }

    getUnreadNotifCount() {
        return this.get('/api/notifications/unread-count');
    }

    markNotifAsRead(id) {
        return this.post(`/api/notifications/read/${id}`);
    }

    markAllNotifsAsRead() {
        return this.post('/api/notifications/read-all');
    }

    // Doctor Dashboard & Management
    getDoctorDashboard() {
        return this.get('/api/doctor/dashboard');
    }

    getDoctorPatients() {
        return this.get('/api/doctor/patients');
    }

    addDoctorPatient(patientData) {
        return this.post('/api/doctor/patients', patientData);
    }

    getDoctorAppointments(params) {
        return this.get('/api/doctor/appointments', params);
    }

    createDoctorAppointment(appointmentData) {
        return this.post('/api/doctor/appointments', appointmentData);
    }

    cancelDoctorAppointment(appointmentId) {
        return this.post('/api/doctor/appointments/cancel', { appointmentId });
    }

    updateAppointmentStatus(appointmentId, status) {
        return this.post('/api/doctor/appointments/status', { appointmentId, status });
    }

    // Admin
    getAdminStats() {
        return this.get('/api/admin/stats');
    }

    getAdminUsers() {
        return this.get('/api/admin/users');
    }

    createAdminMedicine(medData) {
        return this.post('/api/admin/medicines/create', medData);
    }

    updateAdminMedicine(medData) {
        return this.post('/api/admin/medicines/update', medData);
    }

    deleteAdminMedicine(id) {
        return this.delete(`/api/admin/medicines/${id}`);
    }

    createAdminPharmacy(pharmData) {
        return this.post('/api/admin/pharmacies/create', pharmData);
    }

    updateAdminPharmacy(pharmData) {
        return this.post('/api/admin/pharmacies/update', pharmData);
    }

    deleteAdminPharmacy(id) {
        return this.delete(`/api/admin/pharmacies/${id}`);
    }

    // Profile & Settings
    getUserProfile() {
        return this.get('/api/user/profile');
    }

    updateUserProfile(profileData) {
        return this.post('/api/user/profile', profileData);
    }

    changePassword(passwordData) {
        return this.post('/api/user/password', passwordData);
    }

    getUserSettings() {
        return this.get('/api/user/settings');
    }

    updateUserSettings(settingsData) {
        return this.post('/api/user/settings', settingsData);
    }

    // Orders (Pharmacy & Admin)
    getOrders(params) {
        return this.get('/api/orders', params);
    }

    updateOrderStatus(orderId, status) {
        return this.post('/api/orders/status', { orderId, status });
    }

    createOrder(orderData) {
        return this.post('/api/orders/create', orderData);
    }

    // Appointments (Patient & Doctor)
    getAppointments(params) {
        return this.get('/api/appointments', params);
    }

    getDoctors(params) {
        return this.get('/api/appointments/doctors', params);
    }

    getAvailableSlots(doctorId, date) {
        return this.get('/api/appointments/slots', { doctorId, date });
    }

    bookAppointment(appointmentData) {
        return this.post('/api/appointments/book', appointmentData);
    }

    cancelAppointment(appointmentId) {
        return this.post('/api/appointments/cancel', { appointmentId });
    }

    getAppointmentById(id) {
        return this.get(`/api/appointments/${id}`);
    }

    // Doctor Clinical Tools APIs
    getDoctorPatientOverview(patientId) {
        return this.get(`/api/doctor/patients/${patientId}/overview`);
    }

    getDoctorPatientWorkspace(patientId) {
        return this.get(`/api/doctor/patients/${patientId}/workspace`);
    }

    getDoctorPatientTimeline(patientId, params) {
        return this.get(`/api/doctor/patients/${patientId}/timeline`, params);
    }

    getDoctorTreatmentGoals(patientId) {
        return this.get(`/api/doctor/patients/${patientId}/treatment-goals`);
    }

    createDoctorTreatmentGoal(patientId, goalData) {
        return this.post(`/api/doctor/patients/${patientId}/treatment-goals`, goalData);
    }

    addDoctorTreatmentProgress(goalId, progressData) {
        return this.post(`/api/doctor/treatment-goals/${goalId}/progress`, progressData);
    }

    runDoctorAiClinicalSupport(patientIdOrPayload, symptoms, consultationNotes, vitals) {
        if (typeof patientIdOrPayload === 'object' && patientIdOrPayload !== null) {
            return this.post('/api/doctor/ai/clinical-support', patientIdOrPayload);
        }
        return this.post('/api/doctor/ai/clinical-support', {
            patientId: patientIdOrPayload,
            symptoms,
            consultationNotes,
            vitals: vitals || {}
        });
    }

    generateAiClinicalSupport(supportData) {
        return this.runDoctorAiClinicalSupport(supportData);
    }

    checkDoctorPrescriptionSafety(patientIdOrPayload, items) {
        if (typeof patientIdOrPayload === 'object' && patientIdOrPayload !== null) {
            return this.post('/api/doctor/prescriptions/safety-check', patientIdOrPayload);
        }
        return this.post('/api/doctor/prescriptions/safety-check', {
            patientId: patientIdOrPayload,
            items
        });
    }

    checkPrescriptionSafety(safetyData) {
        return this.checkDoctorPrescriptionSafety(safetyData);
    }

    // Admin Users
    createAdminUser(adminData) {
        return this.post('/api/admin/users', adminData);
    }

    createAdminAccount(accountData) {
        return this.post('/api/admin/users', accountData);
    }

    toggleAdminUserStatus(userId, isActive) {
        return this.post('/api/admin/users/status', { userId, isActive });
    }

    // Admin Approvals
    getDoctorApprovals(params) {
        return this.get('/api/admin/approvals/doctors', params);
    }

    getPharmacyApprovals(params) {
        return this.get('/api/admin/approvals/pharmacies', params);
    }

    approveDoctorApplication(id) {
        return this.post(`/api/admin/approvals/doctor/${id}/approve`);
    }

    rejectDoctorApplication(id, reason) {
        return this.post(`/api/admin/approvals/doctor/${id}/reject`, { reason });
    }

    approvePharmacyApplication(id) {
        return this.post(`/api/admin/approvals/pharmacy/${id}/approve`);
    }

    rejectPharmacyApplication(id, reason) {
        return this.post(`/api/admin/approvals/pharmacy/${id}/reject`, { reason });
    }

    // Prescription Upload & Medicine Extraction
    uploadPrescription(uploadData) {
        return this.post('/api/prescriptions/upload', uploadData);
    }

    getPrescriptionUploads() {
        return this.get('/api/prescriptions/uploads');
    }

    getPrescriptionUpload(id) {
        return this.get(`/api/prescriptions/uploads/${id}`);
    }

    confirmPrescriptionMedicines(uploadId, medicines) {
        return this.post(`/api/prescriptions/uploads/${uploadId}/confirm`, { medicines });
    }

    searchPharmaciesForMedicines(searchData) {
        return this.post('/api/pharmacies/search-medicines', searchData);
    }

    // AI Assistant
    askAi(message) {
        return this.post('/api/ai', { message });
    }

    // Pharmacy POS Billing & Barcode Scanning
    searchPharmacyMedicines(query, params = {}) {
        return this.get('/api/pharmacy/medicines/search', { q: query, ...params });
    }

    getMedicineByBarcode(barcode) {
        return this.get(`/api/pharmacy/inventory/barcode/${encodeURIComponent(barcode)}`);
    }

    createBill(billData) {
        return this.post('/api/pharmacy/bills', billData);
    }

    getBills(params) {
        return this.get('/api/pharmacy/bills', params);
    }

    getBillById(id) {
        return this.get(`/api/pharmacy/bills/${id}`);
    }

    cancelBill(id, reason) {
        return this.post(`/api/pharmacy/bills/${id}/cancel`, { reason });
    }

    getInventoryTransactions(params) {
        return this.get('/api/pharmacy/stock-audit', params);
    }

    // Doctor Advanced Tools & Pharmacy Availability
    getDoctorPharmacyAvailability(medicineId, patientId) {
        if (patientId) {
            return this.get(`/api/doctor/patients/${patientId}/pharmacy-availability/${medicineId}`);
        }
        return this.get(`/api/doctor/pharmacy-availability/${medicineId}`);
    }

    // ==========================================
    // Patient Enhancements APIs
    // ==========================================
    getMedicineSupplyStatus() {
        return this.get('/api/patient/medicine-supply');
    }

    getPatientDataSharing() {
        return this.get('/api/patient/data-sharing');
    }

    savePatientDataSharing(data) {
        return this.post('/api/patient/data-sharing', data);
    }

    revokePatientDataSharing(grantId) {
        return this.post(`/api/patient/data-sharing/${grantId}/revoke`);
    }

    getPatientEmergencyCard() {
        return this.get('/api/patient/emergency-card');
    }

    savePatientEmergencyCard(data) {
        return this.post('/api/patient/emergency-card', data);
    }

    regenerateEmergencyToken() {
        return this.post('/api/patient/emergency-card/regenerate-token');
    }

    getPublicEmergencyCard(token) {
        return this.get('/api/emergency/card/public', { token });
    }

    scanEmergencyCard(token) {
        return this.get('/api/emergency/card/scan', { token });
    }

    getPatientVisitPrep() {
        return this.get('/api/patient/visit-prep');
    }

    savePatientVisitPrep(data) {
        return this.post('/api/patient/visit-prep', data);
    }

    deletePatientVisitPrep(prepId) {
        return this.delete(`/api/patient/visit-prep/${prepId}`);
    }

    getPatientMedicalExpenses(params = {}) {
        return this.get('/api/patient/medical-expenses', params);
    }

    getPatientPurchaseHistory() {
        return this.get('/api/patient/purchase-history');
    }

    // ==========================================
    // Emergency & Ambulance APIs
    // ==========================================
    createEmergencyRequest(requestData) {
        return this.post('/api/emergency/requests', requestData);
    }

    getActiveEmergencyRequest() {
        return this.get('/api/emergency/active');
    }

    getPatientEmergencyRequests() {
        return this.get('/api/emergency/requests');
    }

    getEmergencyRequestById(id) {
        return this.get(`/api/emergency/requests/${id}`);
    }

    cancelEmergencyRequest(id, reason) {
        return this.post(`/api/emergency/requests/${id}/cancel`, { reason });
    }

    getDriverAmbulanceProfile() {
        return this.get('/api/ambulance/me');
    }

    getDriverActiveEmergency() {
        return this.get('/api/ambulance/emergency/active');
    }

    getDriverEmergencyLocation(id) {
        return this.get(`/api/ambulance/emergencies/${id}/location`);
    }

    getDriverEmergencyRequests() {
        return this.get('/api/ambulance/requests');
    }

    acceptEmergencyRequest(id) {
        return this.post(`/api/ambulance/requests/${id}/accept`);
    }

    updateEmergencyRequestStatus(id, status, notes) {
        return this.post(`/api/ambulance/requests/${id}/status`, { status, notes });
    }

    updateAmbulanceLocation(latitude, longitude) {
        return this.post('/api/ambulance/location', { latitude, longitude });
    }

    updateAmbulanceStatus(status) {
        return this.post('/api/ambulance/status', { status });
    }

    getAdminAmbulances() {
        return this.get('/api/admin/ambulances');
    }

    createAdminAmbulance(data) {
        return this.post('/api/admin/ambulances', data);
    }

    updateAdminAmbulance(id, data) {
        return this.put(`/api/admin/ambulances/${id}`, data);
    }

    deleteAdminAmbulance(id) {
        return this.delete(`/api/admin/ambulances/${id}`);
    }

    getAdminEmergencyRequests(params = {}) {
        return this.get('/api/admin/emergency/requests', params);
    }

    getAdminEmergencyStats() {
        return this.get('/api/admin/emergency/stats');
    }

    // ==========================================
    // Hospital Management & Dispatch Destination APIs
    // ==========================================
    getAdminHospitalStats() {
        return this.get('/api/admin/hospitals/stats');
    }

    getAdminHospitals(params = {}) {
        return this.get('/api/admin/hospitals', params);
    }

    getAdminHospitalById(id) {
        return this.get(`/api/admin/hospitals/${id}`);
    }

    createAdminHospital(data) {
        return this.post('/api/admin/hospitals', data);
    }

    updateAdminHospital(id, data) {
        return this.put(`/api/admin/hospitals/${id}`, data);
    }

    updateAdminHospitalStatus(id, active) {
        return this.post('/api/admin/hospitals/status', { id, active });
    }

    approveAdminHospital(id) {
        return this.post(`/api/admin/hospitals/${id}/approve`);
    }

    rejectAdminHospital(id, reason) {
        return this.post(`/api/admin/hospitals/${id}/reject`, { reason });
    }

    deleteAdminHospital(id) {
        return this.delete(`/api/admin/hospitals/${id}`);
    }

    suggestHospital(hospitalData) {
        return this.post('/api/ambulance/hospitals', hospitalData);
    }

    getNearbyHospitals(lat, lng, radius = 50.0) {
        return this.get('/api/ambulance/hospitals/nearby', { lat, lng, radius });
    }

    getAmbulanceHospitalById(id) {
        return this.get(`/api/ambulance/hospitals/${id}`);
    }

    selectEmergencyHospital(requestId, hospitalId) {
        return this.post(`/api/ambulance/emergencies/${requestId}/hospital`, { hospitalId });
    }

    getEmergencyHospital(requestId) {
        return this.get(`/api/ambulance/emergencies/${requestId}/hospital`);
    }
}

window.api = new ApiClient();
