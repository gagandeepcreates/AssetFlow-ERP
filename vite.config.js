import { defineConfig } from 'vite';
import { resolve } from 'path';

export default defineConfig({
  build: {
    rollupOptions: {
      input: {
        main: resolve(__dirname, 'index.html'),
        dashboard: resolve(__dirname, 'dashboard.html'),
        assets: resolve(__dirname, 'assets.html'),
        maintenance: resolve(__dirname, 'maintenance.html'),
        orgSetup: resolve(__dirname, 'org-setup.html'),
        transfer: resolve(__dirname, 'transfer.html'),
        booking: resolve(__dirname, 'booking.html'),
        audit: resolve(__dirname, 'audit.html'),
        reports: resolve(__dirname, 'reports.html'),
        notifications: resolve(__dirname, 'notifications.html'),
        register: resolve(__dirname, 'register.html'),
      },
    },
  },
});
