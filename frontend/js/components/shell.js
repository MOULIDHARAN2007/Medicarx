/**
 * MediCareX Application Shell Component
 * Generates role-based Sidebar, Brand Header, Topbar, User Profile snippet, and Responsive Mobile Drawer
 */

const Shell = {
    init(activePageKey = 'dashboard') {
        const user = window.api?.getCurrentUser() || {
            fullName: 'User',
            role: 'PATIENT',
            email: '',
            avatarUrl: 'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=150'
        };

        this.renderSidebar(user, activePageKey);
        this.renderTopbar(user);
        this.setupBackdrop();
        this.setupEventListeners();
        this.fetchUnreadNotifications();
    },

    setupBackdrop() {
        if (!document.getElementById('sidebar-backdrop')) {
            const backdrop = document.createElement('div');
            backdrop.id = 'sidebar-backdrop';
            backdrop.className = 'sidebar-backdrop';
            backdrop.setAttribute('aria-label', 'Close menu overlay');
            backdrop.onclick = () => this.closeSidebar();
            document.body.appendChild(backdrop);
        }
    },

    setupEventListeners() {
        // Close sidebar on Escape key
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape') {
                this.closeSidebar();
            }
        });

        // Close mobile drawer when window resizes to desktop
        window.addEventListener('resize', () => {
            if (window.innerWidth > 768) {
                this.closeSidebar();
            }
        });
    },

    toggleSidebar() {
        const sidebar = document.getElementById('app-sidebar');
        const backdrop = document.getElementById('sidebar-backdrop');
        if (!sidebar) return;
        const isOpen = sidebar.classList.toggle('open');
        if (backdrop) {
            backdrop.classList.toggle('active', isOpen);
        }
        document.body.classList.toggle('sidebar-drawer-open', isOpen);
    },

    openSidebar() {
        const sidebar = document.getElementById('app-sidebar');
        const backdrop = document.getElementById('sidebar-backdrop');
        if (sidebar) sidebar.classList.add('open');
        if (backdrop) backdrop.classList.add('active');
        document.body.classList.add('sidebar-drawer-open');
    },

    closeSidebar() {
        const sidebar = document.getElementById('app-sidebar');
        const backdrop = document.getElementById('sidebar-backdrop');
        if (sidebar) sidebar.classList.remove('open');
        if (backdrop) backdrop.classList.remove('active');
        document.body.classList.remove('sidebar-drawer-open');
    },

    toggleMobileSearch() {
        const topbar = document.getElementById('app-topbar');
        if (topbar) {
            topbar.classList.toggle('search-open');
            const searchInput = topbar.querySelector('.topbar-search input');
            if (searchInput && topbar.classList.contains('search-open')) {
                searchInput.focus();
            }
        }
    },

    getMenuByRole(role) {
        const r = (role || 'PATIENT').toUpperCase();

        if (r === 'DOCTOR') {
            return [
                { category: 'MAIN', items: [
                    { key: 'doctor-dashboard', label: 'Dashboard', icon: 'home', href: '/doctor-dashboard.html' },
                    { key: 'doctor-patients', label: 'Patients', icon: 'users', href: '/doctor-patients.html' },
                    { key: 'doctor-appointments', label: 'Appointments', icon: 'calendar', href: '/doctor-appointments.html' },
                    { key: 'prescriptions', label: 'Prescriptions', icon: 'file-text', href: '/prescription.html' },
                    { key: 'doctor-medicine-search', label: 'Medicine Search', icon: 'search', href: '/doctor-medicine-search.html' },
                    { key: 'records', label: 'Health Records', icon: 'file-text', href: '/records.html' }
                ]},
                { category: 'DOCTOR TOOLS', items: [
                    { key: 'doctor-ai-support', label: 'AI Clinical Support', icon: 'sparkles', href: '/doctor-ai-support.html' },
                    { key: 'doctor-prescription-safety', label: 'Prescription Safety', icon: 'shield', href: '/doctor-prescription-safety.html' },
                    { key: 'doctor-patient-timeline', label: 'Patient Timeline', icon: 'clock', href: '/doctor-patient-timeline.html' },
                    { key: 'doctor-treatment-progress', label: 'Treatment Progress', icon: 'trending-up', href: '/doctor-treatment-progress.html' }
                ]},
                { category: 'ACCOUNT', items: [
                    { key: 'notifications', label: 'Notifications', icon: 'bell', href: '/notifications.html' },
                    { key: 'profile', label: 'Profile', icon: 'user', href: '/profile.html' },
                    { key: 'settings', label: 'Settings', icon: 'settings', href: '/settings.html' }
                ]}
            ];
        } else if (r === 'PHARMACY') {
            return [
                { category: 'PHARMACY OPERATIONS', items: [
                    { key: 'pharmacy-dashboard', label: 'Dashboard', icon: 'home', href: '/pharmacy-dashboard.html' },
                    { key: 'pharmacy-billing', label: 'Billing / POS', icon: 'credit-card', href: '/pharmacy-billing.html' },
                    { key: 'pharmacy-inventory', label: 'Inventory', icon: 'package', href: '/pharmacy-inventory.html' },
                    { key: 'pharmacy-medicines', label: 'Manage Medicines', icon: 'grid', href: '/pharmacy-medicines.html' },
                    { key: 'pharmacy-orders', label: 'Orders', icon: 'shopping-bag', href: '/pharmacy-orders.html' },
                    { key: 'prescriptions', label: 'Prescriptions', icon: 'file-text', href: '/prescription.html' }
                ]},
                { category: 'ACCOUNT', items: [
                    { key: 'notifications', label: 'Notifications', icon: 'bell', href: '/notifications.html' },
                    { key: 'profile', label: 'Profile', icon: 'user', href: '/profile.html' },
                    { key: 'settings', label: 'Settings', icon: 'settings', href: '/settings.html' }
                ]}
            ];
        } else if (r === 'ADMIN') {
            return [
                { category: 'SYSTEM MANAGEMENT', items: [
                    { key: 'admin-dashboard', label: 'Dashboard', icon: 'home', href: '/admin-dashboard.html' },
                    { key: 'admin-hospitals', label: 'Hospitals', icon: 'hospital', href: '/admin-hospitals.html' },
                    { key: 'admin-ambulances', label: 'Ambulance Fleet', icon: 'truck', href: '/admin-ambulances.html' },
                    { key: 'admin-doctor-approvals', label: 'Doctor Approvals', icon: 'activity', href: '/admin-doctor-approvals.html' },
                    { key: 'admin-pharmacy-approvals', label: 'Pharmacy Approvals', icon: 'map-pin', href: '/admin-pharmacy-approvals.html' },
                    { key: 'admin-users', label: 'Users', icon: 'users', href: '/admin-users.html' },
                    { key: 'admin-medicines', label: 'Medicines Catalog', icon: 'package', href: '/admin-medicines.html' },
                    { key: 'admin-pharmacies', label: 'Pharmacies Directory', icon: 'map-pin', href: '/admin-pharmacies.html' },
                    { key: 'admin-inventory', label: 'Global Inventory', icon: 'database', href: '/admin-inventory.html' }
                ]},
                { category: 'ACCOUNT', items: [
                    { key: 'notifications', label: 'Notifications', icon: 'bell', href: '/notifications.html' },
                    { key: 'profile', label: 'Profile', icon: 'user', href: '/profile.html' },
                    { key: 'settings', label: 'System Settings', icon: 'settings', href: '/settings.html' }
                ]}
            ];
        } else if (r === 'AMBULANCE') {
            return [
                { category: 'EMERGENCY DISPATCH', items: [
                    { key: 'ambulance-dashboard', label: 'Dashboard', icon: 'home', href: '/ambulance-dashboard.html' },
                    { key: 'ambulance-requests', label: 'Emergency Requests', icon: 'activity', href: '/ambulance-requests.html' },
                    { key: 'ambulance-active-emergency', label: 'Active Emergency', icon: 'zap', href: '/ambulance-active-emergency.html' },
                    { key: 'ambulance-trip-history', label: 'Trip History', icon: 'clock', href: '/ambulance-trip-history.html' }
                ]},
                { category: 'ACCOUNT', items: [
                    { key: 'notifications', label: 'Notifications', icon: 'bell', href: '/notifications.html' },
                    { key: 'profile', label: 'Profile', icon: 'user', href: '/profile.html' },
                    { key: 'settings', label: 'Settings', icon: 'settings', href: '/settings.html' }
                ]}
            ];
        } else {
            // Patient Menu
            return [
                { category: 'MAIN', items: [
                    { key: 'dashboard', label: 'Dashboard', icon: 'home', href: '/dashboard.html' },
                    { key: 'doctor-search', label: 'Find Doctors', icon: 'users', href: '/doctor-search.html' },
                    { key: 'search', label: 'Search Medicines', icon: 'search', href: '/search.html' },
                    { key: 'pharmacies', label: 'Pharmacies & Map', icon: 'map-pin', href: '/map.html' },
                    { key: 'prescriptions', label: 'Prescriptions', icon: 'file-text', href: '/prescription.html' },
                    { key: 'records', label: 'Health Records', icon: 'activity', href: '/records.html' },
                    { key: 'appointments', label: 'Appointments', icon: 'calendar', href: '/appointments.html' }
                ]},
                { category: 'HEALTH & CARE', items: [
                    { key: 'ai', label: 'AI Assistant', icon: 'message-square', href: '/ai-assistant.html' },
                    { key: 'patient-medicine-supply', label: 'Medicine Supply', icon: 'clock', href: '/patient-medicine-supply.html' },
                    { key: 'patient-visit-prep', label: 'Doctor Visit Prep', icon: 'clipboard', href: '/patient-visit-prep.html' },
                    { key: 'patient-data-sharing', label: 'Privacy & Consent', icon: 'lock', href: '/patient-data-sharing.html' }
                ]},
                { category: 'EMERGENCY & BILLING', items: [
                    { key: 'emergency-center', label: 'Emergency Center', icon: 'zap', href: '/emergency-center.html' },
                    { key: 'patient-emergency-card', label: 'Emergency Health Card', icon: 'shield', href: '/patient-emergency-card.html' },
                    { key: 'patient-medical-expenses', label: 'Medical Expenses', icon: 'credit-card', href: '/patient-medical-expenses.html' },
                    { key: 'patient-purchase-history', label: 'Purchase History', icon: 'shopping-bag', href: '/patient-purchase-history.html' }
                ]},
                { category: 'ACCOUNT', items: [
                    { key: 'notifications', label: 'Notifications', icon: 'bell', href: '/notifications.html' },
                    { key: 'profile', label: 'Profile', icon: 'user', href: '/profile.html' },
                    { key: 'settings', label: 'Settings', icon: 'settings', href: '/settings.html' }
                ]}
            ];
        }
    },

    isItemActive(item, activeKey) {
        if (!item) return false;
        if (item.key === activeKey) return true;
        if ((item.key === 'orders' || item.key === 'pharmacy-orders') && (activeKey === 'orders' || activeKey === 'pharmacy-orders')) return true;
        if (item.href) {
            const currentPath = window.location.pathname;
            const targetPath = item.href.split('?')[0].split('#')[0];
            if (currentPath === targetPath || (currentPath === '/' && (targetPath === '/dashboard.html' || targetPath === '/ambulance-dashboard.html'))) {
                // If it has hash, check hash
                if (item.href.includes('#')) {
                    const hash = window.location.hash;
                    const targetHash = '#' + item.href.split('#')[1];
                    return hash === targetHash;
                }
                return true;
            }
        }
        return false;
    },

    getIconSvg(iconName) {
        const icons = {
            'home': `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m3 9 9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><polyline points="9 22 9 12 15 12 15 22"/></svg>`,
            'search': `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>`,
            'map-pin': `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 10c0 6-8 12-8 12s-8-6-8-12a8 8 0 0 1 16 0Z"/><circle cx="12" cy="10" r="3"/></svg>`,
            'message-square': `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>`,
            'file-text': `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/><line x1="10" y1="9" x2="8" y2="9"/></svg>`,
            'activity': `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/></svg>`,
            'bell': `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9"/><path d="M10.3 21a1.94 1.94 0 0 0 3.4 0"/></svg>`,
            'user': `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>`,
            'users': `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>`,
            'settings': `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z"/><circle cx="12" cy="12" r="3"/></svg>`,
            'package': `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m7.5 4.27 9 5.15"/><path d="M21 8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16Z"/><path d="m3.3 7 8.7 5 8.7-5"/><path d="M12 22V12"/></svg>`,
            'grid': `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect width="7" height="7" x="3" y="3" rx="1"/><rect width="7" height="7" x="14" y="3" rx="1"/><rect width="7" height="7" x="14" y="14" rx="1"/><rect width="7" height="7" x="3" y="14" rx="1"/></svg>`,
            'calendar': `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect width="18" height="18" x="3" y="4" rx="2" ry="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>`,
            'database': `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><ellipse cx="12" cy="5" rx="9" ry="3"/><path d="M3 5V19A9 3 0 0 0 21 19V5"/><path d="M3 12A9 3 0 0 0 21 12"/></svg>`,
            'shopping-bag': `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4Z"/><line x1="3" y1="6" x2="21" y2="6"/><path d="M16 10a4 4 0 0 1-8 0"/></svg>`,
            'credit-card': `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect width="20" height="14" x="2" y="5" rx="2"/><line x1="2" x2="22" y1="10" y2="10"/></svg>`,
            'zap': `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/></svg>`,
            'clock': `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>`,
            'shield': `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>`,
            'lock': `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect width="18" height="11" x="3" y="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>`,
            'clipboard': `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2"/><rect width="8" height="4" x="8" y="2" rx="1" ry="1"/></svg>`,
            'truck': `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect width="16" height="13" x="1" y="3" rx="2"/><polygon points="16 8 20 8 23 11 23 16 16 16 16 8"/><circle cx="5.5" cy="18.5" r="2.5"/><circle cx="18.5" cy="18.5" r="2.5"/></svg>`,
            'sparkles': `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m12 3-1.912 5.813a2 2 0 0 1-1.275 1.275L3 12l5.813 1.912a2 2 0 0 1 1.275 1.275L12 21l1.912-5.813a2 2 0 0 1 1.275-1.275L21 12l-5.813-1.912a2 2 0 0 1-1.275-1.275L12 3Z"/><path d="M5 3v4"/><path d="M19 17v4"/><path d="M3 5h4"/><path d="M17 19h4"/></svg>`,
            'trending-up': `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="22 7 13.5 15.5 8.5 10.5 2 17"/><polyline points="16 7 22 7 22 13"/></svg>`,
            'hospital': `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 6v4"/><path d="M14 14h-4"/><path d="M14 18h-4"/><path d="M14 8h-4"/><path d="M18 12h-4"/><path d="M6 12h4"/><path d="M3 21h18"/><path d="M5 21V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v16"/><path d="M10 9h4"/></svg>`
        };
        return icons[iconName] || icons['home'];
    },

    renderSidebar(user, activeKey) {
        const sidebarEl = document.getElementById('app-sidebar');
        if (!sidebarEl) return;

        const role = (user.role || 'PATIENT').toUpperCase();
        const menuData = this.getMenuByRole(role);
        let menuHtml = '';

        let brandHome = '/dashboard.html';
        if (role === 'DOCTOR') brandHome = '/doctor-dashboard.html';
        else if (role === 'PHARMACY') brandHome = '/pharmacy-dashboard.html';
        else if (role === 'ADMIN') brandHome = '/admin-dashboard.html';
        else if (role === 'AMBULANCE') brandHome = '/ambulance-dashboard.html';

        if (menuData.length > 0 && menuData[0].category) {
            menuHtml = menuData.map(group => `
                <div class="menu-category" style="margin-top: 0.75rem; margin-bottom: 0.25rem; font-size: 10.5px; font-weight: 700; letter-spacing: 0.05em; color: var(--text-muted); text-transform: uppercase;">${group.category}</div>
                ${group.items.map(item => `
                    <a href="${item.href}" class="nav-link ${this.isItemActive(item, activeKey) ? 'active' : ''}" onclick="window.Shell.closeSidebar()">
                        ${this.getIconSvg(item.icon)}
                        <span>${item.label}</span>
                    </a>
                `).join('')}
            `).join('');
        } else {
            menuHtml = `
                <div class="menu-category">Main Navigation</div>
                ${menuData.map(item => `
                    <a href="${item.href}" class="nav-link ${this.isItemActive(item, activeKey) ? 'active' : ''}" onclick="window.Shell.closeSidebar()">
                        ${this.getIconSvg(item.icon)}
                        <span>${item.label}</span>
                    </a>
                `).join('')}
            `;
        }

        sidebarEl.innerHTML = `
            <div class="sidebar-header">
                <a href="${brandHome}" class="sidebar-brand-wrapper">
                    <div class="brand-logo-icon">✚</div>
                    <div class="brand-name">MediCare<span>X</span></div>
                </a>
                <button class="sidebar-close-btn" onclick="window.Shell.closeSidebar()" title="Close Menu" aria-label="Close menu">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <line x1="18" y1="6" x2="6" y2="18"></line>
                        <line x1="6" y1="6" x2="18" y2="18"></line>
                    </svg>
                </button>
            </div>
            <div class="sidebar-menu">
                ${menuHtml}
            </div>
            <div class="sidebar-footer">
                <div class="user-snippet" onclick="window.location.href='/profile.html'" style="cursor: pointer;">
                    <img src="${user.avatarUrl || 'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=150'}" alt="Avatar" class="user-avatar-sm" />
                    <div class="user-info-text">
                        <span class="user-name-text">${user.fullName || 'User'}</span>
                        <span class="user-role-text">${user.role || 'Patient'}</span>
                    </div>
                </div>
                <button class="btn-icon" title="Logout" onclick="window.api.logout()">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg>
                </button>
            </div>
        `;
    },

    renderTopbar(user) {
        const topbarEl = document.getElementById('app-topbar');
        if (!topbarEl) return;

        topbarEl.innerHTML = `
            <div class="topbar-left">
                <button class="mobile-menu-btn" id="mobile-menu-toggle" aria-label="Open Navigation Menu" onclick="window.Shell.toggleSidebar()">
                    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                        <line x1="3" y1="12" x2="21" y2="12"></line>
                        <line x1="3" y1="6" x2="21" y2="6"></line>
                        <line x1="3" y1="18" x2="21" y2="18"></line>
                    </svg>
                </button>
                <a href="/dashboard.html" class="mobile-brand">
                    <div class="brand-logo-icon" style="width: 30px; height: 30px; font-size: 13px;">✚</div>
                    <div class="brand-name" style="font-size: 1.15rem;">MediCare<span>X</span></div>
                </a>
            </div>

            <div class="topbar-search" id="topbar-search-bar">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
                <input type="text" placeholder="Search medicines, pharmacies, doctors..." onkeydown="if(event.key==='Enter') window.location.href='/search.html?q='+encodeURIComponent(this.value)" />
            </div>

            <div class="topbar-actions">
                <button class="mobile-search-toggle-btn" id="mobile-search-toggle" aria-label="Search" onclick="window.Shell.toggleMobileSearch()">
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
                </button>
                <a href="/notifications.html" class="notif-bell-btn" title="Notifications" aria-label="Notifications">
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9"/><path d="M10.3 21a1.94 1.94 0 0 0 3.4 0"/></svg>
                    <span id="topbar-notif-badge" class="notif-badge-pill hidden">0</span>
                </a>
                <div class="topbar-profile" onclick="window.location.href='/profile.html'" style="cursor: pointer;" title="View Profile">
                    <img src="${user.avatarUrl || 'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=150'}" alt="Avatar" class="topbar-avatar" />
                    <div class="topbar-user-info flex flex-col" style="line-height: 1.2;">
                        <span style="font-size: 13px; font-weight: 600; color: var(--navy);">${user.fullName || 'User'}</span>
                        <span style="font-size: 11px; color: var(--text-secondary);">${user.role || 'Patient'}</span>
                    </div>
                </div>
            </div>
        `;
    },

    async fetchUnreadNotifications() {
        try {
            const res = await window.api.getUnreadNotifCount();
            const badge = document.getElementById('topbar-notif-badge');
            if (badge && res.data?.unreadCount > 0) {
                badge.innerText = res.data.unreadCount;
                badge.classList.remove('hidden');
            }
        } catch (e) {
            // Quiet fallback
        }
    }
};

window.Shell = Shell;
