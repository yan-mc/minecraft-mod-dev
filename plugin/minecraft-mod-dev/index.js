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

async function waitForClientJoin(timeoutMs) {
  const deadline = Date.now() + Math.max(5000, timeoutMs);
  let lastScreen = null;
  while (Date.now() < deadline) {
    try {
      const response = await fetch(`http://127.0.0.1:${DEFAULT_PORTS.client}/status`, {
        headers: { 'X-Mcdev-Token': TOKEN },
        signal: AbortSignal.timeout(2000),
      });
      if (response.ok) {
        const body = await response.json();
        const data = body?.data;
        const screen = data?.screen ?? '';
        lastScreen = screen;
        if (screen.endsWith('DisconnectedScreen')) {
          return { joined: false, lastScreen, disconnected: true };
        }
        if (data?.player && !/ConnectScreen|DisconnectedScreen|ReceivingLevelScreen/.test(screen)) {
          return { joined: true, lastScreen };
        }
      }
    } catch {
      // keep waiting
    }
    await new Promise((resolve) => setTimeout(resolve, 2000));
  }
  return { joined: false, lastScreen };
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

function unquote(value) {
  const s = String(value).trim();
  if ((s.startsWith('"') && s.endsWith('"')) || (s.startsWith("'") && s.endsWith("'"))) {
    return s.slice(1, -1);
  }
  return s;
}

function splitTopLevel(text, separator) {
  const parts = [];
  let depth = 0;
  let quote = null;
  let start = 0;
  for (let i = 0; i < text.length; i += 1) {
    const c = text[i];
    if (quote) {
      if (c === quote && text[i - 1] !== '\\') {
        quote = null;
      }
      continue;
    }
    if (c === '"' || c === "'") {
      quote = c;
      continue;
    }
    if (c === '{' || c === '[') {
      depth += 1;
    } else if (c === '}' || c === ']') {
      depth -= 1;
    } else if (c === separator && depth === 0) {
      parts.push(text.slice(start, i));
      start = i + 1;
    }
  }
  parts.push(text.slice(start));
  return parts;
}

function splitKeyValue(text) {
  let depth = 0;
  let quote = null;
  for (let i = 0; i < text.length; i += 1) {
    const c = text[i];
    if (quote) {
      if (c === quote && text[i - 1] !== '\\') {
        quote = null;
      }
      continue;
    }
    if (c === '"' || c === "'") {
      quote = c;
      continue;
    }
    if (c === '{' || c === '[') {
      depth += 1;
    } else if (c === '}' || c === ']') {
      depth -= 1;
    } else if (c === ':' && depth === 0) {
      return [text.slice(0, i), text.slice(i + 1)];
    }
  }
  return null;
}

function parseInlineValue(text) {
  const s = String(text).trim();
  if (s.startsWith('{') && s.endsWith('}')) {
    const object = {};
    const inner = s.slice(1, -1).trim();
    if (!inner) {
      return object;
    }
    for (const part of splitTopLevel(inner, ',')) {
      const kv = splitKeyValue(part);
      if (!kv) {
        continue;
      }
      object[unquote(kv[0])] = parseInlineValue(kv[1]);
    }
    return object;
  }
  if (s.startsWith('[') && s.endsWith(']')) {
    const inner = s.slice(1, -1).trim();
    if (!inner) {
      return [];
    }
    return splitTopLevel(inner, ',').map((item) => parseInlineValue(item));
  }
  if (s.startsWith('"') && s.endsWith('"')) {
    return JSON.parse(s);
  }
  if (s.startsWith("'") && s.endsWith("'")) {
    return s.slice(1, -1).replace(/''/g, "'");
  }
  if (s === 'true') {
    return true;
  }
  if (s === 'false') {
    return false;
  }
  if (s === 'null' || s === '~') {
    return null;
  }
  if (/^-?\d+$/.test(s)) {
    return Number(s);
  }
  if (/^-?\d+\.\d+$/.test(s)) {
    return Number(s);
  }
  return s;
}

function parseYaml(text) {
  const lines = String(text)
    .split(/\r?\n/)
    .filter((line) => line.trim() !== '' && !line.trim().startsWith('#'))
    .map((line) => ({
      indent: (line.match(/^ */) || [''])[0].length,
      content: line.trim(),
    }));
  let index = 0;

  function parseBlock(indent) {
    if (index >= lines.length) {
      return null;
    }
    const line = lines[index];
    if (line.indent < indent) {
      return null;
    }
    if (line.content.startsWith('-')) {
      return parseSequence(line.indent);
    }
    return parseMapping(line.indent);
  }

  function parseSequence(indent) {
    const result = [];
    while (index < lines.length) {
      const line = lines[index];
      if (line.indent !== indent || !line.content.startsWith('-')) {
        break;
      }
      const rest = line.content.slice(1).trim();
      index += 1;
      if (rest === '') {
        const next = lines[index];
        if (next && next.indent > indent) {
          result.push(parseBlock(next.indent));
        } else {
          result.push(null);
        }
      } else if (rest.startsWith('{') || rest.startsWith('[')) {
        result.push(parseInlineValue(rest));
      } else if (/^[A-Za-z0-9_.-]+\s*:/.test(rest)) {
        const kv = splitKeyValue(rest);
        const object = {};
        object[unquote(kv[0])] = parseInlineValue(kv[1]);
        result.push(object);
      } else {
        result.push(parseInlineValue(rest));
      }
    }
    return result;
  }

  function parseMapping(indent) {
    const result = {};
    while (index < lines.length) {
      const line = lines[index];
      if (line.indent !== indent || line.content.startsWith('-')) {
        break;
      }
      const kv = splitKeyValue(line.content);
      if (!kv) {
        break;
      }
      const key = unquote(kv[0]);
      const rest = kv[1].trim();
      index += 1;
      if (rest === '') {
        const next = lines[index];
        if (next && next.indent > indent) {
          result[key] = parseBlock(next.indent);
        } else {
          result[key] = null;
        }
      } else {
        result[key] = parseInlineValue(rest);
      }
    }
    return result;
  }

  if (lines.length === 0) {
    return {};
  }
  return parseBlock(lines[0].indent);
}

function mapCardOp(op) {
  const value = String(op ?? 'eq').trim();
  const mapping = {
    '==': 'eq',
    '=': 'eq',
    eq: 'eq',
    '!=': 'ne',
    ne: 'ne',
    '>': 'gt',
    gt: 'gt',
    '>=': 'gte',
    gte: 'gte',
    '<': 'lt',
    lt: 'lt',
    '<=': 'lte',
    lte: 'lte',
    contains: 'contains',
    exists: 'exists',
  };
  return mapping[value] ?? value;
}

function cardToSteps(card) {
  const steps = [];
  const warnings = [];
  const commands = card?.setup?.commands;
  if (Array.isArray(commands)) {
    for (const cmd of commands) {
      if (typeof cmd === 'string' && cmd.trim() !== '') {
        steps.push({ type: 'command', side: 'server', cmd });
      }
    }
  }

  const actions = Array.isArray(card?.actions) ? card.actions : [];
  for (const action of actions) {
    if (typeof action === 'string') {
      steps.push({ type: 'command', side: 'server', cmd: action });
      continue;
    }
    if (!action || typeof action !== 'object') {
      continue;
    }
    if (action.give) {
      const give = action.give;
      const player = give.player ?? 'Dev';
      const item = give.item;
      const count = give.count ?? 1;
      if (typeof item === 'string') {
        steps.push({ type: 'command', side: 'server', cmd: `give ${player} ${item} ${count}` });
        if (give.select) {
          const tagged = give.nbt ? `${item}{${give.nbt}}` : item;
          steps.push({
            type: 'command',
            side: 'server',
            cmd: `item replace entity ${player} weapon.mainhand with ${tagged} ${count}`,
          });
        }
      }
    } else if (action.select_item) {
      const select = action.select_item;
      const player = select.player ?? 'Dev';
      const count = select.count ?? 1;
      if (typeof select.item === 'string') {
        const tagged = select.nbt ? `${select.item}{${select.nbt}}` : select.item;
        steps.push({
          type: 'command',
          side: 'server',
          cmd: `item replace entity ${player} weapon.mainhand with ${tagged} ${count}`,
        });
      }
    } else if (action.use_on || action.use_item_on) {
      const use = action.use_on ?? action.use_item_on;
      steps.push({
        type: 'use',
        x: use.x,
        y: use.y,
        z: use.z,
        face: use.face ?? 'up',
      });
    } else if (typeof action.wait === 'number') {
      steps.push({ type: 'wait', ms: action.wait });
    } else if (typeof action.command === 'string') {
      steps.push({ type: 'command', side: 'server', cmd: action.command });
    } else {
      warnings.push(`unsupported action: ${JSON.stringify(action)}`);
    }
  }

  const asserts = Array.isArray(card?.assert) ? card.assert : [];
  for (const check of asserts) {
    if (!check || typeof check !== 'object') {
      continue;
    }
    if (check.screen_title !== undefined) {
      steps.push({ type: 'ui' });
      steps.push({ type: 'assert', path: 'data.title', op: 'eq', value: String(check.screen_title) });
    } else if (check.slot_exists !== undefined) {
      const slot = Number(check.slot_exists);
      steps.push({ type: 'ui' });
      steps.push({
        type: 'assert',
        path: `data.slots.${slot}.index`,
        op: 'eq',
        value: String(slot),
      });
    } else if (check.block_entity_nbt) {
      const nbt = check.block_entity_nbt;
      if (Array.isArray(nbt.pos) && nbt.pos.length === 3) {
        const [x, y, z] = nbt.pos;
        const path = nbt.path ?? 'Energy';
        steps.push({ type: 'command', side: 'server', cmd: `data get block ${x} ${y} ${z} ${path}` });
        steps.push({
          type: 'assert',
          path: 'data.result',
          op: mapCardOp(nbt.op),
          value: String(nbt.value),
        });
      } else {
        warnings.push('block_entity_nbt requires pos=[x,y,z]');
      }
    } else if (check.comparator) {
      const comparator = check.comparator;
      if (Array.isArray(comparator.pos) && comparator.pos.length === 3) {
        const [x, y, z] = comparator.pos;
        steps.push({ type: 'block', x, y, z });
        steps.push({
          type: 'assert',
          path: 'data.analog',
          op: mapCardOp(comparator.op),
          value: String(comparator.value),
        });
      } else {
        warnings.push('comparator requires pos=[x,y,z]');
      }
    } else if (check.recipe_output) {
      const recipe = check.recipe_output;
      const id = recipe.recipe ?? recipe.id;
      if (typeof id === 'string') {
        steps.push({ type: 'recipe', id });
        if (recipe.item !== undefined) {
          steps.push({ type: 'assert', path: 'data.item', op: 'eq', value: String(recipe.item) });
        }
        if (recipe.count !== undefined) {
          steps.push({ type: 'assert', path: 'data.count', op: 'eq', value: String(recipe.count) });
        }
      } else {
        warnings.push('recipe_output requires recipe id');
      }
    } else if (check.loot_drop) {
      const loot = check.loot_drop;
      if (typeof loot.block === 'string') {
        steps.push({ type: 'loot', block: loot.block });
        if (loot.item !== undefined) {
          steps.push({ type: 'assert', path: 'data.drops.0.item', op: 'eq', value: String(loot.item) });
        }
        if (loot.count !== undefined) {
          steps.push({ type: 'assert', path: 'data.total', op: 'eq', value: String(loot.count) });
        }
      } else {
        warnings.push('loot_drop requires block');
      }
    } else if (check.loot_break) {
      const loot = check.loot_break;
      const x = loot.x ?? 0;
      const y = loot.y ?? 64;
      const z = loot.z ?? 0;
      if (typeof loot.block === 'string') {
        steps.push({ type: 'command', side: 'server', cmd: `setblock ${x} ${y} ${z} ${loot.block}` });
      }
      steps.push({ type: 'break', x, y, z });
      if (loot.item !== undefined) {
        steps.push({ type: 'assert', path: 'data.drops.0.item', op: 'eq', value: String(loot.item) });
      }
      if (loot.count !== undefined) {
        steps.push({ type: 'assert', path: 'data.total', op: 'eq', value: String(loot.count) });
      }
    } else if (check.item_bar) {
      const bar = check.item_bar;
      if (typeof bar.item === 'string') {
        steps.push({ type: 'item', item: bar.item, nbt: bar.nbt });
        if (bar.visible !== undefined) {
          steps.push({ type: 'assert', path: 'data.visible', op: 'eq', value: String(bar.visible) });
        }
        if (bar.width !== undefined) {
          steps.push({ type: 'assert', path: 'data.width', op: 'eq', value: String(bar.width) });
        }
        if (bar.color !== undefined) {
          steps.push({ type: 'assert', path: 'data.color', op: 'eq', value: String(bar.color) });
        }
      } else {
        warnings.push('item_bar requires item');
      }
    } else if (check.no_exception !== undefined) {
      // no-op: the executor already fails on probe errors
    } else {
      const key = Object.keys(check)[0] ?? 'unknown';
      warnings.push(`unsupported assert: ${key}`);
    }
  }
  return { steps, warnings };
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
      waitJoin: {
        type: 'boolean',
        description: 'For client/both, wait until the player is in-game and retry once on ConnectScreen/Disconnected. Default true.',
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
        if (args.kind === 'both') {
          // Give the server a moment to finish post-start work before the client handshake.
          await new Promise((resolve) => setTimeout(resolve, 2000));
        }
        const client = await launchKind('client', args.projectDir, timeoutMs);
        if (args.waitJoin === false) {
          results.push(client);
        } else {
          const attempts = [];
          let current = client;
          let joined = false;
          for (let attempt = 0; attempt < 3; attempt += 1) {
            if (attempt > 0) {
              await new Promise((resolve) => setTimeout(resolve, 2000));
              current = await launchKind('client', args.projectDir, timeoutMs);
            }
            const wait = await waitForClientJoin(Math.min(timeoutMs, 90000));
            attempts.push({
              attempt: attempt + 1,
              pid: current.pid,
              logPath: current.logPath,
              joined: wait.joined,
              lastScreen: wait.lastScreen,
            });
            if (wait.joined) {
              current.joined = true;
              current.lastScreen = wait.lastScreen;
              current.attempts = attempts;
              if (attempt > 0) {
                current.retry = true;
              }
              results.push(current);
              joined = true;
              break;
            }
            await stopTracked('client');
          }
          if (!joined) {
            results.push({
              kind: 'client',
              joined: false,
              retry: attempts.length > 1,
              lastScreen: attempts[attempts.length - 1]?.lastScreen ?? null,
              attempts,
            });
          }
        }
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
    'Execute an ordered list of probe/scenario steps, or translate a card YAML file into steps. Steps: status, command, block, use, ui, click, screenshot, run_dump, wait, assert. Stops at the first failure (ok=false). assert uses path/op/value against the latest data step or an explicit step index.',
    {
      steps: {
        type: 'array',
        description: 'Ordered scenario steps. Omit when using card.',
        items: {
          type: 'object',
          additionalProperties: true,
          properties: {
            type: {
              type: 'string',
              required: true,
              enum: ['status', 'command', 'block', 'break', 'recipe', 'loot', 'item', 'use', 'ui', 'click', 'screenshot', 'run_dump', 'wait', 'assert'],
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
            op: { type: 'string', enum: ['eq', 'ne', 'contains', 'gt', 'gte', 'lt', 'lte', 'exists'] },
            value: { type: 'string' },
            label: { type: 'string' },
            n: { type: 'integer' },
            id: { type: 'string' },
            block: { type: 'string' },
            item: { type: 'string' },
            nbt: { type: 'string' },
          },
        },
      },
      card: {
        type: 'string',
        description: 'Absolute path to a card YAML (F-001..F-006 style). Translates setup/actions/assert into steps.',
      },
      dryRun: {
        type: 'boolean',
        description: 'Translate/report steps without executing them.',
      },
    },
    async (args) => {
      let steps = Array.isArray(args.steps) ? args.steps : [];
      let warnings = [];
      if (typeof args.card === 'string' && args.card.trim() !== '') {
        if (!existsSync(args.card)) {
          throw new Error(`card not found: ${args.card}`);
        }
        const card = parseYaml(readFileSync(args.card, 'utf8'));
        const translated = cardToSteps(card);
        steps = translated.steps;
        warnings = translated.warnings;
      }
      if (steps.length === 0) {
        throw new Error('provide steps or a card with supported steps');
      }
      if (args.dryRun) {
        return JSON.stringify({
          ok: true,
          dryRun: true,
          card: args.card ?? null,
          warnings,
          steps,
        });
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
              case 'gte':
                ok = Number(actual) >= Number(expected);
                break;
              case 'lt':
                ok = Number(actual) < Number(expected);
                break;
              case 'lte':
                ok = Number(actual) <= Number(expected);
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
            case 'block':
              data = await probeJson('server', `/world/block?x=${step.x ?? 0}&y=${step.y ?? 64}&z=${step.z ?? 0}`, {}, step.port);
              break;
            case 'break':
              data = await probeJson('server', `/world/break?x=${step.x ?? 0}&y=${step.y ?? 64}&z=${step.z ?? 0}`, {}, step.port);
              break;
            case 'recipe':
              data = await probeJson('server', `/recipe?id=${encodeURIComponent(step.id ?? '')}`, {}, step.port);
              break;
            case 'loot':
              data = await probeJson('server', `/loot?block=${encodeURIComponent(step.block ?? '')}&x=${step.x ?? 0}&y=${step.y ?? 64}&z=${step.z ?? 0}`, {}, step.port);
              break;
            case 'item':
              data = await probeJson('server', `/item?item=${encodeURIComponent(step.item ?? '')}&nbt=${encodeURIComponent(step.nbt ?? '')}`, {}, step.port);
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
          return JSON.stringify({ ok: false, failedAt: i, warnings, steps: results });
        }
      }
      return JSON.stringify({ ok: true, warnings, steps: results });
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

