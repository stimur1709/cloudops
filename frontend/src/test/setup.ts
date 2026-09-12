import "@testing-library/jest-dom/vitest";
import { afterEach } from "vitest";
import { cleanup } from "@testing-library/react";
import { resetHttpClientForTests } from "../api/client/http-client";

Object.defineProperties(Element.prototype, {
  hasPointerCapture: {
    configurable: true,
    value: () => false,
  },
  releasePointerCapture: {
    configurable: true,
    value: () => undefined,
  },
  setPointerCapture: {
    configurable: true,
    value: () => undefined,
  },
  scrollIntoView: {
    configurable: true,
    value: () => undefined,
  },
});

afterEach(() => {
  cleanup();
  resetHttpClientForTests();
  window.history.replaceState({}, "", "/");
});
