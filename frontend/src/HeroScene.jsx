import { useEffect, useRef, useState } from 'react';

/*
 * The landing-page hero: a camera lens rendered with plain WebGL (no 3D library).
 * On load the seven-blade aperture opens and a set of prints fans out of the lens into a slow orbit.
 * The lens turns toward the pointer. Motion stops for prefers-reduced-motion, off-screen and hidden tabs.
 */

const TAU = Math.PI * 2;

// ---------------------------------------------------------------- shaders

const VERTEX = `
attribute vec3 aPos;
attribute vec3 aNormal;
attribute vec2 aUv;
uniform mat4 uProj;
uniform mat4 uView;
uniform mat4 uModel;
uniform mat3 uNormalMat;
varying vec3 vN;
varying vec3 vW;
varying vec2 vUv;
void main() {
  vec4 world = uModel * vec4(aPos, 1.0);
  vW = world.xyz;
  vN = uNormalMat * aNormal;
  vUv = aUv;
  gl_Position = uProj * uView * world;
}`;

const METAL = `
precision highp float;
varying vec3 vN;
varying vec3 vW;
varying vec2 vUv;
uniform vec3 uColor;
uniform float uMetal;
uniform float uGloss;
uniform float uRidges;
uniform vec3 uCam;
uniform vec3 uKey;
uniform vec3 uEnvTop;
uniform vec3 uEnvLow;
uniform sampler2D uTex;
uniform float uUseTex;
void main() {
  vec3 n = normalize(vN);
  vec3 v = normalize(uCam - vW);
  vec3 l = normalize(uKey);
  vec3 h = normalize(l + v);
  float ridge = 0.0;
  if (uRidges > 0.0) {
    ridge = sin(vUv.x * uRidges * 6.2831853);
    n = normalize(n + vec3(0.0, 0.0, 0.0) + 0.18 * ridge * normalize(cross(n, vec3(0.0, 0.0, 1.0)) + 1e-4));
  }
  float diffuse = max(dot(n, l), 0.0);
  float spec = pow(max(dot(n, h), 0.0), mix(14.0, 120.0, uGloss));
  vec3 r = reflect(-v, n);
  float sky = smoothstep(-0.35, 0.95, r.y);
  float softbox = smoothstep(0.80, 0.97, r.y) * smoothstep(-0.9, 0.2, r.x);
  float strip = smoothstep(0.86, 0.99, r.x) * 0.7;
  vec3 env = mix(uEnvLow, uEnvTop, sky) + (softbox + strip) * vec3(1.0, 0.96, 0.9);
  float fresnel = pow(1.0 - max(dot(n, v), 0.0), 4.0);
  vec3 dielectric = uColor * (0.16 + 0.62 * diffuse) + env * 0.06 + spec * 0.35;
  vec3 metallic = uColor * env * 1.05 + spec * uColor * 1.6;
  vec3 color = mix(dielectric, metallic, uMetal) + fresnel * mix(uEnvTop, uColor, uMetal) * 0.22;
  if (uRidges > 0.0) color *= 0.78 + 0.22 * smoothstep(-0.2, 0.2, ridge);
  if (uUseTex > 0.5) {
    float ink = texture2D(uTex, vUv).a;
    color = mix(color, vec3(0.86, 0.84, 0.78), ink * 0.92);
  }
  gl_FragColor = vec4(color, 1.0);
}`;

