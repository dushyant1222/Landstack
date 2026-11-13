/// <reference types="vitest" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'node:path';

// The dev server proxies /api to the Spring Boot backend so the session cookie stays same-origin.
export default defineConfig({
  plugins: [react()],
  resolve: { alias: { '@': path.resolve(__dirname, 'src') } },
  server: {
    port: 5173,
    proxy: {
      '/api': { target: process.env.LANDSTACK_BACKEND_URL ?? 'http://localhost:8080', changeOrigin: false },
    },
  },
  build: { sourcemap: false, chunkSizeWarningLimit: 900 },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
    css: false,
  },
});
