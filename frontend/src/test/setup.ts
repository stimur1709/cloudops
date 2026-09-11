import "@testing-library/jest-dom/vitest";
import { afterEach } from "vitest";
import { cleanup } from "@testing-library/react";
import { resetHttpClientForTests } from "../api/client/http-client";

afterEach(() => {
  cleanup();
  resetHttpClientForTests();
  window.history.replaceState({}, "", "/");
});
