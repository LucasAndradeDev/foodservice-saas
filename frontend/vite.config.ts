import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    // Listens on the LAN IP too (not just localhost) so a phone on the same Wi-Fi can open
    // the digital menu during dev — QR codes are built from window.location.origin, so
    // scanning one only works if you loaded the admin panel itself from that LAN IP.
    host: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
