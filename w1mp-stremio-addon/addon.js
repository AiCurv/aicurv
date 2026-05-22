const { addonBuilder } = require("stremio-addon-sdk");
const cheerio = require("cheerio");
const fetch = require("node-fetch");

const BASE_URL = "https://w1mp.com";
const CDN_STATIC = "https://cdnstatic.w1mp.com";
const HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    "Accept-Language": "en-US,en;q=0.5",
};

// ─── Helpers ────────────────────────────────────────────────────────

async function fetchPage(url) {
    const res = await fetch(url, { headers: HEADERS, timeout: 15000 });
    if (!res.ok) throw new Error(`HTTP ${res.status} for ${url}`);
    return await res.text();
}

function fixUrl(url) {
    if (!url) return "";
    if (url.startsWith("//")) return "https:" + url;
    if (url.startsWith("/")) return BASE_URL + url;
    return url;
}

function parseVideoCard(el) {
    const $ = el;
    // Try multiple link selectors — not all cards have .custom-preview-block-video-wrap
    let linkEl = $.find("a.custom-preview-block-video-wrap").first();
    if (!linkEl.length) {
        linkEl = $.find("a[href*='/video/']").first();
    }
    if (!linkEl.length) return null;

    const href = linkEl.attr("href") || "";
    const videoMatch = href.match(/\/video\/(\d+)\//);
    if (!videoMatch) return null;
    const videoId = videoMatch[1];

    const title = $.find(".card-meta .title").first().text().trim();
    if (!title) return null;

    const img = $.find(".card-img img").first();
    const poster = fixUrl(
        img.attr("data-webp") || img.attr("src") || ""
    );

    const badgeEl = $.find(".badges .badge").first();
    const badgeText = badgeEl.text().trim();
    const isHD = badgeEl.find(".hd-badge").length > 0;
    const durationMatch = badgeText.match(/(\d+:\d+)/);
    const duration = durationMatch ? durationMatch[1] : "";

    const preview = fixUrl(img.attr("data-preview") || "");

    const modelEl = $.find(".item-tool.model a").first();
    const modelName = modelEl.text().trim();
    const modelHref = modelEl.attr("href") || "";
    const modelSlugMatch = modelHref.match(/\/models\/([^/]+)\/?/);
    const modelSlug = modelSlugMatch ? modelSlugMatch[1] : "";

    const viewsEl = $.find(".info-item .item-tool").last();
    const views = viewsEl.text().trim();

    return {
        videoId,
        title,
        poster,
        duration,
        isHD,
        preview,
        modelName,
        modelSlug,
        views,
        href: fixUrl(href),
    };
}

function extractVideoCards(html) {
    const $ = cheerio.load(html);
    const videos = [];
    $(".card.thumb_rel.item, .thumbs .card.item").each((_, el) => {
        const card = parseVideoCard($(el));
        if (card) videos.push(card);
    });
    return videos;
}

function extractModelCards(html) {
    const $ = cheerio.load(html);
    const models = [];
    $(".thumbs.models-thumbs .card.item").each((_, el) => {
        const $el = $(el);
        const linkEl = $el.find("a").first();
        const href = linkEl.attr("href") || "";
        const slugMatch = href.match(/\/models\/([^/]+)\/?/);
        if (!slugMatch) return;
        const slug = slugMatch[1];
        const name = $el.find(".title").first().text().trim();
        const img = $el.find(".card-img img").first();
        const poster = fixUrl(img.attr("src") || "");
        const videoCount = $el.find(".info-item .item-tool").first().text().trim();
        const rating = $el.find(".info-item .item-tool").eq(1).text().trim();
        models.push({ slug, name, poster, videoCount, rating });
    });
    return models;
}

// ─── Manifest ───────────────────────────────────────────────────────

const manifest = {
    id: "community.w1mp",
    version: "1.0.0",
    name: "W1MP",
    description: "Browse models, categories, and videos from w1mp.com",
    logo: "https://www.google.com/s2/favicons?domain=w1mp.com&sz=256",
    background: "https://cdnstatic.w1mp.com/static/images/logo.png",
    resources: [
        "catalog",
        { name: "meta", types: ["channel", "movie"], idPrefixes: ["model_", "video_"] },
        { name: "stream", types: ["channel", "movie"], idPrefixes: ["video_"] },
    ],
    types: ["channel", "movie"],
    catalogs: [
        // Models catalog — searchable so models appear in Stremio search
        {
            type: "channel",
            id: "models",
            name: "Models",
            extra: [
                { name: "search", isRequired: false },
                { name: "skip", isRequired: false },
            ],
        },
        // Categories catalog — browse by category
        {
            type: "movie",
            id: "categories",
            name: "Categories",
            extra: [
                { name: "genre", isRequired: false, options: [
                    "Amateur", "Anal", "Arab", "Asian", "Babe", "BBW",
                    "Behind The Scenes", "Big Ass", "Big Dick", "Big Tits",
                    "Blonde", "Blowjob", "Bondage", "Brazilian", "British",
                    "Brunette", "Bukkake", "Casting", "Compilation", "Cosplay",
                    "Creampie", "Cuckold", "Cumshot", "Czech", "Double Penetration",
                    "Ebony", "Euro", "Facial", "Feet", "Female Orgasm", "Fetish",
                    "Fisting", "French", "Gangbang", "German", "Hairy", "Handjob",
                    "Hardcore", "Hentai", "Interracial", "Italian", "Japanese",
                    "Latina", "Lesbian", "Massage", "Masturbation", "Mature",
                    "MILF", "Old Young", "Orgy", "POV", "Public", "Reality",
                    "Red Head", "Role Play", "Romantic", "Rough Sex", "Russian",
                    "School", "Solo Female", "Squirt", "Step Fantasy", "Strap On",
                    "Striptease", "Teen", "Threesome", "Toys", "Vintage"
                ] },
                { name: "skip", isRequired: false },
            ],
        },
        // Latest videos catalog
        {
            type: "movie",
            id: "latest",
            name: "Latest Videos",
            extra: [{ name: "skip", isRequired: false }],
        },
        // Top rated
        {
            type: "movie",
            id: "top_rated",
            name: "Top Rated",
            extra: [{ name: "skip", isRequired: false }],
        },
        // Most popular
        {
            type: "movie",
            id: "most_popular",
            name: "Most Popular",
            extra: [{ name: "skip", isRequired: false }],
        },
    ],
    idPrefixes: ["model_", "video_"],
    behaviorHints: {
        adult: true,
        p2p: false,
        configurable: false,
        configurationRequired: false,
    },
};

const builder = new addonBuilder(manifest);

// ─── Category slug mapping ──────────────────────────────────────────

const CATEGORY_SLUG_MAP = {
    "amateur": "amateur", "anal": "anal", "arab": "arab", "asian": "asian",
    "babe": "babe", "bbw": "bbw", "behind the scenes": "behind-the-scenes",
    "big ass": "big-ass", "big dick": "big-dick", "big tits": "big-tits",
    "blonde": "blonde", "blowjob": "blowjob", "bondage": "bondage",
    "brazilian": "brazilian", "british": "british", "brunette": "brunette",
    "bukkake": "bukkake", "casting": "casting", "compilation": "compilation",
    "cosplay": "cosplay", "creampie": "creampie", "cuckold": "cuckold",
    "cumshot": "cumshot", "czech": "czech", "double penetration": "double-penetration",
    "ebony": "ebony", "euro": "euro", "facial": "facial", "feet": "feet",
    "female orgasm": "female-orgasm", "fetish": "fetish", "fisting": "fisting",
    "french": "french", "gangbang": "gangbang", "german": "german",
    "hairy": "hairy", "handjob": "handjob", "hardcore": "hardcore",
    "hentai": "hentai", "interracial": "interracial", "italian": "italian",
    "japanese": "japanese", "latina": "latina", "lesbian": "lesbian",
    "massage": "massage", "masturbation": "masturbation", "mature": "mature",
    "milf": "milf", "old young": "old-young-18", "orgy": "orgy",
    "pov": "pov", "public": "public", "reality": "reality",
    "red head": "red-head", "role play": "role-play", "romantic": "romantic",
    "rough sex": "rough-sex", "russian": "russian", "school": "school-18",
    "solo female": "solo-female", "squirt": "squirt",
    "step fantasy": "step-fantasy", "strap on": "strap-on",
    "striptease": "striptease", "teen": "teen-18", "threesome": "threesome",
    "toys": "toys", "vintage": "vintage",
};

// ─── CATALOG HANDLER ────────────────────────────────────────────────

builder.defineCatalogHandler(async (args) => {
    const skip = parseInt(args.extra?.skip || "0");
    const page = Math.floor(skip / 36) + 1; // ~36 items per page

    try {
        // ── Models catalog (searchable) ──
        if (args.id === "models" && args.type === "channel") {
            if (args.extra?.search) {
                // Search for models
                const query = args.extra.search;
                const searchUrl = `${BASE_URL}/search/?q=${encodeURIComponent(query)}`;
                const html = await fetchPage(searchUrl);
                const videos = extractVideoCards(html);

                // Extract unique models from search results
                const modelMap = {};
                for (const v of videos) {
                    if (v.modelSlug && !modelMap[v.modelSlug]) {
                        modelMap[v.modelSlug] = v.modelName;
                    }
                }

                // Also try fetching models page and filter
                const modelsHtml = await fetchPage(`${BASE_URL}/models/`);
                const allModels = extractModelCards(modelsHtml);
                const queryLower = query.toLowerCase();
                const matchingModels = allModels.filter(m =>
                    m.name.toLowerCase().includes(queryLower)
                );

                // Merge: prioritize direct model matches, then extracted from videos
                for (const m of matchingModels) {
                    if (!modelMap[m.slug]) {
                        modelMap[m.slug] = m.name;
                    }
                }

                // Also fetch more model pages for better search
                for (let p = 2; p <= 5; p++) {
                    try {
                        const pHtml = await fetchPage(`${BASE_URL}/models/${p}/`);
                        const pModels = extractModelCards(pHtml);
                        for (const m of pModels) {
                            if (m.name.toLowerCase().includes(queryLower) && !modelMap[m.slug]) {
                                modelMap[m.slug] = m.name;
                            }
                        }
                    } catch (e) { break; }
                }

                const metas = Object.entries(modelMap).map(([slug, name]) => ({
                    id: `model_${slug}`,
                    type: "channel",
                    name: name,
                    poster: `https://cdnstatic.w1mp.com/contents/models/0/${slug}.jpg`,
                    posterShape: "poster",
                    description: `${name} - w1mp.com model`,
                }));

                return { metas };
            } else {
                // Browse models (no search)
                const modelsUrl = page > 1
                    ? `${BASE_URL}/models/${page}/`
                    : `${BASE_URL}/models/`;
                const html = await fetchPage(modelsUrl);
                const models = extractModelCards(html);

                const metas = models.map(m => ({
                    id: `model_${m.slug}`,
                    type: "channel",
                    name: m.name,
                    poster: m.poster || `https://cdnstatic.w1mp.com/contents/models/0/${m.slug}.jpg`,
                    posterShape: "poster",
                    description: `${m.videoCount} videos | Rating: ${m.rating}`,
                }));

                return { metas };
            }
        }

        // ── Categories catalog ──
        if (args.id === "categories" && args.type === "movie") {
            let categorySlug = "";
            if (args.extra?.genre) {
                const genreKey = args.extra.genre.toLowerCase();
                categorySlug = CATEGORY_SLUG_MAP[genreKey] || genreKey;
            }

            if (categorySlug) {
                // Browse a specific category
                const catUrl = page > 1
                    ? `${BASE_URL}/categories/${categorySlug}/${page}/`
                    : `${BASE_URL}/categories/${categorySlug}/`;
                const html = await fetchPage(catUrl);
                const videos = extractVideoCards(html);
                const metas = videos.map(v => videoToMetaPreview(v));
                return { metas };
            } else {
                // Show category cards as meta previews
                const catHtml = await fetchPage(`${BASE_URL}/categories/`);
                const $ = cheerio.load(catHtml);
                const metas = [];
                $(".categories-thumbs .card.item").each((_, el) => {
                    const $el = $(el);
                    const linkEl = $el.find("a").first();
                    const href = linkEl.attr("href") || "";
                    const slugMatch = href.match(/\/categories\/([^/]+)\/?/);
                    if (!slugMatch) return;
                    const slug = slugMatch[1];
                    const name = $el.find(".cat-title").text().trim() || slug;
                    const img = $el.find("img").first();
                    const poster = fixUrl(img.attr("src") || "");
                    metas.push({
                        id: `category_${slug}`,
                        type: "movie",
                        name: name,
                        poster: poster,
                        posterShape: "landscape",
                        description: `Browse ${name} videos`,
                    });
                });
                return { metas };
            }
        }

        // ── Latest videos catalog ──
        if (args.id === "latest" && args.type === "movie") {
            const url = page > 1
                ? `${BASE_URL}/latest-updates/${page}/`
                : `${BASE_URL}/latest-updates/`;
            const html = await fetchPage(url);
            const videos = extractVideoCards(html);
            return { metas: videos.map(v => videoToMetaPreview(v)) };
        }

        // ── Top rated catalog ──
        if (args.id === "top_rated" && args.type === "movie") {
            const url = page > 1
                ? `${BASE_URL}/top-rated/${page}/`
                : `${BASE_URL}/top-rated/`;
            const html = await fetchPage(url);
            const videos = extractVideoCards(html);
            return { metas: videos.map(v => videoToMetaPreview(v)) };
        }

        // ── Most popular catalog ──
        if (args.id === "most_popular" && args.type === "movie") {
            const url = page > 1
                ? `${BASE_URL}/most-popular/${page}/`
                : `${BASE_URL}/most-popular/`;
            const html = await fetchPage(url);
            const videos = extractVideoCards(html);
            return { metas: videos.map(v => videoToMetaPreview(v)) };
        }

    } catch (err) {
        console.error("Catalog error:", err.message);
    }

    return { metas: [] };
});

// ─── META HANDLER ───────────────────────────────────────────────────

builder.defineMetaHandler(async (args) => {
    const { id, type } = args;

    try {
        // ── Model meta (channel type) ──
        if (type === "channel" && id.startsWith("model_")) {
            const slug = id.replace("model_", "");
            const modelUrl = `${BASE_URL}/models/${slug}/`;
            const html = await fetchPage(modelUrl);
            const $ = cheerio.load(html);

            const name = $(".viewlist-headline .title").first().text().trim() || slug;
            const desc = $(".viewlist-description").first().text().trim();
            const stats = $(".viewlist-headline .statistic-list .item").map((_, el) => $(el).text().trim()).get();
            const videoCount = stats[0] || "";
            const rating = stats[1] || "";

            // Extract videos on this model's page
            const videos = [];
            $(".card.thumb_rel.item, .thumbs .card.item").each((_, el) => {
                const card = parseVideoCard($(el));
                if (card) {
                    const dirPrefix = Math.floor(parseInt(card.videoId) / 1000) * 1000;
                    videos.push({
                        id: `video_${card.videoId}`,
                        title: card.title,
                        released: new Date().toISOString(),
                        thumbnail: card.poster || `${CDN_STATIC}/contents/videos_screenshots/${dirPrefix}/${card.videoId}/672x378/1.jpg`,
                        overview: card.duration ? `Duration: ${card.duration}${card.isHD ? " | HD" : ""}${card.views ? " | " + card.views : ""}` : "",
                    });
                }
            });

            // We'll also try to load more pages for the model
            for (let p = 2; p <= 3; p++) {
                try {
                    const pHtml = await fetchPage(`${BASE_URL}/models/${slug}/${p}/`);
                    const p$ = cheerio.load(pHtml);
                    const pCards = p$(".card.thumb_rel.item, .thumbs .card.item");
                    if (!pCards.length) break;
                    pCards.each((_, el) => {
                        const card = parseVideoCard(p$(el));
                        if (card) {
                            const dirPrefix = Math.floor(parseInt(card.videoId) / 1000) * 1000;
                            videos.push({
                                id: `video_${card.videoId}`,
                                title: card.title,
                                released: new Date().toISOString(),
                                thumbnail: card.poster || `${CDN_STATIC}/contents/videos_screenshots/${dirPrefix}/${card.videoId}/672x378/1.jpg`,
                                overview: card.duration ? `Duration: ${card.duration}${card.isHD ? " | HD" : ""}${card.views ? " | " + card.views : ""}` : "",
                            });
                        }
                    });
                } catch (e) { break; }
            }

            const modelPoster = `${CDN_STATIC}/contents/models/0/${slug}.jpg`;

            const meta = {
                id: id,
                type: "channel",
                name: name,
                poster: modelPoster,
                posterShape: "poster",
                background: modelPoster,
                description: desc || `${name}${videoCount ? " - " + videoCount : ""}${rating ? " | Rating: " + rating : ""}`,
                releaseInfo: "",
                genres: ["Model"],
                videos: videos,
                behaviorHints: {
                    defaultVideoId: videos.length > 0 ? videos[0].id : undefined,
                },
            };

            return { meta };
        }

        // ── Video meta (movie type) ──
        if (type === "movie" && id.startsWith("video_")) {
            const videoId = id.replace("video_", "");
            // Use embed URL — /video/{id}/ without slug returns 404
            const videoUrl = `${BASE_URL}/embed/${videoId}/`;
            const html = await fetchPage(videoUrl);
            const $ = cheerio.load(html);

            const title = $("meta[property='og:title']").attr("content") || $("title").first().text().trim() || `Video ${videoId}`;
            const poster = $("video").attr("poster") || "";
            const description = $("meta[property='og:description']").attr("content") || "";

            // Extract tags/categories and models from embed page
            const genres = [];
            const cast = [];
            $("a[href*='/models/']").each((_, el) => {
                cast.push({ name: $(el).text().trim() });
            });

            const meta = {
                id: id,
                type: "movie",
                name: title,
                poster: fixUrl(poster),
                posterShape: "landscape",
                background: fixUrl(poster),
                description: description || title,
                releaseInfo: "",
                genres: genres,
                cast: cast,
                behaviorHints: {},
            };

            return { meta };
        }

        // ── Category placeholder meta ──
        if (type === "movie" && id.startsWith("category_")) {
            const slug = id.replace("category_", "");
            const catUrl = `${BASE_URL}/categories/${slug}/`;
            const html = await fetchPage(catUrl);
            const videos = extractVideoCards(html);

            const meta = {
                id: id,
                type: "movie",
                name: slug.replace(/-/g, " ").replace(/\b\w/g, c => c.toUpperCase()),
                poster: "",
                posterShape: "landscape",
                description: `Browse ${slug} videos`,
                genres: [slug],
                // Return videos as meta previews within the meta
                videos: videos.slice(0, 100).map(v => ({
                    id: `video_${v.videoId}`,
                    title: v.title,
                    released: new Date().toISOString(),
                    thumbnail: v.poster,
                })),
                behaviorHints: {
                    defaultVideoId: videos.length > 0 ? `video_${videos[0].videoId}` : undefined,
                },
            };

            return { meta };
        }

    } catch (err) {
        console.error("Meta error:", err.message);
    }

    return { meta: {} };
});

// ─── STREAM HANDLER ─────────────────────────────────────────────────

builder.defineStreamHandler(async (args) => {
    const { id } = args;

    try {
        if (id.startsWith("video_")) {
            const videoId = id.replace("video_", "");
            const streams = [];

            // Use embed URL — /video/{id}/ without slug returns 404
            // Embed page also has <video><source> with the MP4 URL
            const embedUrl = `${BASE_URL}/embed/${videoId}/`;
            const html = await fetchPage(embedUrl);
            const $ = cheerio.load(html);

            // Extract MP4 from <video><source> tag
            $("video source").each((_, el) => {
                const src = $(el).attr("src") || "";
                if (src) {
                    const fullUrl = fixUrl(src);
                    streams.push({
                        name: "W1MP",
                        title: "Direct MP4",
                        url: fullUrl,
                        behaviorHints: {
                            notWebReady: false,
                        },
                    });
                }
            });

            return { streams };
        }
    } catch (err) {
        console.error("Stream error:", err.message);
    }

    return { streams: [] };
});

// ─── Helper: video card to meta preview ──────────────────────────────

function videoToMetaPreview(v) {
    const dirPrefix = Math.floor(parseInt(v.videoId) / 1000) * 1000;
    const poster = v.poster || `${CDN_STATIC}/contents/videos_screenshots/${dirPrefix}/${v.videoId}/672x378/1.jpg`;
    return {
        id: `video_${v.videoId}`,
        type: "movie",
        name: v.title,
        poster: poster,
        posterShape: "landscape",
        description: `${v.duration}${v.isHD ? " HD" : ""}${v.modelName ? " | " + v.modelName : ""}${v.views ? " | " + v.views : ""}`,
        releaseInfo: "",
    };
}

module.exports = builder.getInterface();
