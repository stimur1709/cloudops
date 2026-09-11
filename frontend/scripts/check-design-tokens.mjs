import { readFile, readdir } from "node:fs/promises";
import { extname, join, relative } from "node:path";
import { fileURLToPath } from "node:url";

const root = fileURLToPath(new URL("../src", import.meta.url));
const allowed = new Set(["tokens.css"]);
const violations = [];
const forbidden = [
  /#[0-9a-f]{3,8}\b/gi,
  /\b(?:bg|text|border|ring)-(?:slate|gray|zinc|neutral|stone|red|orange|amber|yellow|lime|green|emerald|teal|cyan|sky|blue|indigo|violet|purple|fuchsia|pink|rose)-\d{2,3}\b/g,
];

async function visit(directory) {
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    const path = join(directory, entry.name);
    if (entry.isDirectory()) {
      await visit(path);
    } else if (
      [".ts", ".tsx", ".css"].includes(extname(entry.name)) &&
      !allowed.has(entry.name)
    ) {
      const contents = await readFile(path, "utf8");
      for (const pattern of forbidden) {
        for (const match of contents.matchAll(pattern)) {
          const line = contents.slice(0, match.index).split("\n").length;
          violations.push(`${relative(root, path)}:${line} ${match[0]}`);
        }
      }
    }
  }
}

await visit(root);
if (violations.length > 0) {
  console.error(`Design token violations:\n${violations.join("\n")}`);
  process.exitCode = 1;
}
