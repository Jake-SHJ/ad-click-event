/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// 개발 서버에서 pipeline 앱의 조회 API(8082)로 프록시한다. 화면은 이 API만 읽는다(헌법 기술 제약).
const queryApi = process.env.PIPELINE_URL ?? 'http://localhost:8082'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/demo': queryApi,
      '/v1': queryApi,
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    globals: true,
  },
})