const IRIS = `
precision highp float;
varying vec3 vN;
varying vec3 vW;
varying vec2 vUv;
uniform float uOpen;
uniform float uTime;
uniform vec3 uBrass;
uniform float uAa;
const float N = 7.0;
float aperture(vec2 p, float radius, float turn) {
  float a = atan(p.y, p.x) + turn;
  float seg = 6.2831853 / N;
  return cos(floor(0.5 + a / seg) * seg - a) * length(p) - radius;
}
void main() {
  vec2 p = vUv;
  float r = length(p);
  if (r > 1.0) discard;
  float turn = uOpen * 1.9;
  float hole = aperture(p, uOpen, turn);

  // What the lens sees: a warm dusk with drifting bokeh.
  vec3 scene = mix(vec3(0.98, 0.66, 0.32), vec3(0.20, 0.11, 0.24), smoothstep(-0.7, 0.85, p.y));
  for (int i = 0; i < 7; i++) {
    float fi = float(i);
    vec2 c = vec2(sin(fi * 2.39 + uTime * 0.13) * 0.48, cos(fi * 1.71 + uTime * 0.09) * 0.42);
    float size = 0.10 + 0.07 * fract(fi * 0.37);
    scene += smoothstep(size, size - 0.03, length(p - c)) * vec3(1.0, 0.86, 0.6) * 0.22;
  }

  // Blades: spiral boundaries, each blade a slightly different matte steel.
  float a = atan(p.y, p.x);
  float seg = 6.2831853 / N;
  float k = (a - turn + r * 2.6) / seg;
  float f = fract(k);
  float idx = floor(k);
  float steel = 0.085 + 0.03 * mod(idx, 2.0) + 0.05 * f;
  vec3 blade = vec3(steel) * mix(0.55, 1.0, smoothstep(0.0, 0.05, f));
  blade += uBrass * 0.06 * smoothstep(0.35, 0.0, abs(p.x * 0.6 + p.y - 0.35));

  float open = smoothstep(-uAa, uAa, hole);
  vec3 color = mix(scene, blade, open);
  color *= 1.0 - 0.35 * smoothstep(0.07, 0.0, abs(hole)) * open;

  // Multi-coated front element: violet and green reflections plus a window highlight.
  float violet = smoothstep(0.035, 0.0, abs(length(p - vec2(-0.18, 0.26)) - 0.62)) * smoothstep(0.35, -0.6, p.x + p.y * 0.3);
  float green = smoothstep(0.03, 0.0, abs(length(p - vec2(0.22, -0.2)) - 0.38)) * smoothstep(-0.1, 0.4, p.x);
  color += violet * vec3(0.42, 0.22, 0.78) * 0.45 + green * vec3(0.22, 0.62, 0.38) * 0.22;
  vec2 w = p - vec2(-0.38, 0.40);
  color += smoothstep(0.20, 0.0, length(w * vec2(1.0, 1.7))) * 0.22;
  color *= mix(0.62, 1.0, smoothstep(1.0, 0.86, r));
  gl_FragColor = vec4(color, 1.0);
}`;

const PRINT = `
precision highp float;
varying vec3 vN;
varying vec3 vW;
varying vec2 vUv;
uniform sampler2D uTex;
uniform vec3 uKey;
uniform vec3 uCam;
uniform float uShade;
void main() {
  vec3 n = normalize(vN);
  if (!gl_FrontFacing) n = -n;
  vec3 v = normalize(uCam - vW);
  float light = 0.80 + 0.22 * max(dot(n, normalize(uKey)), 0.0);
  float sheen = pow(max(dot(reflect(-v, n), normalize(uKey)), 0.0), 40.0) * 0.12;
  vec3 color = texture2D(uTex, vUv).rgb * light * uShade + sheen;
  gl_FragColor = vec4(color, 1.0);
}`;

// ---------------------------------------------------------------- math (column-major)

function identity() {
  const m = new Float32Array(16);
  m[0] = m[5] = m[10] = m[15] = 1;
  return m;
}

function multiply(a, b) {
  const out = new Float32Array(16);
  for (let col = 0; col < 4; col += 1) {
    for (let row = 0; row < 4; row += 1) {
      let sum = 0;
      for (let k = 0; k < 4; k += 1) sum += a[k * 4 + row] * b[col * 4 + k];
      out[col * 4 + row] = sum;
    }
  }
  return out;
}

function perspective(fovy, aspect, near, far) {
  const f = 1 / Math.tan(fovy / 2);
  const m = new Float32Array(16);
  m[0] = f / aspect;
  m[5] = f;
  m[10] = (far + near) / (near - far);
  m[11] = -1;
  m[14] = (2 * far * near) / (near - far);
  return m;
}

function translation(x, y, z) {
  const m = identity();
  m[12] = x; m[13] = y; m[14] = z;
  return m;
}

function rotationX(angle) {
  const m = identity(); const c = Math.cos(angle); const s = Math.sin(angle);
  m[5] = c; m[6] = s; m[9] = -s; m[10] = c;
  return m;
}

