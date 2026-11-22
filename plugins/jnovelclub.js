"use strict";
/**
 * J-Novel Club for Fenrir Read (LNReader-style plugin).
 *
 * Uses J-Novel Club's own app API (labs.j-novel.club), the same one the official apps use.
 * Browsing and chapter lists work without an account. Reading a part needs your J-Novel Club
 * login (Settings of this source → Email / Password); you can read what your membership
 * covers, plus the free preview parts.
 *
 * Pictures inside parts are downloaded with your login and placed straight into the chapter,
 * because J-Novel's picture server only answers signed-in requests.
 */
Object.defineProperty(exports, "__esModule", { value: true });
var fetch_1 = require("@libs/fetch");
var storage_1 = require("@libs/storage");
var novelStatus_1 = require("@libs/novelStatus");

var API = "https://labs.j-novel.club/app/v2";
var EMBED = "https://labs.j-novel.club/embed/v2";
var SITE = "https://j-novel.club/";
var PAGE_SIZE = 40;
var TOKEN_KEY = "jnc_token";
var TOKEN_TIME_KEY = "jnc_token_time";
var TOKEN_MAX_AGE_MS = 6 * 24 * 60 * 60 * 1000; // log in again after ~6 days

function store() {
  return storage_1.storage;
}

function jsonHeaders(token) {
  var h = { Accept: "application/json" };
  if (token) h.Authorization = "Bearer " + token;
  return h;
}

function coverOf(obj) {
  var c = obj && obj.cover;
  if (!c) return undefined;
  return c.coverUrl || c.originalUrl || c.thumbnailUrl || undefined;
}

function creatorsOf(series) {
  var names = [];
  var list = (series && series.creators) || [];
  for (var i = 0; i < list.length; i++) {
    var c = list[i];
    if (c && c.name && names.indexOf(c.name) === -1) names.push(c.name);
  }
  return names;
}

function bytesToBase64Fallback(text) {
  try {
    return btoa(text);
  } catch (e) {
    return "";
  }
}

