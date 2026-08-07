import { spawn } from "node:child_process";
import { dirname, isAbsolute, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const [mode, appDirectory, explicitSource, ...generatorArgs] = process.argv.slice(2);
if (!["generate", "verify"].includes(mode) || !appDirectory) {
  console.error(
    "Usage: generate-typescript-api.mjs <generate|verify> <app-directory> [contract] [generator options]",
  );
  process.exit(2);
}

const appRoot = resolve(process.cwd(), appDirectory);
const sourceInput = explicitSource || process.env.API_DOC_INPUT;
if (!sourceInput) {
  console.error("Missing API contract input. Set API_DOC_INPUT or pass a local path.");
  process.exit(2);
}

let source;
try {
  const url = new URL(sourceInput);
  if (url.protocol !== "file:") {
    console.error("The governed generator accepts a local OpenAPI file only.");
    process.exit(2);
  }
  source = url.pathname;
} catch {
  source = isAbsolute(sourceInput) ? sourceInput : resolve(process.cwd(), sourceInput);
}

const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const generator = resolve(scriptDirectory, "generate_typescript_api.py");
const child = spawn("python3", [generator, mode, appRoot, source, ...generatorArgs], {
  cwd: process.cwd(),
  stdio: "inherit",
  shell: false,
});
child.on("error", (error) => {
  console.error(error);
  process.exit(1);
});
child.on("exit", (code) => process.exit(code ?? 1));