function rotationY(angle) {
  const m = identity(); const c = Math.cos(angle); const s = Math.sin(angle);
  m[0] = c; m[2] = -s; m[8] = s; m[10] = c;
  return m;
}

function rotationZ(angle) {
  const m = identity(); const c = Math.cos(angle); const s = Math.sin(angle);
  m[0] = c; m[1] = s; m[4] = -s; m[5] = c;
  return m;
}

function scaling(s) {
  const m = identity();
  m[0] = m[5] = m[10] = s;
  return m;
}

function compose(...matrices) {
  return matrices.reduce((acc, m) => multiply(acc, m), identity());
}

function normalMatrix(m, scale = 1) {
  const inv = scale ? 1 / scale : 1;
  return new Float32Array([m[0] * inv, m[1] * inv, m[2] * inv, m[4] * inv, m[5] * inv, m[6] * inv, m[8] * inv, m[9] * inv, m[10] * inv]);
}

function transformPoint(m, x, y, z) {
  return [m[0] * x + m[4] * y + m[8] * z + m[12], m[1] * x + m[5] * y + m[9] * z + m[13], m[2] * x + m[6] * y + m[10] * z + m[14]];
}

// ---------------------------------------------------------------- geometry

function tube({ outer, inner, z0, z1, segments = 128, front = true, back = false, innerWall = false, outerWall = true }) {
  const pos = []; const nor = []; const uv = []; const idx = [];
  const ring = (radius, z, nx, ny, nz, radial, v) => {
    const start = pos.length / 3;
    for (let i = 0; i <= segments; i += 1) {
      const t = i / segments; const a = t * TAU;
      const c = Math.cos(a); const s = Math.sin(a);
      pos.push(radius * c, radius * s, z);
      nor.push(radial ? c * nx : nx, radial ? s * nx : ny, nz);
      uv.push(t, v);
    }
    return start;
  };
  const strip = (a, b) => {
    for (let i = 0; i < segments; i += 1) idx.push(a + i, b + i, a + i + 1, a + i + 1, b + i, b + i + 1);
  };
  if (outerWall) strip(ring(outer, z0, 1, 0, 0, true, 0), ring(outer, z1, 1, 0, 0, true, 1));
  if (front) strip(ring(inner, z1, 0, 0, 1, false, 0), ring(outer, z1, 0, 0, 1, false, 1));
  if (back) strip(ring(outer, z0, 0, 0, -1, false, 1), ring(inner, z0, 0, 0, -1, false, 0));
  if (innerWall) strip(ring(inner, z1, -1, 0, 0, true, 1), ring(inner, z0, -1, 0, 0, true, 0));
  return { pos, nor, uv, idx };
}

function quad(width, height, uvScale = 1) {
  const x = width / 2; const y = height / 2;
  return {
    pos: [-x, -y, 0, x, -y, 0, x, y, 0, -x, y, 0],
    nor: [0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1],
    uv: uvScale === 1 ? [0, 1, 1, 1, 1, 0, 0, 0] : [-1, -1, 1, -1, 1, 1, -1, 1],
    idx: [0, 1, 2, 0, 2, 3],
  };
}

// ---------------------------------------------------------------- textures

const PRINT_SCENES = [
  { sky: ['#f6b26b', '#c2566b', '#3b2348'], land: '#24172c', sun: '#ffe0a3', sunY: 0.58, figures: 2 },
  { sky: ['#a9d4e0', '#6f9fb4', '#2c4a5c'], land: '#1d3340', sun: '#f4f1e4', sunY: 0.3, figures: 0 },
  { sky: ['#f9e3a7', '#e7a14a', '#8a4b2a'], land: '#4c2c1b', sun: '#fff6d8', sunY: 0.5, figures: 1 },
  { sky: ['#2b2f52', '#5a3e6e', '#e08c5b'], land: '#191526', sun: '#ffd28a', sunY: 0.72, figures: 2, bokeh: true },
  { sky: ['#dfe8d8', '#9db88e', '#46613f'], land: '#2c3d27', sun: '#fbf8ea', sunY: 0.36, figures: 1 },
  { sky: ['#f3c6b8', '#d77d73', '#5c2c3a'], land: '#341a24', sun: '#ffe9d6', sunY: 0.62, figures: 2 },
  { sky: ['#122033', '#264463', '#c7a35c'], land: '#0c1522', sun: '#f7dd9b', sunY: 0.66, figures: 0, bokeh: true },
];

