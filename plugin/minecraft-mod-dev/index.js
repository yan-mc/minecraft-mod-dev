import { defineTool } from '@deepseek-ai/dsh-tools';
import { spawn, spawnSync } from 'node:child_process';
import {
  copyFileSync,
  existsSync,
  mkdirSync,
  mkdtempSync,
  readFileSync,
  rmSync,
  statSync,
} from 'node:fs';
import { tmpdir } from 'node:os';
import { basename, join } from 'node:path';

const DEFAULT_PORTS = { server: 39001, client: 39002 };
const TOKEN = 'mcdev';

export const name = 'mcdev';
export const inject = ['tools'];

async function probe(side, path, init = {}, port) {
  const p = port ?? DEFAULT_PORTS[side] ?? DEFAULT_PORTS.server;
  const url = `http://127.0.0.1:${p}${path}`;
  const response = await fetch(url, {
    ...init,
    headers: {
      'X-Mcdev-Token': TOKEN,
      ...(init.headers ?? {}),
    },
    signal: AbortSignal.timeout(15000),
  });
  const text = await response.text();
  if (!response.ok) {
    throw new Error(`probe ${side}:${p} ${path} -> HTTP ${response.status}: ${text}`);
  }
  return text;
}

function stringTool(name, description, parameters, handler) {
  return defineTool({
    name,
    description,
    parameters,
    output: {
      schema: {
        type: 'object',
        additionalProperties: false,
        properties: {
          text: { type: 'string', required: true },
        },
      },
      render: (_args, value) => [{ type: 'text', text: value.text }],
    },
    async execute(args) {
      return { text: await handler(args) };
    },
  });
}

const sideParam = {
  type: 'string',
  enum: ['server', 'client'],
  required: true,
  description: 'Which probe to talk to: the dedicated server (39001) or the client (39002).',
};
const portParam = {
  type: 'integer',
  description: 'Override the probe port. Defaults: server 39001, client 39002.',
};

