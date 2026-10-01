import { defineConfig } from 'vite';

export default defineConfig({
  server: {
    port: 5173,
    proxy: {
      '/api': process.env.SPRING_UPSTREAM || 'http://localhost:8080',
    },
  },
});