function seeded(seed) {
  let s = seed;
  return () => { s = (s * 16807) % 2147483647; return (s - 1) / 2147483646; };
}

function printTexture(scene, index, landscape) {
  const canvas = document.createElement('canvas');
  canvas.width = landscape ? 480 : 384;
  canvas.height = landscape ? 384 : 480;
  const g = canvas.getContext('2d');
  const W = canvas.width; const H = canvas.height; const border = 16;
  const random = seeded(index * 7919 + 13);
  g.fillStyle = '#f7f4ee';
  g.fillRect(0, 0, W, H);
  const iw = W - border * 2; const ih = H - border * 2 - (landscape ? 0 : 34);
  g.save();
  g.translate(border, border);
  g.beginPath(); g.rect(0, 0, iw, ih); g.clip();
  const sky = g.createLinearGradient(0, 0, 0, ih);
  sky.addColorStop(0, scene.sky[2]); sky.addColorStop(0.55, scene.sky[1]); sky.addColorStop(1, scene.sky[0]);
  g.fillStyle = sky; g.fillRect(0, 0, iw, ih);
  const sunX = iw * (0.3 + random() * 0.4); const sunY = ih * scene.sunY;
  const glow = g.createRadialGradient(sunX, sunY, 0, sunX, sunY, iw * 0.5);
  glow.addColorStop(0, scene.sun); glow.addColorStop(0.12, scene.sun); glow.addColorStop(0.13, 'rgba(255,240,210,.45)'); glow.addColorStop(1, 'rgba(255,240,210,0)');
  g.fillStyle = glow; g.fillRect(0, 0, iw, ih);
  if (scene.bokeh) {
    for (let i = 0; i < 14; i += 1) {
      g.fillStyle = `rgba(255, ${200 + random() * 55 | 0}, ${140 + random() * 80 | 0}, ${0.12 + random() * 0.2})`;
      g.beginPath(); g.arc(random() * iw, random() * ih * 0.7, 8 + random() * 22, 0, TAU); g.fill();
    }
  }
  const horizon = ih * (0.68 + random() * 0.08);
  g.fillStyle = scene.land;
  g.beginPath(); g.moveTo(0, ih);
  for (let x = 0; x <= iw; x += 8) g.lineTo(x, horizon - Math.sin(x / iw * 3.2 + index) * ih * 0.05 - Math.sin(x / iw * 9 + index * 2) * ih * 0.015);
  g.lineTo(iw, ih); g.closePath(); g.fill();
  for (let f = 0; f < scene.figures; f += 1) {
    const fx = iw * (0.42 + f * 0.11 + random() * 0.04); const fh = ih * (0.2 - f * 0.015); const fy = horizon + ih * 0.02;
    g.fillStyle = scene.land;
    g.beginPath(); g.arc(fx, fy - fh, fh * 0.11, 0, TAU); g.fill();
    g.beginPath(); g.moveTo(fx - fh * 0.15, fy); g.quadraticCurveTo(fx - fh * 0.16, fy - fh * 0.75, fx, fy - fh * 0.86); g.quadraticCurveTo(fx + fh * 0.16, fy - fh * 0.75, fx + fh * (f === 1 ? 0.26 : 0.15), fy); g.closePath(); g.fill();
  }
  const image = g.getImageData(0, 0, iw, ih);
  for (let i = 0; i < image.data.length; i += 4) {
    const n = (random() - 0.5) * 18;
    image.data[i] += n; image.data[i + 1] += n; image.data[i + 2] += n;
  }
  g.putImageData(image, 0, 0);
  g.restore();
  return canvas;
}

function engravingTexture() {
  const canvas = document.createElement('canvas');
  canvas.width = 2048; canvas.height = 64;
  const g = canvas.getContext('2d');
  g.clearRect(0, 0, canvas.width, canvas.height);
  g.fillStyle = '#fff';
  g.font = '600 30px Inter, "Helvetica Neue", Arial, sans-serif';
  g.textBaseline = 'middle';
  const marks = ['FrameBucket', '35 mm', '1 : 1.4', 'Ø 58', 'Made for the moments that matter'];
  let x = 40;
  marks.forEach((mark) => { g.fillText(mark, x, 34); x += g.measureText(mark).width + 90; });
  return canvas;
}