function readJarManifest(jarPath) {
  const win = process.platform === 'win32';
  const jar = process.env.JAVA_HOME
    ? join(process.env.JAVA_HOME, 'bin', win ? 'jar.exe' : 'jar')
    : 'jar';
  const dir = mkdtempSync(join(tmpdir(), 'mcdev-jar-'));
  try {
    const result = spawnSync(jar, ['xf', jarPath, 'META-INF/MANIFEST.MF'], {
      cwd: dir,
      encoding: 'utf8',
      shell: false,
      windowsHide: true,
    });
    if (result.error || result.status !== 0) {
      return {
        checked: false,
        error: result.error?.message || result.stderr || `jar exit ${result.status}`,
      };
    }
    const manifestPath = join(dir, 'META-INF', 'MANIFEST.MF');
    if (!existsSync(manifestPath)) {
      return { checked: false, error: 'MANIFEST.MF missing after extract' };
    }
    return { checked: true, text: readFileSync(manifestPath, 'utf8') };
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
}

function runGradle(projectDir, task, timeoutMs = 900000) {
  return new Promise((resolve, reject) => {
    if (!projectDir || !existsSync(projectDir)) {
      reject(new Error(`projectDir not found: ${projectDir}`));
      return;
    }
    const win = process.platform === 'win32';
    const child = spawn(win ? 'gradlew.bat' : './gradlew', [task, '--console=plain'], {
      cwd: projectDir,
      shell: win,
      windowsHide: true,
    });
    let out = '';
    const cap = 200_000;
    const append = (chunk) => {
      out += chunk.toString();
      if (out.length > cap) {
        out = out.slice(-cap);
      }
    };
    child.stdout.on('data', append);
    child.stderr.on('data', append);
    const timer = setTimeout(() => {
      try {
        child.kill('SIGKILL');
      } catch {
        // ignore
      }
      reject(new Error(`gradle ${task} timed out after ${timeoutMs}ms`));
    }, timeoutMs);
    child.on('error', (error) => {
      clearTimeout(timer);
      reject(error);
    });
    child.on('close', (code) => {
      clearTimeout(timer);
      resolve({ code, output: out });
    });
  });
}

export function apply(ctx) {
  ctx.tools.register(stringTool(
    'mcdev_status',
    'Read the Minecraft probe status. Returns JSON with side, tick/gameTime (server) or screen/fps/player (client).',
    { side: sideParam, port: portParam },
    (args) => probe(args.side, '/status', {}, args.port),
  ));

  ctx.tools.register(stringTool(
    'mcdev_command',
    'Run a Minecraft command on the selected side. Server: console command. Client: send the command to the server as the player. Returns JSON.',
    {
      side: sideParam,
      cmd: {
        type: 'string',
        required: true,
        description: 'Command without a leading slash, e.g. "time set day" or "mcdev fakeplayer spawn Steve".',
      },
      port: portParam,
    },
    (args) => probe(args.side, '/command', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ cmd: args.cmd }),
    }, args.port),
  ));

  ctx.tools.register(stringTool(
    'mcdev_ui',
    'Read the current client Screen: title, AbstractWidget tree (class/x/y/width/height/message/active/visible) and container slots (index/x/y/screenX/screenY/item/count). Returns JSON.',
    { port: portParam },
    (args) => probe('client', '/ui/snapshot', {}, args.port),
  ));

  ctx.tools.register(stringTool(
    'mcdev_click',
    'Click on the current client Screen. Use either GUI-scaled x/y, or a container slot index. shift=true uses quick-move (shift-click). Returns JSON.',
    {
      x: { type: 'integer', description: 'GUI-scaled X (not window pixels).' },
      y: { type: 'integer', description: 'GUI-scaled Y (not window pixels).' },
      slot: { type: 'integer', description: 'Container slot index; takes precedence over x/y.' },
      button: { type: 'integer', description: '0 left, 1 right. Default 0.' },
      shift: { type: 'boolean', description: 'Shift-click / quick-move when true.' },
      port: portParam,
    },
    (args) => {
      const body = { button: args.button ?? 0 };
      if (typeof args.slot === 'number' && args.slot >= 0) {
        body.slot = args.slot;
      } else {
        body.x = args.x ?? 0;
        body.y = args.y ?? 0;
      }
      if (args.shift) {
        body.shift = true;
      }
      return probe('client', '/ui/click', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      }, args.port);
    },
  ));

  ctx.tools.register(stringTool(
    'mcdev_screenshot',
    'Capture the current client frame to a PNG. Returns JSON with path/exists/size/screen. Use read_image on the returned path to see it.',
    { port: portParam },
    (args) => probe('client', '/screenshot', {}, args.port),
  ));

  ctx.tools.register(stringTool(
    'mcdev_run_dump',
    'Write a run snapshot (runId/startedAt/tick/gameTime/frozen/logs) to .mcdev/runs/<id>/snapshot-<ts>.json and return it as JSON.',
    {
      side: sideParam,
      n: { type: 'integer', description: 'Number of recent log lines. Default 200.' },
      port: portParam,
    },
    (args) => probe(args.side, `/run/dump?n=${args.n ?? 200}`, {}, args.port),
  ));

  ctx.tools.register(stringTool(
    'mcdev_install_probe',
    'Install a dev-mapped probe jar into one or more Forge run directories (<runDir>/mods). Validates META-INF/MANIFEST.MF contains "MixinConfigs: mcdev_probe.mixins.json" when the jar tool is available.',
    {
      probeJar: {
        type: 'string',
        required: true,
        description: 'Absolute path to mcdev_probe-*-dev.jar.',
      },
      runDirs: {
        type: 'array',
        required: true,
        description: 'Absolute run directories; each gets <runDir>/mods/<jar>.',
        items: { type: 'string' },
      },
    },
    (args) => {
      const jarPath = args.probeJar;
      if (!existsSync(jarPath)) {
        throw new Error(`probe jar not found: ${jarPath}`);
      }
      const info = statSync(jarPath);
      if (!info.isFile()) {
        throw new Error(`not a file: ${jarPath}`);
      }
      const runDirs = Array.isArray(args.runDirs) ? args.runDirs : [];
      if (runDirs.length === 0) {
        throw new Error('runDirs must be a non-empty array');
      }
      const name = basename(jarPath);
      if (!/-dev\.jar$/i.test(name)) {
        throw new Error(
          'probe jar must be a *-dev.jar (dev-mapped); normal build/reobf jars fail in dev',
        );
      }
      const manifest = readJarManifest(jarPath);
      const hasMixin = manifest.checked
        ? /MixinConfigs\s*:\s*mcdev_probe\.mixins\.json/.test(manifest.text)
        : false;
      if (manifest.checked && !hasMixin) {
        throw new Error(
          'not a dev-mapped probe jar: MANIFEST.MF missing "MixinConfigs: mcdev_probe.mixins.json"',
        );
      }
      const targets = [];
      const warnings = [];
      if (!manifest.checked) {
        warnings.push(`manifest not verified: ${manifest.error}`);
      }
      for (const runDir of runDirs) {
        if (typeof runDir !== 'string' || runDir.trim() === '') {
          continue;
        }
        const modsDir = join(runDir, 'mods');
        mkdirSync(modsDir, { recursive: true });
        const dest = join(modsDir, name);
        copyFileSync(jarPath, dest);
        targets.push({ runDir, dest });
      }
      return JSON.stringify({
        probeJar: jarPath,
        size: info.size,
        manifestChecked: manifest.checked,
        hasMixinConfigs: hasMixin,
        targets,
        warnings,
      });
    },
  ));

  ctx.tools.register(stringTool(
    'mcdev_use',
    'Use the held item on a block (right-click) at x/y/z on the client. Opens chests/crafting tables and triggers block interactions. Returns JSON.',
    {
      x: { type: 'integer', required: true, description: 'Block X.' },
      y: { type: 'integer', required: true, description: 'Block Y.' },
      z: { type: 'integer', required: true, description: 'Block Z.' },
      face: {
        type: 'string',
        enum: ['up', 'down', 'north', 'south', 'east', 'west'],
        description: 'Hit face. Default up.',
      },
      port: portParam,
    },
    (args) => probe('client', '/player/use', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        x: args.x,
        y: args.y,
        z: args.z,
        face: args.face ?? 'up',
      }),
    }, args.port),
  ));

  ctx.tools.register(stringTool(
    'mcdev_build',
    'Run "gradlew build" in a Minecraft 1.20.1 Forge project directory. Returns JSON {exitCode, tail}. Can take minutes.',
    {
      projectDir: {
        type: 'string',
        required: true,
        description: 'Absolute project directory containing gradlew.bat.',
      },
    },
    async (args) => {
      const result = await runGradle(args.projectDir, 'build', 900000);
      return JSON.stringify({
        task: 'build',
        exitCode: result.code,
        tail: result.output.slice(-12000),
      });
    },
  ));

  ctx.tools.register(stringTool(
    'mcdev_gametest',
    'Run "gradlew runGameTestServer" in a Minecraft 1.20.1 Forge project directory. Returns JSON {exitCode, summary, tail}. Exit code equals failed required tests.',
    {
      projectDir: {
        type: 'string',
        required: true,
        description: 'Absolute project directory containing gradlew.bat.',
      },
    },
    async (args) => {
      const result = await runGradle(args.projectDir, 'runGameTestServer', 900000);
      const text = result.output;
      const passed = /All (\d+) required tests passed/.exec(text);
      const failed = /(\d+) required tests failed/.exec(text);
      const summary = passed
        ? `All ${passed[1]} required tests passed`
        : failed
          ? `${failed[1]} required tests failed`
          : 'no summary';
      return JSON.stringify({
        task: 'runGameTestServer',
        exitCode: result.code,
        summary,
        tail: text.slice(-12000),
      });
    },
  ));
}
