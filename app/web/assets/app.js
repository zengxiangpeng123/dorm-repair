/* ==========================================================================
   宿舍维修系统 · 前端公共层
   —— 只做四件事：拿 token、调接口、把时间变成人话、把状态变成颜色。
   不引任何框架，不引任何 CDN（答辩断网也要能跑）。
   ========================================================================== */
(function (global) {
  'use strict';

  const API = '/api/v1';
  const KEY = 'hx.session.';

  /* ---------------- 本地会话 ---------------- */

  function getSession(role) {
    try { return JSON.parse(localStorage.getItem(KEY + role) || 'null'); } catch (e) { return null; }
  }
  function setSession(role, s) { localStorage.setItem(KEY + role, JSON.stringify(s)); }
  function clearSession(role) { localStorage.removeItem(KEY + role); }

  /* ---------------- 调接口 ---------------- */

  class ApiError extends Error {
    constructor(status, err) {
      super((err && err.message) || ('HTTP ' + status));
      this.status = status;
      this.code = err && err.code;
      this.detail = err || {};
    }
  }

  async function request(method, path, { role, body, token } = {}) {
    const headers = {};
    const s = token ? { token } : getSession(role);
    if (s && s.token) headers.Authorization = 'Bearer ' + s.token;
    if (body !== undefined) headers['Content-Type'] = 'application/json';

    const res = await fetch(API + path, {
      method, headers, body: body === undefined ? undefined : JSON.stringify(body)
    });
    let json = null;
    try { json = await res.json(); } catch (e) { /* 空响应 */ }

    if (!res.ok || !json || json.ok === false) {
      throw new ApiError(res.status, (json && json.error) || { message: '服务没有响应' });
    }
    return json.data;
  }

  const api = {
    get: (path, o) => request('GET', path, o || {}),
    post: (path, body, o) => request('POST', path, Object.assign({ body: body || {} }, o || {})),
    put: (path, body, o) => request('PUT', path, Object.assign({ body: body || {} }, o || {}))
  };

  /* ---------------- 时间 ---------------- */

  const MIN = 60 * 1000, HOUR = 60 * MIN, DAY = 24 * HOUR;

  function parse(str) {
    if (!str) return null;
    const m = /^(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2})(?::(\d{2}))?/.exec(str);
    if (!m) return null;
    return Date.UTC(+m[1], +m[2] - 1, +m[3], +m[4] - 8, +m[5], +(m[6] || 0));
  }

  /** 247 分钟 → "4 小时 7 分" */
  function human(ms) {
    if (ms == null) return '—';
    if (ms <= 0) return '已超时';
    const d = Math.floor(ms / DAY), h = Math.floor((ms % DAY) / HOUR), m = Math.round((ms % HOUR) / MIN);
    if (d > 0) return `${d} 天 ${h} 小时`;
    if (h > 0) return m ? `${h} 小时 ${m} 分` : `${h} 小时`;
    return `${Math.max(1, m)} 分钟`;
  }

  /** 紧凑：6d14h / 14h46m / 8m */
  function short(ms) {
    if (ms == null) return '—';
    if (ms <= 0) return '超时';
    const d = Math.floor(ms / DAY), h = Math.floor((ms % DAY) / HOUR), m = Math.floor((ms % HOUR) / MIN);
    if (d > 0) return `${d}d${h}h`;
    if (h > 0) return `${h}h${m}m`;
    return `${m}m`;
  }

  /** "2026-11-17 20:37:51" → "11-17 20:37" */
  function when(str) {
    const t = parse(str);
    if (!t) return '—';
    const d = new Date(t + 8 * HOUR);
    const p = n => String(n).padStart(2, '0');
    return `${p(d.getUTCMonth() + 1)}-${p(d.getUTCDate())} ${p(d.getUTCHours())}:${p(d.getUTCMinutes())}`;
  }

  function clock(ms) {
    const d = new Date(ms + 8 * HOUR);
    const p = n => String(n).padStart(2, '0');
    return `${p(d.getUTCMonth() + 1)}-${p(d.getUTCDate())} ${p(d.getUTCHours())}:${p(d.getUTCMinutes())}`;
  }

  /* ---------------- 状态与级别 ---------------- */

  const STATUS_ORDER = ['PENDING', 'ASSIGNED', 'REPAIRING', 'VERIFYING', 'DONE'];
  const STEP_LABEL = { PENDING: '已提交', ASSIGNED: '已派工', REPAIRING: '上门维修', VERIFYING: '等你验收', DONE: '已完成' };
  const STATUS_TEXT = {
    PENDING: '待派工', ASSIGNED: '已派工', REPAIRING: '维修中', PARTS: '待配件',
    VERIFYING: '待验收', REWORKING: '返修中', DONE: '已完成', CLOSED: '已关闭', CANCELLED: '已取消'
  };

  const esc = s => String(s == null ? '' : s)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');

  const statusPill = st => `<span class="pill" data-st="${esc(st)}">${esc(STATUS_TEXT[st] || st)}</span>`;
  const levelPill = lv => `<span class="pill lv" data-lv="${esc(lv)}">${esc(lv)}</span>`;

  /** 五步进度条：返修中/已取消这类不在主干上的，单独显示，不硬塞 */
  function steps(o) {
    if (o.status === 'CANCELLED') return `<div class="note" data-tone="warn"><span class="i">×</span><div>这单已取消，不用再等师傅。</div></div>`;
    if (o.status === 'REWORKING') {
      return `<div class="note" data-tone="warn"><span class="i">↻</span><div>验收没过，已生成返修单 <b>${esc(o.reworkNo || '')}</b>，师傅会重新上门。</div></div>`;
    }
    if (o.status === 'CLOSED') {
      return `<div class="steps">` + STATUS_ORDER.map((s, i) =>
        `<div class="s done"><div class="dot"></div><div class="lb">${STEP_LABEL[s]}</div></div>`).join('') + `</div>`;
    }
    const idx = o.status === 'PARTS' ? 2 : STATUS_ORDER.indexOf(o.status);
    return `<div class="steps">` + STATUS_ORDER.map((s, i) => {
      const cls = i < idx ? 'done' : (i === idx ? 'now' : '');
      return `<div class="s ${cls}"><div class="dot"></div><div class="lb">${STEP_LABEL[s]}</div></div>`;
    }).join('') + `</div>`;
  }

  /* ---------------- DOM 小工具 ---------------- */

  const $ = (sel, root) => (root || document).querySelector(sel);
  const $$ = (sel, root) => Array.prototype.slice.call((root || document).querySelectorAll(sel));
  const h = html => { const t = document.createElement('template'); t.innerHTML = html.trim(); return t.content.firstElementChild; };

  function toast(msg, tone) {
    let el = $('.toast');
    if (!el) { el = h('<div class="toast"></div>'); document.body.appendChild(el); }
    el.textContent = msg;
    el.setAttribute('data-tone', tone || '');
    requestAnimationFrame(() => el.classList.add('on'));
    clearTimeout(el._t);
    el._t = setTimeout(() => el.classList.remove('on'), tone === 'bad' ? 4200 : 2600);
  }

  /** 把接口错误翻译成人话；409 额外显示"允许哪几步" */
  function explainErr(e) {
    if (!(e instanceof ApiError)) return String(e && e.message || e);
    const d = e.detail || {};
    let s = d.message || e.message;
    if (e.status === 409 && Array.isArray(d.allowed)) {
      s += d.allowed.length
        ? `｜这单现在只能做：${d.allowed.map(x => EVENT_CN[x] || x).join('、')}`
        : '｜这单现在什么也做不了（终态）';
    }
    if (e.status === 501) s += '｜等变更单 #001';
    return s;
  }

  const EVENT_CN = {
    SUBMIT: '提交', ASSIGN: '派工', ARRIVE: '到门打卡', NO_SHOW: '到访未遇', FINISH: '完工',
    NEED_PARTS: '报缺件', PARTS_ARRIVED: '配件到货', ACCEPT: '验收通过', REJECT: '验收不通过',
    AUTO_ACCEPT: '超时自动验收', ARCHIVE: '归档', CANCEL: '撤销'
  };

  /* ---------------- 底部弹层 ---------------- */

  function sheet(opt) {
    const mask = h(`<div class="mask" hidden><div class="sheet"><div class="grab"></div>
      <h3>${esc(opt.title || '')}</h3>
      ${opt.sub ? `<div class="sd">${esc(opt.sub)}</div>` : ''}
      <div class="sheet-body"></div>
    </div></div>`);
    const body = $('.sheet-body', mask);
    if (typeof opt.body === 'string') body.innerHTML = opt.body; else if (opt.body) body.appendChild(opt.body);
    document.body.appendChild(mask);
    requestAnimationFrame(() => { mask.hidden = false; });
    mask.addEventListener('click', ev => { if (ev.target === mask) close(); });
    function close() { mask.hidden = true; setTimeout(() => mask.remove(), 160); }
    return { el: mask, body, close };
  }

  /* ---------------- 身份 ---------------- */

  /** 没登录时弹一个选身份的层，选完把 token 存起来并重新加载 */
  function pickIdentity(role, meta, title) {
    return new Promise(resolve => {
      const list = (meta.accounts || []).filter(a => a.role === role);
      const label = { student: '同学', worker: '师傅', admin: '管理员' }[role] || role;
      const s = sheet({
        title: title || '选一个' + label + '进去',
        sub: '演示用，点一下就登录，不需要密码。',
        body: `<div class="rows" style="border:1px solid var(--line);border-radius:12px;overflow:hidden">` +
          list.map(a => `<button class="row" data-id="${esc(a.id)}" style="border:0;background:#fff;width:100%;text-align:left;font:inherit;cursor:pointer">
            <div class="grow"><b>${esc(a.name)}</b>
              <div class="sk">${esc(a.id)}${a.building ? ' · ' + esc(a.building) + (a.room ? ' ' + esc(a.room) : '') : ''}${a.team ? ' · ' + esc(a.team) : ''}</div>
            </div><span style="color:var(--brand)">进入 ›</span>
          </button>`).join('') + `</div>`
      });
      s.el.addEventListener('click', async ev => {
        const b = ev.target.closest('[data-id]');
        if (!b) return;
        const id = b.getAttribute('data-id');
        b.disabled = true;
        try {
          const r = await api.post('/login', { role, id });
          setSession(role, { token: r.token, user: r.user });
          s.close();
          resolve(r.user);
        } catch (e) {
          toast(explainErr(e), 'bad');
          b.disabled = false;
        }
      });
    });
  }

  /** 每个页面开头都调它：拿 meta、确保有身份、返回 {meta, user} */
  async function start(role, opt) {
    const meta = await api.get('/meta');
    // 演示便利：?as=账号 直接登录（截图、答辩切账号都用它）
    const want = new URLSearchParams(location.search).get('as');
    if (want && (meta.accounts || []).some(a => a.id === want && a.role === role)) {
      const r = await api.post('/login', { role, id: want });
      setSession(role, { token: r.token, user: r.user });
      return { meta, user: r.user, fresh: true };
    }
    let s = getSession(role);
    if (!s) {
      const user = await pickIdentity(role, meta, opt && opt.pickTitle);
      s = getSession(role);
      return { meta, user, fresh: true };
    }
    return { meta, user: s.user, fresh: false };
  }

  function logout(role) { clearSession(role); location.reload(); }

  /** 顶部右上角只放一个"换人"，名字在左边标题里已经有了 */
  function whoBar(role, user) {
    return `<span class="who"><button data-act="logout">换人</button></span>`;
  }

  function bindWho(root, role) {
    const b = $('[data-act="logout"]', root);
    if (b) b.addEventListener('click', () => logout(role));
  }

  /* ---------------- 导航 ---------------- */

  const ICON = {
    home: '<path d="M3 10.5 12 3l9 7.5"/><path d="M5.5 9.5V21h13V9.5"/>',
    list: '<path d="M8 6h13M8 12h13M8 18h13"/><circle cx="3.5" cy="6" r="1"/><circle cx="3.5" cy="12" r="1"/><circle cx="3.5" cy="18" r="1"/>',
    plus: '<path d="M12 5v14M5 12h14"/>',
    user: '<circle cx="12" cy="8" r="3.5"/><path d="M4.5 20c1.4-3.6 4.2-5.5 7.5-5.5s6.1 1.9 7.5 5.5"/>',
    building: '<path d="M4 21V6l8-3 8 3v15"/><path d="M9 21v-5h6v5M8 9h.01M12 9h.01M16 9h.01M8 13h.01M12 13h.01M16 13h.01"/>',
    wrench: '<path d="M15 4a5 5 0 0 0-4.6 7L4 17.4V20h2.6l6.4-6.4A5 5 0 1 0 15 4z"/>',
    box: '<path d="M3 8l9-4 9 4-9 4-9-4z"/><path d="M3 8v8l9 4 9-4V8"/>'
  };

  function nav(role, active, cta) {
    const items = role === 'student'
      ? [['index.html', 'home', '首页'], ['list.html', 'list', '我的报修'],
         ['new.html', 'plus', '报修', 'cta'], ['../lab.html', 'user', '说明']]
      : [['index.html', 'home', '工作台'], ['building.html', 'building', '本楼'],
         ['parts.html', 'box', '待配件'], ['../lab.html', 'user', '说明']];

    const html = items.map(([href, icon, label, kind]) => {
      const on = href.split('/').pop() === active ? ' on' : '';
      if (kind === 'cta') {
        return `<a href="${href}" class="${on}" style="justify-content:center">
          <span style="width:42px;height:42px;margin-top:-14px;border-radius:50%;background:var(--brand);display:grid;place-items:center;box-shadow:0 4px 12px rgba(29,78,216,.34)">
            <svg viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2.2" stroke-linecap="round">${ICON[icon]}</svg>
          </span><span style="margin-top:2px">${label}</span></a>`;
      }
      return `<a href="${href}" class="${on}">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round">${ICON[icon]}</svg>
        <span>${label}</span></a>`;
    }).join('');

    const el = h(`<nav class="nav">${html}</nav>`);
    document.body.appendChild(el);
    return el;
  }

  /** 每秒刷一次倒计时（页面上所有 [data-tick] 元素自己更新） */
  function tickLoop(fn) {
    let last = 0;
    const run = () => { fn(); };
    run();
    return setInterval(run, 1000);
  }

  // 截图模式（?shot=1）：把底部导航放回文档流末尾，否则整页截图里看不到它
  if (new URLSearchParams(location.search).get('shot') && document.body) {
    document.body.classList.add('shot');
  }

  global.App = {
    API, api, ApiError,
    getSession, setSession, clearSession, logout,
    parse, human, short, when, clock, MIN, HOUR, DAY,
    esc, statusPill, levelPill, steps, STATUS_TEXT, STATUS_ORDER, STEP_LABEL,
    $, $$, h, toast, explainErr, EVENT_CN, sheet, nav, whoBar, bindWho, start, pickIdentity, tickLoop
  };
})(window);