// ---------------------------------------------------------------- GL plumbing

function compile(gl, vertexSource, fragmentSource) {
  const program = gl.createProgram();
  [[gl.VERTEX_SHADER, vertexSource], [gl.FRAGMENT_SHADER, fragmentSource]].forEach(([type, source]) => {
    const shader = gl.createShader(type);
    gl.shaderSource(shader, source);
    gl.compileShader(shader);
    if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) throw new Error(gl.getShaderInfoLog(shader) || 'Shader error');
    gl.attachShader(program, shader);
  });
  gl.linkProgram(program);
  if (!gl.getProgramParameter(program, gl.LINK_STATUS)) throw new Error(gl.getProgramInfoLog(program) || 'Link error');
  const uniforms = {};
  const count = gl.getProgramParameter(program, gl.ACTIVE_UNIFORMS);
  for (let i = 0; i < count; i += 1) {
    const info = gl.getActiveUniform(program, i);
    uniforms[info.name] = gl.getUniformLocation(program, info.name);
  }
  return {
    program, uniforms,
    attributes: { aPos: gl.getAttribLocation(program, 'aPos'), aNormal: gl.getAttribLocation(program, 'aNormal'), aUv: gl.getAttribLocation(program, 'aUv') },
  };
}

function upload(gl, geometry) {
  const buffer = (data, target = gl.ARRAY_BUFFER, Type = Float32Array) => {
    const b = gl.createBuffer(); gl.bindBuffer(target, b); gl.bufferData(target, new Type(data), gl.STATIC_DRAW); return b;
  };
  return {
    pos: buffer(geometry.pos), nor: buffer(geometry.nor), uv: buffer(geometry.uv),
    idx: buffer(geometry.idx, gl.ELEMENT_ARRAY_BUFFER, Uint16Array), count: geometry.idx.length,
  };
}

function texture(gl, source, { mipmap = true } = {}) {
  const t = gl.createTexture();
  gl.bindTexture(gl.TEXTURE_2D, t);
  gl.pixelStorei(gl.UNPACK_PREMULTIPLY_ALPHA_WEBGL, false);
  gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, source);
  const pow2 = (n) => (n & (n - 1)) === 0;
  if (mipmap && pow2(source.width) && pow2(source.height)) {
    gl.generateMipmap(gl.TEXTURE_2D);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR_MIPMAP_LINEAR);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.REPEAT);
  } else {
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
  }
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
  return t;
}

const easeOut = (t) => 1 - (1 - Math.min(1, Math.max(0, t))) ** 3;
const easeInOut = (t) => { const x = Math.min(1, Math.max(0, t)); return x < 0.5 ? 4 * x * x * x : 1 - (-2 * x + 2) ** 3 / 2; };

const PALETTES = {
  dark: { envTop: [0.62, 0.56, 0.46], envLow: [0.03, 0.025, 0.02], brass: [0.84, 0.66, 0.33], body: [0.075, 0.07, 0.065], printShade: 0.96 },
  light: { envTop: [0.98, 0.98, 0.96], envLow: [0.30, 0.30, 0.29], brass: [0.66, 0.50, 0.20], body: [0.09, 0.085, 0.08], printShade: 1.02 },
};

// ---------------------------------------------------------------- component

