import { defineTool } from '@deepseek-ai/dsh-tools';
import { spawn, spawnSync } from 'node:child_process';
import {
  copyFileSync,
  createWriteStream,
  existsSync,
  mkdirSync,
  mkdtempSync,
  readFileSync,
  rmSync,
  statSync,
} from 'node:fs';
import { tmpdir } from 'node:os';
import { basename, dirname, join } from 'node:path';

const DEFAULT_PORTS = { server: 39001, client: 39002 };
const TOKEN = 'mcdev';
const processes = new Map();

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

function getPath(value, path) {
  if (!path) {
    return value;
  }
  return String(path).split('.').filter(Boolean).reduce((current, key) => {
    if (current === null || current === undefined) {
      return undefined;
    }
    if (Array.isArray(current) && /^\d+$/.test(key)) {
      return current[Number(key)];
    }
    return current[key];
  }, value);
}

async function probeJson(side, path, init = {}, port) {
  const text = await probe(side, path, init, port);
  try {
    return JSON.parse(text);
  } catch {
    return { raw: text };
  }
}

async function probeStatusOnce(kind, port, timeoutMs = 2000) {
  try {
    const response = await fetch(`http://127.0.0.1:${port}/status`, {
      headers: { 'X-Mcdev-Token': TOKEN },
      signal: AbortSignal.timeout(timeoutMs),
    });
    if (!response.ok) {
      return false;
    }
    const body = await response.json();
    return body?.data?.side === kind;
  } catch {
    return false;
  }
}

async function waitForProbe(kind, port, timeoutMs) {
  const deadline = Date.now() + Math.max(1000, timeoutMs);
  while (Date.now() < deadline) {
    if (await probeStatusOnce(kind, port, 2000)) {
      return true;
    }
    await new Promise((resolve) => setTimeout(resolve, 2000));
  }
  return false;
}

async function launchKind(kind, projectDir, timeoutMs) {
  const port = DEFAULT_PORTS[kind];
  if (await probeStatusOnce(kind, port, 1000)) {
    return { kind, alreadyRunning: true, port };
  }
  const win = process.platform === 'win32';
  const task = kind === 'server' ? 'runServer' : 'runClient';
  const logDir = join(projectDir, '.mcdev');
  mkdirSync(logDir, { recursive: true });
  const logPath = join(logDir, `launch-${kind}-${Date.now()}.log`);
  const logStream = createWriteStream(logPath, { flags: 'a' });
  const child = spawn(win ? 'gradlew.bat' : './gradlew', [task, '--console=plain'], {
    cwd: projectDir,
    shell: win,
    windowsHide: true,
  });
  const entry = {
    kind,
    child,
    pid: child.pid,
    projectDir,
    logPath,
    port,
    startedAt: Date.now(),
    running: true,
    exitCode: null,
    tail: '',
  };
  const capture = (chunk) => {
    entry.tail = (entry.tail + chunk.toString()).slice(-8000);
  };
  child.stdout.on('data', capture);
  child.stderr.on('data', capture);
  child.stdout.pipe(logStream);
  child.stderr.pipe(logStream);
  child.on('error', (error) => {
    entry.running = false;
    entry.error = String(error?.message ?? error);
  });
  child.on('close', (code) => {
    entry.running = false;
    entry.exitCode = code;
    try {
      logStream.end();
    } catch {
      // ignore
    }
  });
  processes.set(kind, entry);

  const ready = await waitForProbe(kind, port, timeoutMs);
  if (!ready) {
    entry.running = false;
    try {
      child.kill();
    } catch {
      // ignore
    }
    processes.delete(kind);
    throw new Error(
      `probe ${kind} did not become ready within ${timeoutMs}ms; log=${logPath}; tail=${entry.tail.slice(-2000)}`,
    );
  }
  return {
    kind,
    alreadyRunning: false,
    pid: child.pid,
    port,
    logPath,
    ready: true,
  };
}

