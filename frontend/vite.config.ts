/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// Tests run in UTC so date formatting is deterministic on every machine and in CI.
process.env.TZ = 'UTC';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    // Same-origin API calls in development: /api is forwarded to the Spring Boot backend.
    proxy: {
      '/api': {
        target: process.env.VITE_API_PROXY_TARGET ?? 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: true,
    // The MUI vendor chunk is ~515 kB minified (159 kB gzipped); it is cached separately from the app code.
    chunkSizeWarningLimit: 600,
    rollupOptions: {
      output: {
        // Vendor chunks change rarely, so browsers keep them cached across app releases.
        manualChunks: {
          react: ['react', 'react-dom', 'react-router-dom'],
          mui: ['@mui/material', '@emotion/react', '@emotion/styled'],
          pickers: ['@mui/x-date-pickers', 'dayjs'],
          data: ['@tanstack/react-query', 'axios', 'react-hook-form', 'cronstrue'],
        },
      },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
    exclude: ['e2e/**', 'node_modules/**', 'dist/**'],
    css: false,
    env: { TZ: 'UTC' },
    testTimeout: 20_000,
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html', 'lcov'],
      reportsDirectory: 'coverage',
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/main.tsx', 'src/test/**', 'src/**/*.test.{ts,tsx}', 'src/vite-env.d.ts', 'src/types/**'],
      thresholds: {
        lines: 70,
        statements: 70,
        functions: 70,
        branches: 70,
      },
    },
  },
});
