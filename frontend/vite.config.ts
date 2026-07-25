import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      '/oauth2': { target: 'http://localhost:8080', changeOrigin: true },
      '/login': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html', 'lcov'],
      thresholds: {
        statements: 90,
        branches: 90,
        functions: 90,
        lines: 90,
      },
      // Setting `exclude` replaces vitest's defaults, so build output has to be
      // listed explicitly: otherwise a coverage run after `npm run build` counts
      // the bundled chunks in dist/ as 0%-covered source and drags the totals
      // toward the 90% gate.
      exclude: [
        'src/api/**',
        'src/main.tsx',
        'src/vite-env.d.ts',
        'src/test/**',
        '**/*.config.*',
        'coverage/**',
        'dist/**',
      ],
    },
  },
})
