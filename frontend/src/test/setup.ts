import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterAll, afterEach, beforeAll } from 'vitest'

import { server } from './server'

const browserGetBoundingClientRect = HTMLElement.prototype.getBoundingClientRect

HTMLElement.prototype.getBoundingClientRect = function getBoundingClientRect() {
  // O jsdom não executa layout CSS. Fornecer dimensões somente para o viewport virtualizado
  // permite testar o comportamento real do virtualizer sem alterar a implementação de produção.
  if (this.classList.contains('records-table__viewport')) {
    return {
      x: 0,
      y: 0,
      width: 960,
      height: 348,
      top: 0,
      right: 960,
      bottom: 348,
      left: 0,
      toJSON: () => ({}),
    } as DOMRect
  }

  return browserGetBoundingClientRect.call(this)
}

class ResizeObserverMock {
  private readonly callback: ResizeObserverCallback

  constructor(callback: ResizeObserverCallback) {
    this.callback = callback
  }

  observe(target: Element) {
    const contentRect = target.getBoundingClientRect()
    const size = {
      inlineSize: contentRect.width,
      blockSize: contentRect.height,
    }

    this.callback(
      [
        {
          target,
          contentRect,
          borderBoxSize: [size],
          contentBoxSize: [size],
          devicePixelContentBoxSize: [size],
        } as unknown as ResizeObserverEntry,
      ],
      this as unknown as ResizeObserver,
    )
  }

  unobserve() {}
  disconnect() {}
}

Object.defineProperty(globalThis, 'ResizeObserver', {
  configurable: true,
  writable: true,
  value: ResizeObserverMock,
})

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  cleanup()
  server.resetHandlers()
})
afterAll(() => server.close())