var JNovelClubPlugin = /** @class */ (function () {
  function JNovelClubPlugin() {
    this.id = "jnovelclub";
    this.name = "J-Novel Club";
    this.icon = "src/en/jnovelclub/icon.png";
    this.site = SITE;
    this.version = "1.1.0";
    this.filters = {};
    this.pluginSettings = {
      email: { value: "", label: "Email (J-Novel Club account)", type: "Text" },
      password: { value: "", label: "Password", type: "Text" },
    };
    this._series = null;
    this._seriesTime = 0;
  }

  // ------------------------------------------------------------------ login

  JNovelClubPlugin.prototype.getToken = async function (forceNew) {
    var s = store();
    var token = s.get(TOKEN_KEY);
    var time = parseInt(s.get(TOKEN_TIME_KEY) || "0", 10);
    if (!forceNew && token && Date.now() - time < TOKEN_MAX_AGE_MS) return token;

    var email = (s.get("email") || "").trim();
    var password = s.get("password") || "";
    if (!email || !password) return null;

    var res = await fetch_1.fetchApi(API + "/auth/login?format=json", {
      method: "POST",
      headers: { Accept: "application/json", "Content-Type": "application/json" },
      body: JSON.stringify({ login: email, password: password, slim: true }),
    });
    if (res.status === 401) throw new Error("J-Novel Club: wrong email or password (check this source's settings).");
    if (!res.ok) throw new Error("J-Novel Club login failed (HTTP " + res.status + ").");
    var data = await res.json();
    if (!data || !data.id) throw new Error("J-Novel Club login failed (no session returned).");
    s.set(TOKEN_KEY, data.id);
    s.set(TOKEN_TIME_KEY, String(Date.now()));
    return data.id;
  };

  JNovelClubPlugin.prototype.apiGet = async function (path, token) {
    var url = API + path + (path.indexOf("?") === -1 ? "?" : "&") + "format=json";
    var res = await fetch_1.fetchApi(url, { headers: jsonHeaders(token) });
    if (!res.ok) throw new Error("J-Novel Club: HTTP " + res.status + " for " + path);
    return res.json();
  };

  // ------------------------------------------------------------------ series list

  JNovelClubPlugin.prototype.allSeries = async function () {
    // The whole catalogue is a few hundred series; fetch it once per 30 minutes and
    // page/search through it here.
    if (this._series && Date.now() - this._seriesTime < 30 * 60 * 1000) return this._series;
    var all = [];
    var skip = 0;
    for (var guard = 0; guard < 60; guard++) {
      var data = await this.apiGet("/series?limit=50&skip=" + skip);
      var page = (data && data.series) || [];
      all = all.concat(page);
      skip += page.length;
      if (!page.length || (data.pagination && data.pagination.lastPage)) break;
    }
    // Light novels only: J-Novel Club's manga series are left out.
    this._series = all.filter(function (s) {
      return String(s.type || "NOVEL").toUpperCase().indexOf("MANGA") === -1;
    });
    this._seriesTime = Date.now();
    return this._series;
  };

  JNovelClubPlugin.prototype.toItem = function (s) {
    return { name: s.title, path: "series/" + (s.slug || s.id), cover: coverOf(s) };
  };

  JNovelClubPlugin.prototype.popularNovels = async function (pageNo, options) {
    var list = (await this.allSeries()).slice();
    if (options && options.showLatestNovels) {
      list.sort(function (a, b) {
        return String(b.created || "").localeCompare(String(a.created || ""));
      });
    }
    var start = (pageNo - 1) * PAGE_SIZE;
    return list.slice(start, start + PAGE_SIZE).map(this.toItem);
  };

  JNovelClubPlugin.prototype.searchNovels = async function (searchTerm, pageNo) {
    var term = String(searchTerm || "").toLowerCase().trim();
    var list = (await this.allSeries()).filter(function (s) {
      if (!term) return true;
      var hay = [s.title, s.shortTitle, s.originalTitle, (s.tags || []).join(" ")]
        .concat(creatorsOf(s))
        .join(" ")
        .toLowerCase();
      return hay.indexOf(term) !== -1;
    });
    var start = (pageNo - 1) * PAGE_SIZE;
    return list.slice(start, start + PAGE_SIZE).map(this.toItem);
  };

  // ------------------------------------------------------------------ novel + parts

  JNovelClubPlugin.prototype.parseNovel = async function (novelPath) {
    var slug = novelPath.replace(/^series\//, "").replace(/\/+$/, "");
    var token = null;
    try {
      token = await this.getToken(false);
    } catch (e) {
      token = null; // listing works without login
    }
    var agg = await this.apiGet("/series/" + encodeURIComponent(slug) + "/aggregate", token);
    var series = agg.series || {};

    var novel = {
      path: novelPath,
      name: series.title || slug,
      cover: coverOf(series),
      summary: series.description || series.shortDescription || "",
      author: creatorsOf(series).join(", "),
      genres: (series.tags || []).join(", "),
      status: novelStatus_1.NovelStatus ? novelStatus_1.NovelStatus.Ongoing : "Ongoing",
      chapters: [],
    };

    var n = 0;
    var volumes = agg.volumes || [];
    for (var v = 0; v < volumes.length; v++) {
      var entry = volumes[v];
      var volume = entry.volume || entry;
      var parts = entry.parts || [];
      for (var p = 0; p < parts.length; p++) {
        var part = parts[p];
        n += 1;
        var title = part.title || ((volume.title || "Volume " + (v + 1)) + " Part " + (p + 1));
        if (part.preview) title += " (free preview)";
        novel.chapters.push({
          name: title,
          path: "part/" + (part.id || part.legacyId),
          chapterNumber: n,
          releaseTime: part.launch || part.created || undefined,
        });
      }
    }
    return novel;
  };

  // ------------------------------------------------------------------ reading

  JNovelClubPlugin.prototype.fetchPart = async function (partId, token) {
    var headers = { Accept: "application/xhtml+xml, text/html" };
    if (token) headers.Authorization = "Bearer " + token;
    var urls = [EMBED + "/" + partId + "/data.xhtml", API + "/parts/" + partId + "/data.xhtml"];
    var last = 0;
    for (var i = 0; i < urls.length; i++) {
      var res = await fetch_1.fetchApi(urls[i], { headers: headers });
      last = res.status;
      if (res.ok) return { status: 200, html: await res.text() };
      if (res.status === 401 || res.status === 403 || res.status === 418) return { status: res.status, html: "" };
    }
    return { status: last, html: "" };
  };

  JNovelClubPlugin.prototype.inlinePictures = async function (html, token) {
    // J-Novel's picture server only serves signed-in requests; fetch each picture and put it
    // into the chapter as data so the reader can show it. JPEG is asked for instead of WebP.
    var re = /<img\b[^>]*?\bsrc\s*=\s*["']([^"']+)["']/gi;
    var seen = {};
    var m;
    var urls = [];
    while ((m = re.exec(html)) !== null) {
      if (!seen[m[1]] && /^https?:\/\//i.test(m[1])) {
        seen[m[1]] = true;
        urls.push(m[1]);
      }
    }
    for (var i = 0; i < urls.length; i++) {
      var original = urls[i];
      var jpg = original.replace("/webp/", "/jpg/");
      var headers = { Accept: "image/jpeg,image/*;q=0.8" };
      if (token) headers.Authorization = "Bearer " + token;
      var b64 = "";
      try {
        b64 = await fetch_1.fetchFile(jpg, { headers: headers });
        if (!b64 && jpg !== original) b64 = await fetch_1.fetchFile(original, { headers: headers });
      } catch (e) {
        b64 = "";
      }
      if (b64) {
        var mime = /\.png(\?|$)/i.test(original) ? "image/png" : jpg !== original || /\.jpe?g(\?|$)/i.test(original) ? "image/jpeg" : "image/webp";
        html = html.split(original).join("data:" + mime + ";base64," + b64);
      }
    }
    return html;
  };

  JNovelClubPlugin.prototype.parseChapter = async function (chapterPath) {
    var partId = chapterPath.replace(/^part\//, "");
    var token = await this.getToken(false);
    var result = await this.fetchPart(partId, token);
    if (result.status === 401 && token) {
      // Session ran out: log in again once.
      token = await this.getToken(true);
      result = await this.fetchPart(partId, token);
    }
    if (result.status !== 200) {
      if (!token) {
        throw new Error(
          "J-Novel Club: this part needs your account. Add your email and password in this source's settings (free preview parts work without one).",
        );
      }
      if (result.status === 401 || result.status === 403 || result.status === 418) {
        throw new Error("J-Novel Club: your membership doesn't cover this part yet.");
      }
      throw new Error("J-Novel Club: couldn't load this part (HTTP " + result.status + ").");
    }
    var html = result.html;
    // Keep only the body of the XHTML document.
    var body = html.match(/<body[^>]*>([\s\S]*)<\/body>/i);
    if (body) html = body[1];
    html = html.replace(/^\s*<\?xml[^>]*\?>/, "");
    return this.inlinePictures(html, token);
  };

  JNovelClubPlugin.prototype.resolveUrl = function (path, isNovel) {
    if (path.indexOf("series/") === 0) return SITE + path;
    return SITE;
  };

  return JNovelClubPlugin;
})();

exports.default = new JNovelClubPlugin();
