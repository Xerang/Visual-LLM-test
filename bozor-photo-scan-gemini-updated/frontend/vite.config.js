import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// 개발 중에는 /api 요청을 Spring Boot(8080)로 넘긴다. 브라우저 입장에서는 같은 주소라 CORS 설정이 필요 없다.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': process.env.SPRING_UPSTREAM || 'http://localhost:8080',
    },
  },
});