async function stopTracked(kind) {
  const entry = processes.get(kind);
  if (!entry) {
    return { kind, stopped: false, reason: 'not tracked' };
  }
  const warnings = [];
  try {
    if (kind === 'server') {
      await probe('server', '/command', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ cmd: 'stop' }),
      }, entry.port);
    } else {
      await probe('client', '/quit', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: '{}',
      }, entry.port);
    }
  } catch (error) {
    warnings.push(String(error?.message ?? error));
  }
  await new Promise((resolve) => setTimeout(resolve, 2000));
  if (entry.running) {
    try {
      if (process.platform === 'win32' && entry.child.pid) {
        spawnSync('taskkill', ['/PID', String(entry.child.pid), '/T', '/F'], {
          windowsHide: true,
        });
      } else {
        entry.child.kill('SIGKILL');
      }
    } catch (error) {
      warnings.push(String(error?.message ?? error));
    }
  }
  processes.delete(kind);
  return {
    kind,
    stopped: true,
    pid: entry.pid,
    logPath: entry.logPath,
    warnings,
  };
}

export function apply(ctx) {
  if (typeof ctx.effect === 'function') {
    try {
      ctx.effect(() => () => {
        for (const entry of processes.values()) {
          try {
            entry.child.kill();
          } catch {
            // ignore
          }
        }
        processes.clear();
      });
    } catch {
      // cleanup registration is best-effort
    }
  }

  ctx.tools.register(stringTool(
    'mcdev_prepare',
    'Start a Forge dev server/client via "gradlew runServer/runClient", wait until the probe is ready, and return PIDs/ports/log paths. Uses the project run directories configured in build.gradle.',
    {
      projectDir: {
        type: 'string',
        required: true,
        description: 'Absolute Forge project directory containing gradlew.bat.',
      },
      kind: {
        type: 'string',
        enum: ['server', 'client', 'both'],
        required: true,
        description: 'Which process(es) to start. "both" starts server, waits, then client.',
      },
      timeoutMs: {
        type: 'integer',
        description: 'Per-process readiness timeout. Default 180000 ms.',
      },
    },
    async (args) => {
      if (!existsSync(args.projectDir)) {
        throw new Error(`projectDir not found: ${args.projectDir}`);
      }
      if (!['server', 'client', 'both'].includes(args.kind)) {
        throw new Error(`invalid kind: ${args.kind}`);
      }
      const timeoutMs = Math.max(5000, Math.min(Number(args.timeoutMs) || 180000, 600000));
      const results = [];
      if (args.kind === 'server' || args.kind === 'both') {
        results.push(await launchKind('server', args.projectDir, timeoutMs));
      }
      if (args.kind === 'client' || args.kind === 'both') {
        results.push(await launchKind('client', args.projectDir, timeoutMs));
      }
      return JSON.stringify({ ok: true, results });
    },
  ));

  ctx.tools.register(stringTool(
    'mcdev_stop',
    'Stop a server/client launched by mcdev_prepare. Sends probe stop/quit first, then force-kills the process tree if needed.',
    {
      side: {
        type: 'string',
        enum: ['server', 'client', 'all'],
        required: true,
        description: 'Which launched process(es) to stop.',
      },
    },
    async (args) => {
      const kinds = args.side === 'all' ? ['client', 'server'] : [args.side];
      const results = [];
      for (const kind of kinds) {
        results.push(await stopTracked(kind));
      }
      return JSON.stringify({ ok: true, results });
    },
  ));

  ctx.tools.register(stringTool(
    'mcdev_launch_status',
    'List processes launched by mcdev_prepare with pid/port/logPath/running.',
    {},
    () => JSON.stringify({
      processes: [...processes.values()].map(({
        kind, pid, projectDir, logPath, port, startedAt, running, exitCode,
      }) => ({
        kind, pid, projectDir, logPath, port, startedAt, running, exitCode,
      })),
    }),
  ));

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
    'mcdev_scenario',
    'Execute an ordered list of probe/scenario steps and return per-step results. Steps: status, command, use, ui, click, screenshot, run_dump, wait, assert. Stops at the first failure (ok=false). assert uses path/op/value against the latest data step or an explicit step index.',
    {
      steps: {
        type: 'array',
        required: true,
        description: 'Ordered scenario steps.',
        items: {
          type: 'object',
          additionalProperties: true,
          properties: {
            type: {
              type: 'string',
              required: true,
              enum: ['status', 'command', 'use', 'ui', 'click', 'screenshot', 'run_dump', 'wait', 'assert'],
            },
            side: { type: 'string', enum: ['server', 'client'] },
            port: { type: 'integer' },
            cmd: { type: 'string' },
            x: { type: 'integer' },
            y: { type: 'integer' },
            z: { type: 'integer' },
            face: { type: 'string', enum: ['up', 'down', 'north', 'south', 'east', 'west'] },
            slot: { type: 'integer' },
            button: { type: 'integer' },
            shift: { type: 'boolean' },
            ms: { type: 'integer' },
            step: { type: 'integer' },
            path: { type: 'string' },
            op: { type: 'string', enum: ['eq', 'ne', 'contains', 'gt', 'lt', 'exists'] },
            value: { type: 'string' },
            label: { type: 'string' },
            n: { type: 'integer' },
          },
        },
      },
    },
    async (args) => {
      const steps = Array.isArray(args.steps) ? args.steps : [];
      if (steps.length === 0) {
        throw new Error('steps must be a non-empty array');
      }
      const results = [];
      let lastDataIndex = -1;

      for (let i = 0; i < steps.length; i += 1) {
        const step = steps[i] ?? {};
        const type = step.type;
        try {
          if (type === 'wait') {
            const ms = Math.max(0, Math.min(Number(step.ms) || 0, 60000));
            await new Promise((resolve) => setTimeout(resolve, ms));
            results.push({ index: i, type, ok: true, ms });
            continue;
          }

          if (type === 'assert') {
            const stepIndex = typeof step.step === 'number' ? step.step : lastDataIndex;
            const target = results[stepIndex]?.data;
            const actual = getPath(target, step.path);
            const expected = step.value;
            const op = step.op ?? 'eq';
            let ok;
            switch (op) {
              case 'eq':
                ok = String(actual) === String(expected);
                break;
              case 'ne':
                ok = String(actual) !== String(expected);
                break;
              case 'contains':
                ok = String(actual).includes(String(expected));
                break;
              case 'gt':
                ok = Number(actual) > Number(expected);
                break;
              case 'lt':
                ok = Number(actual) < Number(expected);
                break;
              case 'exists':
                ok = actual !== undefined;
                break;
              default:
                throw new Error(`unknown assert op: ${op}`);
            }
            if (!ok) {
              throw new Error(
                `assert failed${step.label ? ` (${step.label})` : ''}: actual=${JSON.stringify(actual)} expected ${op} ${JSON.stringify(expected)}`,
              );
            }
            results.push({ index: i, type, ok: true, actual });
            continue;
          }

          let data;
          switch (type) {
            case 'status':
              data = await probeJson(step.side ?? 'server', '/status', {}, step.port);
              break;
            case 'command':
              if (typeof step.cmd !== 'string' || step.cmd.length === 0) {
                throw new Error('command step requires cmd');
              }
              data = await probeJson(step.side ?? 'server', '/command', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ cmd: step.cmd }),
              }, step.port);
              break;
            case 'use':
              data = await probeJson('client', '/player/use', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                  x: step.x,
                  y: step.y,
                  z: step.z,
                  face: step.face ?? 'up',
                }),
              }, step.port);
              break;
            case 'ui':
              data = await probeJson('client', '/ui/snapshot', {}, step.port);
              break;
            case 'click': {
              const body = { button: step.button ?? 0 };
              if (typeof step.slot === 'number' && step.slot >= 0) {
                body.slot = step.slot;
              } else {
                body.x = step.x ?? 0;
                body.y = step.y ?? 0;
              }
              if (step.shift) {
                body.shift = true;
              }
              data = await probeJson('client', '/ui/click', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(body),
              }, step.port);
              break;
            }
            case 'screenshot':
              data = await probeJson('client', '/screenshot', {}, step.port);
              break;
            case 'run_dump':
              data = await probeJson(step.side ?? 'server', `/run/dump?n=${step.n ?? 200}`, {}, step.port);
              break;
            default:
              throw new Error(`unknown step type: ${String(type)}`);
          }
          results.push({ index: i, type, ok: true, data });
          lastDataIndex = results.length - 1;
        } catch (error) {
          results.push({
            index: i,
            type,
            ok: false,
            error: String(error?.message ?? error),
          });
          return JSON.stringify({ ok: false, failedAt: i, steps: results });
        }
      }
      return JSON.stringify({ ok: true, steps: results });
    },
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
