import { defineConfig } from 'vitest/config'

export default defineConfig({
  test: {
    include: ['tests/browser.test.ts'],
    testTimeout: 30000,
    // No jsdom — these tests launch a real browser via Puppeteer
  },
})