export default function HeroScene({ theme = 'dark' }) {
  const canvasRef = useRef(null);
  const themeRef = useRef(theme);
  const [unsupported, setUnsupported] = useState(false);
  themeRef.current = theme;

  useEffect(() => {
    const canvas = canvasRef.current;
    const gl = canvas?.getContext('webgl', { antialias: true, alpha: true, premultipliedAlpha: true, powerPreference: 'low-power' });
    if (!gl) { setUnsupported(true); return undefined; }

    let programs;
    try {
      programs = { metal: compile(gl, VERTEX, METAL), iris: compile(gl, VERTEX, IRIS), print: compile(gl, VERTEX, PRINT) };
    } catch (error) {
      console.warn('FrameBucket hero scene disabled:', error);
      setUnsupported(true);
      return undefined;
    }

    // Lens: front of the barrel faces +z, the glass sits at z = 0.
    const meshes = {
      body: upload(gl, tube({ outer: 1.0, inner: 0.86, z0: -1.55, z1: -0.62, back: true })),
      focus: upload(gl, tube({ outer: 1.07, inner: 1.0, z0: -0.6, z1: -0.22, segments: 192, back: true })),
      brass: upload(gl, tube({ outer: 1.035, inner: 1.0, z0: -0.2, z1: -0.13, back: true })),
      front: upload(gl, tube({ outer: 1.02, inner: 0.8, z0: -0.13, z1: 0.0, front: false, innerWall: true })),
      engraved: upload(gl, tube({ outer: 1.02, inner: 0.8, z0: -0.13, z1: 0.0, outerWall: false })),
      mount: upload(gl, tube({ outer: 0.9, inner: 0.7, z0: -1.75, z1: -1.55, back: true })),
      iris: upload(gl, quad(1.6, 1.6, 2)),
      print: upload(gl, quad(1, 1)),
    };
    const engraving = texture(gl, engravingTexture());
    const prints = PRINT_SCENES.map((scene, index) => {
      const landscape = index % 3 === 1;
      return {
        tex: texture(gl, printTexture(scene, index, landscape), { mipmap: false }),
        w: landscape ? 0.78 : 0.6,
        h: landscape ? 0.62 : 0.75,
        phase: (index / PRINT_SCENES.length) * TAU,
        roll: (seeded(index + 3)() - 0.5) * 0.22,
        lift: (seeded(index + 11)() - 0.5) * 0.35,
      };
    });

    const reduceMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
    const pointer = { x: 0, y: 0, tx: 0, ty: 0 };
    let width = 0; let height = 0; let frame = 0; let visible = true; let start = 0; let last = 0;

    const resize = () => {
      const rect = canvas.getBoundingClientRect();
      const dpr = Math.min(window.devicePixelRatio || 1, 2);
      width = Math.max(1, Math.round(rect.width * dpr));
      height = Math.max(1, Math.round(rect.height * dpr));
      if (canvas.width !== width || canvas.height !== height) { canvas.width = width; canvas.height = height; }
      if (reduceMotion) draw(performance.now());
    };

    const bind = (program, mesh) => {
      const { aPos, aNormal, aUv } = program.attributes;
      const attribute = (location, buffer, size) => {
        if (location < 0) return;
        gl.bindBuffer(gl.ARRAY_BUFFER, buffer);
        gl.enableVertexAttribArray(location);
        gl.vertexAttribPointer(location, size, gl.FLOAT, false, 0, 0);
      };
      attribute(aPos, mesh.pos, 3); attribute(aNormal, mesh.nor, 3); attribute(aUv, mesh.uv, 2);
      gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, mesh.idx);
    };

    function draw(now) {
      if (!start) start = now;
      const t = reduceMotion ? 10 : (now - start) / 1000;
      const dt = Math.min(0.05, (now - (last || now)) / 1000); last = now;
      const palette = PALETTES[themeRef.current] || PALETTES.dark;
      const aspect = width / height;
      const narrow = aspect < 0.9;

      const damping = 1 - Math.exp(-dt * 4);
      pointer.x += (pointer.tx - pointer.x) * (reduceMotion ? 1 : damping);
      pointer.y += (pointer.ty - pointer.y) * (reduceMotion ? 1 : damping);

      gl.viewport(0, 0, width, height);
      gl.clearColor(0, 0, 0, 0);
      gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT);
      gl.enable(gl.DEPTH_TEST);
      gl.disable(gl.CULL_FACE);

      const distance = narrow ? 7.6 : 6.1;
      const proj = perspective((30 * Math.PI) / 180, aspect, 0.1, 40);
      const view = translation(0, 0, -distance);
      const cam = [0, 0, distance];
      const key = [-0.45, 0.85, 0.75];

      // Load sequence: aperture opens 0.3–1.9 s, prints fly out 0.7–2.6 s.
      const open = reduceMotion ? 1 : easeInOut((t - 0.3) / 1.6);
      const spread = reduceMotion ? 1 : easeOut((t - 0.7) / 1.9);
      const breathe = reduceMotion ? 0 : Math.sin(t * 0.6) * 0.012;

      const lensScale = narrow ? 0.82 : 1;
      const lens = compose(
        translation(narrow ? 0 : 0.25, narrow ? 0.1 : 0.12, 0),
        rotationY(-0.52 + pointer.x * 0.28),
        rotationX(0.16 - pointer.y * 0.2),
        scaling(lensScale),
      );

      const metal = programs.metal;
      gl.useProgram(metal.program);
      gl.uniformMatrix4fv(metal.uniforms.uProj, false, proj);
      gl.uniformMatrix4fv(metal.uniforms.uView, false, view);
      gl.uniform3fv(metal.uniforms.uCam, cam);
      gl.uniform3fv(metal.uniforms.uKey, key);
      gl.uniform3fv(metal.uniforms.uEnvTop, palette.envTop);
      gl.uniform3fv(metal.uniforms.uEnvLow, palette.envLow);
      gl.activeTexture(gl.TEXTURE0);
      gl.bindTexture(gl.TEXTURE_2D, engraving);
      gl.uniform1i(metal.uniforms.uTex, 0);
      const part = (mesh, color, metalness, gloss, ridges = 0, useTex = 0, spin = 0) => {
        const model = spin ? multiply(lens, rotationZ(spin)) : lens;
        gl.uniformMatrix4fv(metal.uniforms.uModel, false, model);
        gl.uniformMatrix3fv(metal.uniforms.uNormalMat, false, normalMatrix(model, lensScale));
        gl.uniform3fv(metal.uniforms.uColor, color);
        gl.uniform1f(metal.uniforms.uMetal, metalness);
        gl.uniform1f(metal.uniforms.uGloss, gloss);
        gl.uniform1f(metal.uniforms.uRidges, ridges);
        gl.uniform1f(metal.uniforms.uUseTex, useTex);
        bind(metal, mesh);
        gl.drawElements(gl.TRIANGLES, mesh.count, gl.UNSIGNED_SHORT, 0);
      };
      part(meshes.mount, [0.55, 0.55, 0.56], 1, 0.75);
      part(meshes.body, palette.body, 0.35, 0.55);
      part(meshes.focus, [0.06, 0.058, 0.055], 0.2, 0.3, 96, 0, open * 0.5);
      part(meshes.brass, palette.brass, 1, 0.85);
      part(meshes.front, [0.07, 0.066, 0.06], 0.4, 0.6);
      part(meshes.engraved, [0.07, 0.066, 0.06], 0.4, 0.6, 0, 1, 2.55);

      const iris = programs.iris;
      gl.useProgram(iris.program);
      const irisModel = multiply(lens, translation(0, 0, -0.1));
      gl.uniformMatrix4fv(iris.uniforms.uProj, false, proj);
      gl.uniformMatrix4fv(iris.uniforms.uView, false, view);
      gl.uniformMatrix4fv(iris.uniforms.uModel, false, irisModel);
      gl.uniformMatrix3fv(iris.uniforms.uNormalMat, false, normalMatrix(irisModel, lensScale));
      gl.uniform1f(iris.uniforms.uOpen, 0.06 + open * 0.5 + breathe);
      gl.uniform1f(iris.uniforms.uTime, t);
      gl.uniform3fv(iris.uniforms.uBrass, palette.brass);
      gl.uniform1f(iris.uniforms.uAa, 3 / Math.max(240, Math.min(width, height)));
      bind(iris, meshes.iris);
      gl.drawElements(gl.TRIANGLES, meshes.iris.count, gl.UNSIGNED_SHORT, 0);

      // Prints orbit the lens on a tilted ring; on load they travel out from the glass.
      const print = programs.print;
      gl.useProgram(print.program);
      gl.uniformMatrix4fv(print.uniforms.uProj, false, proj);
      gl.uniformMatrix4fv(print.uniforms.uView, false, view);
      gl.uniform3fv(print.uniforms.uKey, key);
      gl.uniform3fv(print.uniforms.uCam, cam);
      gl.uniform1f(print.uniforms.uShade, palette.printShade);
      gl.uniform1i(print.uniforms.uTex, 0);
      const origin = transformPoint(lens, 0, 0, 0.1);
      const center = narrow ? 0 : 0.25;
      const orbit = reduceMotion ? 0 : t * 0.045;
      const ringX = narrow ? 1.45 : 1.6; const ringZ = narrow ? 1.15 : 1.3;
      prints.forEach((item, index) => {
        const delay = index * 0.06;
        const s = reduceMotion ? 1 : easeOut((spread - delay) / (1 - delay));
        const angle = item.phase + orbit + pointer.x * 0.15;
        const target = [center + Math.cos(angle) * ringX, Math.sin(angle) * 0.42 + item.lift + 0.05, Math.sin(angle) * ringZ];
        const x = origin[0] + (target[0] - origin[0]) * s;
        const y = origin[1] + (target[1] - origin[1]) * s;
        const z = origin[2] + (target[2] - origin[2]) * s;
        const size = 0.12 + 0.88 * s;
        const model = compose(
          translation(x, y, z),
          rotationY(-Math.cos(angle) * 0.55),
          rotationX(-0.08),
          rotationZ(item.roll + (1 - s) * 1.2),
          scaling(size),
        );
        const sized = multiply(model, compose(identity(), (() => { const m = identity(); m[0] = item.w; m[5] = item.h; return m; })()));
        gl.uniformMatrix4fv(print.uniforms.uModel, false, sized);
        gl.uniformMatrix3fv(print.uniforms.uNormalMat, false, normalMatrix(model, size));
        gl.bindTexture(gl.TEXTURE_2D, item.tex);
        bind(print, meshes.print);
        gl.drawElements(gl.TRIANGLES, meshes.print.count, gl.UNSIGNED_SHORT, 0);
      });
    }

    const loop = (now) => {
      frame = 0;
      if (!visible || document.hidden) return;
      draw(now);
      frame = requestAnimationFrame(loop);
    };
    const resume = () => { if (!reduceMotion && !frame && visible && !document.hidden) { last = performance.now(); frame = requestAnimationFrame(loop); } };

    const onPointer = (event) => {
      const rect = canvas.getBoundingClientRect();
      pointer.tx = Math.max(-1, Math.min(1, (event.clientX - (rect.left + rect.width / 2)) / (window.innerWidth / 2)));
      pointer.ty = Math.max(-1, Math.min(1, (event.clientY - (rect.top + rect.height / 2)) / (window.innerHeight / 2)));
    };
    const observer = new ResizeObserver(resize);
    observer.observe(canvas);
    const visibility = new IntersectionObserver(([entry]) => { visible = entry.isIntersecting; resume(); });
    visibility.observe(canvas);
    document.addEventListener('visibilitychange', resume);
    if (!reduceMotion) window.addEventListener('pointermove', onPointer, { passive: true });
    const redraw = () => draw(performance.now());
    canvas.addEventListener('fb-redraw', redraw);
    resize();
    if (reduceMotion) draw(performance.now()); else resume();

    return () => {
      cancelAnimationFrame(frame);
      observer.disconnect();
      visibility.disconnect();
      document.removeEventListener('visibilitychange', resume);
      window.removeEventListener('pointermove', onPointer);
      canvas.removeEventListener('fb-redraw', redraw);
      // Keep the context: React StrictMode remounts effects and would otherwise get a lost context.
      Object.values(meshes).forEach((mesh) => ['pos', 'nor', 'uv', 'idx'].forEach((key) => gl.deleteBuffer(mesh[key])));
      [engraving, ...prints.map((item) => item.tex)].forEach((t) => gl.deleteTexture(t));
      Object.values(programs).forEach((item) => gl.deleteProgram(item.program));
    };
  }, []);

  useEffect(() => {
    // Re-render a still frame when the theme flips under reduced motion.
    if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) canvasRef.current?.dispatchEvent(new Event('fb-redraw'));
  }, [theme]);

  if (unsupported) {
    return <div className="hero-scene hero-scene-fallback" aria-hidden="true"><span /></div>;
  }
  return <canvas ref={canvasRef} className="hero-scene" aria-hidden="true" />;
}
